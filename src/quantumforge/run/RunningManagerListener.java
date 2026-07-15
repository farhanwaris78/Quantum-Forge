/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.run;

public interface RunningManagerListener {

    public abstract void onNodeAdded(RunningNode node);

    public abstract void onNodeRemoved(RunningNode node);

}
