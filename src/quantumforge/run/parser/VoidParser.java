/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.run.parser;

import java.io.File;
import java.io.IOException;

import quantumforge.project.property.ProjectProperty;

public class VoidParser extends LogParser {

    public VoidParser(ProjectProperty property) {
        super(property);
    }

    @Override
    public void parse(File file) throws IOException {
        // NOP
    }

    @Override
    public void startParsing(File file) {
        // NOP
    }

    @Override
    public void endParsing() {
        // NOP
    }
}
