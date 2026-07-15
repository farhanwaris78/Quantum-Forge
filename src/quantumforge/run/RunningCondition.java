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

import quantumforge.input.QEInput;
import quantumforge.project.Project;

@FunctionalInterface
public interface RunningCondition {

    public abstract boolean toRun(Project project, QEInput input);

}
