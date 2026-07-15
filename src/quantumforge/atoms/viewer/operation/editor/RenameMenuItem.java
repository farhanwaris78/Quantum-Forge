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
import java.util.Optional;

import javafx.scene.input.KeyCode;
import quantumforge.atoms.model.Atom;
import quantumforge.atoms.viewer.AtomsViewer;
import quantumforge.atoms.viewer.operation.ViewerEventManager;
import quantumforge.atoms.visible.VisibleAtom;
import quantumforge.com.keys.KeyNames;
import quantumforge.com.periodic.ElementButton;
import quantumforge.com.periodic.PeriodicTable;

public class RenameMenuItem extends EditorMenuItem {

    private static final String ITEM_LABEL = "Rename selected atoms [" + KeyNames.getShortcut(KeyCode.R) + "]";

    public RenameMenuItem(ViewerEventManager manager) {
        super(ITEM_LABEL, manager);
    }

    public RenameMenuItem(EditorMenu editorMenu) {
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

        PeriodicTable periodicTable = new PeriodicTable();
        Optional<ElementButton> optElementButton = periodicTable.showAndWait();
        if (optElementButton == null || !optElementButton.isPresent()) {
            return;
        }

        ElementButton elementButton = optElementButton.get();
        String elementName = elementButton.getText();
        if (elementName == null || elementName.isEmpty()) {
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
                    atom.setName(elementName);
                }
            }
        }
    }
}
