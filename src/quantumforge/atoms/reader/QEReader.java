/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.atoms.reader;

import java.io.File;
import java.io.IOException;

import quantumforge.atoms.model.Cell;
import quantumforge.atoms.model.exception.ZeroVolumCellException;
import quantumforge.input.QEGeometryInput;

public class QEReader extends AtomsReader {

    private QEGeometryInput input;

    public QEReader(String filePath) throws IOException {
        super();
        this.input = new QEGeometryInput(filePath);
    }

    public QEReader(File file) throws IOException {
        super();
        this.input = new QEGeometryInput(file);
    }

    public QEGeometryInput getInput() {
        return this.input;
    }

    @Override
    public Cell readCell() {
        Cell cell = null;
        if (this.input != null) {
            cell = this.input.getCell();
        }

        if (cell == null) {
            double[][] lattice = { { 1.0, 0.0, 0.0 }, { 0.0, 1.0, 0.0 }, { 0.0, 0.0, 1.0 } };
            try {
                cell = new Cell(lattice);
            } catch (ZeroVolumCellException e) {
                e.printStackTrace();
            }
        }

        return cell;
    }
}
