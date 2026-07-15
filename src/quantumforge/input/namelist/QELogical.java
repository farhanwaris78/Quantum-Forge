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

public class QELogical extends QEValueBase {

    private boolean logValue;

    public QELogical(String name, boolean l) {
        super(name);
        this.logValue = l;
    }

    @Override
    public int getIntegerValue() {
        return this.logValue ? 1 : 0;
    }

    @Override
    public double getRealValue() {
        return this.logValue ? 1.0 : 0.0;
    }

    @Override
    public boolean getLogicalValue() {
        return this.logValue;
    }

    @Override
    public String getCharacterValue() {
        return "." + String.valueOf(this.logValue).toUpperCase() + ".";
    }
}
