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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.input.KeyCode;
import quantumforge.app.QEFXMain;
import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.atoms.viewer.AtomsViewer;
import quantumforge.atoms.viewer.operation.ViewerEventManager;
import quantumforge.atoms.visible.VisibleAtom;
import quantumforge.com.keys.KeyNames;

public class DeleteMenuItem extends EditorMenuItem {

    private static final String ITEM_LABEL = "Delete selected atoms [" + KeyNames.getShortcut(KeyCode.D) + "]";

    public DeleteMenuItem(ViewerEventManager manager) {
        super(ITEM_LABEL, manager);
    }

    public DeleteMenuItem(EditorMenu editorMenu) {
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

        Alert alert = new Alert(AlertType.CONFIRMATION);
        QEFXMain.initializeDialogOwner(alert);
        if (numSelected > 1) {
            alert.setHeaderText("Selected atoms will be deleted.");
        } else {
            alert.setHeaderText("Selected atom will be deleted.");
        }
        Optional<ButtonType> optButtonType = alert.showAndWait();
        if (optButtonType == null || !optButtonType.isPresent()) {
            return;
        }
        if (optButtonType.get() != ButtonType.OK) {
            return;
        }

        Cell cell = atomsViewer.getCell();
        if (cell == null) {
            return;
        }

        atomsViewer.storeCell();

        List<Atom> atomsToRemove = new ArrayList<Atom>();
        for (VisibleAtom visibleAtom : visibleAtoms) {
            if (visibleAtom == null) {
                continue;
            }

            if (visibleAtom.isSelected()) {
                Atom atom = visibleAtom.getModel();
                if (atom != null) {
                    atom = atom.getMasterAtom();
                }
                if (atom != null) {
                    atomsToRemove.add(atom);
                }
            }
        }

        for (Atom atom : atomsToRemove) {
            cell.removeAtom(atom);
        }
    }
}
