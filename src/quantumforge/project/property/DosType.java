/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.project.property;

public enum DosType {
    TOTAL(-1, ""),
    PDOS_S(0, "s"),
    PDOS_P(1, "p"),
    PDOS_D(2, "d"),
    PDOS_F(3, "f");

    private int momentum;

    private String orbital;

    private DosType(int momentum, String orbital) {
        this.momentum = momentum;
        this.orbital = orbital;
    }

    public int getMomentum() {
        return this.momentum;
    }

    public String getOrbital() {
        return this.orbital;
    }
}
