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
import quantumforge.input.card.tracer.QESpeciesTracer;
import quantumforge.input.correcter.QEInputCorrecter;
import quantumforge.input.correcter.QESCFInputCorrecter;
import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.tracer.QEHubbardTracer;
import quantumforge.input.namelist.tracer.QESpinTracer;

public class QESCFInput extends QESecondaryInput {

    private QESpeciesTracer speciesTracer;

    public QESCFInput() {
        super();
    }

    public QESCFInput(String fileName) throws IOException {
        super(fileName);
    }

    public QESCFInput(File file) throws IOException {
        super(file);
    }

    @Override
    protected void setupNamelists(QEInputReader reader) throws IOException {
        boolean hasNmlSystem = this.namelists.containsKey(NAMELIST_SYSTEM);

        this.setupNamelist(NAMELIST_CONTROL, reader);
        this.setupNamelist(NAMELIST_SYSTEM, reader);
        this.setupNamelist(NAMELIST_ELECTRONS, reader);

        if (!hasNmlSystem) {
            QENamelist nmlSystem = this.namelists.get(NAMELIST_SYSTEM);
            nmlSystem.addDeletingValue("celldm(1)");
            nmlSystem.addDeletingValue("celldm(2)");
            nmlSystem.addDeletingValue("celldm(3)");
            nmlSystem.addDeletingValue("celldm(4)");
            nmlSystem.addDeletingValue("celldm(5)");
            nmlSystem.addDeletingValue("celldm(6)");
            nmlSystem.addBindingValue("a");
            nmlSystem.addBindingValue("b");
            nmlSystem.addBindingValue("c");
            nmlSystem.addBindingValue("cosab");
            nmlSystem.addBindingValue("cosac");
            nmlSystem.addBindingValue("cosbc");
            nmlSystem.addBindingValue("nspin");
            nmlSystem.addBindingValue("noncolin");
            nmlSystem.addBindingValue("constrained_magnetization");
            nmlSystem.addBindingValue("tot_magnetization");
            nmlSystem.addBindingValue("fixed_magnetization(3)");

            QESpinTracer spinTracer = new QESpinTracer(nmlSystem);
            spinTracer.traceSpin();

            QEHubbardTracer hubbardTracer = new QEHubbardTracer(nmlSystem);
            hubbardTracer.traceHubbard();
        }
    }

    @Override
    protected void setupCards(QEInputReader reader) throws IOException {
        QECard card = this.cards.get(QEAtomicSpecies.CARD_NAME);
        boolean hasSpeciesCards = (card != null) && (card instanceof QEAtomicSpecies);

        if (hasSpeciesCards) {
            if (this.speciesTracer != null) {
                this.speciesTracer.stopTracer();
            }
        }

        this.setupCard(new QEKPoints(), reader);
        this.setupCard(new QECellParameters(), reader);
        this.setupCard(new QEAtomicSpecies(), reader);
        this.setupCard(new QEAtomicPositions(), reader);

        if (hasSpeciesCards) {
            if (this.speciesTracer != null) {
                this.speciesTracer.restartTracer();
            }

        } else {
            QENamelist nmlSystem = this.namelists.get(NAMELIST_SYSTEM);
            QEAtomicSpecies atomicSpecies = (QEAtomicSpecies) this.cards.get(QEAtomicSpecies.CARD_NAME);
            this.speciesTracer = new QESpeciesTracer(nmlSystem, atomicSpecies);
            this.speciesTracer.traceAtomicSpecies();
        }
    }

    @Override
    public QESCFInput copy() {
        QESCFInput input = new QESCFInput();
        QEInputCopier copier = new QEInputCopier(this);
        copier.copyTo(input, false);
        return input;
    }

    @Override
    protected QEInputCorrecter createInputCorrector() {
        return new QESCFInputCorrecter(this);
    }
}
