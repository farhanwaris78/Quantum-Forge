/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.com.file.AtomicFileWriter;
import quantumforge.com.log.AppLog;
import quantumforge.operation.OperationResult;

/**
 * Export the current structure to a temporary XSF file and launch XCrySDen.
 *
 * <p>Uses an argument array (never a shell string). Returns typed results so a
 * missing binary cannot look like a successful open.</p>
 */
public final class XCrySDenLauncher {

    private XCrySDenLauncher() {
        // Utility.
    }

    public static OperationResult<Path> exportXsf(Cell cell, Path target) {
        if (cell == null) {
            return OperationResult.failed("CELL_NULL", "No structure cell is available.", null);
        }
        if (target == null) {
            return OperationResult.failed("TARGET_NULL", "No XSF target path was supplied.", null);
        }
        try {
            AtomicFileWriter.writeUtf8(target, toXsf(cell));
            return OperationResult.success("XSF_WRITTEN", "Wrote XSF to " + target, target);
        } catch (IOException ex) {
            return OperationResult.failed("XSF_WRITE_FAILED",
                    "Could not write XSF: " + ex.getMessage(), ex);
        }
    }

    public static OperationResult<Process> launch(Cell cell) {
        Path executable = findExecutable();
        if (executable == null) {
            return OperationResult.unsupported("XCRYSDEN_NOT_FOUND",
                    "xcrysden was not found on PATH. Install XCrySDen and ensure the executable is available.");
        }
        try {
            Path tempDir = Files.createTempDirectory("quantumforge-xcrysden-");
            Path xsf = tempDir.resolve("structure.xsf");
            OperationResult<Path> export = exportXsf(cell, xsf);
            if (!export.isSuccess()) {
                return OperationResult.failed(export.getCode(), export.getMessage(), null);
            }
            List<String> command = new ArrayList<>();
            command.add(executable.toString());
            command.add("--xsf");
            command.add(xsf.toAbsolutePath().toString());
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            AppLog.info("xcrysden", "Launched " + String.join(" ", command));
            if (process.waitFor(400, TimeUnit.MILLISECONDS)) {
                int code = process.exitValue();
                if (code != 0) {
                    return OperationResult.failed("XCRYSDEN_EXITED",
                            "XCrySDen exited immediately with code " + code
                                    + ". Check X11/DISPLAY and the XCrySDen installation.",
                            null);
                }
            }
            return OperationResult.success("XCRYSDEN_LAUNCHED",
                    "Launched XCrySDen with " + xsf.getFileName(), process);
        } catch (IOException ex) {
            return OperationResult.failed("XCRYSDEN_LAUNCH_FAILED",
                    "Could not start XCrySDen: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return OperationResult.cancelled("XCRYSDEN_INTERRUPTED",
                    "XCrySDen launch was interrupted.");
        }
    }

    public static Path findExecutable() {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return null;
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT)
                .startsWith("windows");
        String[] names = windows
                ? new String[] {"xcrysden.exe", "xcrysden.bat", "xcrysden"}
                : new String[] {"xcrysden"};
        for (String directory : path.split(java.util.regex.Pattern.quote(
                java.io.File.pathSeparator))) {
            if (directory == null || directory.isBlank()) {
                continue;
            }
            for (String name : names) {
                Path candidate = Path.of(directory, name);
                try {
                    if (Files.isRegularFile(candidate)
                            && (windows || Files.isExecutable(candidate))) {
                        return candidate;
                    }
                } catch (SecurityException ignored) {
                    // Continue.
                }
            }
        }
        return null;
    }

    static String toXsf(Cell cell) {
        StringBuilder sb = new StringBuilder();
        sb.append("CRYSTAL\n");
        sb.append("PRIMVEC\n");
        double[][] lattice = cell.copyLattice();
        // Convention matches AtomicExporter/POSCAR: lattice[i] is lattice vector i.
        for (int i = 0; i < 3; i++) {
            sb.append(String.format(Locale.ROOT, "  %16.10f %16.10f %16.10f%n",
                    lattice[i][0], lattice[i][1], lattice[i][2]));
        }
        Atom[] atoms = cell.listAtoms(true);
        sb.append("PRIMCOORD\n");
        sb.append(atoms == null ? 0 : atoms.length).append(" 1\n");
        if (atoms != null) {
            for (Atom atom : atoms) {
                if (atom == null) {
                    continue;
                }
                sb.append(String.format(Locale.ROOT, "%3d %16.10f %16.10f %16.10f%n",
                        atom.getAtomNum(), atom.getX(), atom.getY(), atom.getZ()));
            }
        }
        return sb.toString();
    }
}
