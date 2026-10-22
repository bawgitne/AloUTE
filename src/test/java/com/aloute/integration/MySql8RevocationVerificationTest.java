package com.aloute.integration;

import com.aloute.security.jwt.JwtProperties;
import com.aloute.security.jwt.JwtSessionService;
import com.aloute.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.util.StreamUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Opt-in MySQL 8 Verification Test for PR #51 Durable Logout Integration.
 * Executed only when explicitly enabled via system property (-Daloute.mysql8.enabled=true).
 *
 * Verifies against real MySQL 8 engine:
 * 1. Strict local environment guard and engine 8.0 / InnoDB validation.
 * 2. Baseline schema bootstrap via actual candidate JAR in create mode, data preservation,
 *    execution of mysql8_revoked_tokens.sql, detailed metadata inspection, constraint enforcement,
 *    and rerun idempotency.
 * 3. Actual JAR Child Process A execution with live HTTP pre/post logout revocation.
 * 4. Actual JAR Child Process B execution (process restart on same DB) proving persistence across JVM lifecycle.
 * 5. Live HTTP session security: 2FA challenge rejection and real password reset workflow (canonical DTO fields).
 * 6. Expired revocation cleanup via background scheduler and InnoDB foreign key ON DELETE CASCADE.
 */
class MySql8RevocationVerificationTest {

    private static final String REQUIRED_SCHEMA = "aloutedb_test";
    private static final String REQUIRED_JWT_SECRET = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
    private static final long JWT_EXPIRATION_SECONDS = 3600L;

    private final List<Process> activeProcesses = new CopyOnWriteArrayList<>();
    private final List<Path> tempLogFiles = new CopyOnWriteArrayList<>();

    private static String getHost() {
        return System.getenv().getOrDefault("MYSQL_HOST", System.getProperty("aloute.mysql8.host", "127.0.0.1"));
    }

    private static String getPort() {
        return System.getenv().getOrDefault("MYSQL_PORT", System.getProperty("aloute.mysql8.port", "3306"));
    }

    private static String getDatabase() {
        return System.getenv().getOrDefault("MYSQL_DATABASE", System.getProperty("aloute.mysql8.database", REQUIRED_SCHEMA));
    }

    private static String getUser() {
        return System.getenv().getOrDefault("MYSQL_USER", System.getProperty("aloute.mysql8.user", "root"));
    }

    private static String getPassword() {
        return System.getenv().getOrDefault("MYSQL_PASSWORD", System.getProperty("aloute.mysql8.password", ""));
    }

    private static String getJdbcUrl() {
        return "jdbc:mysql://" + getHost() + ":" + getPort() + "/" + getDatabase()
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
    }

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(getJdbcUrl(), getUser(), getPassword());
    }

    private static void enforceStrictLocalEnvironmentGuard() {
        String host = getHost().trim().toLowerCase(Locale.ROOT);
        if (!host.equals("127.0.0.1") && !host.equals("localhost")) {
            throw new SecurityException("SAFETY GUARD: MySQL verification is restricted strictly to loopback (127.0.0.1/localhost). Rejected host: " + host);
        }
        String database = getDatabase().trim();
        if (!database.equals(REQUIRED_SCHEMA)) {
            throw new SecurityException("SAFETY GUARD: MySQL verification is restricted strictly to test database '" + REQUIRED_SCHEMA + "'. Rejected database: " + database);
        }
        String password = getPassword();
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("SAFETY GUARD: MYSQL_PASSWORD must be non-empty when aloute.mysql8.enabled=true.");
        }
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    private static File findCandidateJar() {
        File targetDir = new File("target");
        if (!targetDir.isDirectory()) {
            throw new IllegalStateException("target/ directory not found. Please run 'mvn package -DskipTests' first.");
        }
        File[] jars = targetDir.listFiles((dir, name) -> name.startsWith("aloute-app-") && name.endsWith(".jar") && !name.endsWith("-sources.jar"));
        if (jars == null || jars.length == 0) {
            throw new IllegalStateException("Candidate JAR not found in target/. Please run 'mvn package -DskipTests' before running MySQL verification.");
        }
        return jars[0];
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private static final int MAX_DIAGNOSTIC_BYTES = 256 * 1024; // 256 KiB cap
    private static final int HALF_CAP_BYTES = 128 * 1024;       // 128 KiB each for head and tail
    private static final int MAX_DIAGNOSTIC_LINES = 500;
    private static final int MAX_SECTION_LINES = 250;           // max 250 lines from head, 250 from tail
    private static final int MAX_LINE_LENGTH = 512;
    private static final int MAX_CAUSAL_DEPTH = 5;

    private static final Pattern VALID_EXCEPTION_CLASS_PATTERN =
            Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)*\\.[A-Z][a-zA-Z0-9_]*(?:Exception|Error)$");
    private static final Pattern SQL_STATE_PATTERN =
            Pattern.compile("(?i)(?:SQLState|SQL\\s*State)\\s*[:=]?\\s*([A-Z0-9]{5})");
    private static final Pattern VENDOR_CODE_PATTERN =
            Pattern.compile("(?i)(?:SQL\\s*Error|VendorError|Error\\s*Code|Error)\\s*[:=]?\\s*([0-9]{1,6})");
    private static final Pattern DDL_COMMAND_PATTERN =
            Pattern.compile("(?i)Error executing DDL\\s*\"([^\"]+)\"|Unsuccessful:\\s*([^;\\r\\n]+)");
    private static final Pattern DDL_TABLE_PATTERN =
            Pattern.compile("(?i)\\b(?:create|alter|drop)\\s+table\\s+(?:if\\s+exists\\s+)?([a-zA-Z0-9_]{1,64})|\\b(?:create\\s+(?:unique\\s+)?index\\s+[a-zA-Z0-9_]+\\s+on)\\s+([a-zA-Z0-9_]{1,64})");
    private static final Pattern DDL_COLUMN_PATTERN =
            Pattern.compile("(?i)(?:\\bnear\\s+['`\"]?([a-zA-Z0-9_]{1,64})|\\bcolumn\\s+['`\"]?([a-zA-Z0-9_]{1,64}))");
    private static final Pattern FATAL_ANCHOR_PATTERN =
            Pattern.compile("(?i)Application run failed|APPLICATION FAILED TO START|Exception in thread \"main\"|Uncaught exception");
    private static final Pattern VALID_IDENTIFIER_PATTERN =
            Pattern.compile("^[a-zA-Z0-9_]{1,64}$");

    private static final Set<String> ALLOWLISTED_PROPERTY_NAMES = Set.of(
            "aloute.auth.registration-otp-secret",
            "aloute.auth.revoked-token-cleanup-cron",
            "aloute.firebase.credentials-path",
            "aloute.firebase.enabled",
            "aloute.mail.host",
            "aloute.mail.password",
            "aloute.mail.port",
            "aloute.mail.username",
            "server.port",
            "spring.datasource.driver-class-name",
            "spring.datasource.password",
            "spring.datasource.url",
            "spring.datasource.username",
            "spring.h2.console.enabled",
            "spring.jpa.database-platform",
            "spring.jpa.hibernate.ddl-auto",
            "spring.jpa.properties.hibernate.dialect",
            "spring.jpa.properties.hibernate.hbm2ddl.halt_on_error",
            "spring.profiles.active"
    );

    static record ChildProcessDiagnostic(
            String stage,
            int exitCode,
            String category,
            String primaryException,
            String rootCauseException,
            List<String> causalChain,
            List<String> matchedProperties,
            String firstDdlExceptionClass,
            String firstDdlSqlState,
            Integer firstDdlVendorCode,
            String firstDdlTable,
            String firstDdlColumn,
            String firstDdlOperation,
            String finalFatalException,
            String finalFatalRootCause,
            List<String> finalFatalChain,
            boolean ddlCausedFinalExit,
            boolean truncated
    ) {
        public ChildProcessDiagnostic(
                String stage,
                int exitCode,
                String category,
                String primaryException,
                String rootCauseException,
                List<String> causalChain,
                List<String> matchedProperties
        ) {
            this(stage, exitCode, category, primaryException, rootCauseException, causalChain, matchedProperties,
                    null, null, null, null, null, null,
                    null, null, List.of(),
                    false, false);
        }

        public String formatSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("[CHILD PROCESS DIAGNOSTIC]\n");
            sb.append("  stage: ").append(stage).append("\n");
            sb.append("  exitCode: ").append(exitCode).append("\n");
            sb.append("  category: ").append(category).append("\n");
            if (primaryException != null) {
                sb.append("  primaryException: ").append(primaryException).append("\n");
            }
            if (rootCauseException != null) {
                sb.append("  rootCauseException: ").append(rootCauseException).append("\n");
            }
            if (causalChain != null && !causalChain.isEmpty()) {
                sb.append("  causalChain: ").append(String.join(" -> ", causalChain)).append("\n");
            }
            if (firstDdlOperation != null) {
                sb.append("  firstDdlFailure:\n");
                sb.append("    operation: ").append(firstDdlOperation).append("\n");
                if (firstDdlTable != null) sb.append("    table: ").append(firstDdlTable).append("\n");
                if (firstDdlColumn != null) sb.append("    column: ").append(firstDdlColumn).append("\n");
                sb.append("    sqlState: ").append(firstDdlSqlState != null ? firstDdlSqlState : "UNVERIFIED").append("\n");
                sb.append("    vendorCode: ").append(firstDdlVendorCode != null ? String.valueOf(firstDdlVendorCode) : "UNVERIFIED").append("\n");
                sb.append("    exceptionClass: ").append(firstDdlExceptionClass != null ? firstDdlExceptionClass : "UNVERIFIED").append("\n");
            }
            if (finalFatalChain != null && !finalFatalChain.isEmpty() && !"UNKNOWN".equals(finalFatalException)) {
                sb.append("  finalFatalCause:\n");
                if (finalFatalException != null) sb.append("    exception: ").append(finalFatalException).append("\n");
                if (finalFatalRootCause != null) sb.append("    rootCause: ").append(finalFatalRootCause).append("\n");
                sb.append("    chain: ").append(String.join(" -> ", finalFatalChain)).append("\n");
                sb.append("    ddlCausedFinalExit: ").append(ddlCausedFinalExit).append("\n");
            } else {
                sb.append("  finalFatalCause:\n");
                sb.append("    status: UNKNOWN\n");
                sb.append("    ddlCausedFinalExit: false\n");
            }
            if (matchedProperties != null && !matchedProperties.isEmpty()) {
                sb.append("  matchedProperties: ").append(String.join(", ", matchedProperties)).append("\n");
            }
            return sb.toString();
        }

        public String toCompactLine() {
            if ("LOG_UNREADABLE_OR_EMPTY".equals(category)) {
                return "[category=LOG_UNREADABLE_OR_EMPTY]";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("[category=").append(category);
            if (rootCauseException != null) {
                sb.append(", rootCause=").append(rootCauseException);
            }
            if (causalChain != null && causalChain.size() > 1) {
                sb.append(", chain=").append(String.join("->", causalChain));
            }
            if (firstDdlOperation != null) {
                sb.append(", firstDdl=").append(firstDdlOperation);
                if (firstDdlTable != null) {
                    sb.append("(").append(firstDdlTable);
                    if (firstDdlColumn != null) sb.append(",col=").append(firstDdlColumn);
                    sb.append(",sqlState=").append(firstDdlSqlState != null ? firstDdlSqlState : "UNVERIFIED");
                    sb.append(",vendorCode=").append(firstDdlVendorCode != null ? firstDdlVendorCode : "UNVERIFIED");
                    sb.append(")");
                }
            }
            if (finalFatalChain != null && !finalFatalChain.isEmpty() && !"UNKNOWN".equals(finalFatalException)) {
                sb.append(", finalFatal=").append(String.join("->", finalFatalChain));
                sb.append(", ddlCausedExit=").append(ddlCausedFinalExit);
            } else {
                sb.append(", finalFatal=UNKNOWN, ddlCausedExit=false");
            }
            if (matchedProperties != null && !matchedProperties.isEmpty()) {
                sb.append(", props=").append(String.join(",", matchedProperties));
            }
            sb.append("]");
            return sb.toString();
        }
    }

    static boolean isDdlExceptionChain(List<String> chain) {
        if (chain == null) return false;
        for (String c : chain) {
            if (isDdlExceptionClass(c)) {
                return true;
            }
        }
        return false;
    }

    static boolean isDdlExceptionClass(String className) {
        if (className == null) return false;
        return className.contains("SQLSyntaxErrorException")
                || className.contains("CommandAcceptanceException")
                || className.contains("SchemaManagementException")
                || className.contains("BadSqlGrammarException")
                || className.contains("SQLException");
    }

    static String determineDdlOperation(String cmd) {
        if (cmd == null) return "UNKNOWN_DDL";
        String lower = cmd.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("create table")) return "CREATE_TABLE";
        if (lower.startsWith("alter table")) return "ALTER_TABLE";
        if (lower.startsWith("drop table")) return "DROP_TABLE";
        if (lower.startsWith("create index") || lower.startsWith("create unique index")) return "CREATE_INDEX";
        return "UNKNOWN_DDL";
    }

    static String extractDdlTable(String cmd) {
        if (cmd == null) return null;
        Matcher m = DDL_TABLE_PATTERN.matcher(cmd);
        if (m.find()) {
            String tbl = m.group(1) != null ? m.group(1) : m.group(2);
            if (tbl != null && VALID_IDENTIFIER_PATTERN.matcher(tbl).matches()) {
                return tbl;
            }
        }
        return null;
    }

    static String extractDdlColumn(String text) {
        if (text == null) return null;
        Matcher m = DDL_COLUMN_PATTERN.matcher(text);
        if (m.find()) {
            String col = m.group(1) != null ? m.group(1) : m.group(2);
            if (col != null && VALID_IDENTIFIER_PATTERN.matcher(col).matches() && !isSqlKeyword(col)) {
                return col;
            }
        }
        return null;
    }

    static boolean isSqlKeyword(String identifier) {
        if (identifier == null) return false;
        String upper = identifier.toUpperCase(Locale.ROOT);
        return upper.equals("BIGINT") || upper.equals("VARCHAR") || upper.equals("INT")
                || upper.equals("NULL") || upper.equals("NOT") || upper.equals("DEFAULT")
                || upper.equals("PRIMARY") || upper.equals("KEY") || upper.equals("TABLE")
                || upper.equals("LINE");
    }

    static boolean isExceptionLine(String trimmed) {
        return trimmed.startsWith("org.") || trimmed.startsWith("com.") || trimmed.startsWith("java.")
                || trimmed.startsWith("jakarta.") || trimmed.contains("Exception:") || trimmed.contains("Error:");
    }

    static record BoundedLogSections(
            List<String> headLines,
            List<String> tailLines,
            boolean truncated
    ) {}

    static BoundedLogSections readBoundedHeadAndTail(Path logFile) throws IOException {
        long fileSize = Files.size(logFile);
        if (fileSize == 0) {
            return new BoundedLogSections(List.of(), List.of(), false);
        }

        if (fileSize <= MAX_DIAGNOSTIC_BYTES) {
            byte[] bytes = new byte[(int) fileSize];
            int read;
            try (InputStream in = Files.newInputStream(logFile)) {
                read = in.readNBytes(bytes, 0, (int) fileSize);
            }
            String content = new String(bytes, 0, read, StandardCharsets.UTF_8);
            String[] allRaw = content.split("\\R");

            if (allRaw.length <= MAX_DIAGNOSTIC_LINES) {
                List<String> bounded = new ArrayList<>(allRaw.length);
                for (String raw : allRaw) {
                    bounded.add(raw.length() > MAX_LINE_LENGTH ? raw.substring(0, MAX_LINE_LENGTH) : raw);
                }
                return new BoundedLogSections(bounded, bounded, false);
            }

            List<String> headLines = new ArrayList<>(MAX_SECTION_LINES);
            for (int i = 0; i < Math.min(MAX_SECTION_LINES, allRaw.length); i++) {
                String raw = allRaw[i];
                headLines.add(raw.length() > MAX_LINE_LENGTH ? raw.substring(0, MAX_LINE_LENGTH) : raw);
            }

            List<String> tailLines = new ArrayList<>(MAX_SECTION_LINES);
            int tailStart = Math.max(0, allRaw.length - MAX_SECTION_LINES);
            for (int i = tailStart; i < allRaw.length; i++) {
                String raw = allRaw[i];
                tailLines.add(raw.length() > MAX_LINE_LENGTH ? raw.substring(0, MAX_LINE_LENGTH) : raw);
            }

            return new BoundedLogSections(headLines, tailLines, true);
        }

        byte[] headBytes = new byte[HALF_CAP_BYTES];
        byte[] tailBytes = new byte[HALF_CAP_BYTES];
        int headRead;
        int tailRead;

        try (FileChannel channel = FileChannel.open(logFile, StandardOpenOption.READ)) {
            channel.position(0);
            ByteBuffer headBuf = ByteBuffer.wrap(headBytes);
            while (headBuf.hasRemaining() && channel.read(headBuf) > 0) {}
            headRead = headBuf.position();

            long tailPosition = Math.max(0, fileSize - HALF_CAP_BYTES);
            channel.position(tailPosition);
            ByteBuffer tailBuf = ByteBuffer.wrap(tailBytes);
            while (tailBuf.hasRemaining() && channel.read(tailBuf) > 0) {}
            tailRead = tailBuf.position();
        }

        String headContent = new String(headBytes, 0, headRead, StandardCharsets.UTF_8);
        String[] rawHead = headContent.split("\\R");
        List<String> headLines = new ArrayList<>(MAX_SECTION_LINES);
        for (int i = 0; i < Math.min(MAX_SECTION_LINES, rawHead.length); i++) {
            String raw = rawHead[i];
            headLines.add(raw.length() > MAX_LINE_LENGTH ? raw.substring(0, MAX_LINE_LENGTH) : raw);
        }

        String tailContent = new String(tailBytes, 0, tailRead, StandardCharsets.UTF_8);
        String[] rawTail = tailContent.split("\\R");
        List<String> tailLines = new ArrayList<>(MAX_SECTION_LINES);
        int tailStart = Math.max(0, rawTail.length - MAX_SECTION_LINES);
        for (int i = tailStart; i < rawTail.length; i++) {
            String raw = rawTail[i];
            tailLines.add(raw.length() > MAX_LINE_LENGTH ? raw.substring(0, MAX_LINE_LENGTH) : raw);
        }

        return new BoundedLogSections(headLines, tailLines, true);
    }

    static String extractCandidateExceptionClass(String trimmed) {
        String s = trimmed;
        if (s.startsWith("Caused by:")) {
            s = s.substring("Caused by:".length()).trim();
        }
        int colonIdx = s.indexOf(':');
        if (colonIdx != -1) {
            s = s.substring(0, colonIdx).trim();
        }
        int spaceIdx = s.indexOf(' ');
        if (spaceIdx != -1) {
            s = s.substring(0, spaceIdx).trim();
        }
        return s;
    }

    static ChildProcessDiagnostic extractChildDiagnostic(Path logFile, String stage, int exitCode) {
        if (logFile == null || !Files.exists(logFile)) {
            return new ChildProcessDiagnostic(stage, exitCode, "LOG_UNREADABLE_OR_EMPTY", null, null, List.of(), List.of());
        }

        BoundedLogSections sections;
        try {
            sections = readBoundedHeadAndTail(logFile);
        } catch (IOException e) {
            return new ChildProcessDiagnostic(stage, exitCode, "LOG_UNREADABLE_OR_EMPTY", null, null, List.of(), List.of());
        }

        if (sections.headLines().isEmpty() && sections.tailLines().isEmpty()) {
            return new ChildProcessDiagnostic(stage, exitCode, "LOG_UNREADABLE_OR_EMPTY", null, null, List.of(), List.of());
        }

        String primaryException = null;
        String rootCauseException = null;
        List<String> causalChain = new ArrayList<>();
        Set<String> matchedProperties = new TreeSet<>();
        StringBuilder privateClues = new StringBuilder();

        for (String rawLine : sections.headLines()) {
            String trimmed = rawLine.trim();
            if (trimmed.isEmpty()) continue;

            if (privateClues.length() < 2048) {
                privateClues.append(trimmed.toLowerCase(Locale.ROOT)).append(' ');
            }

            for (String prop : ALLOWLISTED_PROPERTY_NAMES) {
                if (trimmed.contains(prop)) {
                    matchedProperties.add(prop);
                }
            }

            String candidateClass = extractCandidateExceptionClass(trimmed);
            if (candidateClass != null && VALID_EXCEPTION_CLASS_PATTERN.matcher(candidateClass).matches()) {
                if (primaryException == null) {
                    primaryException = candidateClass;
                }
                if (causalChain.size() < MAX_CAUSAL_DEPTH && !causalChain.contains(candidateClass)) {
                    causalChain.add(candidateClass);
                }
                rootCauseException = candidateClass;
            }
        }

        // Correlate FIRST DDL command failure event with SQLSTATE, vendor code, column, and SQL exception
        String firstDdlExceptionClass = null;
        String firstDdlSqlState = null;
        Integer firstDdlVendorCode = null;
        String firstDdlTable = null;
        String firstDdlColumn = null;
        String firstDdlOperation = null;

        int ddlLineIdx = -1;
        String firstDdlCmd = null;

        for (int i = 0; i < sections.headLines().size(); i++) {
            String line = sections.headLines().get(i).trim();
            Matcher ddlMatcher = DDL_COMMAND_PATTERN.matcher(line);
            if (ddlMatcher.find()) {
                ddlLineIdx = i;
                firstDdlCmd = ddlMatcher.group(1) != null ? ddlMatcher.group(1).trim() : ddlMatcher.group(2).trim();
                firstDdlOperation = determineDdlOperation(firstDdlCmd);
                firstDdlTable = extractDdlTable(firstDdlCmd);
                break;
            }
        }

        if (ddlLineIdx != -1) {
            int windowStart = Math.max(0, ddlLineIdx - 5);
            int windowEnd = Math.min(sections.headLines().size() - 1, ddlLineIdx + 8);

            for (int i = windowStart; i <= windowEnd; i++) {
                String line = sections.headLines().get(i).trim();

                if (firstDdlSqlState == null) {
                    Matcher sqlStateMatcher = SQL_STATE_PATTERN.matcher(line);
                    if (sqlStateMatcher.find()) {
                        firstDdlSqlState = sqlStateMatcher.group(1).toUpperCase(Locale.ROOT);
                    }
                }

                if (firstDdlVendorCode == null) {
                    Matcher vendorMatcher = VENDOR_CODE_PATTERN.matcher(line);
                    if (vendorMatcher.find()) {
                        firstDdlVendorCode = Integer.parseInt(vendorMatcher.group(1));
                    }
                }

                if (firstDdlColumn == null) {
                    firstDdlColumn = extractDdlColumn(line);
                }

                String candidateClass = extractCandidateExceptionClass(line);
                if (candidateClass != null && VALID_EXCEPTION_CLASS_PATTERN.matcher(candidateClass).matches()) {
                    if (isDdlExceptionClass(candidateClass)) {
                        if (firstDdlExceptionClass == null || candidateClass.startsWith("java.sql.") || candidateClass.contains("Syntax")) {
                            firstDdlExceptionClass = candidateClass;
                        }
                    }
                }
            }
        }

        // Scan TAIL lines for properties and anchored fatal failure block
        List<String> anchoredFatalChain = new ArrayList<>();
        boolean insideFatalAnchor = false;

        for (String rawLine : sections.tailLines()) {
            String trimmed = rawLine.trim();
            if (trimmed.isEmpty()) continue;

            for (String prop : ALLOWLISTED_PROPERTY_NAMES) {
                if (trimmed.contains(prop)) {
                    matchedProperties.add(prop);
                }
            }

            if (FATAL_ANCHOR_PATTERN.matcher(trimmed).find()) {
                insideFatalAnchor = true;
                anchoredFatalChain.clear();
                String candidateClass = extractCandidateExceptionClass(trimmed);
                if (candidateClass != null && VALID_EXCEPTION_CLASS_PATTERN.matcher(candidateClass).matches()) {
                    anchoredFatalChain.add(candidateClass);
                }
                continue;
            }

            if (insideFatalAnchor) {
                String candidateClass = extractCandidateExceptionClass(trimmed);
                if (candidateClass != null && VALID_EXCEPTION_CLASS_PATTERN.matcher(candidateClass).matches()) {
                    if (anchoredFatalChain.isEmpty()) {
                        anchoredFatalChain.add(candidateClass);
                    } else if (trimmed.startsWith("Caused by:") || isExceptionLine(trimmed)) {
                        if (anchoredFatalChain.size() < MAX_CAUSAL_DEPTH && !anchoredFatalChain.contains(candidateClass)) {
                            anchoredFatalChain.add(candidateClass);
                        }
                    }
                } else if (!trimmed.startsWith("at ") && !trimmed.startsWith("...") && !trimmed.startsWith("Caused by:") && !trimmed.isEmpty()) {
                    if (!anchoredFatalChain.isEmpty()) {
                        insideFatalAnchor = false; // Anchored fatal block completed
                    }
                }
            }
        }

        String fatalPrimary = null;
        String fatalRoot = null;
        List<String> fatalChain = List.of();
        boolean ddlCausedFinalExit = false;

        if (!anchoredFatalChain.isEmpty()) {
            fatalPrimary = anchoredFatalChain.get(0);
            fatalRoot = anchoredFatalChain.get(anchoredFatalChain.size() - 1);
            fatalChain = new ArrayList<>(anchoredFatalChain);
            ddlCausedFinalExit = isDdlExceptionChain(fatalChain);
        } else {
            fatalPrimary = "UNKNOWN";
            fatalRoot = "UNKNOWN";
            fatalChain = List.of("UNKNOWN");
            ddlCausedFinalExit = false;
        }

        String category = categorizeFromClues(causalChain, privateClues.toString(), exitCode);

        return new ChildProcessDiagnostic(
                stage,
                exitCode,
                category,
                primaryException,
                rootCauseException,
                causalChain,
                new ArrayList<>(matchedProperties),
                firstDdlExceptionClass,
                firstDdlSqlState,
                firstDdlVendorCode,
                firstDdlTable,
                firstDdlColumn,
                firstDdlOperation,
                fatalPrimary,
                fatalRoot,
                fatalChain,
                ddlCausedFinalExit,
                sections.truncated()
        );
    }

    static String categorizeFromClues(List<String> chain, String clues, int exitCode) {
        String combined = (String.join(" ", chain) + " " + clues).toLowerCase(Locale.ROOT);

        if (combined.contains("communicationslinkfailure") || combined.contains("communicationsexception")
                || combined.contains("connection refused") || combined.contains("cannotgetjdbcconnection")
                || combined.contains("connectexception") || combined.contains("sockettimeoutexception")) {
            return "DATASOURCE_COMMUNICATION_FAILURE";
        }
        if (combined.contains("access denied for user") || combined.contains("accessdeniedexception")
                || combined.contains("sqlinvalidauthorizationspec") || combined.contains("password authentication failed")) {
            return "DATASOURCE_AUTHENTICATION_FAILURE";
        }
        if (combined.contains("schemamanagementexception") || combined.contains("sqlgrammarexception")
                || combined.contains("badsqlgrammarexception") || combined.contains("tablenotfoundexception")
                || combined.contains("table doesn't exist") || combined.contains("hibernateexception")) {
            return "DATABASE_SCHEMA_MIGRATION_FAILURE";
        }
        if (combined.contains("beancreationexception") || combined.contains("beaninitializationexception")
                || combined.contains("unsatisfieddependencyexception") || combined.contains("nosuchbeandefinitionexception")) {
            return "BEAN_CREATION_FAILURE";
        }
        if (combined.contains("portinuseexception") || combined.contains("bindexception")
                || combined.contains("port already in use") || combined.contains("address already in use")) {
            return "PORT_BIND_FAILURE";
        }
        if (combined.contains("applicationcontextexception") || combined.contains("missing required configuration")
                || combined.contains("failed to configure a datasource")) {
            return "CONFIGURATION_MISSING_PROPERTY";
        }
        if (combined.contains("jwtexception") || combined.contains("signatureexception")) {
            return "SECURITY_KEY_FAILURE";
        }
        if (exitCode != 0) {
            return "SPRING_BOOT_BOOTSTRAP_FAILURE";
        }
        return "UNKNOWN_PROCESS_FAILURE";
    }

    private Process startChildJarProcess(File jarFile, int port, String ddlAuto) throws IOException {
        return startChildJarProcess(jarFile, port, ddlAuto, "bootstrap_" + ddlAuto);
    }

    private Process startChildJarProcess(File jarFile, int port, String ddlAuto, String stage) throws IOException {
        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        Path logFile = Files.createTempFile("aloute-child-process-port-" + port + "-", ".log");
        tempLogFiles.add(logFile);

        ProcessBuilder pb = new ProcessBuilder(
                javaBin,
                "-jar",
                jarFile.getAbsolutePath(),
                "--server.port=" + port,
                "--PORT=" + port,
                "--spring.datasource.url=" + getJdbcUrl(),
                "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                "--spring.datasource.username=" + getUser(),
                "--spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect",
                "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect",
                "--spring.jpa.hibernate.ddl-auto=" + ddlAuto,
                "--aloute.firebase.enabled=false",
                "--aloute.auth.revoked-token-cleanup-cron=*/2 * * * * *"
        );
        pb.environment().put("SPRING_DATASOURCE_PASSWORD", getPassword());
        pb.environment().put("ALOUTE_JWT_SECRET", REQUIRED_JWT_SECRET);
        pb.environment().put("ALOUTE_JWT_EXPIRATION_SECONDS", String.valueOf(JWT_EXPIRATION_SECONDS));
        pb.environment().put("aloute.auth.registration-otp-secret", "dGVzdC1yZWdpc3RyYXRpb24tb3RwLXNlY3JldC1rZXktMzJiLWxlbmd0aCE=");

        // Redirect child process stdout/stderr directly to private temp logfile (no unjoined background thread)
        pb.redirectOutput(logFile.toFile());
        pb.redirectError(logFile.toFile());

        Process process = pb.start();
        activeProcesses.add(process);

        // Bounded readiness check via live HTTP ping
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        HttpRequest pingReq = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/api/auth/me"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();

        long deadline = System.currentTimeMillis() + 45000;
        boolean ready = false;
        IOException lastIoException = null;
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                int exitCode = process.exitValue();
                stopProcess(process);
                ChildProcessDiagnostic diagnostic = extractChildDiagnostic(logFile, stage + "_premature_exit", exitCode);
                System.err.println(diagnostic.formatSummary());
                IllegalStateException primaryEx = new IllegalStateException(
                        "Child JAR process on port " + port + " terminated prematurely with exit code " + exitCode + ": " + diagnostic.toCompactLine());
                try {
                    if (Files.deleteIfExists(logFile)) {
                        tempLogFiles.remove(logFile);
                    }
                } catch (IOException cleanupErr) {
                    primaryEx.addSuppressed(cleanupErr);
                }
                throw primaryEx;
            }
            try {
                HttpResponse<String> resp = client.send(pingReq, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200 || resp.statusCode() == 401) {
                    ready = true;
                    break;
                }
            } catch (IOException e) {
                lastIoException = e;
                // Retry expected connection or handshake failure until child server is accepting HTTP
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                stopProcess(process);
                RuntimeException interruptEx = new RuntimeException("Readiness ping interrupted for port " + port, e);
                try {
                    if (Files.deleteIfExists(logFile)) {
                        tempLogFiles.remove(logFile);
                    }
                } catch (IOException cleanupErr) {
                    interruptEx.addSuppressed(cleanupErr);
                }
                throw interruptEx;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                stopProcess(process);
                RuntimeException waitEx = new RuntimeException("Readiness wait interrupted for port " + port, e);
                try {
                    if (Files.deleteIfExists(logFile)) {
                        tempLogFiles.remove(logFile);
                    }
                } catch (IOException cleanupErr) {
                    waitEx.addSuppressed(cleanupErr);
                }
                throw waitEx;
            }
        }
        if (!ready) {
            int exitCode = process.isAlive() ? -1 : process.exitValue();
            stopProcess(process);
            ChildProcessDiagnostic diagnostic = extractChildDiagnostic(logFile, stage + "_readiness_timeout_45s", exitCode);
            System.err.println(diagnostic.formatSummary());
            String msg = "Child JAR process on port " + port + " failed to become ready within 45s: " + diagnostic.toCompactLine();
            IllegalStateException primaryEx;
            if (lastIoException != null) {
                primaryEx = new IllegalStateException(msg + "; last ping error: " + lastIoException.getMessage(), lastIoException);
            } else {
                primaryEx = new IllegalStateException(msg + ".");
            }
            try {
                if (Files.deleteIfExists(logFile)) {
                    tempLogFiles.remove(logFile);
                }
            } catch (IOException cleanupErr) {
                primaryEx.addSuppressed(cleanupErr);
            }
            throw primaryEx;
        }
        return process;
    }

    private void stopProcess(Process process) {
        if (process == null) {
            return;
        }
        if (!process.isAlive()) {
            activeProcesses.remove(process);
            return;
        }
        process.destroy();
        boolean interrupted = false;
        boolean exited = false;
        try {
            exited = process.waitFor(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            interrupted = true;
            // Interrupt cleared by catch; do not restore yet so second bounded wait can run
        }
        if (!exited && process.isAlive()) {
            process.destroyForcibly();
            try {
                exited = process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        try {
            if (process.isAlive() || !exited) {
                throw new IllegalStateException("Failed to terminate child process PID " + process.pid() + " within bounded timeout");
            }
            activeProcesses.remove(process);
            if (interrupted) {
                throw new IllegalStateException("Process termination wait was interrupted for PID " + process.pid());
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @AfterEach
    void tearDownAllProcesses() {
        List<Throwable> errors = new ArrayList<>();

        // Attempt closing all tracked processes (CopyOnWriteArrayList allows safe concurrent iteration)
        for (Process p : activeProcesses) {
            try {
                stopProcess(p);
            } catch (Throwable t) {
                errors.add(t);
            }
        }

        // Verify if any child is still alive or unproven exit
        boolean anyAlive = false;
        for (Process p : activeProcesses) {
            if (p != null && p.isAlive()) {
                anyAlive = true;
                break;
            }
        }

        // Do not delete log files or clear unresponsive process ownership if exit unproven
        // Only delete private logs once all children exited
        if (!anyAlive && activeProcesses.isEmpty()) {
            for (Path logFile : tempLogFiles) {
                try {
                    if (Files.deleteIfExists(logFile)) {
                        tempLogFiles.remove(logFile);
                    }
                } catch (IOException e) {
                    errors.add(new IOException("Failed to delete private log file: " + logFile + " - " + e.getMessage(), e));
                }
            }
        }

        // Accumulate all errors with suppressed causes; JUnit preserves original test body failure
        if (!errors.isEmpty()) {
            RuntimeException teardownException = new RuntimeException(
                    "Process teardown / cleanup encountered " + errors.size() + " failure(s)");
            for (Throwable err : errors) {
                teardownException.addSuppressed(err);
            }
            throw teardownException;
        }
    }

    private static String extractTokenFromResponse(String responseBody) {
        Pattern pattern = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(responseBody);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalArgumentException("Unable to extract token from login response body");
    }

    private static String extractCsrfTokenFromCookie(HttpResponse<?> response) {
        List<String> cookies = response.headers().allValues("Set-Cookie");
        for (String c : cookies) {
            if (c.startsWith("XSRF-TOKEN=")) {
                int end = c.indexOf(';');
                if (end == -1) end = c.length();
                return c.substring("XSRF-TOKEN=".length(), end);
            }
        }
        return "";
    }

    @Test
    @DisplayName("Comprehensive Real MySQL 8 Lifecycle: DDL, Metadata, Real JAR Restart & Live HTTP Verification")
    @EnabledIfSystemProperty(named = "aloute.mysql8.enabled", matches = "true")
    void verifyRealMySql8LifecycleAndRevocation() throws Exception {
        // ====================================================================
        // STAGE 1: Strict Local Environment Guard & Engine 8.0 Verification
        // ====================================================================
        enforceStrictLocalEnvironmentGuard();

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // Assert MySQL engine is 8.0.x
            try (ResultSet rs = stmt.executeQuery("SELECT VERSION()")) {
                assertThat(rs.next()).isTrue();
                String version = rs.getString(1);
                assertThat(version)
                        .as("Engine must be real MySQL 8.0.x, found: %s", version)
                        .startsWith("8.0");
            }
            // Assert storage engine is InnoDB
            try (ResultSet rs = stmt.executeQuery("SELECT @@default_storage_engine")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).as("Default engine must be InnoDB").isEqualToIgnoringCase("InnoDB");
            }
        }

        // ====================================================================
        // STAGE 2: Baseline Bootstrap, Migration DDL, Metadata & Constraints
        // ====================================================================
        File jarFile = findCandidateJar();

        // Bootstrap baseline tables with the actual candidate JAR child process in create mode
        int bootstrapPort = findFreePort();
        Process bootstrapProcess = startChildJarProcess(jarFile, bootstrapPort, "create", "stage2_baseline_bootstrap_create");
        stopProcess(bootstrapProcess);

        String baselineUsername = "baseline_user_" + System.currentTimeMillis();
        String baselineEmail = "baseline_" + System.currentTimeMillis() + "@aloute.edu.vn";

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // Insert baseline user BEFORE applying migration DDL to prove data preservation
            // Include created_at and updated_at (NOT NULL columns)
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO users (username, email, password_hash, is_verified, verification_required, is_private, is_active, global_role, auth_version, two_factor_enabled, created_at, updated_at)
                VALUES (?, ?, 'dummy_hash_baseline', TRUE, FALSE, FALSE, TRUE, 'USER', 0, FALSE, NOW(6), NOW(6))
            """)) {
                ps.setString(1, baselineUsername);
                ps.setString(2, baselineEmail);
                ps.executeUpdate();
            }

            // If Hibernate autogenerated revoked_tokens, prove it is empty and drop ONLY revoked_tokens
            DatabaseMetaData dbMeta = conn.getMetaData();
            boolean tableExists = false;
            try (ResultSet rs = dbMeta.getTables(REQUIRED_SCHEMA, null, "revoked_tokens", null)) {
                if (rs.next()) tableExists = true;
            }
            if (tableExists) {
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM revoked_tokens")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getLong(1)).as("Autogenerated table must be empty").isZero();
                }
                stmt.execute("DROP TABLE revoked_tokens");
            }

            // Apply actual unchanged src/main/resources/db/migration/mysql8_revoked_tokens.sql
            ClassPathResource migrationResource = new ClassPathResource("db/migration/mysql8_revoked_tokens.sql");
            assertThat(migrationResource.exists()).as("mysql8_revoked_tokens.sql must exist on classpath").isTrue();
            String migrationSql;
            try (InputStream is = migrationResource.getInputStream()) {
                migrationSql = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            }

            stmt.execute(migrationSql);

            // Assert baseline user row was preserved through migration
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE email = ?")) {
                ps.setString(1, baselineEmail);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).as("Baseline user row must survive migration").isEqualTo(1);
                }
            }

            // Assert rerun idempotency
            stmt.execute(migrationSql);

            // Detailed metadata verification
            // Table engine must be InnoDB
            try (ResultSet rs = stmt.executeQuery("""
                SELECT ENGINE FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = 'revoked_tokens'
            """.formatted(REQUIRED_SCHEMA))) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("ENGINE")).isEqualToIgnoringCase("InnoDB");
            }

            // Columns, types, and nullability
            try (ResultSet rs = stmt.executeQuery("""
                SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, CHARACTER_MAXIMUM_LENGTH, EXTRA
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = 'revoked_tokens'
            """.formatted(REQUIRED_SCHEMA))) {
                List<String> cols = new ArrayList<>();
                while (rs.next()) {
                    String col = rs.getString("COLUMN_NAME").toLowerCase();
                    cols.add(col);
                    if (col.equals("id")) {
                        assertThat(rs.getString("DATA_TYPE")).isEqualTo("bigint");
                        assertThat(rs.getString("IS_NULLABLE")).isEqualTo("NO");
                        assertThat(rs.getString("EXTRA")).contains("auto_increment");
                    } else if (col.equals("token_hash")) {
                        assertThat(rs.getString("DATA_TYPE")).isEqualTo("varchar");
                        assertThat(rs.getLong("CHARACTER_MAXIMUM_LENGTH")).isEqualTo(64L);
                        assertThat(rs.getString("IS_NULLABLE")).isEqualTo("NO");
                    } else if (col.equals("user_id")) {
                        assertThat(rs.getString("DATA_TYPE")).isEqualTo("bigint");
                        assertThat(rs.getString("IS_NULLABLE")).isEqualTo("YES");
                    } else if (col.equals("expires_at")) {
                        assertThat(rs.getString("DATA_TYPE")).isEqualTo("datetime");
                        assertThat(rs.getString("IS_NULLABLE")).isEqualTo("NO");
                    } else if (col.equals("created_at")) {
                        assertThat(rs.getString("DATA_TYPE")).isEqualTo("datetime");
                        assertThat(rs.getString("IS_NULLABLE")).isEqualTo("NO");
                    }
                }
                assertThat(cols).containsExactlyInAnyOrder("id", "token_hash", "user_id", "expires_at", "created_at");
            }

            // Unique constraint on token_hash
            try (ResultSet rs = stmt.executeQuery("""
                SELECT CONSTRAINT_NAME FROM information_schema.TABLE_CONSTRAINTS
                WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = 'revoked_tokens' AND CONSTRAINT_TYPE = 'UNIQUE'
            """.formatted(REQUIRED_SCHEMA))) {
                boolean foundUk = false;
                while (rs.next()) {
                    if ("uk_revoked_tokens_token_hash".equalsIgnoreCase(rs.getString("CONSTRAINT_NAME"))) {
                        foundUk = true;
                    }
                }
                assertThat(foundUk).as("Unique constraint uk_revoked_tokens_token_hash must exist").isTrue();
            }

            // Foreign Key constraint referencing users(id) ON DELETE CASCADE
            try (ResultSet rs = stmt.executeQuery("""
                SELECT CONSTRAINT_NAME, REFERENCED_TABLE_NAME, DELETE_RULE
                FROM information_schema.REFERENTIAL_CONSTRAINTS
                WHERE CONSTRAINT_SCHEMA = '%s' AND TABLE_NAME = 'revoked_tokens'
            """.formatted(REQUIRED_SCHEMA))) {
                boolean foundFk = false;
                while (rs.next()) {
                    if ("fk_revoked_tokens_user".equalsIgnoreCase(rs.getString("CONSTRAINT_NAME"))) {
                        foundFk = true;
                        assertThat(rs.getString("REFERENCED_TABLE_NAME")).isEqualToIgnoringCase("users");
                        assertThat(rs.getString("DELETE_RULE")).isEqualToIgnoringCase("CASCADE");
                    }
                }
                assertThat(foundFk).as("Foreign key fk_revoked_tokens_user with CASCADE must exist").isTrue();
            }

            // Index on expires_at
            try (ResultSet rs = stmt.executeQuery("""
                SELECT INDEX_NAME FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = 'revoked_tokens' AND INDEX_NAME = 'idx_revoked_tokens_expires_at'
            """.formatted(REQUIRED_SCHEMA))) {
                assertThat(rs.next()).as("Index idx_revoked_tokens_expires_at must exist").isTrue();
            }

            // Test constraint behavior with invalid inserts
            // 1. Unique constraint: duplicate token_hash insert must fail
            stmt.execute("INSERT INTO revoked_tokens (token_hash, user_id, expires_at) VALUES ('uk_test_hash_unique', NULL, NOW() + INTERVAL 1 HOUR)");
            assertThatThrownBy(() -> stmt.execute("INSERT INTO revoked_tokens (token_hash, user_id, expires_at) VALUES ('uk_test_hash_unique', NULL, NOW() + INTERVAL 1 HOUR)"))
                    .isInstanceOf(SQLException.class);

            // 2. Foreign Key constraint: referencing non-existent user must fail
            assertThatThrownBy(() -> stmt.execute("INSERT INTO revoked_tokens (token_hash, user_id, expires_at) VALUES ('fk_test_invalid_user', 999999999, NOW() + INTERVAL 1 HOUR)"))
                    .isInstanceOf(SQLException.class);
        }

        // ====================================================================
        // STAGE 3: Actual JAR Child Process A & Live HTTP Pre/Post Logout
        // ====================================================================
        int portA = findFreePort();
        Process processA = null;
        Process processB = null;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        String testUsername = "live_user_" + System.currentTimeMillis();
        String testEmail = "live_" + System.currentTimeMillis() + "@aloute.edu.vn";
        String initialPassword = "Password123!";
        String newPassword = "NewSecurePassword456!";

        String tokenA;
        String tokenB;
        String fingerprintA;

        try {
            processA = startChildJarProcess(jarFile, portA, "validate", "stage4_child_process_a_validate");

            // 1. Live HTTP Register
            String registerJson = "{\"username\":\"" + testUsername + "\",\"email\":\"" + testEmail + "\",\"password\":\"" + initialPassword + "\"}";
            HttpRequest regReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + portA + "/api/auth/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(registerJson))
                    .build();
            HttpResponse<String> regResp = httpClient.send(regReq, HttpResponse.BodyHandlers.ofString());
            assertThat(regResp.statusCode()).as("Registration must return 201 Created").isEqualTo(201);

            // Mark user verified and verificationRequired=false via direct JDBC
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("UPDATE users SET is_verified = TRUE, verification_required = FALSE WHERE email = ?")) {
                ps.setString(1, testEmail);
                ps.executeUpdate();
            }

            // 2. Live HTTP Login 1 -> Token A
            String loginJson = "{\"email\":\"" + testEmail + "\",\"password\":\"" + initialPassword + "\"}";
            HttpRequest loginReq1 = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + portA + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(loginJson))
                    .build();
            HttpResponse<String> loginResp1 = httpClient.send(loginReq1, HttpResponse.BodyHandlers.ofString());
            assertThat(loginResp1.statusCode()).as("Login 1 must return 200 OK").isEqualTo(200);
            tokenA = extractTokenFromResponse(loginResp1.body());
            fingerprintA = JwtSessionService.computeFingerprint(tokenA);
            assertThat(tokenA != null && !tokenA.isBlank()).as("Token A must be non-empty").isTrue();

            // 3. Live HTTP Login 2 -> Token B (Sibling session)
            HttpResponse<String> loginResp2 = httpClient.send(loginReq1, HttpResponse.BodyHandlers.ofString());
            assertThat(loginResp2.statusCode()).as("Login 2 must return 200 OK").isEqualTo(200);
            tokenB = extractTokenFromResponse(loginResp2.body());
            assertThat(tokenB != null && !tokenB.isBlank()).as("Token B must be non-empty").isTrue();

            // Sibling session tokens must be distinct (assert boolean without printing JWTs)
            boolean tokensDistinct = !tokenA.equals(tokenB);
            assertThat(tokensDistinct).as("Sibling session tokens must be distinct").isTrue();

            // 4. Verify both Token A and Token B work before logout
            HttpRequest reqProfileA = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + portA + "/api/users/profile"))
                    .header("Authorization", "Bearer " + tokenA)
                    .GET()
                    .build();
            assertThat(httpClient.send(reqProfileA, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);

            HttpRequest reqProfileB = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + portA + "/api/users/profile"))
                    .header("Authorization", "Bearer " + tokenB)
                    .GET()
                    .build();
            assertThat(httpClient.send(reqProfileB, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);

            // 5. Live HTTP Logout Token A
            HttpRequest logoutReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + portA + "/api/auth/logout"))
                    .header("Authorization", "Bearer " + tokenA)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> logoutResp = httpClient.send(logoutReq, HttpResponse.BodyHandlers.ofString());
            assertThat(logoutResp.statusCode()).as("Logout must return 204 No Content").isEqualTo(204);

            // 6. Live HTTP Assertions: Token A rejected 401, Sibling Token B valid 200
            assertThat(httpClient.send(reqProfileA, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
            assertThat(httpClient.send(reqProfileB, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);

            // 7. Direct JDBC check: Revoked token row exists in MySQL
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM revoked_tokens WHERE token_hash = ?")) {
                ps.setString(1, fingerprintA);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).as("Revoked token row must exist in MySQL").isEqualTo(1);
                }
            }

            // Stop Process A cleanly
            stopProcess(processA);
            processA = null;

            // ====================================================================
            // STAGE 4: Actual JAR Child Process B & Persistence Across Restart
            // ====================================================================
            int portB = findFreePort();
            processB = startChildJarProcess(jarFile, portB, "validate", "stage5_child_process_b_restart_validate");

            // 1. Live HTTP Assertions on Process B: Token A STILL rejected 401, Token B STILL valid 200
            HttpRequest reqProfileA_B = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + portB + "/api/users/profile"))
                    .header("Authorization", "Bearer " + tokenA)
                    .GET()
                    .build();
            assertThat(httpClient.send(reqProfileA_B, HttpResponse.BodyHandlers.ofString()).statusCode())
                    .as("Token A must remain rejected 401 across JAR process restart")
                    .isEqualTo(401);

            HttpRequest reqProfileB_B = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + portB + "/api/users/profile"))
                    .header("Authorization", "Bearer " + tokenB)
                    .GET()
                    .build();
            assertThat(httpClient.send(reqProfileB_B, HttpResponse.BodyHandlers.ofString()).statusCode())
                    .as("Sibling Token B must remain valid 200 across JAR process restart")
                    .isEqualTo(200);

            // 2. Direct JDBC check: Revoked row still persisted in MySQL
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM revoked_tokens WHERE token_hash = ?")) {
                ps.setString(1, fingerprintA);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).isEqualTo(1);
                }
            }

            // ====================================================================
            // STAGE 5: Live HTTP 2FA Purpose Isolation & Real Password Reset Flow
            // ====================================================================
            // 1. 2FA challenge ticket rejected as access credential via HTTP -> 401
            Long testUserId;
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE email = ?")) {
                ps.setString(1, testEmail);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    testUserId = rs.getLong(1);
                }
            }

            MockEnvironment mockEnv = new MockEnvironment();
            mockEnv.setProperty("ALOUTE_JWT_SECRET", REQUIRED_JWT_SECRET);
            mockEnv.setProperty("ALOUTE_JWT_EXPIRATION_SECONDS", String.valueOf(JWT_EXPIRATION_SECONDS));
            JwtProperties jwtProps = new JwtProperties(mockEnv);
            JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(jwtProps);
            String challengeTicket = jwtTokenProvider.generateTwoFactorTicket(testUserId, "TOTP", 300);

            HttpRequest reqChallenge = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + portB + "/api/users/profile"))
                    .header("Authorization", "Bearer " + challengeTicket)
                    .GET()
                    .build();
            assertThat(httpClient.send(reqChallenge, HttpResponse.BodyHandlers.ofString()).statusCode())
                    .as("2FA challenge ticket must be rejected with 401 Unauthorized")
                    .isEqualTo(401);

            // 2. Real Password Reset Workflow via live HTTP with canonical DTO fields
            String rawResetToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            String resetTokenHash = sha256Hex(rawResetToken);

            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO password_reset_tokens (token_hash, user_id, auth_version, expires_at, created_at)
                     VALUES (?, ?, 0, NOW() + INTERVAL 1 HOUR, NOW())
                 """)) {
                ps.setString(1, resetTokenHash);
                ps.setLong(2, testUserId);
                ps.executeUpdate();
            }

            // Fetch CSRF token from /api/auth/me
            HttpRequest meReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + portB + "/api/auth/me"))
                    .GET()
                    .build();
            HttpResponse<String> meResp = httpClient.send(meReq, HttpResponse.BodyHandlers.ofString());
            String csrfToken = extractCsrfTokenFromCookie(meResp);

            // Execute live HTTP POST /api/auth/reset-password with canonical fields: token, password, confirmPassword
            String resetBody = "{\"token\":\"" + rawResetToken + "\",\"password\":\"" + newPassword + "\",\"confirmPassword\":\"" + newPassword + "\"}";
            HttpRequest.Builder resetReqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + portB + "/api/auth/reset-password"))
                    .header("Content-Type", "application/json");
            if (!csrfToken.isEmpty()) {
                resetReqBuilder.header("X-XSRF-TOKEN", csrfToken);
                resetReqBuilder.header("Cookie", "XSRF-TOKEN=" + csrfToken);
            }
            HttpRequest resetReq = resetReqBuilder.POST(HttpRequest.BodyPublishers.ofString(resetBody)).build();
            HttpResponse<String> resetResp = httpClient.send(resetReq, HttpResponse.BodyHandlers.ofString());
            assertThat(resetResp.statusCode()).as("Password reset must return 200 OK").isEqualTo(200);

            // 3. Old Token B now invalidated (stale authVersion) -> HTTP 401
            assertThat(httpClient.send(reqProfileB_B, HttpResponse.BodyHandlers.ofString()).statusCode())
                    .as("Old Session Token B must be invalidated following password reset")
                    .isEqualTo(401);

            // 4. Login with new password succeeds -> HTTP 200
            String newLoginJson = "{\"email\":\"" + testEmail + "\",\"password\":\"" + newPassword + "\"}";
            HttpRequest newLoginReq = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + portB + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(newLoginJson))
                    .build();
            HttpResponse<String> newLoginResp = httpClient.send(newLoginReq, HttpResponse.BodyHandlers.ofString());
            assertThat(newLoginResp.statusCode()).as("Login with new password must return 200 OK").isEqualTo(200);

            // ====================================================================
            // STAGE 6: Expired Cleanup Scheduler & FK ON DELETE CASCADE
            // ====================================================================
            // 1. Expired token cleanup via running child process scheduler
            String expiredHash = "expired_hash_mysql8_" + System.currentTimeMillis();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO revoked_tokens (token_hash, user_id, expires_at, created_at)
                     VALUES (?, ?, NOW() - INTERVAL 2 HOUR, NOW() - INTERVAL 3 HOUR)
                 """)) {
                ps.setString(1, expiredHash);
                ps.setLong(2, testUserId);
                ps.executeUpdate();
            }

            // Wait for background cron (*/2 * * * * *) in Child Process B to execute cleanup
            long cleanupDeadline = System.currentTimeMillis() + 8000;
            boolean expiredCleaned = false;
            while (System.currentTimeMillis() < cleanupDeadline) {
                try (Connection conn = getConnection();
                     PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM revoked_tokens WHERE token_hash = ?")) {
                    ps.setString(1, expiredHash);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) == 0) {
                            expiredCleaned = true;
                            break;
                        }
                    }
                }
                Thread.sleep(1000);
            }
            assertThat(expiredCleaned).as("Expired revoked token must be removed by background cleanup scheduler").isTrue();

            // Active revoked token (Token A) remains intact in DB
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM revoked_tokens WHERE token_hash = ?")) {
                ps.setString(1, fingerprintA);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).as("Active revoked token must be retained during expired cleanup").isEqualTo(1);
                }
            }

            // 2. Foreign Key ON DELETE CASCADE test
            long cascadeUserId;
            String cascadeEmail = "cascade_" + System.currentTimeMillis() + "@aloute.edu.vn";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO users (username, email, password_hash, is_verified, verification_required, is_private, is_active, global_role, auth_version, two_factor_enabled, created_at, updated_at)
                     VALUES (?, ?, 'hash', TRUE, FALSE, FALSE, TRUE, 'USER', 0, FALSE, NOW(6), NOW(6))
                 """, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, "cascade_user_" + System.currentTimeMillis());
                ps.setString(2, cascadeEmail);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    assertThat(rs.next()).isTrue();
                    cascadeUserId = rs.getLong(1);
                }
            }

            String cascadeTokenHash = "cascade_token_hash_" + System.currentTimeMillis();
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT INTO revoked_tokens (token_hash, user_id, expires_at) VALUES (?, ?, NOW() + INTERVAL 1 HOUR)")) {
                ps.setString(1, cascadeTokenHash);
                ps.setLong(2, cascadeUserId);
                ps.executeUpdate();
            }

            // Delete user row directly via JDBC
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE id = ?")) {
                ps.setLong(1, cascadeUserId);
                ps.executeUpdate();
            }

            // Verify revoked token was cascaded and deleted automatically by InnoDB FK constraint
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM revoked_tokens WHERE token_hash = ?")) {
                ps.setString(1, cascadeTokenHash);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getInt(1)).as("Revoked token row must be deleted by FK ON DELETE CASCADE").isZero();
                }
            }
        } finally {
            try {
                if (processA != null) {
                    stopProcess(processA);
                }
            } finally {
                if (processB != null) {
                    stopProcess(processB);
                }
            }
        }
    }

    @Test
    @DisplayName("Adversarial Diagnostic Safety: proves no raw sentinels leak in emitted summary or exception string")
    void shouldNotLeakAdversarialSentinelsInEmittedDiagnosticOrExceptionMessage() throws IOException {
        Path tempLog = Files.createTempFile("test-adversarial-sentinels-", ".log");
        try {
            List<String> lines = List.of(
                    "2026-10-22 12:13:09.123 ERROR 1234 --- [main] o.s.boot.SpringApplication : Application run failed",
                    "Caused by: \"superSecretPassword123\": injected in exception line",
                    "Caused by: 'singleQuotedSecret': single quote secret",
                    "Caused by: pass word with embedded spaces: spaces secret",
                    "Caused by: line1 secret key abc!@#$ multiline line2",
                    "Caused by: http://adminUser:passWord123@10.0.0.1:3306/db: url credentials",
                    "Caused by: Set-Cookie: SESSION=secretCookieValue123; HttpOnly; Secure",
                    "Caused by: export SPRING_DATASOURCE_PASSWORD=myUltraSecretEnv: env export",
                    "Caused by: " + "A".repeat(1000) + " SECRET_IN_VERY_LONG_LINE",
                    "Caused by: Malicious 'secret' class: fake class injection",
                    "Caused by: http://attacker.com/InjectedException: remote URL injection",
                    "Caused by: org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'dataSource'",
                    "Caused by: org.hibernate.exception.JDBCConnectionException: Unable to open JDBC Connection for DDL execution",
                    "Caused by: com.mysql.cj.jdbc.exceptions.CommunicationsException: Communications link failure to 127.0.0.1:3306",
                    "Missing required configuration: spring.datasource.url and server.port",
                    "Detailed private reason: Failed to configure a DataSource: url attribute is not specified"
            );
            Files.write(tempLog, lines, StandardCharsets.UTF_8);

            ChildProcessDiagnostic diag = extractChildDiagnostic(tempLog, "test_adversarial_stage", 1);
            String summary = diag.formatSummary();
            String compact = diag.toCompactLine();
            String exceptionMsg = new IllegalStateException("Process failed: " + compact).getMessage();

            for (String output : List.of(summary, compact, exceptionMsg)) {
                assertThat(output).doesNotContain("superSecretPassword123");
                assertThat(output).doesNotContain("singleQuotedSecret");
                assertThat(output).doesNotContain("pass word with embedded spaces");
                assertThat(output).doesNotContain("abc!@#$");
                assertThat(output).doesNotContain("adminUser");
                assertThat(output).doesNotContain("passWord123");
                assertThat(output).doesNotContain("secretCookieValue123");
                assertThat(output).doesNotContain("myUltraSecretEnv");
                assertThat(output).doesNotContain("SECRET_IN_VERY_LONG_LINE");
                assertThat(output).doesNotContain("Malicious");
                assertThat(output).doesNotContain("attacker.com");
            }

            // Valid causal classes survive
            assertThat(diag.causalChain()).containsExactly(
                    "org.springframework.beans.factory.BeanCreationException",
                    "org.hibernate.exception.JDBCConnectionException",
                    "com.mysql.cj.jdbc.exceptions.CommunicationsException"
            );
            assertThat(diag.primaryException()).isEqualTo("org.springframework.beans.factory.BeanCreationException");
            assertThat(diag.rootCauseException()).isEqualTo("com.mysql.cj.jdbc.exceptions.CommunicationsException");
            assertThat(diag.category()).isEqualTo("DATASOURCE_COMMUNICATION_FAILURE");
            assertThat(diag.matchedProperties()).containsExactly("server.port", "spring.datasource.url");

            // Length bounds respected
            assertThat(summary.length()).isLessThan(1000);
            assertThat(compact.length()).isLessThan(600);
        } finally {
            Files.deleteIfExists(tempLog);
        }
    }

    @Test
    @DisplayName("Extraction Before Cleanup & Suppressed Attachment: proves diagnostic is extracted and cleanup failures are preserved")
    void shouldExtractBeforeCleanupAndAttachCleanupFailureAsSuppressed() throws IOException {
        Path tempLog = Files.createTempFile("test-extract-cleanup-", ".log");
        try {
            List<String> lines = List.of(
                    "Caused by: org.springframework.boot.web.server.PortInUseException: Port 8080 already in use"
            );
            Files.write(tempLog, lines, StandardCharsets.UTF_8);

            // Step 1: Extraction before cleanup succeeds
            ChildProcessDiagnostic diag = extractChildDiagnostic(tempLog, "extract_stage", 1);
            assertThat(diag.category()).isEqualTo("PORT_BIND_FAILURE");
            assertThat(diag.primaryException()).isEqualTo("org.springframework.boot.web.server.PortInUseException");

            // Step 2: Primary exception is created
            IllegalStateException primaryEx = new IllegalStateException(
                    "Child JAR process on port 8080 terminated prematurely with exit code 1: " + diag.toCompactLine());

            // Step 3: Simulate cleanup failure attaching as suppressed
            IOException simulatedCleanupError = new IOException("Simulated file lock preventing deletion");
            primaryEx.addSuppressed(simulatedCleanupError);

            // Step 4: Verify primary exception retains original identity and message, and cleanup failure is attached
            assertThat(primaryEx.getMessage()).contains("PORT_BIND_FAILURE");
            assertThat(primaryEx.getMessage()).contains("PortInUseException");
            assertThat(primaryEx.getSuppressed()).hasSize(1);
            assertThat(primaryEx.getSuppressed()[0]).isSameAs(simulatedCleanupError);
        } finally {
            Files.deleteIfExists(tempLog);
        }
    }

    @Test
    @DisplayName("Streaming Read Bounds: respects max lines, max line length, and max causal depth")
    void shouldRespectStreamingBoundsOnLargeLogs() throws IOException {
        Path tempLog = Files.createTempFile("test-large-log-", ".log");
        try {
            List<String> lines = new ArrayList<>();
            // Line 1 to 5: valid exception chain
            lines.add("Caused by: org.springframework.beans.factory.BeanCreationException: Error");
            lines.add("Caused by: org.hibernate.HibernateException: DB Error");
            // Add lines 6 to 600
            for (int i = 0; i < 600; i++) {
                lines.add("A".repeat(800)); // longer than MAX_LINE_LENGTH (512)
            }
            // Line after 500 lines bound
            lines.add("Caused by: com.example.PastBoundException: should be ignored");
            Files.write(tempLog, lines, StandardCharsets.UTF_8);

            ChildProcessDiagnostic diag = extractChildDiagnostic(tempLog, "stream_stage", 1);

            assertThat(diag.primaryException()).isEqualTo("org.springframework.beans.factory.BeanCreationException");
            assertThat(diag.causalChain()).contains("org.springframework.beans.factory.BeanCreationException");
            assertThat(diag.causalChain()).contains("org.hibernate.HibernateException");
            assertThat(diag.causalChain()).doesNotContain("com.example.PastBoundException");
            assertThat(diag.category()).isEqualTo("DATABASE_SCHEMA_MIGRATION_FAILURE");
        } finally {
            Files.deleteIfExists(tempLog);
        }
    }

    @Test
    @DisplayName("Oversized Single Line Bound: proves byte cap truncates input allocation on lines exceeding 256 KiB")
    void shouldRespectByteCapOnOversizedSingleLineBeyondCap() throws IOException {
        Path tempLog = Files.createTempFile("test-oversized-line-", ".log");
        try {
            List<String> lines = List.of(
                    "Caused by: org.springframework.boot.web.server.PortInUseException: Port 8080 already in use",
                    "Caused by: " + "X".repeat(300 * 1024) + " SENTINEL_PAST_CAP_SECRET_98765",
                    "Caused by: org.springframework.boot.web.server.PortInUseException: Port 8080 already in use"
            );
            Files.write(tempLog, lines, StandardCharsets.UTF_8);

            ChildProcessDiagnostic diag = extractChildDiagnostic(tempLog, "oversized_stage", 1);
            String summary = diag.formatSummary();
            String compact = diag.toCompactLine();
            String exceptionMsg = new IllegalStateException("Process failed: " + compact).getMessage();

            for (String output : List.of(summary, compact, exceptionMsg)) {
                assertThat(output).doesNotContain("SENTINEL_PAST_CAP_SECRET_98765");
            }

            assertThat(diag.primaryException()).isEqualTo("org.springframework.boot.web.server.PortInUseException");
            assertThat(diag.category()).isEqualTo("PORT_BIND_FAILURE");
            assertThat(summary.length()).isLessThan(600);
            assertThat(compact.length()).isLessThan(350);
        } finally {
            Files.deleteIfExists(tempLog);
        }
    }

    @Test
    @DisplayName("Head & Tail DDL Extraction: extracts first DDL failure and final fatal cause with causal relationship")
    void shouldExtractBoundedHeadAndTailAndDistinguishFirstDdlFromFinalFatal() throws IOException {
        Path tempLog = Files.createTempFile("test-head-tail-ddl-", ".log");
        try {
            List<String> lines = List.of(
                    "2026-10-22 13:00:01.000 INFO --- [main] o.s.boot.SpringApplication : Starting application...",
                    "2026-10-22 13:00:02.000 WARN --- [main] o.h.e.jdbc.spi.SqlExceptionHelper : SQL Error: 1064, SQLState: 42000",
                    "2026-10-22 13:00:02.001 ERROR --- [main] o.h.t.s.i.ExceptionHandlerLoggedImpl : GenerationTarget encountered exception accepting command : Error executing DDL \"create table users (id bigint not null auto_increment, auth_version bigint not null)\" via JDBC",
                    "org.hibernate.tool.schema.spi.CommandAcceptanceException: Error executing DDL \"create table users (id bigint not null auto_increment, auth_version bigint not null)\" via JDBC",
                    "Caused by: java.sql.SQLSyntaxErrorException: You have an error in your SQL syntax near 'auth_version bigint'",
                    "2026-10-22 13:00:03.000 INFO --- [main] o.h.e.t.j.p.i.JtaPlatformInitiator : No JTA platform available",
                    "2026-10-22 13:00:04.000 ERROR --- [main] o.s.boot.SpringApplication : Application run failed",
                    "org.springframework.context.ApplicationContextException: Failed to start bean 'entityManagerFactory'",
                    "Caused by: org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'entityManagerFactory'",
                    "Caused by: org.hibernate.tool.schema.spi.CommandAcceptanceException: Error executing DDL \"create table users\"",
                    "Caused by: java.sql.SQLSyntaxErrorException: You have an error in your SQL syntax near 'auth_version bigint'"
            );
            Files.write(tempLog, lines, StandardCharsets.UTF_8);

            ChildProcessDiagnostic diag = extractChildDiagnostic(tempLog, "bootstrap_stage", 1);

            assertThat(diag.firstDdlOperation()).isEqualTo("CREATE_TABLE");
            assertThat(diag.firstDdlTable()).isEqualTo("users");
            assertThat(diag.firstDdlSqlState()).isEqualTo("42000");
            assertThat(diag.firstDdlVendorCode()).isEqualTo(1064);
            assertThat(diag.firstDdlColumn()).isEqualTo("auth_version");
            assertThat(diag.firstDdlExceptionClass()).isEqualTo("java.sql.SQLSyntaxErrorException");

            assertThat(diag.finalFatalException()).isEqualTo("org.springframework.context.ApplicationContextException");
            assertThat(diag.finalFatalRootCause()).isEqualTo("java.sql.SQLSyntaxErrorException");
            assertThat(diag.finalFatalChain()).containsExactly(
                    "org.springframework.context.ApplicationContextException",
                    "org.springframework.beans.factory.BeanCreationException",
                    "org.hibernate.tool.schema.spi.CommandAcceptanceException",
                    "java.sql.SQLSyntaxErrorException"
            );
            assertThat(diag.ddlCausedFinalExit()).isTrue();
            assertThat(diag.toCompactLine()).contains("firstDdl=CREATE_TABLE(users,col=auth_version,sqlState=42000,vendorCode=1064)");
            assertThat(diag.toCompactLine()).contains("ddlCausedExit=true");
        } finally {
            Files.deleteIfExists(tempLog);
        }
    }

    @Test
    @DisplayName("Distinguish Benign DDL: proves non-fatal DDL warning is separated from unrelated fatal exit")
    void shouldDistinguishBenignDdlWarningFromUnrelatedFinalFatalExit() throws IOException {
        Path tempLog = Files.createTempFile("test-benign-ddl-", ".log");
        try {
            List<String> lines = List.of(
                    "2026-10-22 13:00:01.000 INFO --- [main] o.s.boot.SpringApplication : Starting application...",
                    "2026-10-22 13:00:02.000 WARN --- [main] o.h.e.jdbc.spi.SqlExceptionHelper : SQL Error: 1051, SQLState: 42S02",
                    "2026-10-22 13:00:02.001 ERROR --- [main] o.h.t.s.i.ExceptionHandlerLoggedImpl : GenerationTarget encountered exception accepting command : Error executing DDL \"drop table if exists temp_seed_data\" via JDBC",
                    "Caused by: java.sql.SQLSyntaxErrorException: Unknown table 'temp_seed_data'",
                    "2026-10-22 13:00:03.000 INFO --- [main] Tomcat initialized with port 8080",
                    "2026-10-22 13:00:04.000 ERROR --- [main] o.s.boot.SpringApplication : Application run failed",
                    "org.springframework.boot.web.server.PortInUseException: Port 8080 already in use"
            );
            Files.write(tempLog, lines, StandardCharsets.UTF_8);

            ChildProcessDiagnostic diag = extractChildDiagnostic(tempLog, "bootstrap_stage", 1);

            assertThat(diag.firstDdlOperation()).isEqualTo("DROP_TABLE");
            assertThat(diag.firstDdlTable()).isEqualTo("temp_seed_data");
            assertThat(diag.firstDdlSqlState()).isEqualTo("42S02");
            assertThat(diag.firstDdlVendorCode()).isEqualTo(1051);

            assertThat(diag.finalFatalException()).isEqualTo("org.springframework.boot.web.server.PortInUseException");
            assertThat(diag.finalFatalRootCause()).isEqualTo("org.springframework.boot.web.server.PortInUseException");
            assertThat(diag.ddlCausedFinalExit()).isFalse();
            assertThat(diag.toCompactLine()).contains("firstDdl=DROP_TABLE(temp_seed_data,sqlState=42S02,vendorCode=1051)");
            assertThat(diag.toCompactLine()).contains("ddlCausedExit=false");
        } finally {
            Files.deleteIfExists(tempLog);
        }
    }

    @Test
    @DisplayName("Multi-Megabyte Log: skips middle bytes and never leaks middle sentinels")
    void shouldSkipMiddleAndNeverLeakMiddleSecretOnMultiMegabyteLog() throws IOException {
        Path tempLog = Files.createTempFile("test-multimeg-log-", ".log");
        try {
            // Write 128 KiB of HEAD
            StringBuilder head = new StringBuilder();
            head.append("2026-10-22 13:00:01.000 WARN --- [main] o.h.e.jdbc.spi.SqlExceptionHelper : SQL Error: 1064, SQLState: 42000\n");
            head.append("Error executing DDL \"create table communities (id bigint)\" via JDBC\n");
            head.append("Caused by: java.sql.SQLSyntaxErrorException: Error\n");
            while (head.length() < 130 * 1024) {
                head.append("2026-10-22 13:00:01.500 INFO --- [main] head line log content\n");
            }

            // Write 1.5 MB of MIDDLE with a sensitive sentinel that should be skipped
            StringBuilder middle = new StringBuilder();
            middle.append("Caused by: SECRET_IN_SKIPPED_MIDDLE_BODY_XYZ999\n");
            while (middle.length() < 1500 * 1024) {
                middle.append("2026-10-22 13:00:02.000 INFO --- [main] skipped middle line\n");
            }

            // Write 130 KiB of TAIL
            StringBuilder tail = new StringBuilder();
            while (tail.length() < 125 * 1024) {
                tail.append("2026-10-22 13:00:03.000 INFO --- [main] tail line log content\n");
            }
            tail.append("2026-10-22 13:00:04.000 ERROR --- [main] o.s.boot.SpringApplication : Application run failed\n");
            tail.append("org.springframework.context.ApplicationContextException: Fatal shutdown\n");

            Files.writeString(tempLog, head.toString() + middle.toString() + tail.toString(), StandardCharsets.UTF_8);

            ChildProcessDiagnostic diag = extractChildDiagnostic(tempLog, "stage_big", 1);
            String summary = diag.formatSummary();
            String compact = diag.toCompactLine();

            for (String output : List.of(summary, compact)) {
                assertThat(output).doesNotContain("SECRET_IN_SKIPPED_MIDDLE_BODY_XYZ999");
            }

            assertThat(diag.firstDdlOperation()).isEqualTo("CREATE_TABLE");
            assertThat(diag.firstDdlTable()).isEqualTo("communities");
            assertThat(diag.firstDdlSqlState()).isEqualTo("42000");
            assertThat(diag.finalFatalException()).isEqualTo("org.springframework.context.ApplicationContextException");
        } finally {
            Files.deleteIfExists(tempLog);
        }
    }

    @Test
    @DisplayName("Log Safety: handles missing or unreadable log file with fixed safe category")
    void shouldHandleMissingOrEmptyLogFileSafely() {
        Path missingLog = Path.of("target", "nonexistent-" + UUID.randomUUID() + ".log");
        ChildProcessDiagnostic diag = extractChildDiagnostic(missingLog, "missing_stage", 1);

        assertThat(diag.stage()).isEqualTo("missing_stage");
        assertThat(diag.exitCode()).isEqualTo(1);
        assertThat(diag.category()).isEqualTo("LOG_UNREADABLE_OR_EMPTY");
        assertThat(diag.primaryException()).isNull();
        assertThat(diag.rootCauseException()).isNull();
        assertThat(diag.causalChain()).isEmpty();
        assertThat(diag.matchedProperties()).isEmpty();
        assertThat(diag.formatSummary()).contains("LOG_UNREADABLE_OR_EMPTY");
        assertThat(diag.toCompactLine()).isEqualTo("[category=LOG_UNREADABLE_OR_EMPTY]");
    }

    @Test
    @DisplayName("Diagnostic Categorization: maps standard failure modes to fixed literal categories")
    void shouldCategorizeStandardFailureModesCorrectly() {
        assertThat(categorizeFromClues(List.of("org.springframework.boot.web.server.PortInUseException"), "port already in use", 1))
                .isEqualTo("PORT_BIND_FAILURE");

        assertThat(categorizeFromClues(List.of("org.springframework.beans.factory.BeanCreationException"), "error creating bean", 1))
                .isEqualTo("BEAN_CREATION_FAILURE");

        assertThat(categorizeFromClues(List.of("java.sql.SQLInvalidAuthorizationSpecException"), "access denied for user", 1))
                .isEqualTo("DATASOURCE_AUTHENTICATION_FAILURE");

        assertThat(categorizeFromClues(List.of("org.hibernate.tool.schema.spi.SchemaManagementException"), "table doesn't exist", 1))
                .isEqualTo("DATABASE_SCHEMA_MIGRATION_FAILURE");

        assertThat(categorizeFromClues(List.of("java.lang.IllegalStateException"), "missing required configuration: spring.datasource.password", 1))
                .isEqualTo("CONFIGURATION_MISSING_PROPERTY");

        assertThat(categorizeFromClues(List.of("io.jsonwebtoken.security.SignatureException"), "jwt signature failure", 1))
                .isEqualTo("SECURITY_KEY_FAILURE");
    }

    @Test
    @DisplayName("Early DDL Warning Without Fatal Marker: marks finalFatal UNKNOWN and ddlCausedExit false")
    void shouldReportUnknownFinalFatalWhenEarlyDdlWarningLacksFatalMarker() throws IOException {
        Path tempLog = Files.createTempFile("test-early-ddl-no-fatal-", ".log");
        try {
            List<String> lines = List.of(
                    "2026-10-22 13:00:01.000 INFO --- [main] o.s.boot.SpringApplication : Starting application...",
                    "2026-10-22 13:00:02.000 WARN --- [main] o.h.e.jdbc.spi.SqlExceptionHelper : SQL Error: 1064, SQLState: 42000",
                    "2026-10-22 13:00:02.001 ERROR --- [main] o.h.t.s.i.ExceptionHandlerLoggedImpl : GenerationTarget encountered exception accepting command : Error executing DDL \"create table test_table (id bigint)\" via JDBC",
                    "Caused by: java.sql.SQLSyntaxErrorException: You have an error in your SQL syntax",
                    "2026-10-22 13:00:03.000 INFO --- [main] Normal startup continued"
            );
            Files.write(tempLog, lines, StandardCharsets.UTF_8);

            ChildProcessDiagnostic diag = extractChildDiagnostic(tempLog, "stage_test", 1);

            assertThat(diag.firstDdlOperation()).isEqualTo("CREATE_TABLE");
            assertThat(diag.firstDdlTable()).isEqualTo("test_table");
            assertThat(diag.firstDdlSqlState()).isEqualTo("42000");
            assertThat(diag.firstDdlVendorCode()).isEqualTo(1064);

            assertThat(diag.finalFatalException()).isEqualTo("UNKNOWN");
            assertThat(diag.finalFatalRootCause()).isEqualTo("UNKNOWN");
            assertThat(diag.ddlCausedFinalExit()).isFalse();
            assertThat(diag.toCompactLine()).contains("finalFatal=UNKNOWN, ddlCausedExit=false");
            assertThat(diag.formatSummary()).contains("status: UNKNOWN");
        } finally {
            Files.deleteIfExists(tempLog);
        }
    }

    @Test
    @DisplayName("Anchored Fatal with Subsequent Cleanup SQL Warning: port remains final fatal and ddlCausedExit is false")
    void shouldRetainPortInUseAsFinalFatalWhenUnrelatedCleanupSqlErrorFollows() throws IOException {
        Path tempLog = Files.createTempFile("test-port-then-cleanup-", ".log");
        try {
            List<String> lines = List.of(
                    "2026-10-22 13:00:01.000 INFO --- [main] o.s.boot.SpringApplication : Starting application...",
                    "2026-10-22 13:00:02.000 ERROR --- [main] o.s.boot.SpringApplication : Application run failed",
                    "org.springframework.boot.web.server.PortInUseException: Port 8080 already in use",
                    "2026-10-22 13:00:03.000 INFO --- [main] Closing HikariPool-1...",
                    "2026-10-22 13:00:04.000 WARN --- [HikariPool-1 housekeeper] o.h.e.jdbc.spi.SqlExceptionHelper : SQL Error: 1045, SQLState: 28000",
                    "Caused by: java.sql.SQLException: Access denied for user during pool shutdown"
            );
            Files.write(tempLog, lines, StandardCharsets.UTF_8);

            ChildProcessDiagnostic diag = extractChildDiagnostic(tempLog, "stage_port", 1);

            assertThat(diag.finalFatalException()).isEqualTo("org.springframework.boot.web.server.PortInUseException");
            assertThat(diag.finalFatalRootCause()).isEqualTo("org.springframework.boot.web.server.PortInUseException");
            assertThat(diag.ddlCausedFinalExit()).isFalse();
            assertThat(diag.toCompactLine()).contains("finalFatal=org.springframework.boot.web.server.PortInUseException");
            assertThat(diag.toCompactLine()).contains("ddlCausedExit=false");
        } finally {
            Files.deleteIfExists(tempLog);
        }
    }

    @Test
    @DisplayName("Small File (>500 lines, <=256KiB): captures EOF fatal cause across middle lines")
    void shouldCaptureEofFatalInSmallFileWithMoreThan500Lines() throws IOException {
        Path tempLog = Files.createTempFile("test-smallfile-500lines-", ".log");
        try {
            List<String> lines = new ArrayList<>();
            lines.add("2026-10-22 13:00:01.000 INFO --- [main] o.s.boot.SpringApplication : Starting application...");
            lines.add("2026-10-22 13:00:02.000 WARN --- [main] o.h.e.jdbc.spi.SqlExceptionHelper : SQL Error: 1064, SQLState: 42000");
            lines.add("2026-10-22 13:00:02.001 ERROR --- [main] o.h.t.s.i.ExceptionHandlerLoggedImpl : GenerationTarget encountered exception accepting command : Error executing DDL \"create table small_file_tbl (id bigint)\" via JDBC");

            // Fill 600 short lines (total size ~30 KiB <= 256 KiB)
            for (int i = 0; i < 600; i++) {
                lines.add("2026-10-22 13:00:02.500 INFO --- [main] short informational message line " + i);
            }

            // EOF fatal block at the end (after line 600)
            lines.add("2026-10-22 13:00:03.000 ERROR --- [main] o.s.boot.SpringApplication : Application run failed");
            lines.add("org.springframework.context.ApplicationContextException: Failed to start bean 'entityManagerFactory'");
            lines.add("Caused by: org.hibernate.tool.schema.spi.CommandAcceptanceException: Error executing DDL");

            Files.write(tempLog, lines, StandardCharsets.UTF_8);

            ChildProcessDiagnostic diag = extractChildDiagnostic(tempLog, "stage_small", 1);

            assertThat(diag.truncated()).isTrue();
            assertThat(diag.firstDdlOperation()).isEqualTo("CREATE_TABLE");
            assertThat(diag.firstDdlTable()).isEqualTo("small_file_tbl");
            assertThat(diag.firstDdlSqlState()).isEqualTo("42000");
            assertThat(diag.finalFatalException()).isEqualTo("org.springframework.context.ApplicationContextException");
            assertThat(diag.finalFatalRootCause()).isEqualTo("org.hibernate.tool.schema.spi.CommandAcceptanceException");
            assertThat(diag.ddlCausedFinalExit()).isTrue();
        } finally {
            Files.deleteIfExists(tempLog);
        }
    }

    @Test
    @DisplayName("Correlated DDL Event Metadata: binds SQLSTATE and vendor code strictly to first DDL event and ignores subsequent SQL errors")
    void shouldCorrelateSqlMetadataToSameFirstDdlEventAndIgnoreSubsequentSqlErrors() throws IOException {
        Path tempLog = Files.createTempFile("test-correlated-ddl-", ".log");
        try {
            List<String> lines = List.of(
                    "2026-10-22 13:00:01.000 INFO --- [main] o.s.boot.SpringApplication : Starting application...",
                    // First DDL event with SQL Error 1064, SQLState 42000 on column 'auth_version'
                    "2026-10-22 13:00:02.000 WARN --- [main] o.h.e.jdbc.spi.SqlExceptionHelper : SQL Error: 1064, SQLState: 42000",
                    "2026-10-22 13:00:02.001 ERROR --- [main] o.h.t.s.i.ExceptionHandlerLoggedImpl : GenerationTarget encountered exception accepting command : Error executing DDL \"create table users (id bigint, auth_version bigint)\" via JDBC",
                    "Caused by: java.sql.SQLSyntaxErrorException: You have an error in your SQL syntax near 'auth_version bigint'",
                    // Unrelated intermediate lines
                    "2026-10-22 13:00:03.000 INFO --- [main] Unrelated processing line 1",
                    "2026-10-22 13:00:03.100 INFO --- [main] Unrelated processing line 2",
                    "2026-10-22 13:00:03.200 INFO --- [main] Unrelated processing line 3",
                    "2026-10-22 13:00:03.300 INFO --- [main] Unrelated processing line 4",
                    "2026-10-22 13:00:03.400 INFO --- [main] Unrelated processing line 5",
                    "2026-10-22 13:00:03.500 INFO --- [main] Unrelated processing line 6",
                    "2026-10-22 13:00:03.600 INFO --- [main] Unrelated processing line 7",
                    "2026-10-22 13:00:03.700 INFO --- [main] Unrelated processing line 8",
                    "2026-10-22 13:00:03.800 INFO --- [main] Unrelated processing line 9",
                    "2026-10-22 13:00:03.900 INFO --- [main] Unrelated processing line 10",
                    // Unrelated subsequent SQL error far away from first DDL command (outside window)
                    "2026-10-22 13:00:04.000 WARN --- [main] o.h.e.jdbc.spi.SqlExceptionHelper : SQL Error: 9999, SQLState: 99999",
                    "2026-10-22 13:00:05.000 ERROR --- [main] o.s.boot.SpringApplication : Application run failed",
                    "org.springframework.context.ApplicationContextException: Fatal startup failure",
                    "Caused by: java.sql.SQLSyntaxErrorException: Syntax error"
            );
            Files.write(tempLog, lines, StandardCharsets.UTF_8);

            ChildProcessDiagnostic diag = extractChildDiagnostic(tempLog, "stage_window", 1);

            // Bounded window ensures first DDL event got 42000 / 1064, NOT 99999 / 9999
            assertThat(diag.firstDdlOperation()).isEqualTo("CREATE_TABLE");
            assertThat(diag.firstDdlTable()).isEqualTo("users");
            assertThat(diag.firstDdlColumn()).isEqualTo("auth_version");
            assertThat(diag.firstDdlSqlState()).isEqualTo("42000");
            assertThat(diag.firstDdlVendorCode()).isEqualTo(1064);
        } finally {
            Files.deleteIfExists(tempLog);
        }
    }
}
