/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.atoms.viewer.operation.editor;

import javafx.scene.input.KeyCode;
import quantumforge.atoms.viewer.AtomsViewer;
import quantumforge.atoms.viewer.operation.ViewerEventManager;
import quantumforge.com.keys.KeyNames;

public class CenterMenuItem extends EditorMenuItem {

    private static final String ITEM_LABEL = "Centering [" + KeyNames.getShortcut(KeyCode.C) + "]";

    public CenterMenuItem(ViewerEventManager manager) {
        super(ITEM_LABEL, manager);
    }

    public CenterMenuItem(EditorMenu editorMenu) {
        super(ITEM_LABEL, editorMenu);
    }

    @Override
    protected void editAtoms() {
        if (this.manager == null) {
            return;
        }

        AtomsViewer atomsViewer = this.manager.getAtomsViewer();
        if (atomsViewer != null) {
            atomsViewer.setCellToCenter();
        }
    }
}
