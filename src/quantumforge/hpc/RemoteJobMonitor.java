/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.hpc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import quantumforge.com.log.AppLog;
import quantumforge.operation.OperationResult;
import quantumforge.ssh.SshTransport;

/**
 * Bounded remote job status polling with stated backoff semantics.
 *
 * <p>The poll loop never storms the scheduler and never lies about failures:
 * unchanged polls grow LINEARLY by +initial per poll (capped at maxDelay);
 * a poll that maps a NEW state resets the interval to the initial delay;
 * transport-error polls back off x2 (capped); a terminal mapping
 * (COMPLETED/FAILED/CANCELLED) stops the loop; UNKNOWN is not terminal for
     * monitoring (reconciliation keeps polling). The interval never goes below
     * {@link #MIN_POLL_MS}.</p>
 */
public final class RemoteJobMonitor implements AutoCloseable {

    /** Default first poll interval when none is given. */
    public static final Duration DEFAULT_INITIAL = Duration.ofSeconds(5);

    /** Default maximum poll interval when none is given. */
    public static final Duration DEFAULT_MAX_DELAY = Duration.ofMinutes(2);

    /** Hard floor under every poll interval, in milliseconds. */
    public static final long MIN_POLL_MS = 1000L;

    public static final class StatusUpdate {
        private final JobRecord record;
        private final String rawStatus;
        private final boolean terminal;
        private final String message;

        public StatusUpdate(JobRecord record, String rawStatus, boolean terminal, String message) {
            this.record = record;
            this.rawStatus = rawStatus == null ? "" : rawStatus;
            this.terminal = terminal;
            this.message = message == null ? "" : message;
        }

        public JobRecord getRecord() { return this.record; }
        public String getRawStatus() { return this.rawStatus; }
        public boolean isTerminal() { return this.terminal; }
        public String getMessage() { return this.message; }
    }

    private final SshTransport transport;
    private final SchedulerAdapter adapter;
    private final JobRecord record;
    private final JobQueueStore queueStore;
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final List<Consumer<StatusUpdate>> listeners = new ArrayList<>();
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> future;
    private volatile boolean closed;
    private volatile long currentDelayMs;

    public RemoteJobMonitor(SshTransport transport, SchedulerAdapter adapter, JobRecord record) {
        this(transport, adapter, record, null, DEFAULT_INITIAL, DEFAULT_MAX_DELAY);
    }

    public RemoteJobMonitor(SshTransport transport, SchedulerAdapter adapter, JobRecord record,
                            JobQueueStore queueStore, Duration initialDelay, Duration maxDelay) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.record = Objects.requireNonNull(record, "record");
        this.queueStore = queueStore;
        this.initialDelay = initialDelay == null ? DEFAULT_INITIAL : initialDelay;
        this.maxDelay = maxDelay == null ? DEFAULT_MAX_DELAY : maxDelay;
        this.currentDelayMs = Math.max(MIN_POLL_MS, this.initialDelay.toMillis());
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "quantumforge-remote-monitor");
            t.setDaemon(true);
            return t;
        });
    }

    public void addListener(Consumer<StatusUpdate> listener) {
        if (listener != null) {
            synchronized (this.listeners) {
                this.listeners.add(listener);
            }
        }
    }

    public synchronized void start() {
        if (this.closed) {
            throw new IllegalStateException("monitor is closed");
        }
        if (this.future != null) {
            return;
        }
        scheduleNext(0L);
    }

    public OperationResult<StatusUpdate> pollOnce() {
        if (!this.transport.isConnected()) {
            return OperationResult.unsupported("SSH_NOT_CONNECTED",
                    "Transport is not connected; cannot poll job status.");
        }
        String jobId = this.record.getSchedulerJobId();
        if (jobId == null || jobId.isBlank()) {
            return OperationResult.failed("JOB_ID_MISSING",
                    "No scheduler job id to poll.", null);
        }
        try {
            Path stdout = Files.createTempFile("qf-mon-", ".out");
            Path stderr = Files.createTempFile("qf-mon-", ".err");
            OperationResult<Integer> exec = this.transport.exec(
                    this.adapter.statusCommand(jobId), stdout, stderr);
            String raw = Files.isRegularFile(stdout) ? Files.readString(stdout).trim() : "";
            String err = Files.isRegularFile(stderr) ? Files.readString(stderr).trim() : "";
            if (!exec.isSuccess() && raw.isEmpty()) {
                // The transport contract: SSH_EXEC_FAILED means the remote
                // scheduler command RAN and exited non-zero; every other
                // failure (SSH_NOT_CONNECTED, SSH_EXEC_ERROR, ...) is
                // transport-shaped. A scheduler-shaped absence needs the
                // adapter's DOCUMENTED stderr needle - "squeue printed
                // nothing" is indistinguishable from a broken query.
                boolean remoteNonZero = "SSH_EXEC_FAILED".equals(exec.getCode());
                if (remoteNonZero && this.adapter.isJobAbsent(err)) {
                    String message = "job absent per the " + this.adapter.name()
                            + " adapter's documented needle (" + abbrev(err, 120) + ")";
                    this.record.transition(JobState.UNKNOWN, message);
                    StatusUpdate update = new StatusUpdate(this.record, raw, true, message);
                    persist();
                    notifyListeners(update);
                    return OperationResult.success("MONITOR_GONE", message, update);
                }
                if (remoteNonZero) {
                    // The scheduler RAN and refused us, but we own no needle
                    // explaining it. Honest report: the query is UNREADABLE -
                    // non-terminal, no state transition, polling continues.
                    String message = "status query unreadable (exit non-zero, no"
                            + " documented absence needle): " + exec.getMessage()
                            + (err.isEmpty() ? "" : (" / " + abbrev(err, 120)));
                    AppLog.warn("monitor", message);
                    return OperationResult.failed("MONITOR_QUERY_UNREADABLE", message, null);
                }
                // A TRANSPORT failure is never a verdict: no transition, no
                // terminal flag - back off and poll again.
                String message = exec.getMessage() + (err.isEmpty() ? "" : (" / "
                        + abbrev(err, 120)));
                AppLog.warn("monitor", "transport-shaped poll failure: " + message);
                return OperationResult.failed("MONITOR_ERROR",
                        "transport failure (never a status verdict): " + message, null);
            }
            JobState mapped = mapStatus(this.adapter.name(), raw);
            boolean terminal = isTerminal(mapped);
            boolean stateChanged = this.record.getState() != mapped;
            if (stateChanged) {
                this.record.transition(mapped, "poll: " + raw);
            }
            StatusUpdate update = new StatusUpdate(this.record, raw, terminal,
                    "status=" + mapped + " raw=" + raw);
            persist();
            notifyListeners(update);
            if (terminal) {
                stopScheduling();
            } else if (stateChanged) {
                // A fresh transition just happened: reset the interval to the
                // initial delay so follow-up transitions are caught quickly.
                // Unchanged polls keep scheduleNext's linear growth; transport
                // errors back off x2 - both capped at maxDelay.
                this.currentDelayMs = Math.max(MIN_POLL_MS, this.initialDelay.toMillis());
            }
            return OperationResult.success("MONITOR_OK", update.getMessage(), update);
        } catch (Exception ex) {
            // Network blip: backoff and continue.
            this.currentDelayMs = Math.min(this.maxDelay.toMillis(), this.currentDelayMs * 2);
            AppLog.warn("monitor", "poll failed, backoff=" + this.currentDelayMs + "ms: "
                    + ex.getMessage());
            return OperationResult.failed("MONITOR_ERROR", ex.getMessage(), ex);
        }
    }

    private void scheduleNext(long delayMs) {
        if (this.closed) {
            return;
        }
        this.future = this.scheduler.schedule(() -> {
            if (this.closed) {
                return;
            }
            OperationResult<StatusUpdate> result = pollOnce();
            if (this.closed) {
                return;
            }
            if (result.isSuccess() && result.getValue().isPresent()
                    && result.getValue().get().isTerminal()) {
                return;
            }
            if (!result.isSuccess()) {
                this.currentDelayMs = Math.min(this.maxDelay.toMillis(),
                        Math.max(this.initialDelay.toMillis(), this.currentDelayMs * 2));
            } else {
                // gentle growth to reduce load
                this.currentDelayMs = Math.min(this.maxDelay.toMillis(),
                        this.currentDelayMs + this.initialDelay.toMillis());
            }
            scheduleNext(this.currentDelayMs);
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void stopScheduling() {
        if (this.future != null) {
            this.future.cancel(false);
            this.future = null;
        }
    }

    private void persist() {
        if (this.queueStore == null) {
            return;
        }
        try {
            this.queueStore.put(this.record);
        } catch (Exception ex) {
            AppLog.warn("monitor", "queue persist failed: " + ex.getMessage());
        }
    }

    private void notifyListeners(StatusUpdate update) {
        List<Consumer<StatusUpdate>> copy;
        synchronized (this.listeners) {
            copy = List.copyOf(this.listeners);
        }
        for (Consumer<StatusUpdate> listener : copy) {
            try {
                listener.accept(update);
            } catch (RuntimeException ex) {
                AppLog.warn("monitor", "listener failed: " + ex.getMessage());
            }
        }
    }

    /**
     * Scheduler-aware status mapping. Exact single-token outputs (the shape of
     * {@code squeue -h -o %T}) are mapped by the OWNED truth table
     * ({@code JobStateGuard.mapSignal}) - this is what fixes the real
     * divergences of the old substring mapper (PBS {@code F} means FINISHED,
     * not FAILED; PJM codes were entirely unmapped). When the guard does not
     * recognize the whole token (multi-token dumps such as {@code qstat -f}
     * output), the legacy substring pass runs as the labeled fallback.
     */
    static JobState mapStatus(String scheduler, String raw) {
        String sig = raw == null ? "" : raw.trim();
        if (!sig.isEmpty()) {
            quantumforge.operation.OperationResult<quantumforge.remote.JobStateGuard.State>
                    guarded = quantumforge.remote.JobStateGuard.mapSignal(scheduler, sig);
            if (guarded.isSuccess() && guarded.getValue().isPresent()
                    && !"JOBSTATE_UNKNOWN_SIGNAL".equals(guarded.getCode())) {
                return JobState.valueOf(guarded.getValue().get().name());
            }
        }
        return legacySubstringMap(sig);
    }

    /**
     * Legacy scheduler-less mapping (substring pass over the raw text). Kept
     * as the fallback for multi-line status dumps that the per-scheduler
     * guard table intentionally does not pattern-match.
     */
    static JobState mapStatus(String raw) {
        return legacySubstringMap(raw);
    }

    private static JobState legacySubstringMap(String raw) {
        if (raw == null || raw.isBlank()) {
            return JobState.UNKNOWN;
        }
        String u = raw.toUpperCase(Locale.ROOT);
        if (u.contains("PENDING") || u.contains("QUEUED") || u.contains("Q ")
                || u.equals("PD") || u.contains("QW") || u.contains("HQ")) {
            return JobState.PENDING;
        }
        if (u.contains("RUNNING") || u.contains("R ") || u.equals("R")
                || u.contains("ACTIVE") || u.contains("CF") || u.contains("CG")) {
            // CG = completing on SLURM; treat as running until gone
            if (u.contains("CG") || u.contains("COMPLETING")) {
                return JobState.RUNNING;
            }
            return JobState.RUNNING;
        }
        if (u.contains("COMPLETED") || u.contains("DONE") || u.contains("CD")
                || u.contains("EXIT_STATUS") && u.contains("0")) {
            return JobState.COMPLETED;
        }
        if (u.contains("FAILED") || u.contains("F ") || u.equals("F")
                || u.contains("TIMEOUT") || u.contains("NODE_FAIL") || u.contains("OUT_OF_MEMORY")) {
            return JobState.FAILED;
        }
        if (u.contains("CANCEL") || u.contains("CA") || u.contains("DELETED")) {
            return JobState.CANCELLED;
        }
        return JobState.UNKNOWN;
    }

    /**
     * Terminal for MONITORING purposes. UNKNOWN is intentionally NOT terminal
     * here (the monitor keeps polling after a reconciliation edge); the #95
     * guard's own isTerminal treats UNKNOWN as terminal for the STATE MACHINE
     * - the difference is stated in the JOB_MONITOR_AUDIT kind.
     */
    static boolean isTerminal(JobState state) {
        return state == JobState.COMPLETED || state == JobState.FAILED
                || state == JobState.CANCELLED;
    }

    private static String abbrev(String text, int max) {
        if (text == null) {
            return "";
        }
        String single = text.replace('\n', ' ').trim();
        return single.length() <= max ? single : single.substring(0, max) + "...";
    }

    @Override
    public void close() {
        this.closed = true;
        stopScheduling();
        this.scheduler.shutdownNow();
    }
}
