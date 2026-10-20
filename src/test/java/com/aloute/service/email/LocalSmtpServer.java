package com.aloute.service.email;

import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
public class LocalSmtpServer implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final int port;
    private final Thread acceptThread;
    private volatile boolean running = true;
    private final List<String> receivedMessages = new CopyOnWriteArrayList<>();
    private final CountDownLatch messageReceivedLatch = new CountDownLatch(1);
    private SSLContext sslContext;
    private volatile boolean authenticated = false;
    private volatile boolean simulateFailure = false;

    public void setSimulateFailure(boolean simulateFailure) {
        this.simulateFailure = simulateFailure;
    }

    public void clearMessages() {
        receivedMessages.clear();
    }

    public boolean awaitMessageCount(int minCount, long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (System.currentTimeMillis() < deadline) {
            if (receivedMessages.size() >= minCount) {
                return true;
            }
            Thread.sleep(50);
        }
        return receivedMessages.size() >= minCount;
    }

    public LocalSmtpServer() throws Exception {
        this("/test-smtp-keystore.p12");
    }

    public LocalSmtpServer(String keystoreResource) throws Exception {
        this.serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        this.port = serverSocket.getLocalPort();
        initSsl(keystoreResource);

        this.acceptThread = new Thread(this::runServer, "local-smtp-server-" + port);
        this.acceptThread.setDaemon(true);
        this.acceptThread.start();
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    private void initSsl(String keystoreResource) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream is = getClass().getResourceAsStream(keystoreResource)) {
            if (is == null) {
                throw new IllegalStateException("Test keystore " + keystoreResource + " not found on classpath");
            }
            ks.load(is, "changeit".toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, "changeit".toCharArray());
        this.sslContext = SSLContext.getInstance("TLS");
        this.sslContext.init(kmf.getKeyManagers(), null, null);
    }

    public int getPort() {
        return port;
    }

    public List<String> getReceivedMessages() {
        return Collections.unmodifiableList(new ArrayList<>(receivedMessages));
    }

    public boolean awaitMessage(long timeout, TimeUnit unit) throws InterruptedException {
        return messageReceivedLatch.await(timeout, unit);
    }

    private void runServer() {
        while (running && !serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                handleConnection(socket);
            } catch (Exception e) {
                if (!running || serverSocket.isClosed()) {
                    break;
                }
                log.warn("SMTP accept error: {}", e.getMessage());
            }
        }
    }

    private void handleConnection(Socket plainSocket) {
        if (simulateFailure) {
            try {
                plainSocket.close();
            } catch (Exception ignored) {}
            return;
        }
        new Thread(() -> {
            boolean connectionTlsStarted = false;
            boolean connectionAuthenticated = false;
            try {
                plainSocket.setSoTimeout(10000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(plainSocket.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(plainSocket.getOutputStream(), StandardCharsets.UTF_8));

                writer.write("220 localhost ESMTP Ready\r\n");
                writer.flush();

                Socket currentSocket = plainSocket;

                while (running) {
                    String line = reader.readLine();
                    if (line == null) break;

                    String upper = line.trim().toUpperCase();
                    if (upper.startsWith("EHLO") || upper.startsWith("HELO")) {
                        writer.write("250-localhost\r\n");
                        writer.write("250-STARTTLS\r\n");
                        writer.write("250-AUTH LOGIN PLAIN\r\n");
                        writer.write("250 8BITMIME\r\n");
                        writer.flush();
                    } else if (upper.equals("STARTTLS")) {
                        writer.write("220 2.0.0 Ready to start TLS\r\n");
                        writer.flush();

                        SSLSocket sslSocket = (SSLSocket) sslContext.getSocketFactory().createSocket(
                                plainSocket,
                                plainSocket.getInetAddress().getHostAddress(),
                                plainSocket.getPort(),
                                true
                        );
                        sslSocket.setUseClientMode(false);
                        sslSocket.startHandshake();

                        connectionTlsStarted = true;
                        currentSocket = sslSocket;
                        reader = new BufferedReader(new InputStreamReader(sslSocket.getInputStream(), StandardCharsets.UTF_8));
                        writer = new BufferedWriter(new OutputStreamWriter(sslSocket.getOutputStream(), StandardCharsets.UTF_8));
                    } else if (upper.startsWith("AUTH LOGIN")) {
                        if (!connectionTlsStarted) {
                            writer.write("538 5.7.1 Encryption required for requested authentication mechanism\r\n");
                            writer.flush();
                            continue;
                        }
                        writer.write("334 VXNlcm5hbWU6\r\n");
                        writer.flush();
                        String userB64 = reader.readLine();
                        String username = "";
                        try {
                            if (userB64 != null) {
                                username = new String(java.util.Base64.getDecoder().decode(userB64.trim()), StandardCharsets.UTF_8);
                            }
                        } catch (Exception ignored) {}

                        writer.write("334 UGFzc3dvcmQ6\r\n");
                        writer.flush();
                        String passB64 = reader.readLine();
                        String password = "";
                        try {
                            if (passB64 != null) {
                                password = new String(java.util.Base64.getDecoder().decode(passB64.trim()), StandardCharsets.UTF_8);
                            }
                        } catch (Exception ignored) {}

                        if ("testuser".equals(username) && "testpass".equals(password)) {
                            connectionAuthenticated = true;
                            this.authenticated = true;
                            writer.write("235 2.7.0 Authentication successful\r\n");
                        } else {
                            writer.write("535 5.7.8 Authentication credentials invalid\r\n");
                        }
                        writer.flush();
                    } else if (upper.startsWith("AUTH PLAIN")) {
                        if (!connectionTlsStarted) {
                            writer.write("538 5.7.1 Encryption required for requested authentication mechanism\r\n");
                            writer.flush();
                            continue;
                        }
                        String plainPayload = "";
                        if (line.trim().length() > 10) {
                            plainPayload = line.trim().substring(10).trim();
                        } else {
                            writer.write("334 \r\n");
                            writer.flush();
                            String authLine = reader.readLine();
                            if (authLine != null) {
                                plainPayload = authLine.trim();
                            }
                        }
                        boolean valid = false;
                        try {
                            byte[] decoded = java.util.Base64.getDecoder().decode(plainPayload);
                            String decodedStr = new String(decoded, StandardCharsets.UTF_8);
                            String[] parts = decodedStr.split("\0");
                            if (parts.length >= 2) {
                                String u = parts.length == 2 ? parts[0] : parts[1];
                                String p = parts.length == 2 ? parts[1] : parts[2];
                                if ("testuser".equals(u) && "testpass".equals(p)) {
                                    valid = true;
                                }
                            }
                        } catch (Exception ignored) {}

                        if (valid) {
                            connectionAuthenticated = true;
                            this.authenticated = true;
                            writer.write("235 2.7.0 Authentication successful\r\n");
                        } else {
                            writer.write("535 5.7.8 Authentication credentials invalid\r\n");
                        }
                        writer.flush();
                    } else if (upper.startsWith("MAIL FROM:")) {
                        if (!connectionAuthenticated) {
                            writer.write("530 5.7.0 Authentication required\r\n");
                            writer.flush();
                        } else {
                            writer.write("250 2.1.0 Sender OK\r\n");
                            writer.flush();
                        }
                    } else if (upper.startsWith("RCPT TO:")) {
                        if (!connectionAuthenticated) {
                            writer.write("530 5.7.0 Authentication required\r\n");
                            writer.flush();
                        } else {
                            writer.write("250 2.1.5 Recipient OK\r\n");
                            writer.flush();
                        }
                    } else if (upper.equals("DATA")) {
                        if (!connectionAuthenticated) {
                            writer.write("530 5.7.0 Authentication required\r\n");
                            writer.flush();
                        } else {
                            writer.write("354 Start mail input; end with <CRLF>.<CRLF>\r\n");
                            writer.flush();

                            StringBuilder emailContent = new StringBuilder();
                            while (true) {
                                String dataLine = reader.readLine();
                                if (dataLine == null || dataLine.equals(".")) {
                                    break;
                                }
                                emailContent.append(dataLine).append("\n");
                            }
                            receivedMessages.add(emailContent.toString());
                            messageReceivedLatch.countDown();

                            writer.write("250 2.0.0 OK: queued\r\n");
                            writer.flush();
                        }
                    } else if (upper.equals("QUIT")) {
                        writer.write("221 2.0.0 Bye\r\n");
                        writer.flush();
                        break;
                    } else if (upper.startsWith("RSET")) {
                        writer.write("250 2.0.0 OK\r\n");
                        writer.flush();
                    } else if (upper.startsWith("NOOP")) {
                        writer.write("250 2.0.0 OK\r\n");
                        writer.flush();
                    } else {
                        writer.write("500 5.5.1 Command unrecognized\r\n");
                        writer.flush();
                    }
                }

                try {
                    currentSocket.close();
                } catch (Exception ignored) {}
            } catch (Exception e) {
                log.debug("SMTP connection terminated: {}", e.getMessage());
            }
        }, "smtp-worker-" + plainSocket.getPort()).start();
    }

    @Override
    public void close() {
        running = false;
        try {
            serverSocket.close();
        } catch (Exception ignored) {}
    }
}
