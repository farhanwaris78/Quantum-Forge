/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.hpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.operation.OperationResult;

class JobQueueAuditTest {

    @TempDir
    Path tempDir;

    private static String record(String jobId, String state, String... historyStates) {
        StringBuilder history = new StringBuilder();
        for (int i = 0; i < historyStates.length; i++) {
            if (i > 0) {
                history.append(',');
            }
            history.append("{\"state\":\"").append(historyStates[i])
                    .append("\",\"at\":\"2026-07-20T0").append(i + 1)
                    .append(":00:00Z\",\"note\":\"t\"}");
        }
        return "{\"jobId\":\"" + jobId + "\",\"scheduler\":\"slurm\",\"siteId\":\"\","
                + "\"projectPath\":\"\",\"schedulerJobId\":\"\",\"state\":\"" + state
                + "\",\"history\":[" + history + "]}";
    }

    @Test
    void cleanStoreAuditsCleanWithHistogram() throws IOException {
        Files.writeString(this.tempDir.resolve(JobQueueStore.DEFAULT_FILE_NAME),
                "# QuantumForge job queue\n\n"
                        + record("jobA", "RUNNING", "STAGED", "SUBMITTED", "PENDING", "RUNNING") + "\n"
                        + record("jobB", "COMPLETED", "STAGED", "SUBMITTED", "PENDING", "RUNNING", "COMPLETED") + "\n");
        OperationResult<JobQueueAudit.Audit> result =
                JobQueueAudit.audit(this.tempDir.resolve(JobQueueStore.DEFAULT_FILE_NAME));
        assertTrue(result.isSuccess(), result.toString());
        JobQueueAudit.Audit audit = result.getValue().orElseThrow();
        assertEquals(4, audit.getTotalLines());
        assertEquals(2, audit.getSkippedLines(), "comment + blank lines counted, never lost");
        assertEquals(0, audit.getMalformedCount());
        assertTrue(audit.getDuplicates().isEmpty());
        assertEquals(2, audit.getCleanCount());
        Map<JobState, Integer> histogram = audit.getHistogram();
        assertEquals(1, histogram.get(JobState.RUNNING).intValue());
        assertEquals(1, histogram.get(JobState.COMPLETED).intValue());
    }

    @Test
    void malformedDuplicateAndIllegalEdgesAreNamedRaw() throws IOException {
        Path file = this.tempDir.resolve("job-queue.jsonl");
        Files.writeString(file,
                "# header\n"
                        + "this is not json at all\n"                                  // line 2 malformed
                        + record("dup", "PENDING", "STAGED", "SUBMITTED", "PENDING") + "\n"
                        + record("dup", "RUNNING", "STAGED", "SUBMITTED", "RUNNING") + "\n"
                        + record("bad-history", "RUNNING", "STAGED", "RUNNING", "PENDING") + "\n");
        JobQueueAudit.Audit audit = JobQueueAudit.audit(file).getValue().orElseThrow();
        assertEquals(1, audit.getMalformedCount());
        assertEquals(java.util.List.of(2), audit.getMalformedLines(),
                "the loader would DROP line 2 silently; the audit names it raw");
        assertEquals(java.util.List.of("dup"), audit.getDuplicates(),
                "duplicate jobId named; load() semantics are last-wins");
        assertEquals(2, audit.getOccurrences().get("dup").intValue());

        JobQueueAudit.RecordVerdict bad = null;
        for (JobQueueAudit.RecordVerdict record : audit.getRecords()) {
            if (record.getJobId().equals("bad-history")) {
                bad = record;
            }
        }
        assertTrue(bad != null);
        assertFalse(bad.isClean());
        assertTrue(bad.getProblems().stream().anyMatch(p ->
                        p.contains("RUNNING -> PENDING") && p.contains("ILLEGAL")),
                bad.getProblems().toString());
    }

    @Test
    void inconsistentFinalAndBackwardTimestampsFlag() throws IOException {
        Path file = this.tempDir.resolve("queue.jsonl");
        // Final claims COMPLETED but the recorded history never left RUNNING.
        // And history timestamps below run backward by construction.
        String line = "{\"jobId\":\"weird\",\"scheduler\":\"pbs\",\"siteId\":\"\","
                + "\"projectPath\":\"\",\"schedulerJobId\":\"\",\"state\":\"COMPLETED\","
                + "\"history\":["
                + "{\"state\":\"STAGED\",\"at\":\"2026-07-20T02:00:00Z\",\"note\":\"\"},"
                + "{\"state\":\"SUBMITTED\",\"at\":\"2026-07-20T01:00:00Z\",\"note\":\"\"},"
                + "{\"state\":\"RUNNING\",\"at\":\"2026-07-20T03:00:00Z\",\"note\":\"\"}]}";
        Files.writeString(file, "# h\n" + line + "\n");
        JobQueueAudit.Audit audit = JobQueueAudit.audit(file).getValue().orElseThrow();
        JobQueueAudit.RecordVerdict record = audit.getRecords().get(0);
        assertTrue(record.getProblems().stream().anyMatch(p -> p.contains("INCONSISTENT")),
                record.getProblems().toString());
        assertTrue(record.getProblems().stream().anyMatch(p -> p.contains("BACKWARD")),
                record.getProblems().toString());
        assertEquals(JobState.COMPLETED, record.getFinalState());

        // Missing state field surfaces the loader's silent STAGED default.
        String stateless = "{\"jobId\":\"statless-1\",\"scheduler\":\"sge\","
                + "\"history\":[{\"state\":\"STAGED\",\"at\":\"2026-07-20T01:00:00Z\","
                + "\"note\":\"\"}]}";
        Path file2 = this.tempDir.resolve("queue2.jsonl");
        Files.writeString(file2, stateless + "\n");
        JobQueueAudit.RecordVerdict second =
                JobQueueAudit.audit(file2).getValue().orElseThrow().getRecords().get(0);
        assertTrue(second.getProblems().stream().anyMatch(p ->
                p.contains("missing state field")), second.getProblems().toString());
    }

    @Test
    void missingAndOversizedFilesRefuse() {
        assertEquals("QUEUE_FILE", JobQueueAudit.audit(
                this.tempDir.resolve("nope.jsonl")).getCode());
        assertEquals("QUEUE_FILE", JobQueueAudit.audit(null).getCode());
    }

    @Test
    void reconciliationEdgesCountedNotFlagged() throws IOException {
        Path file = this.tempDir.resolve("queue3.jsonl");
        Files.writeString(file,
                record("recon", "UNKNOWN", "STAGED", "SUBMITTED", "UNKNOWN") + "\n");
        JobQueueAudit.Audit audit = JobQueueAudit.audit(file).getValue().orElseThrow();
        JobQueueAudit.RecordVerdict record = audit.getRecords().get(0);
        assertTrue(record.isClean(), record.getProblems().toString());
        assertEquals(1, record.getReconciliationEdges(),
                "-> UNKNOWN is reconciliation, not progress - counted, never flagged");
        assertEquals(1, audit.getHistogram().get(JobState.UNKNOWN).intValue());
    }
}
