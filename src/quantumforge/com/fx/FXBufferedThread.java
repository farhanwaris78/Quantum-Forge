/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.com.fx;

import java.util.LinkedList;
import java.util.Queue;

public final class FXBufferedThread implements Runnable {

    private static final long DEF_SLEEP_TIME = 100L;

    private long sleepTime;

    private boolean skip;

    private boolean running;

    private Queue<Runnable> runnables;

    public FXBufferedThread() {
        this(false);
    }

    public FXBufferedThread(boolean skip) {
        this(0L, skip);
    }

    public FXBufferedThread(long sleepTime, boolean skip) {
        if (sleepTime > 0L) {
            this.sleepTime = sleepTime;
        } else {
            this.sleepTime = DEF_SLEEP_TIME;
        }

        this.skip = skip;

        this.running = false;
        this.runnables = null;
    }

    public void runLater(Runnable runnable) {
        if (runnable == null) {
            return;
        }

        boolean toStartThread = false;

        synchronized (this) {
            if (this.runnables == null) {
                this.runnables = new LinkedList<Runnable>();
            }

            this.runnables.offer(runnable);

            if (!this.running) {
                this.running = true;
                toStartThread = true;
            }
        }

        if (toStartThread) {
            Thread thread = new Thread(this);
            thread.start();
        }
    }

    @Override
    public void run() {
        long initTime = this.sleepTime / 2L;
        if (initTime > 0L) {
            synchronized (this) {
                try {
                    this.wait(initTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        while (true) {
            Runnable runnable = null;

            synchronized (this) {
                if (this.runnables != null) {
                    int n = this.skip ? this.runnables.size() : 1;
                    for (int i = 0; i < n; i++) {
                        runnable = this.runnables.poll();
                    }
                }

                if (runnable == null) {
                    this.running = false;
                }
            }

            if (runnable == null) {
                break;
            }

            Runnable runnable_ = runnable;
            FXThread<Object> thread = new FXThread<Object>(() -> {
                runnable_.run();
                return null;
            });

            thread.runAndWait();

            synchronized (this) {
                try {
                    this.wait(this.sleepTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
