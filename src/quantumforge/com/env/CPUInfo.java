/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.com.env;

public abstract class CPUInfo {

    private static CPUInfo instance = null;

    public static CPUInfo getInstance() {
        if (instance == null) {
            if (Environments.isWindows()) {
                instance = new WindowsCPUInfo();

            } else if (Environments.isMac()) {
                instance = new MacCPUInfo();

            } else if (Environments.isLinux()) {
                instance = new LinuxCPUInfo();
            }
        }

        return instance;
    }

    private int numCPUs;

    protected CPUInfo() {
        this.numCPUs = this.countNumCPUs();
    }

    public final int getNumCPUs() {
        return this.numCPUs;
    }

    protected abstract int countNumCPUs();

}
