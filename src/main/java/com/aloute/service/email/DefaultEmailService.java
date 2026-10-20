package com.aloute.service.email;

import jakarta.annotation.PostConstruct;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Properties;

@Service
@Slf4j
public class DefaultEmailService implements EmailService {

    private final Environment environment;

    @Value("${aloute.mail.host:}")
    private String smtpHost;

    @Value("${aloute.mail.port:587}")
    private int smtpPort;

    @Value("${aloute.mail.username:}")
    private String smtpUsername;

    @Value("${aloute.mail.password:}")
    private String smtpPassword;

    @Value("${aloute.mail.from:noreply@aloute.edu.vn}")
    private String smtpFrom;

    @Value("${aloute.mail.starttls.enable:true}")
    private boolean starttlsEnable;

    @Value("${aloute.mail.ssl.checkserveridentity:true}")
    private boolean sslCheckServerIdentity;

    @Autowired
    public DefaultEmailService(Environment environment) {
        this.environment = Objects.requireNonNull(environment, "Environment must not be null");
    }

    private boolean isProduction() {
        return environment.acceptsProfiles(Profiles.of("prod", "production"));
    }

    @PostConstruct
    public void validateConfiguration() {
        if (isProduction()) {
            if (smtpHost == null || smtpHost.isBlank()) {
                throw new IllegalStateException("Production startup failed: SMTP host (aloute.mail.host) is required and cannot be blank in production profile");
            }
            if (smtpUsername == null || smtpUsername.isBlank() || smtpPassword == null || smtpPassword.isBlank()) {
                throw new IllegalStateException("Production startup failed: SMTP credentials (aloute.mail.username, aloute.mail.password) must be configured in production profile");
            }
            if (!starttlsEnable || !sslCheckServerIdentity) {
                throw new IllegalStateException("Production startup failed: Unsafe SMTP configuration rejected: STARTTLS and SSL server identity verification must be enabled for authenticated transport");
            }
        } else if (smtpHost != null && !smtpHost.isBlank()) {
            if (!starttlsEnable || !sslCheckServerIdentity) {
                throw new IllegalStateException("Unsafe SMTP configuration rejected: STARTTLS and SSL server identity verification must be enabled for authenticated transport");
            }
            if (smtpUsername == null || smtpUsername.isBlank() || smtpPassword == null || smtpPassword.isBlank()) {
                throw new IllegalStateException("Invalid SMTP configuration: username and password must be configured when SMTP host is specified");
            }
        }
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetUrl) {
        log.info("Password reset email dispatch requested for recipient {}", sanitizeForLog(toEmail));

        if (smtpHost != null && !smtpHost.isBlank()) {
            sendViaSmtp(toEmail, resetUrl);
        } else if (isProduction()) {
            log.error("Email delivery attempted in production without configured SMTP host for recipient {}", sanitizeForLog(toEmail));
            throw new IllegalStateException("Email delivery simulation is prohibited in production profile: aloute.mail.host must be configured");
        } else {
            log.info("SMTP host not configured; email delivery simulated for recipient {}", sanitizeForLog(toEmail));
        }
    }

    @Override
    public void sendRegistrationOtpEmail(String toEmail, String otpCode) {
        log.info("Registration OTP email dispatch requested for recipient {}", sanitizeForLog(toEmail));

        if (smtpHost != null && !smtpHost.isBlank()) {
            sendOtpViaSmtp(toEmail, otpCode);
        } else if (isProduction()) {
            log.error("Email delivery attempted in production without configured SMTP host for recipient {}", sanitizeForLog(toEmail));
            throw new IllegalStateException("Email delivery simulation is prohibited in production profile: aloute.mail.host must be configured");
        } else {
            log.info("SMTP host not configured; email delivery simulated for recipient {}", sanitizeForLog(toEmail));
        }
    }

    private void sendViaSmtp(String toEmail, String resetUrl) {
        if (!starttlsEnable || !sslCheckServerIdentity) {
            log.error("Unsafe SMTP configuration rejected");
            throw new IllegalStateException("Unsafe SMTP configuration rejected: STARTTLS and SSL server identity verification must be enabled for authenticated transport");
        }
        if (smtpUsername == null || smtpUsername.isBlank() || smtpPassword == null || smtpPassword.isBlank()) {
            log.error("Missing SMTP credentials for configured host {}", smtpHost);
            throw new IllegalStateException("Missing SMTP credentials: username and password are required for authenticated transport");
        }
        try {
            Properties props = new Properties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.port", String.valueOf(smtpPort));
            props.put("mail.smtp.connectiontimeout", "5000");
            props.put("mail.smtp.timeout", "5000");
            props.put("mail.smtp.writetimeout", "5000");

            // Mandatory authenticated transport and strict CA / hostname verification
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
            props.put("mail.smtp.ssl.checkserveridentity", "true");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpUsername, smtpPassword);
                }
            });

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(smtpFrom));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
            message.setSubject("Dat lai mat khau ALOUTE", StandardCharsets.UTF_8.name());
            message.setText("Truy cap lien ket sau de dat lai mat khau:\n" + resetUrl, StandardCharsets.UTF_8.name());

            Transport.send(message);
            log.info("Reset notification dispatched via SMTP host {}:{}", smtpHost, smtpPort);
        } catch (Exception ex) {
            log.error("Failed to deliver external email notification via SMTP host {}: {}", smtpHost, ex.getClass().getSimpleName());
            throw new MailDeliveryException("Failed to deliver external email notification via SMTP host " + smtpHost + ": " + ex.getClass().getSimpleName(), ex);
        }
    }

    private void sendOtpViaSmtp(String toEmail, String otpCode) {
        if (!starttlsEnable || !sslCheckServerIdentity) {
            log.error("Unsafe SMTP configuration rejected");
            throw new IllegalStateException("Unsafe SMTP configuration rejected: STARTTLS and SSL server identity verification must be enabled for authenticated transport");
        }
        if (smtpUsername == null || smtpUsername.isBlank() || smtpPassword == null || smtpPassword.isBlank()) {
            log.error("Missing SMTP credentials for configured host {}", smtpHost);
            throw new IllegalStateException("Missing SMTP credentials: username and password are required for authenticated transport");
        }
        try {
            Properties props = new Properties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.port", String.valueOf(smtpPort));
            props.put("mail.smtp.connectiontimeout", "5000");
            props.put("mail.smtp.timeout", "5000");
            props.put("mail.smtp.writetimeout", "5000");

            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
            props.put("mail.smtp.ssl.checkserveridentity", "true");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpUsername, smtpPassword);
                }
            });

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(smtpFrom));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
            message.setSubject("[AloUTE] Ma xac thuc tai khoan", StandardCharsets.UTF_8.name());
            message.setText("Ma xac thuc dang ky tai khoan AloUTE cua ban la: " + otpCode + "\n\nMa nay co hieu luc trong 5 phut.", StandardCharsets.UTF_8.name());

            Transport.send(message);
            log.info("Registration OTP notification dispatched via SMTP host {}:{}", smtpHost, smtpPort);
        } catch (Exception ex) {
            log.error("Failed to deliver external email notification via SMTP host {}: {}", smtpHost, ex.getClass().getSimpleName());
            throw new MailDeliveryException("Failed to deliver external email notification via SMTP host " + smtpHost + ": " + ex.getClass().getSimpleName(), ex);
        }
    }

    public static String sanitizeForLog(String email) {
        if (email == null) return "null";
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) return "***" + (atIndex >= 0 ? email.substring(atIndex) : "");
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
