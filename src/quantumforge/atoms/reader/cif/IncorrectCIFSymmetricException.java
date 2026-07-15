/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.atoms.reader.cif;

public class IncorrectCIFSymmetricException extends Exception {

    public IncorrectCIFSymmetricException() {
        super("symmetric operator is incorrect.");
    }

    public IncorrectCIFSymmetricException(String operator) {
        super("symmetric operator is incorrect: " + operator + ".");
    }

    public IncorrectCIFSymmetricException(Exception e) {
        super(e);
    }

}
