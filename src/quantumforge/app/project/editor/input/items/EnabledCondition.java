/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.editor.input.items;

import quantumforge.input.namelist.QEValue;

@FunctionalInterface
public interface EnabledCondition {

    public abstract boolean isEnabled(String name, QEValue value);

}
