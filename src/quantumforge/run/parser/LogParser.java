/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.run.parser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import quantumforge.com.io.LiveFileTailer;
import quantumforge.com.log.AppLog;
import quantumforge.project.property.ProjectProperty;

public abstract class LogParser {

    private static final long SLEEP_TIME = 2000L; // Reduced for live graphing

    private boolean parsing;

    private boolean ending;

    protected ProjectProperty property;

    /** Optional line-oriented tailer for subclasses that prefer incremental UTF-8 reads. */
    private LiveFileTailer tailer;

    public LogParser(ProjectProperty property) {
        if (property == null) {
            throw new IllegalArgumentException("property is null.");
        }

        this.parsing = false;
        this.ending = false;
        this.property = property;
        this.tailer = null;
    }

    public abstract void parse(File file) throws IOException;

    /**
     * Optional hook: subclasses may override to consume complete lines only.
     * Default implementation falls back to full-file {@link #parse(File)}.
     */
    protected void parseCompleteLines(java.util.List<String> lines) throws IOException {
        // Default: no-op; full-file parse remains the source of truth.
    }

    public void startParsing(File file) {
        if (file == null) {
            return;
        }

        synchronized (this) {
            this.parsing = true;
            this.ending = false;
            this.tailer = new LiveFileTailer(file.toPath());
        }

        Thread thread = new Thread(() -> {
            while (true) {
                synchronized (this) {
                    if (!this.parsing) {
                        break;
                    }
                }

                try {
                    LiveFileTailer active;
                    synchronized (this) {
                        active = this.tailer;
                    }
                    if (active != null) {
                        java.util.List<String> lines = active.pollLines();
                        if (!lines.isEmpty()) {
                            this.parseCompleteLines(lines);
                        }
                    }
                    // Full re-parse keeps existing energy/geometry models correct.
                    this.parse(file);
                } catch (Exception e) {
                    AppLog.warn("parser", "Live parse failed: " + e.getMessage());
                }

                synchronized (this) {
                    if (!this.parsing) {
                        break;
                    }

                    try {
                        this.wait(SLEEP_TIME);
                    } catch (Exception e) {
                        AppLog.warn("parser", "Parser wait interrupted: " + e.getMessage());
                    }
                }
            }

            try {
                LiveFileTailer active;
                synchronized (this) {
                    active = this.tailer;
                }
                if (active != null) {
                    java.util.List<String> rest = active.flushPartial();
                    if (!rest.isEmpty()) {
                        this.parseCompleteLines(rest);
                    }
                }
                this.parse(file);
            } catch (Exception e) {
                AppLog.warn("parser", "Final parse failed: " + e.getMessage());
            }

            synchronized (this) {
                this.ending = false;
                this.notifyAll();
            }
        }, "quantumforge-log-parser");

        thread.setDaemon(true);
        thread.start();
    }

    public void endParsing() {
        synchronized (this) {
            if (!this.parsing) {
                return;
            }

            this.parsing = false;
            this.ending = true;
            this.notifyAll();
        }

        synchronized (this) {
            while (this.ending) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    AppLog.warn("parser", "Interrupted while stopping parser");
                    break;
                }
            }

            this.parsing = false;
            this.ending = false;
            this.tailer = null;
        }
    }

    protected Path currentTailPath() {
        return this.tailer == null ? null : this.tailer.getPath();
    }
}
