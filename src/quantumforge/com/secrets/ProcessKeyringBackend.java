/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.com.secrets;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import quantumforge.com.log.AppLog;

/**
 * OS keyring access via platform CLI tools (no JNI):
 * <ul>
 *   <li>Linux: {@code secret-tool} (libsecret / Secret Service)</li>
 *   <li>macOS: {@code security} keychain</li>
 *   <li>Windows: {@code cmdkey} / PowerShell DPAPI-backed Credential Manager is
 *       intentionally limited; this backend reports unavailable unless
 *       {@code secret-tool}-compatible tooling is present</li>
 * </ul>
 *
 * <p>If the tool is missing or fails, {@link #isAvailable()} is false and
 * {@link SecretStore} keeps secrets in process memory only.</p>
 */
public final class ProcessKeyringBackend implements SecretStore.OsKeyringBackend {

    public enum Platform {
        LIBSECRET,
        KEYCHAIN,
        UNAVAILABLE
    }

    private static final String SERVICE = "ai.quantumforge.secrets";
    private final Platform platform;
    private final String toolPath;

    public ProcessKeyringBackend(Platform platform, String toolPath) {
        this.platform = platform == null ? Platform.UNAVAILABLE : platform;
        this.toolPath = toolPath;
    }

    public static ProcessKeyringBackend detect() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("linux")) {
            String tool = findOnPath("secret-tool");
            if (tool != null) {
                return new ProcessKeyringBackend(Platform.LIBSECRET, tool);
            }
            return new ProcessKeyringBackend(Platform.UNAVAILABLE, null);
        }
        if (os.contains("mac")) {
            String tool = findOnPath("security");
            if (tool != null) {
                return new ProcessKeyringBackend(Platform.KEYCHAIN, tool);
            }
            return new ProcessKeyringBackend(Platform.UNAVAILABLE, null);
        }
        // Windows Credential Manager needs a dedicated helper; do not pretend.
        return new ProcessKeyringBackend(Platform.UNAVAILABLE, null);
    }

    @Override
    public String name() {
        return switch (this.platform) {
            case LIBSECRET -> "libsecret/secret-tool";
            case KEYCHAIN -> "macos-keychain/security";
            case UNAVAILABLE -> "os-keyring (unavailable)";
        };
    }

    @Override
    public boolean isAvailable() {
        return this.platform != Platform.UNAVAILABLE && this.toolPath != null;
    }

    @Override
    public void store(String key, char[] secret) {
        if (!isAvailable() || key == null || secret == null) {
            return;
        }
        try {
            if (this.platform == Platform.LIBSECRET) {
                run(List.of(this.toolPath, "store", "--label", "QuantumForge:" + key,
                                "service", SERVICE, "account", key),
                        new String(secret));
            } else if (this.platform == Platform.KEYCHAIN) {
                // delete then add to update
                run(List.of(this.toolPath, "delete-generic-password",
                        "-s", SERVICE, "-a", key), null, true);
                run(List.of(this.toolPath, "add-generic-password",
                                "-s", SERVICE, "-a", key, "-w", new String(secret), "-U"),
                        null);
            }
        } catch (Exception ex) {
            AppLog.warn("keyring", "store failed for " + key + ": " + ex.getMessage());
            throw new RuntimeException("keyring store failed", ex);
        }
    }

    @Override
    public char[] load(String key) {
        if (!isAvailable() || key == null) {
            return null;
        }
        try {
            if (this.platform == Platform.LIBSECRET) {
                String out = run(List.of(this.toolPath, "lookup",
                        "service", SERVICE, "account", key), null, true);
                return out == null || out.isEmpty() ? null : out.toCharArray();
            }
            if (this.platform == Platform.KEYCHAIN) {
                String out = run(List.of(this.toolPath, "find-generic-password",
                        "-s", SERVICE, "-a", key, "-w"), null, true);
                return out == null || out.isEmpty() ? null : out.trim().toCharArray();
            }
            return null;
        } catch (Exception ex) {
            AppLog.warn("keyring", "load failed for " + key + ": " + ex.getMessage());
            return null;
        }
    }

    @Override
    public void delete(String key) {
        if (!isAvailable() || key == null) {
            return;
        }
        try {
            if (this.platform == Platform.LIBSECRET) {
                run(List.of(this.toolPath, "clear", "service", SERVICE, "account", key), null, true);
            } else if (this.platform == Platform.KEYCHAIN) {
                run(List.of(this.toolPath, "delete-generic-password",
                        "-s", SERVICE, "-a", key), null, true);
            }
        } catch (Exception ex) {
            AppLog.warn("keyring", "delete failed for " + key + ": " + ex.getMessage());
        }
    }

    private String run(List<String> command, String stdin) throws Exception {
        return run(command, stdin, false);
    }

    private String run(List<String> command, String stdin, boolean allowNonZero) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        if (stdin != null) {
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    process.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(stdin);
                writer.flush();
            }
        } else {
            process.getOutputStream().close();
        }
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (out.length() > 0) {
                    out.append('\n');
                }
                out.append(line);
            }
        }
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("keyring command timed out");
        }
        if (process.exitValue() != 0 && !allowNonZero) {
            throw new IllegalStateException("keyring exit " + process.exitValue()
                    + ": " + out);
        }
        return out.toString();
    }

    private static String findOnPath(String name) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return null;
        }
        for (String dir : path.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            if (dir == null || dir.isBlank()) {
                continue;
            }
            java.nio.file.Path candidate = java.nio.file.Path.of(dir, name);
            try {
                if (java.nio.file.Files.isRegularFile(candidate)
                        && java.nio.file.Files.isExecutable(candidate)) {
                    return candidate.toString();
                }
            } catch (SecurityException ignored) {
                // continue
            }
        }
        return null;
    }
}
