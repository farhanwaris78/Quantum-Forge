/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.project;

import java.io.File;
import java.io.IOException;

import quantumforge.input.QEInput;

@FunctionalInterface
public interface InputGenerator {

    public abstract QEInput generate(File file) throws IOException;

}
