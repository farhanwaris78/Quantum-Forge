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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class MacCPUInfo extends CPUInfo {

    private static final String CHK_MAC_CPUCOM = "system_profiler SPHardwareDataType";

    private static final String CPU_WORD = "Cores";

    public MacCPUInfo() {
        super();
    }

    @Override
    protected int countNumCPUs() {

        int numCPUs = 1;

        try {
            Process process = Runtime.getRuntime().exec(CHK_MAC_CPUCOM);

            int exitCode = 0;
            try {
                exitCode = process.waitFor();
            } catch (InterruptedException e) {
                exitCode = 1;
            }
            if (exitCode != 0) {
                return 1;
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(process
                .getInputStream()));

            String line = null;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.contains(CPU_WORD)){

                    String[] nCPULine = line.split(" ", 0);

                    String strCPUs = null;
                    if (nCPULine != null && nCPULine.length > 0) {
                        strCPUs = nCPULine[nCPULine.length - 1];
                    }

                    if (strCPUs != null) {
                        try {
                            numCPUs = Integer.parseInt(strCPUs);
                        } catch (NumberFormatException e) {
                            numCPUs = 1;
                        }
                    } else {
                        numCPUs = 1;
                    }

                    if (numCPUs <= 0){
                        numCPUs = 1;
                    }

                    break;
                }
            }

        } catch (IOException e) {
            numCPUs = 1;
            e.printStackTrace();
        }

        return numCPUs;
    }
}
