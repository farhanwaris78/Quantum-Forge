/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.ssh;

import java.util.Objects;
import java.util.function.Function;

import quantumforge.hpc.JobRecord;
import quantumforge.operation.OperationResult;

/**
 * Roadmap #96 (FX-thread-depth slice, batch 139): the connect-then-submit
 * sequence extracted from the GUI so that (a) the network phase can run on a
 * background daemon with its ordering and cleanup rules unit-tested
 * headlessly, and (b) the JavaFX surface stays a thin marshal-in/marshal-out
 * shell.
 *
 * <p>Ownership and honesty rules owned here (all pinned by
 * {@code RemoteSubmitChainTest}):</p>
 * <ol>
 *   <li>a failed connect means the submit is NEVER attempted - no scheduler
 *       command may run against a connection we could not honestly open;</li>
 *   <li>on a connected submit that FAILS, the chain CLOSES the transport
 *       exactly once (flagged by {@code transportClosedByChain}) - a dead-end
 *       connection is never handed back to leak;</li>
 *   <li>on success the LIVE transport moves to the caller, which then owns
 *       its lifecycle exactly once (the GUI hands it to the job-monitor
 *       dialog or closes it - never both, never neither);</li>
 *   <li>the connector is INJECTED: the GUI passes {@code HostKeyAcceptance}
 *       (whose host-key prompt is batch-139 thread-safe by audit), tests
 *       pass scripted transports - this chain never builds its own.</li>
 * </ol>
 */
public final class RemoteSubmitChain {

    private RemoteSubmitChain() {
    }

    /** Outcome of one connect+submit attempt, with ownership flags exposed. */
    public static final class SubmitOutcome {
        private final OperationResult<SshTransport> connect;
        private final OperationResult<JobRecord> submit;  // null when never attempted
        private final SshTransport transport;             // non-null only on full success
        private final boolean transportClosedByChain;

        SubmitOutcome(OperationResult<SshTransport> connect, OperationResult<JobRecord> submit,
                SshTransport transport, boolean transportClosedByChain) {
            this.connect = connect;
            this.submit = submit;
            this.transport = transport;
            this.transportClosedByChain = transportClosedByChain;
        }

        /** Always present: the connect attempt's typed result. */
        public OperationResult<SshTransport> getConnect() { return this.connect; }

        /**
         * The submit attempt's typed result, or null when it was never
         * attempted - a null here is a TRUTHFUL "not run", not an error.
         */
        public OperationResult<JobRecord> getSubmit() { return this.submit; }

        /** True ONLY when connect succeeded and submit succeeded. */
        public boolean isSubmitted() {
            return this.submit != null && this.submit.isSuccess();
        }

        /**
         * The live transport on full success - the CALLER now owns its
         * exactly-once close. Null in every other case.
         */
        public SshTransport getTransport() { return this.transport; }

        /**
         * True when the chain already closed a failed-submit transport; the
         * caller must then NOT close anything (there is nothing left to close).
         */
        public boolean wasTransportClosedByChain() { return this.transportClosedByChain; }
    }

    /**
     * Run one connect attempt and, only when it honestly succeeds, one submit
     * attempt. Never throws for expected failures - every refused step is a
     * typed result inside the outcome.
     */
    public static SubmitOutcome connectAndSubmit(SSHServer server, SSHJob job,
            Function<SSHServer, OperationResult<SshTransport>> connector) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(connector, "connector - the chain never builds its own");
        OperationResult<SshTransport> connect =
                Objects.requireNonNull(connector.apply(server),
                        "connector returned a null result - the chain refuses to guess");
        if (!connect.isSuccess() || connect.getValue().isEmpty()) {
            // No submit against a connection we could not honestly open, and
            // nothing to close: the connector fail-closed on its side already.
            return new SubmitOutcome(connect, null, null, false);
        }
        SshTransport transport = connect.getValue().get();
        job.setTransport(transport);
        OperationResult<JobRecord> submit = job.postJobToServerResult();
        if (!submit.isSuccess()) {
            // Exactly-once cleanup INSIDE the chain - documented by the flag.
            transport.close();
            return new SubmitOutcome(connect, submit, null, true);
        }
        return new SubmitOutcome(connect, submit, transport, false);
    }
}
