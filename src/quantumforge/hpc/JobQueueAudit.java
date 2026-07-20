/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.hpc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import quantumforge.operation.OperationResult;
import quantumforge.remote.JobStateGuard;

/**
 * Roadmap #105 (review slice): a strictly READ-ONLY audit of a durable
 * job-queue JSONL file ({@link JobQueueStore#DEFAULT_FILE_NAME} or any peer
 * file in the same format). The store's own load semantics are intentionally
 * FORGIVING - blank/# comment lines skipped, malformed lines dropped, unknown
 * states mapped to UNKNOWN - and the last line wins on a duplicate jobId. A
 * forgiving loader is exactly why an audit needs its own RAW verdicts: this
 * auditor re-reads the raw text and reports what the loader would silently
 * tolerate.
 *
 * <ul>
 *   <li>counted (sampled, bounded): blank/comment lines, MALFORMED lines the
 *       loader would drop, duplicate jobIds (last-wins named), final-state
 *       histogram;</li>
 *   <li>per-record: each history edge judged by the batch-119 typed chain
 *       ({@link JobStateGuard}) - backward/self/terminal violations named with
 *       jobId + edge index, reconciliation (-&gt; UNKNOWN) edges counted
 *       separately, timestamps must be non-decreasing, the local id must be
 *       parseable, and the final state must equal the last history entry
 *       (else the store would claim a state its own history never reached -
 *       flagged INCONSISTENT, not rewritten);</li>
 *   <li>all code paths are honest about scope: nothing is repaired, migrated,
 *       rewritten, or even SUGGESTED as a command - review only, zero writes.</li>
 * </ul>
 *
 * <p>Codes: QUEUE_FILE (missing/oversized/unreadable file), QUEUE_OK.</p>
 */
public final class JobQueueAudit {

    /** Hard cap: audit never scans more than this many bytes (bounded read). */
    public static final long MAX_FILE_BYTES = 4_000_000L;
    /** At most this many problems are listed per category; the rest are counted. */
    public static final int MAX_LISTED = 12;

    /** One audited record's verdicts. */
    public static final class RecordVerdict {
        private final String jobId;
        private final String scheduler;
        private final JobState finalState;
        private final int historyLength;
        private final List<String> problems;    // named chain/timestamp/id violations
        private final int reconciliationEdges;  // -> UNKNOWN is honest, counted apart

        RecordVerdict(String jobId, String scheduler, JobState finalState,
                      int historyLength, List<String> problems, int reconciliationEdges) {
            this.jobId = jobId;
            this.scheduler = scheduler;
            this.finalState = finalState;
            this.historyLength = historyLength;
            this.problems = List.copyOf(problems);
            this.reconciliationEdges = reconciliationEdges;
        }

        public String getJobId() { return this.jobId; }
        public String getScheduler() { return this.scheduler; }
        public JobState getFinalState() { return this.finalState; }
        public int getHistoryLength() { return this.historyLength; }
        public List<String> getProblems() { return this.problems; }
        public int getReconciliationEdges() { return this.reconciliationEdges; }
        public boolean isClean() { return this.problems.isEmpty(); }
    }

    /** Whole-file audit value. */
    public static final class Audit {
        private final Path file;
        private final int totalLines;
        private final int skippedLines;       // blank or # comment
        private final List<Integer> malformedLines;
        private final int malformedCount;
        private final List<String> duplicates;           // jobIds seen more than once
        private final Map<String, Integer> occurrences;  // insertion-ordered counts
        private final List<RecordVerdict> records;
        private final Map<JobState, Integer> histogram;  // over LAST occurrence per jobId

        Audit(Path file, int totalLines, int skippedLines, List<Integer> malformedLines,
              int malformedCount, List<String> duplicates, Map<String, Integer> occurrences,
              List<RecordVerdict> records, Map<JobState, Integer> histogram) {
            this.file = file;
            this.totalLines = totalLines;
            this.skippedLines = skippedLines;
            this.malformedLines = List.copyOf(malformedLines);
            this.malformedCount = malformedCount;
            this.duplicates = List.copyOf(duplicates);
            this.occurrences = Map.copyOf(occurrences);
            this.records = List.copyOf(records);
            this.histogram = new EnumMap<>(histogram);
        }

        public Path getFile() { return this.file; }
        public int getTotalLines() { return this.totalLines; }
        public int getSkippedLines() { return this.skippedLines; }
        public List<Integer> getMalformedLines() { return this.malformedLines; }
        public int getMalformedCount() { return this.malformedCount; }
        public List<String> getDuplicates() { return this.duplicates; }
        public Map<String, Integer> getOccurrences() { return this.occurrences; }
        public List<RecordVerdict> getRecords() { return this.records; }
        public Map<JobState, Integer> getHistogram() { return this.histogram; }
        public int getCleanCount() {
            int n = 0;
            for (RecordVerdict record : this.records) {
                if (record.isClean()) {
                    n++;
                }
            }
            return n;
        }
        public int getProblemTotal() {
            int n = this.malformedCount + this.duplicates.size();
            for (RecordVerdict record : this.records) {
                n += record.getProblems().size();
            }
            return n;
        }
    }

    private static final Pattern ID_PATTERN =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private static final Pattern STATE_OBJECT_PATTERN =
            Pattern.compile("\"state\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern AT_OBJECT_PATTERN =
            Pattern.compile("\"at\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern HISTORY_PATTERN =
            Pattern.compile("\"history\"\\s*:\\s*\\[(.*)]\\s*}\\s*$");

    private JobQueueAudit() {
        // Utility.
    }

    static String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\n", "\n")
                .replace("\\r", "\r").replace("\\\\", "\\");
    }

    /** Audit {@code file} without modifying it (or anything else). */
    public static OperationResult<Audit> audit(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return OperationResult.failed("QUEUE_FILE",
                    "No such queue file - audit needs an existing JSONL artifact.", null);
        }
        final long size;
        try {
            size = Files.size(file);
        } catch (IOException ex) {
            return OperationResult.failed("QUEUE_FILE",
                    "Could not stat the queue file: " + ex.getMessage(), ex);
        }
        if (size > MAX_FILE_BYTES) {
            return OperationResult.failed("QUEUE_FILE",
                    "Queue file is " + size + " bytes, above the bounded-read cap of "
                            + MAX_FILE_BYTES + " - refusing an unbounded scan.", null);
        }
        final List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return OperationResult.failed("QUEUE_FILE",
                    "Could not read the queue file: " + ex.getMessage(), ex);
        }

        int skipped = 0;
        List<Integer> malformed = new ArrayList<>();
        Map<String, Integer> occurrences = new LinkedHashMap<>();
        Map<String, String> lastLineById = new LinkedHashMap<>();
        Map<String, Integer> lastLineNoById = new LinkedHashMap<>();
        int total = 0;
        for (String line : lines) {
            total++;
            if (line == null || line.isBlank() || line.startsWith("#")) {
                skipped++;
                continue;
            }
            JobRecord probe = JobQueueStore.fromJsonLine(line);
            if (probe == null) {
                malformed.add(total);
                continue;
            }
            occurrences.merge(probe.getJobId(), 1, Integer::sum);
            lastLineById.put(probe.getJobId(), line);
            lastLineNoById.put(probe.getJobId(), total);
        }
        List<String> duplicates = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : occurrences.entrySet()) {
            if (entry.getValue() > 1) {
                duplicates.add(entry.getKey());
            }
        }

        List<RecordVerdict> records = new ArrayList<>();
        for (Map.Entry<String, String> entry : lastLineById.entrySet()) {
            records.add(auditRecord(entry.getKey(), entry.getValue(),
                    lastLineNoById.get(entry.getKey())));
        }
        Map<JobState, Integer> histogram = new EnumMap<>(JobState.class);
        for (RecordVerdict record : records) {
            histogram.merge(record.getFinalState(), 1, Integer::sum);
        }
        return OperationResult.success("QUEUE_OK",
                "Audited " + records.size() + " job records ("
                        + malformed.size() + " malformed line(s), "
                        + duplicates.size() + " duplicate id(s)).",
                new Audit(file, total, skipped, malformed, malformed.size(), duplicates,
                        occurrences, records, histogram));
    }

    /**
     * Audit ONE raw line in full: local-id grammar, final-state consistency,
     * every history edge against the typed chain, timestamps non-decreasing.
     * Never throws on rogue content - problems are collected, not fatal.
     */
    private static RecordVerdict auditRecord(String jobId, String line, int lineNo) {
        List<String> problems = new ArrayList<>();
        if (!ID_PATTERN.matcher(jobId).matches()) {
            problems.add("line " + lineNo + ": local jobId '" + jobId
                    + "' violates the owned grammar [A-Za-z0-9][A-Za-z0-9._-]{0,127}");
        }
        String scheduler = JobQueueStore.extractString(line, "scheduler");
        String stateText = JobQueueStore.extractString(line, "state");
        if (stateText == null) {
            problems.add("line " + lineNo + ": missing state field - the loader silently "
                    + "defaults such a record to STAGED");
        }
        JobState parsed;
        try {
            parsed = stateText == null || stateText.isBlank()
                    ? JobState.STAGED
                    : JobState.valueOf(stateText.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            parsed = JobState.UNKNOWN;
            problems.add("line " + lineNo + ": state '" + stateText
                    + "' is not a JobState name - the store maps it to UNKNOWN");
        }

        List<JobState> historyStates = new ArrayList<>();
        List<Instant> historyTimes = new ArrayList<>();
        Matcher history = HISTORY_PATTERN.matcher(line);
        if (!history.find()) {
            problems.add("line " + lineNo + ": no well-formed trailing history array");
        } else {
            Matcher stateM = STATE_OBJECT_PATTERN.matcher(history.group(1));
            while (stateM.find()) {
                String text = unescape(stateM.group(1));
                try {
                    historyStates.add(JobState.valueOf(text.toUpperCase(java.util.Locale.ROOT)));
                } catch (IllegalArgumentException ex) {
                    problems.add("line " + lineNo + ": history entry '" + text
                            + "' is not a JobState name");
                }
            }
            Matcher atM = AT_OBJECT_PATTERN.matcher(history.group(1));
            while (atM.find()) {
                String text = unescape(atM.group(1));
                try {
                    historyTimes.add(Instant.parse(text));
                } catch (RuntimeException ex) {
                    problems.add("line " + lineNo + ": history timestamp '" + text
                            + "' is not ISO-8601");
                }
            }
        }
        int reconciliation = 0;
        for (int i = 1; i < historyStates.size(); i++) {
            OperationResult<JobStateGuard.Verdict> verdict = JobStateGuard.transition(
                    historyStates.get(i - 1).name(), historyStates.get(i).name());
            if (!verdict.isSuccess()) {
                String tag = "line " + lineNo + ": history edge " + i + " "
                        + historyStates.get(i - 1) + " -> " + historyStates.get(i)
                        + " is ILLEGAL per the batch-119 typed chain";
                if (problems.size() < 64) {
                    problems.add(tag);
                }
            } else if (verdict.getValue().isPresent()
                    && verdict.getValue().get().isReconciliation()) {
                reconciliation++;
            }
        }
        for (int i = 1; i < historyTimes.size() && i <= historyStates.size(); i++) {
            if (historyTimes.get(i).isBefore(historyTimes.get(i - 1))) {
                problems.add("line " + lineNo + ": history timestamp at edge " + i
                        + " goes BACKWARD (" + historyTimes.get(i - 1) + " -> "
                        + historyTimes.get(i) + ") - recorded causality is broken");
            }
        }
        if (!historyStates.isEmpty()
                && historyStates.get(historyStates.size() - 1) != parsed) {
            problems.add("line " + lineNo + ": final state " + parsed
                    + " contradicts the last history entry "
                    + historyStates.get(historyStates.size() - 1)
                    + " - INCONSISTENT record (the store would claim a state its own "
                    + "history never reached)");
        }
        if (historyStates.isEmpty() && problems.isEmpty()) {
            problems.add("line " + lineNo + ": history parsed empty - nothing to audit");
        }
        return new RecordVerdict(jobId, scheduler == null ? "" : scheduler, parsed,
                historyStates.size(), problems, reconciliation);
    }
}
