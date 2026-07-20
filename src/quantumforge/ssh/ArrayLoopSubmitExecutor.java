/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.ssh;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import quantumforge.hpc.ArrayDeckTemplate;
import quantumforge.hpc.ArraySweepPlanner;
import quantumforge.hpc.ArrayTaskIntent;
import quantumforge.hpc.JobRecord;
import quantumforge.hpc.JobState;
import quantumforge.hpc.SchedulerAdapter;
import quantumforge.operation.OperationResult;

/**
 * Roadmap #93 (site-side executor, per-task loop slice, batch 143): actually
 * stage and submit an array sweep ONE TASK AT A TIME against a CONNECTED
 * transport - the wire-side counterpoint to the batch-141 guarded draft's
 * PER-TASK SUBMIT LOOP shape, and the only honest path for adapters that
 * deliberately own no array form (PBS: the batch-141 Pro-vs-Torque
 * divergence, which this executor never has to resolve because it uses only
 * the documented plain {@code submitCommand}).
 *
 * <p>The per-task distinction is REAL here, not ceremonial: each task
 * directory receives its OWN rendered deck (batch-137 grammar) whose staging
 * pin is the batch-140 digest from the single owner
 * {@link ArrayTaskIntent#digestOfRenderedDeck} - the record's history note
 * IS the provenance seam the intent JSONL anchors, verifiable offline.</p>
 *
 * <ol>
 *   <li>LOCAL GUARD CHECK FIRST (identical consent model to the batch-142
 *       single-array executor, deliberately before any remote step): the
 *       reviewed script must NOT still carry the preview's {@code exit 2}
 *       REQUIRED-EDIT guard (SUBMIT_GUARD_PRESENT);</li>
 *   <li>ONLY the loop shape executes: an adapter that OWNS an array form
 *       refuses with SUBMIT_LOOP_WRONG_SHAPE naming the single-array
 *       executor - silently choosing the slow path would hide the better,
 *       owned answer;</li>
 *   <li>the deck template is validated through batch-137's
 *       {@code ArrayDeckTemplate.validate} itself (its DECK_* codes pass
 *       through) - nobody may hand this executor a pre-built template that
 *       could disagree with the sweep;</li>
 *   <li>per task, in order: mkdir task dir, verified-upload the rendered
 *       deck as {@value #TASK_DECK_NAME}, verified-upload the reviewed
 *       script as {@value #TASK_SCRIPT_NAME}, then the adapter's own
 *       documented {@code submitCommand}, its own {@code parseJobId} on that
 *       one stdout;</li>
 *   <li>FAIL-FAST WITH PARTIAL TRUTH: the first anomalous task stops the
 *       loop (a failed step passes its typed code through; an unparseable id
 *       is SUBMIT_ID_UNPARSEABLE) and the 4-arg payload carries every record
 *       written so far - the message names which ids MAY already be running
 *       and that nothing is auto-cancelled blind. Blindly continuing to
 *       submit while an ambiguous job exists would multiply unaccountable
 *       work;</li>
 *   <li>working-directory honesty: submits are invoked as
 *       {@code <submit binary> <absolute script path>}; where the job's
 *       runtime cwd lands is the SITE's scheduler rule (checklist [2] of the
 *       draft family) - the deck simply sits next to the script as
 *       {@value #TASK_DECK_NAME}, and the success message says so verbatim.
 *   </li>
 * </ol>
 */
public final class ArrayLoopSubmitExecutor {

    /** Per-task deck name inside each staged task directory (a contract). */
    public static final String TASK_DECK_NAME = "input.deck";
    /** Per-task script name inside each staged task directory (a contract). */
    public static final String TASK_SCRIPT_NAME = "job.sh";

    private final SSHFileTransfer transfers;
    private final SshTransport transport;
    private final String stagingRoot;

    public ArrayLoopSubmitExecutor(SshTransport transport, String stagingRoot) {
        this.transport = transport;
        this.stagingRoot = RemotePathGuard.normalizeStagingRoot(stagingRoot);
        this.transfers = new SSHFileTransfer(new SSHServer("array-loop-executor"));
        this.transfers.setTransport(transport);
        this.transfers.setStagingRoot(this.stagingRoot);
    }

    /** Per-task outcome of one loop submit attempt, in task order. */
    public static final class LoopSubmitReport {
        private final String remoteDirectory;
        private final int taskCount;
        private final List<JobRecord> records;

        LoopSubmitReport(String remoteDirectory, int taskCount, List<JobRecord> records) {
            this.remoteDirectory = remoteDirectory;
            this.taskCount = taskCount;
            this.records = Collections.unmodifiableList(new ArrayList<>(records));
        }

        /** The unique remote base directory that holds every task dir. */
        public String getRemoteDirectory() { return this.remoteDirectory; }

        /** The sweep's task count - attempted or not, this many were owed. */
        public int getTaskCount() { return this.taskCount; }

        /** One record per task WRITTEN so far (task order); never null. */
        public List<JobRecord> getRecords() { return this.records; }

        /** Tasks with a parsed scheduler id (genuinely accountable work). */
        public int getSubmittedCount() {
            int done = 0;
            for (JobRecord record : this.records) {
                String id = record.getSchedulerJobId();
                if (id != null && !id.isBlank()) {
                    done++;
                }
            }
            return done;
        }
    }

    /**
     * Submit one sweep task-by-task. Codes: SUBMIT_LOOP_OK on success;
     * SUBMIT_GUARD_PRESENT, SSH_LOCAL_MISSING, SUBMIT_LOOP_WRONG_SHAPE,
     * DECK_* (pass-through), SSH_SUBMISSION_UNAVAILABLE,
     * SUBMIT_ID_UNPARSEABLE and staged/exec pass-through codes on refusals.
     */
    public OperationResult<LoopSubmitReport> submitLoop(ArraySweepPlanner.SweepPlan sweep,
            SchedulerAdapter adapter, Path reviewedLocalScript, String deckTemplateText) {
        if (sweep == null) {
            throw new NullPointerException("sweep is required");
        }
        if (adapter == null) {
            throw new NullPointerException("adapter is required - tokens/parse are its");
        }
        if (deckTemplateText == null) {
            throw new NullPointerException("deck template text is required - a loop "
                    + "without per-task decks would submit identical ceremonial jobs");
        }
        // (1) local consent check BEFORE anything else: the guard must be gone.
        if (reviewedLocalScript == null || !Files.isRegularFile(reviewedLocalScript)) {
            return OperationResult.failed("SSH_LOCAL_MISSING",
                    "the reviewed loop script is missing on disk - nothing was staged or "
                            + "submitted.", null);
        }
        String scriptText;
        try {
            scriptText = Files.readString(reviewedLocalScript);
        } catch (IOException ex) {
            return OperationResult.failed("SSH_LOCAL_MISSING",
                    "could not read the reviewed loop script: " + ex.getMessage(), ex);
        }
        if (scriptText.lines().anyMatch(line -> line.trim().startsWith("exit 2"))) {
            return OperationResult.failed("SUBMIT_GUARD_PRESENT",
                    "the reviewed script still carries the preview's exit-2 REQUIRED-EDIT "
                            + "guard - removing it by hand IS the consent step. The "
                            + "executor refuses to guess consent; nothing was staged or "
                            + "submitted.",
                    null);
        }
        if (this.transport == null || !this.transport.isConnected()) {
            return OperationResult.unsupported("SSH_SUBMISSION_UNAVAILABLE",
                    "No connected SSH transport; the sweep was not staged or submitted.");
        }
        // (2) the loop is the ONLY shape this executor owns.
        if (adapter.arraySubmitSpec().isSupported()) {
            return OperationResult.failed("SUBMIT_LOOP_WRONG_SHAPE",
                    "the '" + adapter.name() + "' adapter OWNS an array form (its spec "
                            + "names it) - submit this sweep through the single-array "
                            + "executor (ArraySubmitExecutor) instead; the per-task loop "
                            + "is the honest fallback for adapters without one, never a "
                            + "silent replacement for the owned path. Nothing was staged "
                            + "or submitted.",
                    null);
        }
        // (3) the deck grammar is batch-137's; this executor validates, never trusts.
        OperationResult<ArrayDeckTemplate.DeckTemplate> templated =
                ArrayDeckTemplate.validate(sweep, deckTemplateText);
        if (!templated.isSuccess() || templated.getValue().isEmpty()) {
            return OperationResult.failed(templated.getCode(),
                    templated.getMessage() + " (loop staging refused - nothing was "
                            + "staged or submitted)", null);
        }
        ArrayDeckTemplate.DeckTemplate deck = templated.getValue().get();
        int count = sweep.getValues().size();
        String taskToken = UUID.randomUUID().toString().substring(0, 8);
        String remoteBase = RemotePathGuard.uniqueJobDirectory(this.stagingRoot,
                sweep.getJobBaseName() + "-" + taskToken);
        List<JobRecord> records = new ArrayList<>();
        try {
            OperationResult<Void> mkdirBase = this.transport.mkdirRemote(remoteBase);
            if (!mkdirBase.isSuccess()) {
                return OperationResult.failed(mkdirBase.getCode(),
                        mkdirBase.getMessage() + " (loop staging aborted before any "
                                + "task directory existed)", report(remoteBase, count, records),
                        null);
            }
            String scriptPin;
            try {
                scriptPin = SyncChecksumCache.sha256(reviewedLocalScript);
            } catch (IOException hashFail) {
                return OperationResult.failed("TRANSFER_LOCAL_UNREADABLE",
                        "could not hash the reviewed script: " + hashFail.getMessage(),
                        report(remoteBase, count, records), hashFail);
            }
            for (int i = 1; i <= count; i++) {
                String taskDir = remoteBase + "/" + sweep.taskDirectory(i);
                JobRecord record = new JobRecord(
                        sweep.taskDirectory(i) + "-" + taskToken, adapter.name(), "",
                        this.stagingRoot);
                records.add(record);
                OperationResult<Void> mkdirTask = this.transport.mkdirRemote(taskDir);
                if (!mkdirTask.isSuccess()) {
                    record.transition(JobState.FAILED,
                            "task mkdir refused: " + mkdirTask.getMessage());
                    return loopFailure(mkdirTask.getCode(), i,
                            mkdirTask.getMessage()
                                    + " (task " + i + " staging aborted)",
                            remoteBase, count, records);
                }
                // Deck first: the per-task distinction, pinned by the digest owner.
                String rendered = deck.renderTaskDeck(i);
                String deckPin = ArrayTaskIntent.digestOfRenderedDeck(rendered);
                Path localDeck = Files.createTempFile("qf-loop-deck-", ".in");
                Files.writeString(localDeck, rendered, StandardCharsets.UTF_8);
                OperationResult<Void> deckStaged = this.transfers.uploadVerifiedResult(
                        localDeck, relative(taskDir + "/" + TASK_DECK_NAME), deckPin,
                        false);
                deleteQuietly(localDeck);
                if (!deckStaged.isSuccess()) {
                    record.transition(JobState.FAILED,
                            "deck staging refused: " + deckStaged.getMessage());
                    return loopFailure(deckStaged.getCode(), i,
                            deckStaged.getMessage() + " (task " + i + " deck staging "
                                    + "failed before its script moved)",
                            remoteBase, count, records);
                }
                OperationResult<Void> scriptStaged = this.transfers.uploadVerifiedResult(
                        reviewedLocalScript, relative(taskDir + "/" + TASK_SCRIPT_NAME),
                        scriptPin, false);
                if (!scriptStaged.isSuccess()) {
                    record.transition(JobState.FAILED,
                            "script staging refused: " + scriptStaged.getMessage());
                    return loopFailure(scriptStaged.getCode(), i,
                            scriptStaged.getMessage() + " (task " + i + " script staging "
                                    + "failed before its submit)",
                            remoteBase, count, records);
                }
                record.transition(JobState.STAGED,
                        "deck+script staged (verified uploads); deck sha256 " + deckPin);
                String remoteScript = taskDir + "/" + TASK_SCRIPT_NAME;
                Path stdout = Files.createTempFile("qf-loop-", ".out");
                Path stderr = Files.createTempFile("qf-loop-", ".err");
                OperationResult<Integer> exec = this.transport.exec(
                        adapter.submitCommand(remoteScript), stdout, stderr);
                String raw = Files.isRegularFile(stdout)
                        ? Files.readString(stdout).trim() : "";
                deleteQuietly(stdout);
                deleteQuietly(stderr);
                record.transition(JobState.SUBMITTED, "per-task submit invoked");
                if (!exec.isSuccess()) {
                    record.transition(JobState.FAILED, exec.getMessage());
                    return loopFailure(exec.getCode(), i,
                            exec.getMessage() + " (task " + i + " submit command failed; "
                                    + "its staged directory remains at " + taskDir + ")",
                            remoteBase, count, records);
                }
                Optional<String> parsed = adapter.parseJobId(raw);
                if (parsed.isEmpty() || parsed.get().isBlank()) {
                    record.transition(JobState.UNKNOWN,
                            "submit exited clean but the id would not parse");
                    return loopFailure("SUBMIT_ID_UNPARSEABLE", i,
                            "task " + i + ": the '" + adapter.name() + "' adapter could "
                                    + "not parse a job id from the submit output ("
                                    + abbrev(raw, 120) + ") - the submission MAY still "
                                    + "exist; check the queue by hand before retrying "
                                    + "(nothing is auto-cancelled blind).",
                            remoteBase, count, records);
                }
                record.setSchedulerJobId(parsed.get());
                record.transition(JobState.PENDING, "task " + i + " job id " + parsed.get()
                        + " parsed by the " + adapter.name() + " adapter");
            }
            LoopSubmitReport done = report(remoteBase, count, records);
            return OperationResult.success("SUBMIT_LOOP_OK",
                    "Per-task loop submitted: " + done.getSubmittedCount() + " of "
                            + count + " task id(s) parsed under " + remoteBase
                            + ". Each submit ran the '" + adapter.name() + "' adapter's "
                            + "documented command as <binary> <absolute script path> - "
                            + "where the job's working directory lands is YOUR site's "
                            + "scheduler rule; each deck sits next to its script as "
                            + TASK_DECK_NAME + ".",
                    done);
        } catch (Exception ex) {
            return OperationResult.failed("SFTP_TRANSFER_ERROR",
                    "array loop executor failed: " + ex.getMessage(),
                    report(remoteBase, count, records), ex);
        }
    }

    private OperationResult<LoopSubmitReport> loopFailure(String code, int failedTask,
            String detail, String remoteBase, int count, List<JobRecord> records) {
        LoopSubmitReport partial = report(remoteBase, count, records);
        int accountable = partial.getSubmittedCount();
        int neverAttempted = count - records.size();
        StringBuilder message = new StringBuilder(detail);
        message.append(" Loop stopped at task ").append(failedTask).append(" of ")
                .append(count).append(": ").append(accountable)
                .append(" task id(s) were parsed and those job(s) MAY be running - ");
        if (accountable > 0) {
            List<String> ids = new ArrayList<>();
            for (JobRecord record : records) {
                String id = record.getSchedulerJobId();
                if (id != null && !id.isBlank()) {
                    ids.add(id);
                }
            }
            message.append("ids [").append(String.join(", ", ids)).append("]; ");
        }
        message.append(neverAttempted).append(" task(s) were never attempted; nothing "
                + "is auto-cancelled blind - review the queue by hand.");
        return OperationResult.failed(code, message.toString(), partial, null);
    }

    private LoopSubmitReport report(String remoteBase, int count, List<JobRecord> records) {
        return new LoopSubmitReport(remoteBase, count, records);
    }

    private String relative(String absoluteRemotePath) {
        return absoluteRemotePath.substring(this.stagingRoot.length() + 1);
    }

    private static void deleteQuietly(Path temp) {
        try {
            Files.deleteIfExists(temp);
        } catch (IOException ignored) {
            // A scratch temp left behind is honest clutter, never masked state.
        }
    }

    private static String abbrev(String text, int max) {
        if (text == null) {
            return "";
        }
        String single = text.replace('\n', ' ').trim();
        return single.length() <= max ? single : single.substring(0, max) + "...";
    }
}
