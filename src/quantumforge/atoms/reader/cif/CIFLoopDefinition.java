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

import java.util.ArrayList;
import java.util.List;

public class CIFLoopDefinition {

    private List<String> names;

    public CIFLoopDefinition() {
        this.names = new ArrayList<String>();
    }

    public int numNames() {
        return this.names.size();
    }

    public boolean hasName(String name) {
        return this.names.contains(name);
    }

    public boolean addName(String name) {
        return this.names.add(name);
    }

    public boolean isEmpty() {
        return this.names.isEmpty();
    }

    public void clear() {
        this.names.clear();
    }

    public int indexOf(String name) {
        return this.names.indexOf(name);
    }
}
