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

import quantumforge.project.property.ProjectProperty;

public abstract class LogParser {

    private static final long SLEEP_TIME = 2000L; // Reduced for live graphing

    private boolean parsing;

    private boolean ending;

    protected ProjectProperty property;

    public LogParser(ProjectProperty property) {
        if (property == null) {
            throw new IllegalArgumentException("property is null.");
        }

        this.parsing = false;
        this.ending = false;
        this.property = property;
    }

    public abstract void parse(File file) throws IOException;

    public void startParsing(File file) {
        if (file == null) {
            return;
        }

        synchronized (this) {
            this.parsing = true;
            this.ending = false;
        }

        Thread thread = new Thread(() -> {
            while (true) {
                synchronized (this) {
                    if (!this.parsing) {
                        break;
                    }
                }

                try {
                    this.parse(file);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                synchronized (this) {
                    if (!this.parsing) {
                        break;
                    }

                    try {
                        this.wait(SLEEP_TIME);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            try {
                this.parse(file);
            } catch (Exception e) {
                e.printStackTrace();
            }

            synchronized (this) {
                this.ending = false;
                this.notifyAll();
            }
        });

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
                    e.printStackTrace();
                }
            }

            this.parsing = false;
            this.ending = false;
        }
    }
}
