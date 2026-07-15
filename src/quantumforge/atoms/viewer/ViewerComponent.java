/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.atoms.viewer;

import javafx.scene.Node;

public abstract class ViewerComponent<N extends Node> extends ViewerComponentBase<AtomsViewer, N> {

    public ViewerComponent(AtomsViewer atomsViewer) {
        super(atomsViewer);
    }

}
