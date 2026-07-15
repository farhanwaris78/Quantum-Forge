/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.viewer.modeler.slabmodel;

public class MillerIndexException extends Exception {

    public MillerIndexException() {
        super("incorrect miller index.");
    }

    public MillerIndexException(String message) {
        super(message);
    }

    public MillerIndexException(Exception e) {
        super(e);
    }

}
