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

public interface AtomDesignListener {

    public abstract void onAtomicRadiusChanged(AtomDesign atomDesign, double radius);

    public abstract void onAtomicColorChanged(AtomDesign atomDesign, Color color);

    public abstract void onAtomsStyleChanged(AtomDesign atomDesign, AtomsStyle atomsStyle);

    public abstract void onBondWidthChanged(AtomDesign atomDesign, double bondWidth);

}
