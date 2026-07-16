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

import quantumforge.input.card.QEAtomicSpecies;
import quantumforge.input.card.QECard;
import quantumforge.input.correcter.QEInputCorrecter;

public abstract class QESecondaryInput extends QEInput {

    private QEInput parentInput;

    protected QESecondaryInput() {
        super();
        this.parentInput = null;
    }

    protected QESecondaryInput(String fileName) throws IOException {
        super(fileName);
        this.parentInput = null;
    }

    protected QESecondaryInput(File file) throws IOException {
        super(file);
        this.parentInput = null;
    }

    public void setParentInput(QEInput parentInput) {
        this.parentInput = parentInput;
        this.reload();
        this.bindToParentAtomicSpecies();
    }

    @Override
    public void reload() {
        if (this.parentInput == null) {
            return;
        }

        QEInputCopier copier = new QEInputCopier(this.parentInput);
        copier.copyTo(this);

        QEInputCorrecter inputCorrecter = this.getInputCorrector();
        if (inputCorrecter != null) {
            inputCorrecter.correctInput();
        }
    }

    private void bindToParentAtomicSpecies() {
        if (this.parentInput == null) {
            return;
        }

        QECard srcCard = this.parentInput.getCard(QEAtomicSpecies.CARD_NAME);
        if (srcCard == null || !(srcCard instanceof QEAtomicSpecies)) {
            return;
        }

        QECard dstCard = this.getCard(QEAtomicSpecies.CARD_NAME);
        if (dstCard == null || !(dstCard instanceof QEAtomicSpecies)) {
            return;
        }

        srcCard.addListener(event -> {
            srcCard.copyTo(dstCard);
        });
    }
}
