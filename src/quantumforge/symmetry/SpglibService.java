/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.symmetry;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.com.log.AppLog;
import quantumforge.operation.OperationResult;

/**
 * Isolated spglib access via a versioned JSON-line Python sidecar protocol.
 *
 * <p>Java never imports Python. If the sidecar is missing or fails, the service
 * returns typed unsupported/failed results rather than inventing space groups.</p>
 */
public final class SpglibService {

    public static final String PROTOCOL_VERSION = "2";

    public static final class Dataset {
        private final int spaceGroupNumber;
        private final String internationalSymbol;
        private final String hallSymbol;
        private final double tolerance;
        private final String spglibVersion;
        private final Map<String, Object> raw;

        public Dataset(int spaceGroupNumber, String internationalSymbol, String hallSymbol,
                       double tolerance, String spglibVersion, Map<String, Object> raw) {
            this.spaceGroupNumber = spaceGroupNumber;
            this.internationalSymbol = internationalSymbol == null ? "" : internationalSymbol;
            this.hallSymbol = hallSymbol == null ? "" : hallSymbol;
            this.tolerance = tolerance;
            this.spglibVersion = spglibVersion == null ? "" : spglibVersion;
            this.raw = raw == null ? Map.of() : Map.copyOf(raw);
        }

        public int getSpaceGroupNumber() { return this.spaceGroupNumber; }
        public String getInternationalSymbol() { return this.internationalSymbol; }
        public String getHallSymbol() { return this.hallSymbol; }
        public double getTolerance() { return this.tolerance; }
        public String getSpglibVersion() { return this.spglibVersion; }
        public Map<String, Object> getRaw() { return this.raw; }
    }

    private final Path pythonExecutable;
    private final Path sidecarScript;
    private final long timeoutSeconds;

    public SpglibService(Path pythonExecutable, Path sidecarScript) {
        this(pythonExecutable, sidecarScript, 30L);
    }

    public SpglibService(Path pythonExecutable, Path sidecarScript, long timeoutSeconds) {
        this.pythonExecutable = pythonExecutable;
        this.sidecarScript = sidecarScript;
        this.timeoutSeconds = Math.max(1L, timeoutSeconds);
    }

    public static SpglibService detectDefault() {
        Path script = Path.of("tools", "spglib_sidecar.py");
        if (!Files.isRegularFile(script)) {
            script = Path.of(System.getProperty("user.dir", "."), "tools", "spglib_sidecar.py");
        }
        Path python = findPython();
        return new SpglibService(python, script);
    }

    public boolean isAvailable() {
        return this.pythonExecutable != null && Files.isRegularFile(this.sidecarScript);
    }

    
    public OperationResult<StandardizedCell> standardize(Cell cell, double symmetryTolerance, boolean toPrimitive) {
        if (cell == null) {
            return OperationResult.failed("CELL_NULL", "No cell supplied for spglib standardize.", null);
        }
        if (!isAvailable()) {
            return OperationResult.unsupported("SPGLIB_SIDECAR_UNAVAILABLE",
                    "spglib sidecar is not available for cell standardization.");
        }
        if (!(symmetryTolerance > 0.0) || !Double.isFinite(symmetryTolerance)) {
            return OperationResult.failed("SPGLIB_TOLERANCE",
                    "symmetry tolerance must be a positive finite value.", null);
        }
        try {
            String op = toPrimitive ? "standardize_primitive" : "standardize_conventional";
            String request = buildRequest(cell, symmetryTolerance, op);
            String response = invoke(request);
            return parseStandardized(response, symmetryTolerance, toPrimitive ? "primitive" : "conventional");
        } catch (Exception ex) {
            AppLog.error("spglib", "standardize failed", ex);
            return OperationResult.failed("SPGLIB_STANDARDIZE_FAILED",
                    "spglib standardize failed: " + ex.getMessage(), ex);
        }
    }

    public OperationResult<SeekPathResult> seekPath(Cell cell, double symmetryTolerance) {
        if (cell == null) {
            return OperationResult.failed("CELL_NULL", "No cell supplied for seekpath.", null);
        }
        if (!isAvailable()) {
            return OperationResult.unsupported("SPGLIB_SIDECAR_UNAVAILABLE",
                    "sidecar is not available for seekpath.");
        }
        if (!(symmetryTolerance > 0.0) || !Double.isFinite(symmetryTolerance)) {
            return OperationResult.failed("SPGLIB_TOLERANCE",
                    "symmetry tolerance must be a positive finite value.", null);
        }
        try {
            String request = buildRequest(cell, symmetryTolerance, "seekpath");
            String response = invoke(request);
            return parseSeekPath(response, symmetryTolerance);
        } catch (Exception ex) {
            AppLog.error("spglib", "seekpath failed", ex);
            return OperationResult.failed("SEEKPATH_FAILED",
                    "seekpath failed: " + ex.getMessage(), ex);
        }
    }


    public OperationResult<Dataset> getDataset(Cell cell, double symmetryTolerance) {
        if (cell == null) {
            return OperationResult.failed("CELL_NULL", "No cell supplied for spglib.", null);
        }
        if (!isAvailable()) {
            return OperationResult.unsupported("SPGLIB_SIDECAR_UNAVAILABLE",
                    "spglib sidecar is not available (python/script missing). "
                            + "Install spglib in an isolated environment and provide tools/spglib_sidecar.py.");
        }
        if (!(symmetryTolerance > 0.0) || !Double.isFinite(symmetryTolerance)) {
            return OperationResult.failed("SPGLIB_TOLERANCE",
                    "symmetry tolerance must be a positive finite value.", null);
        }
        try {
            String request = buildRequest(cell, symmetryTolerance, "get_dataset");
            String response = invoke(request);
            return parseResponse(response, symmetryTolerance);
        } catch (Exception ex) {
            AppLog.error("spglib", "spglib service failed", ex);
            return OperationResult.failed("SPGLIB_FAILED",
                    "spglib sidecar failed: " + ex.getMessage(), ex);
        }
    }

    static String buildRequest(Cell cell, double tolerance) {
        return buildRequest(cell, tolerance, "get_dataset");
    }

    static String buildRequest(Cell cell, double tolerance, String op) {
        double[][] lattice = cell.copyLattice();
        Atom[] atoms = cell.listAtoms(true);
        StringBuilder json = new StringBuilder();
        json.append('{');
        field(json, "protocol", PROTOCOL_VERSION, true);
        field(json, "op", op == null ? "get_dataset" : op, false);
        json.append(",\"tolerance\":").append(tolerance);
        json.append(",\"lattice\":[");
        for (int i = 0; i < 3; i++) {
            if (i > 0) json.append(',');
            json.append('[').append(lattice[i][0]).append(',')
                    .append(lattice[i][1]).append(',')
                    .append(lattice[i][2]).append(']');
        }
        json.append("],\"positions\":[");
        // Fractional positions would be ideal; for protocol v1 we send Cartesian
        // and let the sidecar convert using the lattice inverse.
        if (atoms != null) {
            for (int i = 0; i < atoms.length; i++) {
                if (i > 0) json.append(',');
                Atom atom = atoms[i];
                json.append('[').append(atom.getX()).append(',')
                        .append(atom.getY()).append(',')
                        .append(atom.getZ()).append(']');
            }
        }
        json.append("],\"numbers\":[");
        if (atoms != null) {
            for (int i = 0; i < atoms.length; i++) {
                if (i > 0) json.append(',');
                json.append(atoms[i].getAtomNum());
            }
        }
        json.append("]}");
        return json.toString();
    }

    private String invoke(String request) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(
                this.pythonExecutable.toString(), this.sidecarScript.toAbsolutePath().toString());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
            writer.write(request);
            writer.write('\n');
            writer.flush();
        }
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append('\n');
                if (response.length() > 1_000_000) {
                    break;
                }
            }
        }
        boolean finished = process.waitFor(this.timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("spglib sidecar timed out after " + this.timeoutSeconds + "s");
        }
        if (process.exitValue() != 0) {
            throw new IOException("spglib sidecar exit " + process.exitValue()
                    + ": " + response.toString().trim());
        }
        return response.toString().trim();
    }

    private static OperationResult<Dataset> parseResponse(String response, double tolerance) {
        if (response == null || response.isBlank()) {
            return OperationResult.failed("SPGLIB_EMPTY", "Empty spglib response.", null);
        }
        // Minimal JSON field extraction (avoid new deps).
        if (response.contains("\"error\"")) {
            String err = extractString(response, "error");
            return OperationResult.failed("SPGLIB_ERROR",
                    err == null ? "spglib reported an error" : err, null);
        }
        Integer number = extractInt(response, "number");
        String symbol = extractString(response, "international");
        if (symbol == null) {
            symbol = extractString(response, "international_symbol");
        }
        String hall = extractString(response, "hall");
        String version = extractString(response, "spglib_version");
        if (number == null || symbol == null) {
            return OperationResult.failed("SPGLIB_PARSE",
                    "Could not parse spglib dataset from: "
                            + response.substring(0, Math.min(200, response.length())), null);
        }
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("number", number);
        raw.put("international", symbol);
        if (hall != null) raw.put("hall", hall);
        if (version != null) raw.put("spglib_version", version);
        Dataset dataset = new Dataset(number, symbol, hall == null ? "" : hall,
                tolerance, version == null ? "" : version, raw);
        return OperationResult.success("SPGLIB_OK",
                "spglib dataset " + number + " (" + symbol + ")", dataset);
    }

    private static Integer extractInt(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)")
                .matcher(json);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }

    private static String extractString(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"")
                .matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static void field(StringBuilder json, String name, String value, boolean first) {
        if (!first) {
            json.append(',');
        }
        json.append('"').append(name).append("\":\"").append(escape(value)).append('"');
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Path findPython() {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String dir : path.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            for (String name : new String[] {"python3", "python"}) {
                Path candidate = Path.of(dir, name);
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }
}
