/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.hpc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import quantumforge.com.file.AtomicFileWriter;
import quantumforge.com.log.AppLog;

/**
 * Lightweight durable job queue (JSONL) for GUI restart reconstruction.
 *
 * <p>This is intentionally simpler than SQLite WAL while still providing
 * append/load/update semantics and atomic rewrites. A future revision can
 * migrate to SQLite without changing {@link JobRecord}.</p>
 */
public final class JobQueueStore {

    public static final String DEFAULT_FILE_NAME = "job-queue.jsonl";

    private final Path file;
    private final Map<String, JobRecord> jobs = new LinkedHashMap<>();

    public JobQueueStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    public Path getFile() {
        return this.file;
    }

    public synchronized void load() throws IOException {
        this.jobs.clear();
        if (!Files.isRegularFile(this.file)) {
            return;
        }
        for (String line : Files.readAllLines(this.file, StandardCharsets.UTF_8)) {
            if (line == null || line.isBlank() || line.startsWith("#")) {
                continue;
            }
            JobRecord record = fromJsonLine(line);
            if (record != null) {
                this.jobs.put(record.getJobId(), record);
            }
        }
    }

    public synchronized void save() throws IOException {
        Path parent = this.file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        StringBuilder out = new StringBuilder();
        out.append("# QuantumForge job queue\n");
        for (JobRecord record : this.jobs.values()) {
            out.append(toJsonLine(record)).append('\n');
        }
        AtomicFileWriter.writeUtf8(this.file, out.toString());
    }

    public synchronized void put(JobRecord record) throws IOException {
        Objects.requireNonNull(record, "record");
        this.jobs.put(record.getJobId(), record);
        save();
    }

    public synchronized Optional<JobRecord> get(String jobId) {
        return Optional.ofNullable(this.jobs.get(jobId));
    }

    public synchronized List<JobRecord> list() {
        return List.copyOf(this.jobs.values());
    }

    public synchronized List<JobRecord> listActive() {
        List<JobRecord> out = new ArrayList<>();
        for (JobRecord record : this.jobs.values()) {
            JobState state = record.getState();
            if (state == JobState.STAGED || state == JobState.SUBMITTED
                    || state == JobState.PENDING || state == JobState.RUNNING
                    || state == JobState.UNKNOWN) {
                out.add(record);
            }
        }
        return out;
    }

    public synchronized boolean remove(String jobId) throws IOException {
        boolean removed = this.jobs.remove(jobId) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    static String toJsonLine(JobRecord record) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        field(json, "jobId", record.getJobId(), true);
        field(json, "scheduler", record.getScheduler(), false);
        field(json, "siteId", record.getSiteId(), false);
        field(json, "projectPath", record.getProjectPath(), false);
        field(json, "schedulerJobId",
                record.getSchedulerJobId() == null ? "" : record.getSchedulerJobId(), false);
        field(json, "state", record.getState().name(), false);
        json.append(",\"history\":[");
        List<JobRecord.Transition> history = record.getHistory();
        for (int i = 0; i < history.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            JobRecord.Transition tr = history.get(i);
            json.append('{');
            field(json, "state", tr.getState().name(), true);
            field(json, "at", tr.getAt().toString(), false);
            field(json, "note", tr.getNote(), false);
            json.append('}');
        }
        json.append("]}");
        return json.toString();
    }

    static JobRecord fromJsonLine(String line) {
        try {
            String jobId = extractString(line, "jobId");
            String scheduler = extractString(line, "scheduler");
            if (jobId == null || scheduler == null) {
                return null;
            }
            JobRecord record = new JobRecord(jobId, scheduler,
                    nullToEmpty(extractString(line, "siteId")),
                    nullToEmpty(extractString(line, "projectPath")));
            String schedulerJobId = extractString(line, "schedulerJobId");
            if (schedulerJobId != null && !schedulerJobId.isBlank()) {
                record.setSchedulerJobId(schedulerJobId);
            }
            String state = extractString(line, "state");
            if (state != null && !state.isBlank()) {
                try {
                    JobState parsed = JobState.valueOf(state.toUpperCase(Locale.ROOT));
                    if (parsed != JobState.STAGED) {
                        record.transition(parsed, "loaded");
                    }
                } catch (IllegalArgumentException ignored) {
                    record.transition(JobState.UNKNOWN, "loaded unknown state " + state);
                }
            }
            return record;
        } catch (RuntimeException ex) {
            AppLog.warn("job-queue", "Skipping bad job line: " + ex.getMessage());
            return null;
        }
    }

    private static void field(StringBuilder json, String name, String value, boolean first) {
        if (!first) {
            json.append(',');
        }
        json.append('"').append(name).append("\":\"")
                .append(escape(value == null ? "" : value)).append('"');
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String extractString(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
                .matcher(json);
        if (!m.find()) {
            return null;
        }
        return m.group(1)
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\\\", "\\");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
