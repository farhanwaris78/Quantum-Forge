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

public interface DosInterface {

    public abstract DosType getType();

    public abstract boolean isSpinPolarized();

    public abstract int getAtomIndex();

    public abstract String getAtomName();

    public abstract int numPoints();

    public abstract double getEnergy(int i);

    public abstract double getDosUp(int i);

    public abstract double getDosDown(int i);

}
