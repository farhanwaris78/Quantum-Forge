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

public class IncorrectAtomNameException extends Exception {

    public IncorrectAtomNameException() {
        super("atomic name is incorrect.");
    }

    public IncorrectAtomNameException(String name) {
        super("atomic name is incorrect: " + name + ".");
    }

    public IncorrectAtomNameException(Exception e) {
        super(e);
    }

}
