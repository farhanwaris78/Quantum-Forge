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

import quantumforge.input.card.QECard;
import quantumforge.input.namelist.QENamelist;

public class QEInputCopier {

    private QEInput srcInput;

    public QEInputCopier(QEInput srcInput) {
        if (srcInput == null) {
            throw new IllegalArgumentException("srcInput is null.");
        }

        this.srcInput = srcInput;
    }

    public void copyTo(QEInput dstInput) {
        this.copyTo(dstInput, true);
    }

    public void copyTo(QEInput dstInput, boolean protect) {
        if (dstInput == null) {
            throw new IllegalArgumentException("dstInput is null.");
        }

        String[] keyNamelists = QEInput.listNamelistKeys();
        for (String keyNamelist : keyNamelists) {
            QENamelist srcNamelist = this.srcInput.getNamelist(keyNamelist);
            QENamelist dstNamelist = dstInput.getNamelist(keyNamelist);
            if (srcNamelist != null && dstNamelist != null) {
                srcNamelist.copyTo(dstNamelist, protect);
            }
        }

        String[] keyCards = QEInput.listCardKeys();
        for (String keyCard : keyCards) {
            QECard srcCard = this.srcInput.getCard(keyCard);
            QECard dstCard = dstInput.getCard(keyCard);
            if (srcCard != null && dstCard != null) {
                srcCard.copyTo(dstCard, protect);
            }
        }
    }
}
