/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.com.log;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * Lightweight structured logger without third-party backends.
 *
 * <p>Messages are written as single-line UTF-8 records with severity, job ID,
 * component, and privacy redaction for common secret patterns. Rotation is
 * size-based and keeps one backup.</p>
 */
public final class AppLog {

    public enum Level {
        DEBUG, INFO, WARN, ERROR
    }

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT)
                    .withZone(ZoneOffset.UTC);
    private static final Pattern PASSWORD = Pattern.compile(
            "(?i)(password|passwd|pwd|secret|token|api[_-]?key|authorization)\\s*[=:]\\s*\\S+");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._\\-+=/]+");
    private static final long MAX_BYTES = 5L * 1024L * 1024L;

    private static final AtomicReference<Path> LOG_FILE = new AtomicReference<>();
    private static final AtomicReference<String> JOB_ID = new AtomicReference<>("-");
    private static final Object LOCK = new Object();

    private AppLog() {
        // Utility class.
    }

    public static void configure(Path logFile) {
        LOG_FILE.set(Objects.requireNonNull(logFile, "logFile"));
        try {
            Path parent = logFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException ignored) {
            // Doctor and headless tools must still start.
        }
    }

    public static void setJobId(String jobId) {
        if (jobId == null || jobId.trim().isEmpty()) {
            JOB_ID.set("-");
        } else {
            JOB_ID.set(jobId.trim());
        }
    }

    public static String newJobId() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        setJobId(id);
        return id;
    }

    public static void debug(String component, String message) {
        log(Level.DEBUG, component, message, null);
    }

    public static void info(String component, String message) {
        log(Level.INFO, component, message, null);
    }

    public static void warn(String component, String message) {
        log(Level.WARN, component, message, null);
    }

    public static void error(String component, String message) {
        log(Level.ERROR, component, message, null);
    }

    public static void error(String component, String message, Throwable cause) {
        log(Level.ERROR, component, message, cause);
    }

    public static String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String redacted = PASSWORD.matcher(text).replaceAll("$1=***");
        return BEARER.matcher(redacted).replaceAll("Bearer ***");
    }

    public static Path currentLogFile() {
        return LOG_FILE.get();
    }

    private static void log(Level level, String component, String message, Throwable cause) {
        String safeComponent = component == null || component.trim().isEmpty() ? "app" : component.trim();
        String safeMessage = redact(message == null ? "" : message.replace('\n', ' ').replace('\r', ' '));
        StringBuilder line = new StringBuilder(160);
        line.append(TIME.format(Instant.now()))
                .append(' ')
                .append(level.name())
                .append(" job=")
                .append(JOB_ID.get())
                .append(" component=")
                .append(safeComponent)
                .append(" msg=")
                .append(safeMessage);
        if (cause != null) {
            line.append(" cause=")
                    .append(cause.getClass().getSimpleName())
                    .append(':')
                    .append(redact(String.valueOf(cause.getMessage())).replace('\n', ' '));
        }
        String record = line.toString();
        if (level == Level.ERROR || level == Level.WARN) {
            System.err.println(record);
        } else {
            System.out.println(record);
        }
        Path file = LOG_FILE.get();
        if (file == null) {
            return;
        }
        synchronized (LOCK) {
            try {
                rotateIfNeeded(file);
                StringBuilder payload = new StringBuilder(record);
                if (cause != null) {
                    payload.append(System.lineSeparator()).append(stackTrace(cause));
                }
                payload.append(System.lineSeparator());
                Files.writeString(file, payload.toString(), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ignored) {
                // Logging must never break calculation paths.
            }
        }
    }

    private static void rotateIfNeeded(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) < MAX_BYTES) {
            return;
        }
        Path backup = file.resolveSibling(file.getFileName().toString() + ".1");
        Files.deleteIfExists(backup);
        Files.move(file, backup);
    }

    private static String stackTrace(Throwable cause) {
        StringWriter writer = new StringWriter();
        cause.printStackTrace(new PrintWriter(writer));
        return redact(writer.toString());
    }
}
