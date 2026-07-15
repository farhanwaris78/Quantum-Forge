/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.atoms.viewer.operation.key;

import javafx.scene.input.KeyCode;

public class KeyPressedAnsatz {

    private KeyCode keyCode;
    private boolean shortStatus;
    private boolean shiftStatus;
    private boolean altStatus;

    public KeyPressedAnsatz(KeyCode keyCode) {
        this(keyCode, false, false, false);
    }

    public KeyPressedAnsatz(KeyCode keyCode, boolean shortStatus, boolean shiftStatus, boolean altStatus) {
        this.keyCode = keyCode;
        this.shortStatus = shortStatus;
        this.shiftStatus = shiftStatus;
        this.altStatus = altStatus;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (this.altStatus ? 1231 : 1237);
        result = prime * result + (this.shortStatus ? 1231 : 1237);
        result = prime * result + ((this.keyCode == null) ? 0 : this.keyCode.hashCode());
        result = prime * result + (this.shiftStatus ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (this.getClass() != obj.getClass()) {
            return false;
        }

        KeyPressedAnsatz other = (KeyPressedAnsatz) obj;
        if (this.altStatus != other.altStatus) {
            return false;
        }
        if (this.shortStatus != other.shortStatus) {
            return false;
        }
        if (this.keyCode != other.keyCode) {
            return false;
        }
        if (this.shiftStatus != other.shiftStatus) {
            return false;
        }

        return true;
    }
}
