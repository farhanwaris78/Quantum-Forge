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
public interface WarningCondition {

    public static final int OK = 0;

    public static final int WARNING = 1;

    public static final int ERROR = 2;

    public abstract int getStatus(String name, QEValue value);

}
