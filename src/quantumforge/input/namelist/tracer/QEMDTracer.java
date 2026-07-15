/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.input.namelist.tracer;

import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValue;
import quantumforge.input.namelist.QEValueBuffer;

public class QEMDTracer {

    private boolean busy;

    private QENamelist nmlControl;

    private QENamelist nmlIons;

    public QEMDTracer(QENamelist nmlControl, QENamelist nmlIons) {
        if (nmlControl == null) {
            throw new IllegalArgumentException("nmlControl is null.");
        }

        if (nmlIons == null) {
            throw new IllegalArgumentException("nmlIons is null.");
        }

        this.busy = false;
        this.nmlControl = nmlControl;
        this.nmlIons = nmlIons;
    }

    public void traceMd() {
        this.setupCalculation();
    }

    private void setupCalculation() {
        QEValueBuffer valueBuffer = this.nmlControl.getValueBuffer("calculation");

        if (valueBuffer.hasValue()) {
            this.updateCalculation(valueBuffer.getValue());
        } else {
            this.updateCalculation(null);
        }

        valueBuffer.addListener(value -> {
            this.updateCalculation(value);
        });
    }

    private void updateCalculation(QEValue value) {
        if (this.busy) {
            return;
        }

        this.busy = true;

        if (value != null) {
            if ("md".equals(value.getCharacterValue())) {
                QEValue ionValue = this.nmlIons.getValue("ion_dynamics");
                String ion = ionValue == null ? null : ionValue.getCharacterValue();
                if (ion == null || (!ion.startsWith("langevin"))) {
                    this.nmlIons.setValue("ion_dynamics = verlet");
                }

            } else if ("vc-md".equals(value.getCharacterValue())) {
                this.nmlIons.setValue("ion_dynamics = beeman");
            }
        }

        this.busy = false;
    }
}
