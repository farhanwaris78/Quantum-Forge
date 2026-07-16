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
 * Bounded remote job status polling with exponential backoff.
 *
 * <p>Does not storm the scheduler: starts at {@code initialDelay}, doubles up to
 * {@code maxDelay}, and stops on terminal states or explicit close.</p>
 */
public final class RemoteJobMonitor implements AutoCloseable {

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
        this(transport, adapter, record, null, Duration.ofSeconds(5), Duration.ofMinutes(2));
    }

    public RemoteJobMonitor(SshTransport transport, SchedulerAdapter adapter, JobRecord record,
                            JobQueueStore queueStore, Duration initialDelay, Duration maxDelay) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.record = Objects.requireNonNull(record, "record");
        this.queueStore = queueStore;
        this.initialDelay = initialDelay == null ? Duration.ofSeconds(5) : initialDelay;
        this.maxDelay = maxDelay == null ? Duration.ofMinutes(2) : maxDelay;
        this.currentDelayMs = Math.max(1000L, this.initialDelay.toMillis());
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
                // Job disappeared from queue often means completed/cancelled.
                String message = exec.getMessage() + (err.isEmpty() ? "" : (" / " + err));
                boolean terminal = true;
                this.record.transition(JobState.UNKNOWN, "status unavailable: " + message);
                StatusUpdate update = new StatusUpdate(this.record, raw, terminal, message);
                persist();
                notifyListeners(update);
                return OperationResult.success("MONITOR_GONE", message, update);
            }
            JobState mapped = mapStatus(raw);
            boolean terminal = isTerminal(mapped);
            if (this.record.getState() != mapped) {
                this.record.transition(mapped, "poll: " + raw);
            }
            StatusUpdate update = new StatusUpdate(this.record, raw, terminal,
                    "status=" + mapped + " raw=" + raw);
            persist();
            notifyListeners(update);
            if (terminal) {
                stopScheduling();
            } else {
                // backoff on success path too, but milder: reset toward initial after change
                this.currentDelayMs = Math.min(this.maxDelay.toMillis(),
                        Math.max(this.initialDelay.toMillis(), this.currentDelayMs));
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

    static JobState mapStatus(String raw) {
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

    static boolean isTerminal(JobState state) {
        return state == JobState.COMPLETED || state == JobState.FAILED
                || state == JobState.CANCELLED;
    }

    @Override
    public void close() {
        this.closed = true;
        stopScheduling();
        this.scheduler.shutdownNow();
    }
}
