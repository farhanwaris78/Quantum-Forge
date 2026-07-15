/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.input.namelist;

public class QEReal extends QEValueBase {

    private double realValue;

    public QEReal(String name, double r) {
        super(name);
        this.realValue = r;
    }

    @Override
    public int getIntegerValue() {
        return (int) this.realValue;
    }

    @Override
    public double getRealValue() {
        return this.realValue;
    }

    @Override
    public boolean getLogicalValue() {
        int intValue = (int) this.realValue;
        return intValue != 0;
    }

    @Override
    public String getCharacterValue() {
        return String.format("%12.5e", Math.abs(this.realValue) < 1.0e-20 ? 0.0 : this.realValue);
    }
}
