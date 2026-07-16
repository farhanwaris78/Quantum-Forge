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
import java.time.Instant;
import java.util.Objects;

import quantumforge.ver.Version;

/**
 * Local-first crash reporter.
 *
 * <p>Creates a redacted diagnostic text bundle under the QuantumForge home
 * directory. Nothing is uploaded automatically.</p>
 */
public final class CrashReporter {

    private static volatile boolean installed;

    private CrashReporter() {
        // Utility class.
    }

    public static synchronized void install(Path diagnosticsDirectory) {
        if (installed) {
            return;
        }
        Objects.requireNonNull(diagnosticsDirectory, "diagnosticsDirectory");
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                Path report = writeBundle(diagnosticsDirectory, thread, throwable);
                AppLog.error("crash", "Uncaught exception; diagnostic bundle: " + report, throwable);
                System.err.println("QuantumForge crash report written to: " + report);
            } catch (Exception reportingFailure) {
                System.err.println("QuantumForge could not write a crash report: " + reportingFailure);
                throwable.printStackTrace(System.err);
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });
        installed = true;
        AppLog.info("crash", "Local crash reporter installed at " + diagnosticsDirectory);
    }

    public static Path writeBundle(Path diagnosticsDirectory, Thread thread, Throwable throwable)
            throws IOException {
        Files.createDirectories(diagnosticsDirectory);
        String stamp = Instant.now().toString().replace(':', '-');
        Path report = diagnosticsDirectory.resolve("crash-" + stamp + ".txt");
        StringWriter body = new StringWriter();
        try (PrintWriter out = new PrintWriter(body)) {
            out.println("QuantumForge local crash report");
            out.println("Generated: " + Instant.now());
            out.println("Application: " + Version.VERSION_NAME + " " + Version.VERSION);
            out.println("QE compatibility target: " + Version.SUPPORTED_QE_VERSION);
            out.println("Java: " + System.getProperty("java.runtime.version", "unknown"));
            out.println("OS: " + System.getProperty("os.name", "unknown")
                    + " " + System.getProperty("os.version", "")
                    + " / " + System.getProperty("os.arch", ""));
            out.println("Thread: " + (thread == null ? "unknown" : thread.getName()));
            out.println();
            out.println("--- exception ---");
            if (throwable != null) {
                throwable.printStackTrace(out);
            } else {
                out.println("(no throwable)");
            }
            out.println();
            out.println("--- properties (redacted) ---");
            out.println("user.home=" + System.getProperty("user.home", ""));
            out.println("java.home=" + System.getProperty("java.home", ""));
            out.println("file.encoding=" + System.getProperty("file.encoding", ""));
            out.println("DISPLAY=" + AppLog.redact(String.valueOf(System.getenv("DISPLAY"))));
            out.println();
            out.println("This report is local only. No automatic upload is performed.");
            out.println("Do not attach licensed POTCAR files, private structures, or API keys.");
        }
        Files.writeString(report, AppLog.redact(body.toString()), StandardCharsets.UTF_8);
        return report;
    }
}
