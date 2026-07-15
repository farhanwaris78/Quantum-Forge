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

import quantumforge.atoms.viewer.AtomsViewer;
import quantumforge.atoms.viewer.operation.ViewerEventManager;
import quantumforge.atoms.visible.VisibleAtom;

public class MoveMenuItem extends EditorMenuItem {

    private static final String ITEM_LABEL = "Move selected atoms";

    public MoveMenuItem(ViewerEventManager manager) {
        super(ITEM_LABEL, manager);
    }

    public MoveMenuItem(EditorMenu editorMenu) {
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

        int numSelected = 0;
        for (VisibleAtom visibleAtom : visibleAtoms) {
            if (visibleAtom.isSelected()) {
                numSelected++;
            }
        }
        if (numSelected < 1) {
            return;
        }

        VisibleAtom principleAtom = this.manager.getPrincipleAtom();
        if (principleAtom == null) {
            return;
        }

        atomsViewer.setCompassMode(principleAtom);
    }
}
