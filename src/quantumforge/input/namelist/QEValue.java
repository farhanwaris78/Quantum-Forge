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

public interface QEValue {

    public abstract String getName();

    public abstract int getIntegerValue();

    public abstract double getRealValue();

    public abstract boolean getLogicalValue();

    public abstract String getCharacterValue();

    public abstract String toString(int length);

    public abstract String toString();

}
