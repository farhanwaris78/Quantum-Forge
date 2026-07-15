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

import quantumforge.com.str.SmartSplitter;

public class CIFSingleValue {

    private String name;

    private String value;

    public CIFSingleValue() {
        this.name = null;
        this.value = null;
    }

    public boolean isName(String name) {
        if (this.name == null || this.name.isEmpty()) {
            return false;
        }

        return this.name.equalsIgnoreCase(name);
    }

    public boolean hasValue() {
        if (this.name == null || this.name.isEmpty()) {
            return false;
        }

        if (this.value == null || this.value.isEmpty()) {
            return false;
        }

        return true;
    }

    public String getValue() {
        return this.value;
    }

    public boolean read(String line) {
        if (line == null || line.isEmpty()) {
            return false;
        }

        //String[] subLines = line.trim().split("\\s+");
        String[] subLines = SmartSplitter.split(line.trim());
        if (subLines == null || subLines.length < 2) {
            return false;
        }

        if (!subLines[0].startsWith("_")) {
            return false;
        }

        this.name = subLines[0];

        this.value = subLines[1];

        return true;
    }
}
