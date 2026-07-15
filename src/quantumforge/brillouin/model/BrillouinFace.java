/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.brillouin.model;

import java.util.ArrayList;
import java.util.List;

public class BrillouinFace {

    private List<double[]> vertices;

    public BrillouinFace() {
        this.vertices = new ArrayList<double[]>();
    }

    public void addVertex(double x, double y, double z) {
        this.vertices.add(new double[] { x, y, z });
    }

}
