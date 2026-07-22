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

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.viewer.AtomsViewer;
import quantumforge.atoms.visible.VisibleAtom;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

public class MeasureMenuItem extends EditorMenuItem {

    public MeasureMenuItem(EditorMenu menu) {
        super("Measure distance", menu);
    }

    @Override
    protected void editAtoms() {
        AtomsViewer atomsViewer = this.editorMenu.getManager().getAtomsViewer();
        if (atomsViewer == null) {
            return;
        }

        List<VisibleAtom> visibleAtoms = atomsViewer.getVisibleAtoms();
        if (visibleAtoms == null) {
            return;
        }

        List<Atom> selectedAtoms = new ArrayList<>();
        for (VisibleAtom visibleAtom : visibleAtoms) {
            if (visibleAtom.isSelected()) {
                selectedAtoms.add(visibleAtom.getModel());
            }
        }

        if (selectedAtoms.size() != 2) {
            Alert alert = new Alert(AlertType.WARNING);
            alert.setTitle("Measurement");
            alert.setHeaderText("Invalid selection");
            alert.setContentText("Please select exactly two atoms to measure the distance.");
            alert.showAndWait();
            return;
        }

        Atom atom1 = selectedAtoms.get(0);
        Atom atom2 = selectedAtoms.get(1);

        double dx = atom1.getX() - atom2.getX();
        double dy = atom1.getY() - atom2.getY();
        double dz = atom1.getZ() - atom2.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Measurement");
        alert.setHeaderText("Distance between atoms");
        alert.setContentText(String.format("Distance between %s and %s: %.6f \u00c5", 
            atom1.getName(), atom2.getName(), distance));
        alert.showAndWait();
    }
}
