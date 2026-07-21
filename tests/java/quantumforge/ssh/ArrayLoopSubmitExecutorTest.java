/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.hpc.ArrayDeckTemplate;
import quantumforge.hpc.ArraySweepPlanner;
import quantumforge.hpc.ArrayTaskIntent;
import quantumforge.hpc.PbsSchedulerAdapter;
import quantumforge.hpc.SlurmSchedulerAdapter;
import quantumforge.operation.OperationResult;

/**
 * Batch-143 (#93 site-side executor, per-task loop slice): the identical
 * consent gate, the loop-only shape contract, per-task verified staging of
 * deck+script with the batch-140 digest seam, adapter-owned id parsing per
 * task, and fail-fast partial truth - all against a scripted wire.
 */
class ArrayLoopSubmitExecutorTest {

    @TempDir
    Path tempDir;

    private static final String REVIEWED_SCRIPT = """
            #!/bin/bash
            #PBS -N si-cut
            set -euo pipefail
            cd "$(dirname "$0")"
            pw.x < input.deck > output.txt
            """;

    private static final String DECK_TEMPLATE = """
            &CONTROL
                calculation = 'scf'
            /
            &SYSTEM
                ibrav = 2,
                ecutwfc = 30.0,
            /
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
    void happyPathLoopsEachTaskWithVerifiedStagingAndOwnedParsing() throws Exception {
        ScriptedWire fake = new ScriptedWire();
        fake.qsubOutputs.addAll(List.of("101.pbs01\n", "102.pbs01\n", "103.pbs01\n"));
        ArrayLoopSubmitExecutor executor = new ArrayLoopSubmitExecutor(fake, "/scratch");
        OperationResult<ArrayLoopSubmitExecutor.LoopSubmitReport> submitted =
                executor.submitLoop(sweep(), new PbsSchedulerAdapter(), reviewedScript(),
                        DECK_TEMPLATE);
        assertTrue(submitted.isSuccess(), submitted.toString());
        assertEquals("SUBMIT_LOOP_OK", submitted.getCode());
        ArrayLoopSubmitExecutor.LoopSubmitReport report =
                submitted.getValue().orElseThrow();
        assertEquals(3, report.getTaskCount());
        assertEquals(3, report.getSubmittedCount());
        assertEquals(3, report.getRecords().size());
        assertEquals(List.of("101", "102", "103"), report.getRecords().stream()
                .map(record -> record.getSchedulerJobId()).toList(),
                "the pbs adapter's own parseJobId owns id extraction per task");
        assertTrue(submitted.getMessage().contains("3 of 3 task id(s) parsed"),
                submitted.getMessage());
        assertTrue(submitted.getMessage().contains("YOUR site's scheduler rule")
                        && submitted.getMessage().contains("input.deck"),
                "the working-directory honesty and the deck-name contract ride the "
                        + "success message: " + submitted.getMessage());
        // The exact mandated sequence: base mkdir, then per task
        // [mkdir, deck(test,upload,sha256sum,mv), script(test,upload,sha256sum,mv), qsub].
        List<String> expected = new ArrayList<>(List.of("mkdir"));
        for (int task = 0; task < 3; task++) {
            expected.addAll(List.of("mkdir", "test", "upload", "sha256sum", "mv",
                    "test", "upload", "sha256sum", "mv", "qsub"));
        }
        assertEquals(expected, fake.steps,
                "per task: dir, verified deck upload, verified script upload, submit");
        assertTrue(fake.removals.isEmpty(), "a clean loop staging removes nothing");
        assertEquals(6, fake.moves.size(), "two verified renames per task");
        // The qsub targets are the staged FINAL script paths, one per task dir.
        assertEquals(3, fake.qsubScripts.size());
        for (int i = 0; i < 3; i++) {
            String script = fake.qsubScripts.get(i);
            assertTrue(script.startsWith("/scratch/jobs/si-cut-")
                            && script.contains("/si-cut-00" + (i + 1) + "/job.sh"),
                    "the staged final per-task script path: " + script);
            assertFalse(script.endsWith(".qftmp"),
                    "never the scratch temp name: " + script);
        }
        // The provenance seam: the record's deck digest IS the intent's anchor.
        ArrayDeckTemplate.DeckTemplate deck = ArrayDeckTemplate
                .validate(sweep(), DECK_TEMPLATE).getValue().orElseThrow();
        ArrayTaskIntent.TaskIntentPlan intents = ArrayTaskIntent
                .plan(sweep(), deck).getValue().orElseThrow();
        for (int i = 0; i < 3; i++) {
            String note = report.getRecords().get(i).getHistory().stream()
                    .map(t -> t.getNote()).filter(n -> n.contains("deck sha256 "))
                    .findFirst().orElseThrow();
            assertTrue(note.contains(intents.getTasks().get(i).getInputSha256()),
                    "task " + (i + 1) + ": the wire record's staged digest equals the "
                            + "intent JSONL's input_sha256 - one owner, no drift: " + note);
        }
    }

    @Test
    void consentGuardRunsBeforeAnythingElse() throws Exception {
        Path guarded = this.tempDir.resolve("preview.sh");
        Files.writeString(guarded,
                "#!/bin/bash\nexit 2  # REQUIRED-EDIT guard: preview must not run as-is\n");
        ScriptedWire fake = new ScriptedWire();
        OperationResult<ArrayLoopSubmitExecutor.LoopSubmitReport> refused =
                new ArrayLoopSubmitExecutor(fake, "/scratch").submitLoop(sweep(),
                        new PbsSchedulerAdapter(), guarded, DECK_TEMPLATE);
        assertFalse(refused.isSuccess());
        assertEquals("SUBMIT_GUARD_PRESENT", refused.getCode());
        assertTrue(refused.getMessage().contains("IS the consent step"),
                refused.getMessage());
        assertTrue(fake.steps.isEmpty(),
                "consent is LOCAL: not even the base mkdir may precede it");
    }

    @Test
    void adaptersWithOwnedArrayFormsRefuseTheSlowPath() throws Exception {
        ScriptedWire fake = new ScriptedWire();
        OperationResult<ArrayLoopSubmitExecutor.LoopSubmitReport> refused =
                new ArrayLoopSubmitExecutor(fake, "/scratch").submitLoop(sweep(),
                        new SlurmSchedulerAdapter(), reviewedScript(), DECK_TEMPLATE);
        assertFalse(refused.isSuccess());
        assertEquals("SUBMIT_LOOP_WRONG_SHAPE", refused.getCode());
        assertTrue(refused.getMessage().contains("ArraySubmitExecutor")
                        && refused.getMessage().contains("never a silent replacement"),
                refused.getMessage());
        assertTrue(fake.steps.isEmpty(), "the shape check precedes all staging");
    }

    @Test
    void deckGrammarRefusalsPassThroughBeforeStaging() throws Exception {
        ScriptedWire fake = new ScriptedWire();
        OperationResult<ArrayLoopSubmitExecutor.LoopSubmitReport> refused =
                new ArrayLoopSubmitExecutor(fake, "/scratch").submitLoop(sweep(),
                        new PbsSchedulerAdapter(), reviewedScript(),
                        "&SYSTEM\n    ibrav = 2,\n/\n");
        assertFalse(refused.isSuccess());
        assertEquals("DECK_KEYWORD", refused.getCode(),
                "the batch-137 grammar owns this refusal; the loop only passes it on");
        assertTrue(refused.getMessage().contains("loop staging refused"),
                refused.getMessage());
        assertTrue(fake.steps.isEmpty(), "an invalid deck never reaches the wire");
    }

    @Test
    void offlineRefusesTypedAndTouchless() throws Exception {
        ScriptedWire fake = new ScriptedWire();
        fake.connected = false;
        OperationResult<ArrayLoopSubmitExecutor.LoopSubmitReport> refused =
                new ArrayLoopSubmitExecutor(fake, "/scratch").submitLoop(sweep(),
                        new PbsSchedulerAdapter(), reviewedScript(), DECK_TEMPLATE);
        assertFalse(refused.isSuccess());
        assertEquals("SSH_SUBMISSION_UNAVAILABLE", refused.getCode());
        assertTrue(fake.steps.isEmpty());
    }

    @Test
    void aFailedSubmitStopsTheLoopWithNamedPartialTruth() throws Exception {
        ScriptedWire fake = new ScriptedWire();
        fake.qsubOutputs.addAll(List.of("201.cluster\n", "", "203.cluster\n"));
        fake.failQsubAt = 2; // 1-based task whose qsub exits non-zero
        OperationResult<ArrayLoopSubmitExecutor.LoopSubmitReport> result =
                new ArrayLoopSubmitExecutor(fake, "/scratch").submitLoop(sweep(),
                        new PbsSchedulerAdapter(), reviewedScript(), DECK_TEMPLATE);
        assertFalse(result.isSuccess());
        assertEquals("SSH_EXEC_FAILED", result.getCode(),
                "the wire's own typed code passes through, never re-labelled");
        ArrayLoopSubmitExecutor.LoopSubmitReport partial =
                result.getValue().orElseThrow(() -> new AssertionError(
                        "partial truth MUST travel with the failure"));
        assertEquals(2, partial.getRecords().size(), "task 3 was never attempted");
        assertEquals(1, partial.getSubmittedCount());
        assertEquals("201", partial.getRecords().get(0).getSchedulerJobId());
        assertTrue(result.getMessage().contains("Loop stopped at task 2 of 3"),
                result.getMessage());
        assertTrue(result.getMessage().contains("1 task id(s) were parsed and those "
                + "job(s) MAY be running"), result.getMessage());
        assertTrue(result.getMessage().contains("ids [201]"), result.getMessage());
        assertTrue(result.getMessage().contains("1 task(s) were never attempted")
                        && result.getMessage().contains("nothing is auto-cancelled blind"),
                result.getMessage());
        assertEquals("qsub", fake.steps.get(fake.steps.size() - 1),
                "the loop really stopped at the failed submit - no further staging");
        assertEquals(1 + 10 + 10, fake.steps.size(),
                "base mkdir + one full task + one task up to its failed qsub");
        assertTrue(fake.removals.isEmpty(), "no cleanup that could hint at cancellation");
    }

    @Test
    void anUnparseableIdStopsTheLoopWithoutInventingOne() throws Exception {
        ScriptedWire fake = new ScriptedWire();
        fake.qsubOutputs.addAll(List.of("301.cluster\n", "weird response from server\n"));
        OperationResult<ArrayLoopSubmitExecutor.LoopSubmitReport> result =
                new ArrayLoopSubmitExecutor(fake, "/scratch").submitLoop(sweep(),
                        new PbsSchedulerAdapter(), reviewedScript(), DECK_TEMPLATE);
        assertFalse(result.isSuccess());
        assertEquals("SUBMIT_ID_UNPARSEABLE", result.getCode());
        ArrayLoopSubmitExecutor.LoopSubmitReport partial =
                result.getValue().orElseThrow();
        assertEquals(2, partial.getRecords().size());
        assertEquals(1, partial.getSubmittedCount());
        assertTrue(result.getMessage().contains("the submission MAY still exist"),
                result.getMessage());
        assertTrue(result.getMessage().contains("ids [301]")
                        && result.getMessage().contains("nothing is auto-cancelled blind"),
                result.getMessage());
        assertTrue(partial.getRecords().get(1).getSchedulerJobId() == null
                        || partial.getRecords().get(1).getSchedulerJobId().isBlank(),
                "no invented id for the ambiguous task");
    }

    @Test
    void nullArgumentsFailLoudlyNotSilently() throws Exception {
        ArrayLoopSubmitExecutor executor = new ArrayLoopSubmitExecutor(
                new ScriptedWire(), "/scratch");
        assertThrows(NullPointerException.class,
                () -> executor.submitLoop(null, new PbsSchedulerAdapter(),
                        reviewedScript(), DECK_TEMPLATE));
        assertThrows(NullPointerException.class,
                () -> executor.submitLoop(sweep(), null, reviewedScript(), DECK_TEMPLATE));
        NullPointerException noDeck = assertThrows(NullPointerException.class,
                () -> executor.submitLoop(sweep(), new PbsSchedulerAdapter(),
                        reviewedScript(), null));
        assertTrue(noDeck.getMessage().contains("ceremonial"), noDeck.getMessage());
        assertThrows(NullPointerException.class,
                () -> ArrayTaskIntent.digestOfRenderedDeck(null),
                "the digest owner refuses to hash nothing");
    }

    /**
     * Scripted wire: stores uploads, answers sha256sum ONLY from stored bytes,
     * and answers qsub calls in order from {@code qsubOutputs} (a 1-based
     * {@code failQsubAt} exits non-zero instead).
     */
    static final class ScriptedWire implements SshTransport {
        final List<String> steps = new ArrayList<>();
        final List<String> removals = new ArrayList<>();
        final List<String> moves = new ArrayList<>();
        final List<String> qsubScripts = new ArrayList<>();
        final List<String> qsubOutputs = new ArrayList<>();
        final Map<String, byte[]> remote = new HashMap<>();
        boolean connected = true;
        int failQsubAt = -1;
        int qsubCalls = 0;

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
                        return OperationResult.failed("SSH_EXEC_FAILED", "no such file",
                                null);
                    }
                    Files.writeString(stdoutFile,
                            ArraySubmitExecutorTest.ScriptedTransport.sha256Hex(data) + "  "
                                    + command[1] + "\n");
                    return OperationResult.success("SSH_EXEC_OK", "ok", 0);
                case "mv":
                    this.moves.add(command[2] + "->" + command[3]);
                    this.remote.put(command[3], this.remote.remove(command[2]));
                    return OperationResult.success("SSH_EXEC_OK", "ok", 0);
                case "qsub":
                    this.qsubCalls++;
                    if (this.failQsubAt == this.qsubCalls) {
                        return OperationResult.failed("SSH_EXEC_FAILED",
                                "qsub exited 153", null);
                    }
                    this.qsubScripts.add(command[1]);
                    String out = this.qsubOutputs.size() >= this.qsubCalls
                            ? this.qsubOutputs.get(this.qsubCalls - 1) : "";
                    Files.writeString(stdoutFile, out);
                    return OperationResult.success("SSH_EXEC_OK", "ok", 0);
                default:
                    return OperationResult.failed("SSH_EXEC_FAILED", "unknown cmd", null);
                }
            } catch (Exception ex) {
                return OperationResult.failed("SSH_EXEC_ERROR", ex.getMessage(), null);
            }
        }
        @Override public OperationResult<Void> uploadFile(Path localFile,
                String remotePath) {
            this.steps.add("upload");
            try {
                this.remote.put(remotePath, Files.readAllBytes(localFile));
            } catch (Exception ex) {
                return OperationResult.failed("SFTP_UPLOAD", ex.getMessage(), null);
            }
            return OperationResult.success("OK", "ok", null);
        }
        @Override public OperationResult<Void> downloadFile(String remotePath,
                Path localFile) {
            return OperationResult.success("OK", "ok", null);
        }
        @Override public OperationResult<Void> mkdirRemote(String remotePath) {
            this.steps.add("mkdir");
            return OperationResult.success("OK", "ok", null);
        }
        @Override public void close() { this.connected = false; }
    }
}
