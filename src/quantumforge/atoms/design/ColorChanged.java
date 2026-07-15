/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.atoms.design;

import javafx.scene.paint.Color;

@FunctionalInterface
public interface ColorChanged {

    public abstract void onColorChanged(Color color);

}
