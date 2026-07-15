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

public class WindowsCPUInfo extends CPUInfo {

    private static final String NUM_PROC_VAR = "NUMBER_OF_PROCESSORS";

    public WindowsCPUInfo() {
        super();
    }

    @Override
    protected int countNumCPUs() {
        try {
            String strNcpu = System.getenv(NUM_PROC_VAR);
            if (strNcpu != null) {
                int ncpu = Integer.parseInt(strNcpu);
                return Math.max(ncpu, 1);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return 1;
    }
}
