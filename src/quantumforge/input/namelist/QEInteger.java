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

public class QEInteger extends QEValueBase {

    private int intValue;

    public QEInteger(String name, int i) {
        super(name);
        this.intValue = i;
    }

    @Override
    public int getIntegerValue() {
        return this.intValue;
    }

    @Override
    public double getRealValue() {
        return (double) this.intValue;
    }

    @Override
    public boolean getLogicalValue() {
        return this.intValue != 0;
    }

    @Override
    public String getCharacterValue() {
        return String.valueOf(this.intValue);
    }
}
