/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.atoms.model.exception;

public class ZeroVolumCellException extends Exception {

    public ZeroVolumCellException() {
        super("volume of cell is zero.");
    }

    public ZeroVolumCellException(String message) {
        super(message);
    }

    public ZeroVolumCellException(Exception e) {
        super(e);
    }

}
