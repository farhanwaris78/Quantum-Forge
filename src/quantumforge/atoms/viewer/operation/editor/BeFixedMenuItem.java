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

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.atoms.model.property.AtomProperty;
import quantumforge.atoms.viewer.AtomsViewer;
import quantumforge.atoms.viewer.operation.ViewerEventManager;
import quantumforge.atoms.visible.VisibleAtom;

public class BeFixedMenuItem extends EditorMenuItem {

    private static final String ITEM_LABEL = "Let selected atoms be FIXED";

    public BeFixedMenuItem(ViewerEventManager manager) {
        super(ITEM_LABEL, manager);
    }

    public BeFixedMenuItem(EditorMenu editorMenu) {
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
            if (visibleAtom != null && visibleAtom.isSelected()) {
                numSelected++;
            }
        }
        if (numSelected < 1) {
            return;
        }

        Cell cell = atomsViewer.getCell();
        if (cell == null) {
            return;
        }

        atomsViewer.storeCell();

        for (VisibleAtom visibleAtom : visibleAtoms) {
            if (visibleAtom != null && visibleAtom.isSelected()) {
                Atom atom = visibleAtom.getModel();
                if (atom != null) {
                    atom = atom.getMasterAtom();
                }
                if (atom != null) {
                    atom.setProperty(AtomProperty.FIXED_X, true);
                    atom.setProperty(AtomProperty.FIXED_Y, true);
                    atom.setProperty(AtomProperty.FIXED_Z, true);
                }
            }
        }
    }
}
