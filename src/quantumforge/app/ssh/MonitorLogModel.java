/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.ssh;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import quantumforge.hpc.RemoteJobMonitor.StatusUpdate;

/**
 * Ring buffer + line formatter for the FX job-monitor dialog (Roadmap #96
 * GUI slice). Pure JDK on purpose - every display decision (exact line
 * shape, timestamp source, terminal marker, empty-raw quoting, eviction
 * honesty) is pinned headlessly here, so the JavaFX shell above it stays
 * thin and the threading rules live in the shell's javadoc where they
 * belong.
 *
 * <p>Honesty rules owned by this model:</p>
 * <ul>
 *   <li>timestamps are RECEIPT times ({@link Instant} supplied by the
 *       caller, typically {@code Instant.now()} at the moment the update
 *       reached the dialog thread), never remote-claimed times we cannot
 *       prove - stamping happens ONLY here so the shell cannot forget;</li>
 *   <li>an empty raw status renders quoted ({@code raw=''}) - a blank field
 *       is never silently reformatted into a fake status;</li>
 *   <li>the ring has an owned, stated bound
 *       ({@value #MAX_LINES}); evictions are COUNTED and the count is
 *       exposed - a dropped status line is never invisible;</li>
 *   <li>local notes (manual-poll failure codes, dialog lifecycle lines) are
 *       prefixed {@code * } so scheduler-observed lines and dialog-observed
 *       lines can never be confused for one another;</li>
 *   <li>an empty note is not swallowed: it renders as an explicit
 *       placeholder sentence.</li>
 * </ul>
 */
public final class MonitorLogModel {

    /** Owned ring bound, stated on every overflow. */
    public static final int MAX_LINES = 200;

    private final Deque<String> lines = new ArrayDeque<>();
    private int dropped;

    /**
     * Append a scheduler-observed update stamped at receipt; returns the
     * exact line rendered.
     */
    public String appendUpdate(Instant receivedAt, StatusUpdate update) {
        Objects.requireNonNull(update, "update");
        return append(stamp(receivedAt) + " " + formatted(update));
    }

    /** Append a dialog-side note (manual-poll failure, lifecycle). */
    public String appendNote(Instant receivedAt, String note) {
        String safe = note == null ? "" : note.trim();
        if (safe.isEmpty()) {
            safe = "(empty note - nothing to show)";
        }
        return append(stamp(receivedAt) + " * " + safe);
    }

    /** The scheduler-observed line body, exact. Exposed for pinning. */
    public static String formatted(StatusUpdate update) {
        String message = update.getMessage() == null ? "" : update.getMessage();
        return "state=" + update.getRecord().getState()
                + " raw='" + update.getRawStatus() + "'"
                + (update.isTerminal() ? " (terminal)" : "")
                + (message.isEmpty() ? "" : " - " + message);
    }

    private static String stamp(Instant receivedAt) {
        Instant at = receivedAt == null ? Instant.EPOCH : receivedAt;
        return "[" + at + "]";
    }

    private String append(String line) {
        this.lines.addLast(line);
        while (this.lines.size() > MAX_LINES) {
            this.lines.removeFirst();
            this.dropped++;
        }
        return line;
    }

    /** Current visible lines, oldest first. */
    public List<String> snapshot() {
        return new ArrayList<>(this.lines);
    }

    /** The full text the dialog shows, joined by newlines. */
    public String text() {
        return String.join("\n", this.lines);
    }

    public int getDroppedCount() {
        return this.dropped;
    }

    /**
     * The eviction notice - empty while nothing was dropped, else the exact
     * sentence the dialog subtitle shows.
     */
    public String droppedNotice() {
        if (this.dropped <= 0) {
            return "";
        }
        return "(" + this.dropped + " older line(s) dropped - cap " + MAX_LINES + ")";
    }
}
