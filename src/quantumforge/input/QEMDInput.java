/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.input;

import java.io.File;
import java.io.IOException;

import quantumforge.input.card.QEAtomicPositions;
import quantumforge.input.card.QEAtomicSpecies;
import quantumforge.input.card.QECellParameters;
import quantumforge.input.card.QEKPoints;
import quantumforge.input.correcter.QEInputCorrecter;
import quantumforge.input.correcter.QEMDInputCorrecter;
import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.tracer.QEMDTracer;

public class QEMDInput extends QESecondaryInput {

    public QEMDInput() {
        super();
    }

    public QEMDInput(String fileName) throws IOException {
        super(fileName);
    }

    public QEMDInput(File file) throws IOException {
        super(file);
    }

    @Override
    protected void setupNamelists(QEInputReader reader) throws IOException {
        boolean hasNmlControl = this.namelists.containsKey(NAMELIST_CONTROL);
        boolean hasNmlIons = this.namelists.containsKey(NAMELIST_IONS);

        this.setupNamelist(NAMELIST_CONTROL, reader);
        this.setupNamelist(NAMELIST_SYSTEM, reader);
        this.setupNamelist(NAMELIST_ELECTRONS, reader);
        this.setupNamelist(NAMELIST_IONS, reader);
        this.setupNamelist(NAMELIST_CELL, reader);

        if (!hasNmlControl) {
            QENamelist nmlControl = this.namelists.get(NAMELIST_CONTROL);
            nmlControl.addProtectedValue("restart_mode");
            nmlControl.addProtectedValue("max_seconds");
            nmlControl.addProtectedValue("calculation");
            nmlControl.addProtectedValue("nstep");
            nmlControl.addProtectedValue("dt");
            nmlControl.addProtectedValue("etot_conv_thr");
            nmlControl.addProtectedValue("forc_conv_thr");
        }

        if ((!hasNmlControl) && (!hasNmlIons)) {
            QENamelist nmlControl = this.namelists.get(NAMELIST_CONTROL);
            QENamelist nmlIons = this.namelists.get(NAMELIST_IONS);
            QEMDTracer mdTracer = new QEMDTracer(nmlControl, nmlIons);
            mdTracer.traceMd();
        }
    }

    @Override
    protected void setupCards(QEInputReader reader) throws IOException {
        this.setupCard(new QEKPoints(), reader);
        this.setupCard(new QECellParameters(), reader);
        this.setupCard(new QEAtomicSpecies(), reader);
        this.setupCard(new QEAtomicPositions(), reader);
    }

    @Override
    public QEMDInput copy() {
        QEMDInput input = new QEMDInput();
        QEInputCopier copier = new QEInputCopier(this);
        copier.copyTo(input, false);
        return input;
    }

    @Override
    protected QEInputCorrecter createInputCorrector() {
        return new QEMDInputCorrecter(this);
    }
}
