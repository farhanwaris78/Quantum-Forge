/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.symmetry;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import quantumforge.com.log.AppLog;
import quantumforge.operation.OperationResult;

/**
 * Restricted JSON-RPC Interoperability Service for Atomic Simulation Environment (ASE) 
 * tools, allowing secure structural conversions (cif/xyz/poscar) via isolated 
 * python workers without running raw shell commands (Roadmap #115).
 */
public final class QEAsinteropService {

    public static final String PROTOCOL_VERSION = "1";

    private final Path pythonExecutable;
    private final Path sidecarScript;
    private final long timeoutSeconds;

    public QEAsinteropService(Path pythonExecutable, Path sidecarScript) {
        this(pythonExecutable, sidecarScript, 30L);
    }

    public QEAsinteropService(Path pythonExecutable, Path sidecarScript, long timeoutSeconds) {
        this.pythonExecutable = pythonExecutable;
        this.sidecarScript = sidecarScript;
        this.timeoutSeconds = Math.max(1L, timeoutSeconds);
    }

    public static QEAsinteropService detectDefault() {
        Path script = Path.of("tools", "ase_sidecar.py");
        if (!Files.isRegularFile(script)) {
            script = Path.of(System.getProperty("user.dir", "."), "tools", "ase_sidecar.py");
        }
        Path python = findPython();
        return new QEAsinteropService(python, script);
    }

    public boolean isAvailable() {
        return this.pythonExecutable != null && Files.isRegularFile(this.sidecarScript);
    }

    /**
     * Converts a structure from one standard format to another using ASE.
     * 
     * @param formatIn e.g. "cif", "xyz", "poscar"
     * @param formatOut e.g. "poscar", "cif", "xyz"
     * @param content raw string content of the input structure file
     */
    public OperationResult<String> convert(String formatIn, String formatOut, String content) {
        if (formatIn == null || formatOut == null || content == null || content.isBlank()) {
            return OperationResult.failed("ASE_INVALID_PARAMS", "Invalid format or empty content.", null);
        }

        if (!isAvailable()) {
            return OperationResult.unsupported("ASE_INTEROP_UNAVAILABLE", 
                "ASE interop sidecar is not available (python/script missing).");
        }

        try {
            // Build standard JSON-RPC request
            JsonObject req = new JsonObject();
            req.addProperty("protocol", PROTOCOL_VERSION);
            req.addProperty("op", "convert");
            req.addProperty("format_in", formatIn.trim().toLowerCase());
            req.addProperty("format_out", formatOut.trim().toLowerCase());
            req.addProperty("data", content);

            String response = invoke(req.toString());
            
            JsonObject root = new Gson().fromJson(response, JsonObject.class);
            if (root.has("error")) {
                return OperationResult.failed("ASE_INTEROP_ERROR", root.get("error").getAsString(), null);
            }

            if (root.has("data")) {
                String converted = root.get("data").getAsString();
                return OperationResult.success("ASE_INTEROP_OK", "Structure converted successfully.", converted);
            }

            return OperationResult.failed("ASE_PARSE_ERROR", "Could not parse converted structure from response.", null);

        } catch (Exception e) {
            AppLog.error("ase-interop", "ASE conversion failed", e);
            return OperationResult.failed("ASE_INTEROP_FAILED", "ASE interop failed: " + e.getMessage(), e);
        }
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
                if (response.length() > 5_000_000) {
                    break; // prevent memory overflow
                }
            }
        }

        boolean finished = process.waitFor(this.timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("ASE interop sidecar timed out after " + this.timeoutSeconds + "s");
        }

        if (process.exitValue() != 0) {
            throw new IOException("ASE interop sidecar exit " + process.exitValue()
                    + ": " + response.toString().trim());
        }

        return response.toString().trim();
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
