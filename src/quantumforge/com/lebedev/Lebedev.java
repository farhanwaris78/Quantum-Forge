/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.com.lebedev;

public class Lebedev {

    private static Lebedev instance = null;

    public Lebedev getInstance() {
        if (instance == null) {
            instance = new Lebedev();
        }

        return instance;
    }

    private Lebedev() {
        // NOP
    }

}
