/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.atoms.model.event;

public interface CellEventListener extends ModelEventListener {

    public abstract void onLatticeMoved(CellEvent event);

    public abstract void onAtomAdded(CellEvent event);

    public abstract void onAtomRemoved(CellEvent event);

    public abstract void onBondAdded(CellEvent event);

    public abstract void onBondRemoved(CellEvent event);

}
