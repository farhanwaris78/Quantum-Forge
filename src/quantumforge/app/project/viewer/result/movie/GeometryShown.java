/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.viewer.result.movie;

import quantumforge.project.property.ProjectGeometry;

@FunctionalInterface
public interface GeometryShown {

    public abstract void onGeometryShown(int index, int size, ProjectGeometry geometry);

}
