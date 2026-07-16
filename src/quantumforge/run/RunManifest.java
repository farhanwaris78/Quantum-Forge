/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import quantumforge.com.file.AtomicFileWriter;
import quantumforge.ver.Version;

/**
 * Provenance record for one executed Quantum ESPRESSO stage.
 *
 * <p>Stores command array, working directory, executable hash when available,
 * input/output hashes, timestamps, and exit code so a plotted result can be
 * traced back to the process that produced it.</p>
 */
public final class RunManifest {

    public static final String FILE_NAME = ".quantumforge.run-manifest.jsonl";

    private final String jobId;
    private final String stage;
    private final List<String> command;
    private final String workingDirectory;
    private final String startedAt;
    private String finishedAt;
    private Integer exitCode;
    private String status;
    private String executablePath;
    private String executableSha256;
    private String inputPath;
    private String inputSha256;
    private String stdoutPath;
    private String stdoutSha256;
    private String stderrPath;
    private String stderrSha256;
    private String qeVersion;
    private final Map<String, String> environment;

    public RunManifest(String jobId, String stage, List<String> command, Path workingDirectory) {
        this.jobId = Objects.requireNonNull(jobId, "jobId");
        this.stage = Objects.requireNonNull(stage, "stage");
        this.command = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(command, "command")));
        this.workingDirectory = workingDirectory == null ? "" : workingDirectory.toAbsolutePath().toString();
        this.startedAt = Instant.now().toString();
        this.status = "STARTED";
        this.environment = new LinkedHashMap<>();
        this.environment.put("OMP_NUM_THREADS", System.getenv().getOrDefault("OMP_NUM_THREADS", ""));
    }

    public void setExecutable(Path executable) {
        if (executable == null) {
            return;
        }
        this.executablePath = executable.toAbsolutePath().toString();
        this.executableSha256 = sha256OrEmpty(executable);
    }

    public void setInput(Path input) {
        if (input == null) {
            return;
        }
        this.inputPath = input.toAbsolutePath().toString();
        this.inputSha256 = sha256OrEmpty(input);
    }

    public void setOutputs(Path stdout, Path stderr) {
        if (stdout != null) {
            this.stdoutPath = stdout.toAbsolutePath().toString();
            this.stdoutSha256 = sha256OrEmpty(stdout);
        }
        if (stderr != null) {
            this.stderrPath = stderr.toAbsolutePath().toString();
            this.stderrSha256 = sha256OrEmpty(stderr);
        }
    }

    public void setQeVersion(String version) {
        this.qeVersion = version == null ? "" : version;
    }

    public void putEnvironment(String key, String value) {
        if (key != null && !key.isBlank()) {
            this.environment.put(key, value == null ? "" : value);
        }
    }

    public void finish(int exitCode, String status) {
        this.exitCode = exitCode;
        this.status = status == null ? "FINISHED" : status;
        this.finishedAt = Instant.now().toString();
    }

    public String toJsonLine() {
        StringBuilder json = new StringBuilder(512);
        json.append('{');
        field(json, "schema", "quantumforge.run-manifest.v1", true);
        field(json, "quantumforgeVersion", Version.VERSION, false);
        field(json, "jobId", this.jobId, false);
        field(json, "stage", this.stage, false);
        field(json, "status", this.status, false);
        field(json, "startedAt", this.startedAt, false);
        field(json, "finishedAt", this.finishedAt == null ? "" : this.finishedAt, false);
        json.append(",\"exitCode\":").append(this.exitCode == null ? "null" : this.exitCode);
        json.append(",\"command\":[");
        for (int i = 0; i < this.command.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(quote(this.command.get(i)));
        }
        json.append(']');
        field(json, "workingDirectory", this.workingDirectory, false);
        field(json, "executablePath", nullToEmpty(this.executablePath), false);
        field(json, "executableSha256", nullToEmpty(this.executableSha256), false);
        field(json, "inputPath", nullToEmpty(this.inputPath), false);
        field(json, "inputSha256", nullToEmpty(this.inputSha256), false);
        field(json, "stdoutPath", nullToEmpty(this.stdoutPath), false);
        field(json, "stdoutSha256", nullToEmpty(this.stdoutSha256), false);
        field(json, "stderrPath", nullToEmpty(this.stderrPath), false);
        field(json, "stderrSha256", nullToEmpty(this.stderrSha256), false);
        field(json, "qeVersion", nullToEmpty(this.qeVersion), false);
        json.append(",\"environment\":{");
        boolean first = true;
        for (Map.Entry<String, String> entry : this.environment.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(quote(entry.getKey())).append(':').append(quote(entry.getValue()));
        }
        json.append("}}");
        return json.toString();
    }

    public void appendToProject(Path projectDirectory) throws IOException {
        Objects.requireNonNull(projectDirectory, "projectDirectory");
        Files.createDirectories(projectDirectory);
        Path manifest = projectDirectory.resolve(FILE_NAME);
        String existing = Files.isRegularFile(manifest)
                ? Files.readString(manifest, StandardCharsets.UTF_8)
                : "";
        if (!existing.isEmpty() && !existing.endsWith("\n")) {
            existing = existing + "\n";
        }
        AtomicFileWriter.writeUtf8(manifest, existing + toJsonLine() + "\n");
    }

    private static void field(StringBuilder json, String name, String value, boolean first) {
        if (!first) {
            json.append(',');
        }
        json.append(quote(name)).append(':').append(quote(value));
    }

    private static String quote(String value) {
        if (value == null) {
            return "\"\"";
        }
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
            case '\\':
            case '"':
                out.append('\\').append(ch);
                break;
            case '\n':
                out.append("\\n");
                break;
            case '\r':
                out.append("\\r");
                break;
            case '\t':
                out.append("\\t");
                break;
            default:
                if (ch < 0x20) {
                    out.append(String.format("\\u%04x", (int) ch));
                } else {
                    out.append(ch);
                }
            }
        }
        out.append('"');
        return out.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    static String sha256OrEmpty(Path file) {
        try {
            if (file == null || !Files.isRegularFile(file)) {
                return "";
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Files.readAllBytes(file));
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException | RuntimeException ex) {
            return "";
        }
    }
}
