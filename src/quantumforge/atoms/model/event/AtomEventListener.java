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

public interface AtomEventListener extends ModelEventListener {

    public abstract void onAtomRenamed(AtomEvent event);

    public abstract void onAtomMoved(AtomEvent event);

}
