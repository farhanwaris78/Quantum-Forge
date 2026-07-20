/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.ssh;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import quantumforge.hpc.ArraySubmitPlan;
import quantumforge.hpc.ArraySweepPlanner;
import quantumforge.hpc.JobRecord;
import quantumforge.hpc.JobState;
import quantumforge.hpc.SchedulerAdapter;
import quantumforge.operation.OperationResult;

/**
 * Roadmap #93 (site-side executor, single-array slice, batch 142): actually
 * stage and submit ONE array script against a CONNECTED transport - the
 * wire-side counterpoint to the batch-141 guarded draft. It exists because
 * the draft can only ever be reviewed; execution needs a separate, narrower
 * surface with its own refusal ladder, and this is it:
 *
 * <ol>
 *   <li>LOCAL GUARD CHECK FIRST (cheapest and most user-actionable,
 *       deliberately before any remote step): the reviewed script must NOT
 *       still carry the preview's {@code exit 2} REQUIRED-EDIT guard -
 *       removing the guard by hand IS the user's consent step, so an
 *       executor that skipped this check would have no consent model at all
 *       (SUBMIT_GUARD_PRESENT);</li>
 *   <li>ONLY the single-array shape executes: an adapter without owned
 *       array knowledge refuses early with its reason and the named next
 *       step (SUBMIT_ARRAY_UNSUPPORTED_SHAPE - the batch-143 per-task loop
 *       executor {@code ArrayLoopSubmitExecutor} is the honest wire for
 *       that shape, not something silently simulated here);</li>
 *   <li>staging rides the batch-133 verified upload (temp-&gt;sha256-&gt;mv);
 *       the pin is computed from the local bytes with the same-package
 *       digest owner, never re-derived;</li>
 *   <li>the exec line is composed ONLY by
 *       {@link ArraySubmitPlan#composeArrayTokens} - the draft's and the
 *       wire's flag order come from one method by construction;</li>
 *   <li>job-id parsing delegates to the adapter's own
 *       {@code parseJobId(stdout)}; an UNPARSEABLE response is
 *       SUBMIT_ID_UNPARSEABLE and carries the record as partial truth (4-arg
 *       failure) with the message stating the job MAY exist - an
 *       unparseable response is never proof of failure and nothing
 *       auto-cancels blind;</li>
 *   <li>every remote failure passes its own typed code through with the
 *       record transitioned (SSHJob's exact convention), so a failure is
 *       reviewable to the same standard as a success.</li>
 * </ol>
 */
public final class ArraySubmitExecutor {

    private final SSHFileTransfer transfers;
    private final SshTransport transport;
    private final String stagingRoot;

    public ArraySubmitExecutor(SshTransport transport, String stagingRoot) {
        this.transport = transport;
        this.stagingRoot = RemotePathGuard.normalizeStagingRoot(stagingRoot);
        this.transfers = new SSHFileTransfer(new SSHServer("array-executor"));
        this.transfers.setTransport(transport);
        this.transfers.setStagingRoot(this.stagingRoot);
    }

    /**
     * Submit one array script for a validated sweep. Codes:
     * SUBMIT_ARRAY_OK on success; see the class javadoc for the refusal
     * ladder.
     */
    public OperationResult<JobRecord> submitArray(ArraySweepPlanner.SweepPlan sweep,
            SchedulerAdapter adapter, Path reviewedLocalScript) {
        if (sweep == null) {
            throw new NullPointerException("sweep is required");
        }
        if (adapter == null) {
            throw new NullPointerException("adapter is required - tokens/parse are its");
        }
        // (1) local consent check BEFORE anything else: the guard must be gone.
        if (reviewedLocalScript == null || !Files.isRegularFile(reviewedLocalScript)) {
            return OperationResult.failed("SSH_LOCAL_MISSING",
                    "the reviewed array script is missing on disk - nothing was staged or "
                            + "submitted.", null);
        }
        String scriptText;
        try {
            scriptText = Files.readString(reviewedLocalScript);
        } catch (IOException ex) {
            return OperationResult.failed("SSH_LOCAL_MISSING",
                    "could not read the reviewed array script: " + ex.getMessage(),
                    ex);
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
                    "No connected SSH transport; the array was not staged or submitted.");
        }
        if (!adapter.arraySubmitSpec().isSupported()) {
            return OperationResult.failed("SUBMIT_ARRAY_UNSUPPORTED_SHAPE",
                    "the '" + adapter.name() + "' adapter owns no array form - "
                            + adapter.arraySubmitSpec().getUnsupportedReason()
                            + "; submit this sweep through the batch-143 per-task loop "
                            + "executor (ArrayLoopSubmitExecutor) instead - nothing was "
                            + "staged or submitted here either.",
                    null);
        }
        int count = sweep.getValues().size();
        String taskToken = UUID.randomUUID().toString().substring(0, 8);
        String remoteDir = RemotePathGuard.uniqueJobDirectory(this.stagingRoot,
                sweep.getJobBaseName() + "-" + taskToken);
        String remoteScript = remoteDir + "/job-array.sh";
        JobRecord record = new JobRecord(sweep.getJobBaseName() + "-" + taskToken,
                adapter.name(), "", this.stagingRoot);
        try {
            OperationResult<Void> mkdir = this.transport.mkdirRemote(remoteDir);
            if (!mkdir.isSuccess()) {
                record.transition(JobState.FAILED,
                        "staging mkdir refused: " + mkdir.getMessage());
                return OperationResult.failed(mkdir.getCode(),
                        mkdir.getMessage() + " (array staging aborted before any "
                                + "script bytes moved)", record, null);
            }
            String pin;
            try {
                pin = SyncChecksumCache.sha256(reviewedLocalScript);
            } catch (IOException hashFail) {
                record.transition(JobState.FAILED,
                        "local script hash failed: " + hashFail.getMessage());
                return OperationResult.failed("TRANSFER_LOCAL_UNREADABLE",
                        "could not hash the reviewed script: " + hashFail.getMessage(),
                        record, hashFail);
            }
            OperationResult<Void> staged = this.transfers.uploadVerifiedResult(
                    reviewedLocalScript,
                    remoteScript.substring(this.stagingRoot.length() + 1), pin, false);
            if (!staged.isSuccess()) {
                record.transition(JobState.FAILED, "script staging refused: "
                        + staged.getMessage());
                return OperationResult.failed(staged.getCode(),
                        staged.getMessage() + " (array staging failed before submit)",
                        record, null);
            }
            record.transition(JobState.STAGED, "script staged (verified upload): "
                    + remoteScript);
            String[] tokens = ArraySubmitPlan.composeArrayTokens(adapter, count,
                    remoteScript);
            Path stdout = Files.createTempFile("qf-array-", ".out");
            Path stderr = Files.createTempFile("qf-array-", ".err");
            OperationResult<Integer> exec = this.transport.exec(tokens, stdout, stderr);
            String raw = Files.isRegularFile(stdout) ? Files.readString(stdout).trim()
                    : "";
            record.transition(JobState.SUBMITTED, "array submit invoked");
            if (!exec.isSuccess()) {
                record.transition(JobState.FAILED, exec.getMessage());
                return OperationResult.failed(exec.getCode(),
                        exec.getMessage() + " (array submit command failed; the "
                                + "staged script remains at " + remoteScript + ")",
                        record, null);
            }
            Optional<String> parsed = adapter.parseJobId(raw);
            if (parsed.isEmpty() || parsed.get().isBlank()) {
                record.transition(JobState.UNKNOWN,
                        "submit returned HTTP-clean exit but an unparseable id");
                return OperationResult.failed("SUBMIT_ID_UNPARSEABLE",
                        "the '" + adapter.name() + "' adapter could not parse a job id "
                                + "from the submit output (" + abbrev(raw, 120) + ") - "
                                + "the submission MAY still exist; check the queue by hand "
                                + "before retrying (nothing is auto-cancelled blind). The "
                                + "staged script remains at " + remoteScript + ".",
                        record, null);
            }
            record.setSchedulerJobId(parsed.get());
            record.transition(JobState.PENDING, "array job id " + parsed.get()
                    + " parsed by the " + adapter.name() + " adapter");
            return OperationResult.success("SUBMIT_ARRAY_OK",
                    "Array submitted: job id " + parsed.get() + " governs " + count
                            + " task(s) in " + remoteDir + "; task addressing follows the "
                            + "'" + adapter.name() + "' adapter's id grammar (its "
                            + "requireJobId owns the documented id/index forms).",
                    record);
        } catch (Exception ex) {
            record.transition(JobState.FAILED, "executor error: " + ex.getMessage());
            return OperationResult.failed("SFTP_TRANSFER_ERROR",
                    "array submit executor failed: " + ex.getMessage(), record, ex);
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
