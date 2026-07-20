/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.hpc.ArraySweepPlanner;
import quantumforge.hpc.JobRecord;
import quantumforge.hpc.PbsSchedulerAdapter;
import quantumforge.hpc.SlurmSchedulerAdapter;
import quantumforge.operation.OperationResult;

/**
 * Batch-142 (#93 site-side executor, single-array slice): the refusal ladder
 * (local consent check first, offline, unsupported shape), verified-upload
 * staging, single-owner token composition, adapter-owned id parsing, and the
 * unparseable-id partial-truth path - all against a scripted wire.
 */
class ArraySubmitExecutorTest {

    @TempDir
    Path tempDir;

    private static final String REVIEWED_SCRIPT = """
            #!/bin/bash
            #SBATCH --array=1-3
            #SBATCH --job-name=si-cut
            set -euo pipefail
            TASK=$(sed -n "${SLURM_ARRAY_TASK_ID}p" tasks.jsonl)
            echo "task=$TASK"
            """;

    private static ArraySweepPlanner.SweepPlan sweep() {
        return ArraySweepPlanner.plan("ecutwfc", 30.0, 10.0, 3, "si-cut")
                .getValue().orElseThrow();
    }

    private Path reviewedScript() throws Exception {
        Path script = this.tempDir.resolve("job-array.sh");
        Files.writeString(script, REVIEWED_SCRIPT);
        return script;
    }

    @Test
    void happyPathStagesVerifiedThenSubmitsWithOwnedTokens() throws Exception {
        ScriptedTransport fake = new ScriptedTransport();
        fake.sbatchOutput = "Submitted batch job 777\n";
        ArraySubmitExecutor executor = new ArraySubmitExecutor(fake, "/scratch");
        OperationResult<JobRecord> submitted = executor.submitArray(sweep(),
                new SlurmSchedulerAdapter(), reviewedScript());
        assertTrue(submitted.isSuccess(), submitted.toString());
        assertEquals("SUBMIT_ARRAY_OK", submitted.getCode());
        JobRecord record = submitted.getValue().orElseThrow();
        assertEquals("777", record.getSchedulerJobId());
        assertTrue(submitted.getMessage().contains("governs 3 task(s)"),
                submitted.getMessage());
        assertEquals(List.of("mkdir", "test", "upload", "sha256sum", "mv", "sbatch"),
                fake.steps,
                "the exact mandated sequence: stage dir, overwrite pre-check, verified "
                        + "upload (temp -> sha256 -> rename), then array submit");
        assertTrue(fake.removals.isEmpty(), "a clean staging removes nothing");
        String[] exec = fake.lastExec;
        assertEquals("sbatch", exec[0]);
        assertEquals("--array=1-3", exec[1],
                "the token composition is the draft's own composeArrayTokens");
        assertTrue(exec[2].startsWith("/scratch/jobs/si-cut-")
                        && exec[2].endsWith("/job-array.sh"),
                "the staged final path under the unique job dir - never the .qftmp "
                        + "scratch name: " + exec[2]);
        assertTrue(!exec[2].endsWith(".qftmp"));
        assertTrue(record.getHistory().stream().anyMatch(t ->
                t.getNote().contains("verified upload")), record.getHistory().toString());
        assertTrue(record.getHistory().stream().anyMatch(t ->
                t.getNote().contains("parsed by the slurm adapter")),
                record.getHistory().toString());
    }

    @Test
    void theExitTwoGuardIsAConsentCheckBeforeAnyRemoteStep() throws Exception {
        Path guarded = this.tempDir.resolve("preview.sh");
        Files.writeString(guarded,
                "#!/bin/bash\nexit 2  # REQUIRED-EDIT guard: preview must not run as-is\n"
                        + "#SBATCH --array=1-3\n");
        ScriptedTransport fake = new ScriptedTransport();
        ArraySubmitExecutor executor = new ArraySubmitExecutor(fake, "/scratch");
        OperationResult<JobRecord> refused = executor.submitArray(sweep(),
                new SlurmSchedulerAdapter(), guarded);
        assertFalse(refused.isSuccess());
        assertEquals("SUBMIT_GUARD_PRESENT", refused.getCode());
        assertTrue(fake.steps.isEmpty(),
                "consent is a LOCAL check first: not even a mkdir may precede it");
        assertTrue(refused.getMessage().contains("IS the consent step"),
                refused.getMessage());
    }

    @Test
    void adaptersWithoutOwnedArrayFormsRefuseBeforeStaging() throws Exception {
        ScriptedTransport fake = new ScriptedTransport();
        ArraySubmitExecutor executor = new ArraySubmitExecutor(fake, "/scratch");
        OperationResult<JobRecord> refused = executor.submitArray(sweep(),
                new PbsSchedulerAdapter(), reviewedScript());
        assertFalse(refused.isSuccess());
        assertEquals("SUBMIT_ARRAY_UNSUPPORTED_SHAPE", refused.getCode());
        assertTrue(refused.getMessage().contains("PBS Professional"), refused.getMessage(),
                "the adapter's divergence reason rides the refusal");
        assertTrue(refused.getMessage().contains("per-task loop executor is the remaining"),
                refused.getMessage());
        assertTrue(fake.steps.isEmpty(), "the shape check precedes all staging");
    }

    @Test
    void unparseableIdCarriesTheRecordAsPartialTruthAndNeverCancelsBlind() throws Exception {
        ScriptedTransport fake = new ScriptedTransport();
        fake.sbatchOutput = "unusual response from scheduler\n";
        ArraySubmitExecutor executor = new ArraySubmitExecutor(fake, "/scratch");
        OperationResult<JobRecord> result = executor.submitArray(sweep(),
                new SlurmSchedulerAdapter(), reviewedScript());
        assertFalse(result.isSuccess());
        assertEquals("SUBMIT_ID_UNPARSEABLE", result.getCode());
        assertTrue(result.getValue().isPresent(),
                "the record MUST travel with the failure - it names the staged script");
        JobRecord record = result.getValue().orElseThrow();
        assertTrue(record.getSchedulerJobId() == null || record.getSchedulerJobId().isBlank(),
                "no invented id");
        assertTrue(result.getMessage().contains("MAY still exist")
                        && result.getMessage().contains("nothing is auto-cancelled blind"),
                result.getMessage());
        assertTrue(fake.removals.isEmpty(), "no cleanup that could hint at cancellation");
        assertEquals(List.of("mkdir", "test", "upload", "sha256sum", "mv", "sbatch"),
                fake.steps);
    }

    @Test
    void offlineAndMissingScriptRefuseTyped() throws Exception {
        ScriptedTransport fake = new ScriptedTransport();
        fake.connected = false;
        ArraySubmitExecutor executor = new ArraySubmitExecutor(fake, "/scratch");
        OperationResult<JobRecord> offline = executor.submitArray(sweep(),
                new SlurmSchedulerAdapter(), reviewedScript());
        assertFalse(offline.isSuccess());
        assertEquals("SSH_SUBMISSION_UNAVAILABLE", offline.getCode());
        OperationResult<JobRecord> missing = new ArraySubmitExecutor(new ScriptedTransport(),
                "/scratch").submitArray(sweep(), new SlurmSchedulerAdapter(),
                this.tempDir.resolve("nope.sh"));
        assertFalse(missing.isSuccess());
        assertEquals("SSH_LOCAL_MISSING", missing.getCode());
    }

    /** Scripted wire: stores uploads, answers sha256sum from stored bytes only. */
    static final class ScriptedTransport implements SshTransport {
        final List<String> steps = new ArrayList<>();
        final List<String> removals = new ArrayList<>();
        final List<String> moves = new ArrayList<>();
        final Map<String, byte[]> remote = new HashMap<>();
        String sbatchOutput = "Submitted batch job 777\n";
        boolean connected = true;
        String[] lastExec;

        @Override public OperationResult<Void> connect() {
            this.connected = true;
            return OperationResult.success("OK", "ok", null);
        }
        @Override public boolean isConnected() { return this.connected; }
        @Override public OperationResult<Integer> exec(String[] command, Path stdoutFile,
                Path stderrFile) {
            this.steps.add(command[0]);
            try {
                switch (command[0]) {
                case "test":
                    return this.remote.containsKey(command[2])
                            ? OperationResult.success("SSH_EXEC_OK", "ok", 0)
                            : OperationResult.failed("SSH_EXEC_FAILED", "exited 1", null);
                case "sha256sum":
                    byte[] data = this.remote.get(command[1]);
                    if (data == null) {
                        return OperationResult.failed("SSH_EXEC_FAILED", "no such file", null);
                    }
                    Files.writeString(stdoutFile, sha256Hex(data) + "  " + command[1] + "\n");
                    return OperationResult.success("SSH_EXEC_OK", "ok", 0);
                case "mv":
                    this.moves.add(command[2] + "->" + command[3]);
                    this.remote.put(command[3], this.remote.remove(command[2]));
                    return OperationResult.success("SSH_EXEC_OK", "ok", 0);
                case "sbatch":
                    this.lastExec = command;
                    Files.writeString(stdoutFile, this.sbatchOutput);
                    return OperationResult.success("SSH_EXEC_OK", "ok", 0);
                default:
                    return OperationResult.failed("SSH_EXEC_FAILED", "unknown cmd", null);
                }
            } catch (Exception ex) {
                return OperationResult.failed("SSH_EXEC_ERROR", ex.getMessage(), null);
            }
        }
        @Override public OperationResult<Void> uploadFile(Path localFile, String remotePath) {
            this.steps.add("upload");
            try {
                this.remote.put(remotePath, Files.readAllBytes(localFile));
            } catch (Exception ex) {
                return OperationResult.failed("SFTP_UPLOAD", ex.getMessage(), null);
            }
            return OperationResult.success("OK", "ok", null);
        }
        @Override public OperationResult<Void> downloadFile(String remotePath, Path localFile) {
            return OperationResult.success("OK", "ok", null);
        }
        @Override public OperationResult<Void> mkdirRemote(String remotePath) {
            this.steps.add("mkdir");
            return OperationResult.success("OK", "ok", null);
        }
        @Override public void close() { this.connected = false; }

        static String sha256Hex(byte[] data) {
            try {
                java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                byte[] digest = md.digest(data);
                StringBuilder hex = new StringBuilder();
                for (byte b : digest) {
                    hex.append(String.format(java.util.Locale.ROOT, "%02x", b));
                }
                return hex.toString();
            } catch (java.security.NoSuchAlgorithmException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }
}
