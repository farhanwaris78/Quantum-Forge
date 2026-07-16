/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import quantumforge.com.path.QEPath;

/**
 * Snapshot of a configured Quantum ESPRESSO binary directory.
 *
 * <p>Presence of an executable is not scientific validation. This profile only
 * records what the selected directory actually contains and what {@code pw.x}
 * reports as its version banner.</p>
 */
public final class QEExecutableProfile {

    private static final Pattern VERSION = Pattern.compile(
            "(?i)Program\\s+PWSCF\\s+v\\.?\\s*([0-9]+(?:\\.[0-9]+){1,3})|"
                    + "(?i)Quantum\\s+ESPRESSO\\s+v\\.?\\s*([0-9]+(?:\\.[0-9]+){1,3})|"
                    + "(?i)\\bv\\.?\\s*([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)\\b");

    private static final String[] CORE = {
            "pw.x", "ph.x", "dos.x", "projwfc.x", "bands.x", "neb.x", "pp.x", "dynmat.x", "matdyn.x", "q2r.x"
    };

    private final Path binDirectory;
    private final Map<String, Path> executables;
    private final String versionText;
    private final String version;
    private final boolean mpiLauncherPresent;
    private final List<String> diagnostics;

    private QEExecutableProfile(Path binDirectory, Map<String, Path> executables, String versionText,
                                String version, boolean mpiLauncherPresent, List<String> diagnostics) {
        this.binDirectory = binDirectory;
        this.executables = Collections.unmodifiableMap(new LinkedHashMap<>(executables));
        this.versionText = versionText;
        this.version = version;
        this.mpiLauncherPresent = mpiLauncherPresent;
        this.diagnostics = Collections.unmodifiableList(new ArrayList<>(diagnostics));
    }

    public static QEExecutableProfile probeConfigured() {
        String configured = QEPath.getPath();
        Path directory = null;
        if (configured != null && !configured.trim().isEmpty()) {
            directory = Path.of(configured.trim());
        }
        return probe(directory);
    }

    public static QEExecutableProfile probe(Path binDirectory) {
        Map<String, Path> found = new LinkedHashMap<>();
        List<String> diagnostics = new ArrayList<>();
        Path resolved = binDirectory;
        if (resolved == null) {
            diagnostics.add("No Quantum ESPRESSO bin directory is configured.");
            return new QEExecutableProfile(null, found, "", "", false, diagnostics);
        }
        if (!Files.isDirectory(resolved)) {
            diagnostics.add("Configured QE path is not a directory: " + resolved);
            return new QEExecutableProfile(resolved, found, "", "", false, diagnostics);
        }

        for (String name : CORE) {
            Path candidate = resolveExecutable(resolved, name);
            if (candidate != null) {
                found.put(name, candidate);
            }
        }
        if (!found.containsKey("pw.x")) {
            diagnostics.add("pw.x was not found under " + resolved);
        }

        String versionText = "";
        String version = "";
        Path pw = found.get("pw.x");
        if (pw != null) {
            try {
                versionText = runBanner(pw, Duration.ofSeconds(8));
                version = extractVersion(versionText);
                if (version.isEmpty()) {
                    diagnostics.add("Could not parse a QE version from pw.x output.");
                }
            } catch (Exception ex) {
                diagnostics.add("Failed to execute pw.x for version detection: " + ex.getMessage());
            }
        }

        boolean mpi = resolveExecutable(resolved, "mpirun") != null
                || resolveExecutable(resolved, "mpiexec") != null
                || findOnPath("mpirun") != null
                || findOnPath("mpiexec") != null
                || (QEPath.getMPIPath() != null && !QEPath.getMPIPath().isBlank()
                    && (resolveExecutable(Path.of(QEPath.getMPIPath()), "mpirun") != null
                        || resolveExecutable(Path.of(QEPath.getMPIPath()), "mpiexec") != null));

        return new QEExecutableProfile(resolved, found, versionText, version, mpi, diagnostics);
    }

    public Path getBinDirectory() {
        return this.binDirectory;
    }

    public Map<String, Path> getExecutables() {
        return this.executables;
    }

    public String getVersionText() {
        return this.versionText;
    }

    public String getVersion() {
        return this.version;
    }

    public boolean isMpiLauncherPresent() {
        return this.mpiLauncherPresent;
    }

    public List<String> getDiagnostics() {
        return this.diagnostics;
    }

    public boolean hasCorePw() {
        return this.executables.containsKey("pw.x");
    }

    public String toDoctorReport() {
        StringBuilder out = new StringBuilder();
        if (this.binDirectory == null) {
            out.append("[WARN] QE profile: no bin directory configured.");
            return out.toString();
        }
        out.append(this.hasCorePw() ? "[OK] " : "[WARN] ");
        out.append("QE profile directory: ").append(this.binDirectory);
        if (!this.version.isEmpty()) {
            out.append(" (pw.x ").append(this.version).append(')');
        }
        out.append(System.lineSeparator());
        out.append("      executables: ").append(String.join(", ", this.executables.keySet()));
        out.append(System.lineSeparator());
        out.append("      MPI launcher available: ").append(this.mpiLauncherPresent ? "yes" : "no/unknown");
        for (String diagnostic : this.diagnostics) {
            out.append(System.lineSeparator()).append("      note: ").append(diagnostic);
        }
        return out.toString();
    }

    static String extractVersion(String banner) {
        if (banner == null || banner.isBlank()) {
            return "";
        }
        Matcher matcher = VERSION.matcher(banner);
        if (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                if (matcher.group(i) != null) {
                    return matcher.group(i);
                }
            }
        }
        return "";
    }

    private static Path resolveExecutable(Path directory, String name) {
        Objects.requireNonNull(directory, "directory");
        Path unix = directory.resolve(name);
        if (Files.isRegularFile(unix) && Files.isExecutable(unix)) {
            return unix;
        }
        Path windows = directory.resolve(name + ".exe");
        if (Files.isRegularFile(windows)) {
            return windows;
        }
        Path bat = directory.resolve(name + ".bat");
        if (Files.isRegularFile(bat)) {
            return bat;
        }
        return null;
    }

    private static Path findOnPath(String name) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return null;
        }
        for (String directory : path.split(java.util.regex.Pattern.quote(
                System.getProperty("path.separator", ":")))) {
            if (directory == null || directory.isBlank()) {
                continue;
            }
            Path candidate = resolveExecutable(Path.of(directory), name);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static String runBanner(Path executable, Duration timeout) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(executable.toString(), "-v");
        builder.redirectErrorStream(true);
        Process process = builder.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
                if (output.length() > 16_384) {
                    break;
                }
            }
        }
        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("pw.x version probe timed out");
        }
        if (output.length() == 0) {
            // Some builds print the banner only without -v and exit non-zero without input.
            ProcessBuilder fallback = new ProcessBuilder(executable.toString());
            fallback.redirectErrorStream(true);
            Process second = fallback.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(second.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                long deadline = System.nanoTime() + timeout.toNanos();
                while (System.nanoTime() < deadline && (line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                    if (output.length() > 16_384 || line.toLowerCase(Locale.ROOT).contains("usage")) {
                        break;
                    }
                }
            } finally {
                second.destroyForcibly();
            }
        }
        return output.toString();
    }
}
