/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.atoms.visible;

import javafx.scene.shape.Sphere;

public class AtomicSphere extends Sphere {

    private static final int SPHERE_DIV_HIGH = 24;
    private static final int SPHERE_DIV_LOW = 16;

    private VisibleAtom visibleAtom;

    public AtomicSphere(VisibleAtom visibleAtom, boolean divHigh) {
        super(1.0, divHigh ? SPHERE_DIV_HIGH : SPHERE_DIV_LOW);
        this.visibleAtom = visibleAtom;
    }

    public VisibleAtom getVisibleAtom() {
        return this.visibleAtom;
    }

}
