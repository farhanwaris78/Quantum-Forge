/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import quantumforge.input.QEInput;
import quantumforge.input.QESCFInput;
import quantumforge.input.card.QEAtomicSpecies;
import quantumforge.input.card.QEAtomicPositions;
import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValue;
import quantumforge.operation.OperationResult;

/**
 * One-level, name-based workspace scan of a single project directory
 * (Roadmap #132 light seam): QE input files (composition/calculation extracted
 * through the real parser) and log files (status from exact QE marker strings,
 * stated as a heuristic) are catalogued; everything else is counted, not
 * touched. This is deliberately NOT the indexed provenance search the full #132
 * targets - it touches no path outside the given directory and indexes no
 * secret.
 */
public final class WorkspaceLightIndex {

    /** Heuristic completion markers, printed with their name on every report. */
    public static final String MARKER_DONE = "JOB DONE.";
    public static final String MARKER_FAILURE = "Error in routine";
    public static final String MARKER_NOCONV = "convergence NOT achieved";

    private static final long MAX_PARSE_BYTES = 8L * 1024L * 1024L;
    private static final int MAX_FILES = 10_000;

    /** One catalogued artifact row. */
    public static final class WorkspaceEntry {
        private final String fileName;
        private final String kind;       // INPUT or LOG
        private final String composition;
        private final int atomCount;
        private final String calculation;
        private final String status;

        WorkspaceEntry(String fileName, String kind, String composition,
                int atomCount, String calculation, String status) {
            this.fileName = fileName;
            this.kind = kind;
            this.composition = composition;
            this.atomCount = atomCount;
            this.calculation = calculation;
            this.status = status;
        }

        public String getFileName() { return this.fileName; }
        public String getKind() { return this.kind; }
        public String getComposition() { return this.composition; }
        public int getAtomCount() { return this.atomCount; }
        public String getCalculation() { return this.calculation; }
        public String getStatus() { return this.status; }
    }

    /** Scan outcome: entries plus skip bookkeeping for honest accounting. */
    public static final class WorkspaceScan {
        private final List<WorkspaceEntry> entries = new ArrayList<>();
        private int otherFiles;
        private int oversizedFiles;
        private int parseErrors;

        public List<WorkspaceEntry> getEntries() { return List.copyOf(this.entries); }
        public int getOtherFiles() { return this.otherFiles; }
        public int getOversizedFiles() { return this.oversizedFiles; }
        public int getParseErrors() { return this.parseErrors; }
    }

    private WorkspaceLightIndex() { }

    /** Codes: WS_IO, WS_EMPTY. Individual bad files mark their row, not the scan. */
    public static OperationResult<WorkspaceScan> scan(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return OperationResult.failed("WS_IO",
                    "The workspace directory does not exist.", null);
        }
        java.io.File[] files = directory.toFile().listFiles();
        if (files == null) {
            return OperationResult.failed("WS_IO",
                    "The workspace directory cannot be listed (permissions?).", null);
        }
        WorkspaceScan scan = new WorkspaceScan();
        int listed = 0;
        for (java.io.File file : files) {
            if (listed >= MAX_FILES) {
                break;
            }
            if (!file.isFile()) {
                continue; // one-level scan: subdirectories are not entered (stated)
            }
            listed++;
            String name = file.getName();
            String lower = name.toLowerCase(Locale.ROOT);
            try {
                if (lower.endsWith(".in") || lower.endsWith(".inp")) {
                    if (file.length() > MAX_PARSE_BYTES) {
                        scan.oversizedFiles++;
                        continue;
                    }
                    scanInput(file, scan);
                } else if (lower.contains(".log") || lower.endsWith(".out")) {
                    if (file.length() > MAX_PARSE_BYTES) {
                        scan.oversizedFiles++;
                        continue;
                    }
                    scanLog(file, scan);
                } else {
                    scan.otherFiles++;
                }
            } catch (Exception ex) {
                scan.parseErrors++;
                scan.entries.add(new WorkspaceEntry(name, "INPUT", "-", -1, "-",
                        "unparsed: " + abbreviate(ex.getMessage())));
            }
        }
        if (scan.entries.isEmpty() && scan.otherFiles == 0 && scan.oversizedFiles == 0) {
            return OperationResult.failed("WS_EMPTY",
                    "The directory holds no files - nothing to catalogue.", null);
        }
        if (scan.entries.isEmpty()) {
            return OperationResult.failed("WS_EMPTY",
                    "No QE input (*.in) or log (*.log/*.out) artifacts were found in this "
                            + "directory (other files present are untouched and counted).",
                    null);
        }
        return OperationResult.success("WS_OK",
                "Catalogued " + scan.entries.size() + " artifact(s).", scan);
    }

    private static void scanInput(java.io.File file, WorkspaceScan scan)
            throws IOException {
        String text = new String(Files.readAllBytes(file.toPath()),
                StandardCharsets.UTF_8);
        QESCFInput input = new QESCFInput();
        input.updateInputData(text);
        Set<String> labels = new LinkedHashSet<>();
        QEAtomicSpecies species = (QEAtomicSpecies) input.getCard(QEAtomicSpecies.CARD_NAME);
        if (species != null) {
            for (int i = 0; i < species.numSpecies(); i++) {
                if (species.getLabel(i) != null) {
                    labels.add(species.getLabel(i).trim());
                }
            }
        }
        int atoms = input.getAtoms() == null ? 0 : input.getAtoms().size();
        if (atoms == 0) {
            QEAtomicPositions positions = (QEAtomicPositions) input.getCard(QEAtomicPositions.CARD_NAME);
            if (positions != null) {
                atoms = positions.numPositions();
            }
        }
        String calculation = "-";
        QENamelist control = input.getNamelist(QEInput.NAMELIST_CONTROL);
        if (control != null) {
            QEValue value = control.getValue("calculation");
            if (value != null && value.getCharacterValue() != null) {
                calculation = stripQuotes(value.getCharacterValue().trim());
            }
        }
        String parsedCalculation = parseCalculation(text);
        if (parsedCalculation != null) {
            calculation = parsedCalculation;
        }
        scan.entries.add(new WorkspaceEntry(file.getName(), "INPUT",
                labels.isEmpty() ? "-" : String.join(",", labels), atoms, calculation,
                "input (not a run status)"));
    }

    private static String parseCalculation(String text) {
        if (text == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?im)^\s*calculation\s*=\s*([^,\s!]+)")
                .matcher(text);
        if (matcher.find()) {
            return stripQuotes(matcher.group(1).trim());
        }
        return null;
    }

    private static void scanLog(java.io.File file, WorkspaceScan scan)
            throws IOException {
        String text = new String(Files.readAllBytes(file.toPath()),
                StandardCharsets.UTF_8);
        String status;
        if (text.contains(MARKER_DONE)) {
            status = "completed (marker: " + MARKER_DONE + ")";
        } else if (text.contains(MARKER_FAILURE)) {
            status = "error (marker: " + MARKER_FAILURE + ")";
        } else if (text.contains(MARKER_NOCONV)) {
            status = "not converged (marker: " + MARKER_NOCONV + ")";
        } else {
            status = "unknown (no QE status marker)";
        }
        scan.entries.add(new WorkspaceEntry(file.getName(), "LOG", "-", -1, "-", status));
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2
                && ((value.startsWith("'") && value.endsWith("'"))
                        || (value.startsWith("\"") && value.endsWith("\"")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String abbreviate(String message) {
        if (message == null) {
            return "unknown error";
        }
        return message.length() <= 80 ? message : message.substring(0, 77) + "...";
    }
}
