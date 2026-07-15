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

import java.util.List;

import javafx.scene.input.KeyCode;
import quantumforge.atoms.viewer.AtomsViewer;
import quantumforge.atoms.viewer.operation.ViewerEventManager;
import quantumforge.atoms.visible.VisibleAtom;
import quantumforge.com.keys.KeyNames;

public class SelectAllMenuItem extends EditorMenuItem {

    private static final String ITEM_LABEL = "Select all atoms [" + KeyNames.getShortcut(KeyCode.A) + "]";

    public SelectAllMenuItem(ViewerEventManager manager) {
        super(ITEM_LABEL, manager);
    }

    public SelectAllMenuItem(EditorMenu editorMenu) {
        super(ITEM_LABEL, editorMenu);
    }

    @Override
    protected void editAtoms() {
        if (this.manager == null) {
            return;
        }

        AtomsViewer atomsViewer = this.manager.getAtomsViewer();
        if (atomsViewer == null) {
            return;
        }

        List<VisibleAtom> visibleAtoms = atomsViewer.getVisibleAtoms();
        for (VisibleAtom visibleAtom : visibleAtoms) {
            if (visibleAtom != null) {
                visibleAtom.setSelected(true);
            }
        }

        this.manager.clearPrincipleAtom();
    }
}
