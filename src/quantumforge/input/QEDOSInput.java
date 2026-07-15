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
import quantumforge.input.card.QECard;
import quantumforge.input.card.QECellParameters;
import quantumforge.input.card.QEKPoints;
import quantumforge.input.correcter.QEDOSInputCorrecter;
import quantumforge.input.correcter.QEInputCorrecter;
import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.tracer.QEDOSTracer;

public class QEDOSInput extends QESecondaryInput {

    public QEDOSInput() {
        super();
    }

    public QEDOSInput(String fileName) throws IOException {
        super(fileName);
    }

    public QEDOSInput(File file) throws IOException {
        super(file);
    }

    @Override
    protected void setupNamelists(QEInputReader reader) throws IOException {
        boolean hasNmlControl = this.namelists.containsKey(NAMELIST_CONTROL);
        boolean hasNmlSystem = this.namelists.containsKey(NAMELIST_SYSTEM);
        boolean hasNmlDos = this.namelists.containsKey(NAMELIST_DOS);
        boolean hasNmlProjwfc = this.namelists.containsKey(NAMELIST_PROJWFC);

        this.setupNamelist(NAMELIST_CONTROL, reader);
        this.setupNamelist(NAMELIST_SYSTEM, reader);
        this.setupNamelist(NAMELIST_ELECTRONS, reader);
        this.setupNamelist(NAMELIST_DOS, reader);
        this.setupNamelist(NAMELIST_PROJWFC, reader);

        if (!hasNmlControl) {
            QENamelist nmlControl = this.namelists.get(NAMELIST_CONTROL);
            nmlControl.addProtectedValue("restart_mode");
            nmlControl.addProtectedValue("calculation");
        }

        if (!hasNmlSystem) {
            QENamelist nmlSystem = this.namelists.get(NAMELIST_SYSTEM);
            nmlSystem.addProtectedValue("nbnd");
            nmlSystem.addProtectedValue("occupations");
            nmlSystem.addProtectedValue("smearing");
            nmlSystem.addProtectedValue("degauss");
        }

        if ((!hasNmlDos) && (!hasNmlProjwfc)) {
            QENamelist nmlDos = this.namelists.get(NAMELIST_DOS);
            QENamelist nmlProjwfc = this.namelists.get(NAMELIST_PROJWFC);
            QEDOSTracer dosTracer = new QEDOSTracer(nmlDos, nmlProjwfc);
            dosTracer.traceDos();
        }
    }

    @Override
    protected void setupCards(QEInputReader reader) throws IOException {
        this.setupCard(new QEKPoints(), reader);
        this.setupCard(new QECellParameters(), reader);
        this.setupCard(new QEAtomicSpecies(), reader);
        this.setupCard(new QEAtomicPositions(), reader);

        QECard cardKPoints = this.cards.get(QEKPoints.CARD_NAME);
        cardKPoints.setProtectedToCopy(true);
    }

    @Override
    public QEDOSInput copy() {
        QEDOSInput input = new QEDOSInput();
        QEInputCopier copier = new QEInputCopier(this);
        copier.copyTo(input, false);
        return input;
    }

    @Override
    protected QEInputCorrecter createInputCorrector() {
        return new QEDOSInputCorrecter(this);
    }
}
