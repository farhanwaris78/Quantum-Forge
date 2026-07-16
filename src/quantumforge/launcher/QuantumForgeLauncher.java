/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.launcher;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import quantumforge.app.QEFXMain;
import quantumforge.capability.CapabilityRegistry;
import quantumforge.com.env.Environments;
import quantumforge.com.log.AppLog;
import quantumforge.com.log.CrashReporter;
import quantumforge.run.QEExecutableProfile;
import quantumforge.ver.Version;

/**
 * Stable command-line entry point for packaged distributions.
 *
 * <p>This class deliberately does not extend {@code javafx.application.Application}.
 * That lets the JVM load JavaFX from the packaged module path before handing control
 * to the GUI, and provides diagnostics that also work on headless machines.</p>
 */
public final class QuantumForgeLauncher {

    private static final List<String> QE_EXECUTABLES = Arrays.asList(
            "pw.x", "ph.x", "dos.x", "projwfc.x", "bands.x", "neb.x", "pp.x");

    private QuantumForgeLauncher() {
        // Utility class.
    }

    public static void main(String[] args) {
        configureDiagnostics();
        if (hasOnlyOption(args, "--version") || hasOnlyOption(args, "-V")) {
            printVersion();
            return;
        }
        if (hasOnlyOption(args, "--help") || hasOnlyOption(args, "-h")) {
            printHelp();
            return;
        }
        if (hasOnlyOption(args, "--doctor")) {
            int status = runDoctor();
            if (status != 0) {
                System.exit(status);
            }
            return;
        }
        if (hasOnlyOption(args, "--capabilities")) {
            System.out.print(CapabilityRegistry.createReport());
            return;
        }
        if (hasOnlyOption(args, "--update") || hasOnlyOption(args, "--uninstall")) {
            System.err.println("This native installation is managed by your operating-system installer/package manager.");
            System.err.println("Use apt/pacman, Windows Installed apps, or the macOS app/package procedure.");
            System.err.println("Portable installations handle this option in the quantumforge launcher script.");
            System.exit(2);
        }

        AppLog.info("launcher", "Starting QuantumForge GUI " + Version.VERSION);
        QEFXMain.main(args == null ? new String[0] : args);
    }

    private static void configureDiagnostics() {
        try {
            String projects = Environments.getProjectsPath();
            if (projects != null && !projects.isBlank()) {
                Path logDir = Path.of(projects, "logs");
                AppLog.configure(logDir.resolve("quantumforge.log"));
                CrashReporter.install(logDir.resolve("crashes"));
            }
        } catch (RuntimeException ignored) {
            // Headless doctor and packaging smoke tests must still run.
        }
    }

    private static boolean hasOnlyOption(String[] args, String option) {
        return args != null && args.length == 1 && option.equals(args[0]);
    }

    private static void printVersion() {
        System.out.println(Version.VERSION_NAME + " " + Version.VERSION);
        System.out.println("Java " + System.getProperty("java.version", "unknown")
                + " (" + System.getProperty("java.vendor", "unknown") + ")");
        System.out.println("Quantum ESPRESSO input compatibility target: "
                + Version.SUPPORTED_QE_VERSION);
    }

    private static void printHelp() {
        System.out.println("Usage: quantumforge [OPTION] [FILE ...]");
        System.out.println();
        System.out.println("Launch the QuantumForge desktop application or open input files.");
        System.out.println();
        System.out.println("  --doctor       check Java, display, and external calculation tools");
        System.out.println("  --capabilities show honest integration maturity and required next work");
        System.out.println("  --version      print application and runtime versions");
        System.out.println("  --update     safely update a portable user installation");
        System.out.println("  --uninstall  remove a portable user installation (keeps research data)");
        System.out.println("  --help       show this help");
        System.out.println();
        System.out.println("Update and uninstall are implemented by the platform launcher scripts.");
    }

    private static int runDoctor() {
        int failures = 0;
        System.out.println("QuantumForge diagnostic report");
        System.out.println("==============================");
        System.out.println("[OK] Application version: " + Version.VERSION);

        int javaFeature = Runtime.version().feature();
        if (javaFeature >= 17) {
            System.out.println("[OK] Java runtime: " + System.getProperty("java.runtime.version"));
        } else {
            System.out.println("[FAIL] Java 17 or newer is required; found " + javaFeature);
            failures++;
        }

        boolean headless = GraphicsEnvironment.isHeadless();
        String display = System.getenv("DISPLAY");
        String wayland = System.getenv("WAYLAND_DISPLAY");
        if (isUnixLike() && (display == null || display.isBlank())
                && (wayland == null || wayland.isBlank())) {
            System.out.println("[WARN] No DISPLAY or WAYLAND_DISPLAY is set. The GUI cannot open in this shell.");
        } else if (headless) {
            System.out.println("[WARN] Java reports a headless graphics environment.");
        } else {
            System.out.println("[OK] Graphical display detected.");
        }

        List<String> missingQe = new ArrayList<>();
        for (String executable : QE_EXECUTABLES) {
            if (findOnPath(executable) == null) {
                missingQe.add(executable);
            }
        }
        if (missingQe.isEmpty()) {
            System.out.println("[OK] Core Quantum ESPRESSO executables are available on PATH.");
        } else {
            System.out.println("[WARN] Quantum ESPRESSO tools not found on PATH: "
                    + String.join(", ", missingQe));
            System.out.println("       Configure the QE directory inside QuantumForge before calculations.");
        }

        System.out.println(QEExecutableProfile.probeConfigured().toDoctorReport());

        Path log = AppLog.currentLogFile();
        if (log != null) {
            System.out.println("[OK] Structured log file: " + log);
        } else {
            System.out.println("[INFO] Structured log file is not configured yet.");
        }

        reportOptional("thermo_pw", "thermo_pw.x", CapabilityRegistry.THERMO_PW);
        reportOptional("phonopy", "phonopy", CapabilityRegistry.PHONOPY);
        reportOptional("BoltzTraP2", isWindows() ? "btp2.exe" : "btp2", CapabilityRegistry.BOLTZTRAP2);
        reportOptional("XCrySDen", isWindows() ? "xcrysden.exe" : "xcrysden", CapabilityRegistry.XCRYSDEN);
        reportOptional("VASP", isWindows() ? "vasp_std.exe" : "vasp_std", CapabilityRegistry.VASP);
        reportOptional("CASTEP", isWindows() ? "castep.exe" : "castep", CapabilityRegistry.CASTEP);

        if (failures == 0) {
            System.out.println("Result: launcher prerequisites passed (warnings may require configuration). ");
        } else {
            System.out.println("Result: " + failures + " required prerequisite(s) failed.");
        }
        return failures == 0 ? 0 : 2;
    }

    private static void reportOptional(String name, String executable, String capabilityId) {
        File path = findOnPath(executable);
        String integration = CapabilityRegistry.get(capabilityId).getStatus().getLabel();
        if (path == null) {
            System.out.println("[INFO] Optional " + name + " executable not found (" + executable
                    + "); integration status: " + integration + ".");
        } else {
            System.out.println("[INFO] Optional " + name + " executable found at " + path.getAbsolutePath()
                    + "; integration status: " + integration + " (executable presence is not workflow validation).");
        }
    }

    private static File findOnPath(String executable) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        names.add(executable);
        if (isWindows() && !executable.toLowerCase().endsWith(".exe")) {
            names.add(executable + ".exe");
            names.add(executable + ".bat");
            names.add(executable + ".cmd");
        }
        for (String directory : path.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            for (String name : names) {
                File candidate = new File(directory, name);
                try {
                    if (candidate.isFile() && (isWindows() || candidate.canExecute())) {
                        return candidate;
                    }
                } catch (SecurityException ignored) {
                    // Continue searching; doctor must not fail because one PATH entry is unreadable.
                }
            }
        }
        return null;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().startsWith("windows");
    }

    private static boolean isUnixLike() {
        return !isWindows();
    }
}
