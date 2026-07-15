/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.export;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import quantumforge.project.property.ProjectGeometry;
import quantumforge.project.property.ProjectGeometryList;

/**
 * Exporter for Machine Learning Potential (MLP) training data.
 * Supports DeepMD-kit and basic ExtXYZ format.
 */
public class MLPTrainingExporter {

    public static void exportToExtXYZ(ProjectGeometryList geomList, String filePath) throws IOException {
        if (geomList == null || filePath == null) return;

        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(filePath)))) {
            for (int i = 0; i < geomList.numGeometries(); i++) {
                ProjectGeometry geom = geomList.getGeometry(i);
                int nAtoms = geom.numAtoms();
                writer.println(nAtoms);
                
                double[][] cell = geom.getCell();
                writer.printf("Lattice=\"%.6f %.6f %.6f %.6f %.6f %.6f %.6f %.6f %.6f\" ",
                    cell[0][0], cell[0][1], cell[0][2],
                    cell[1][0], cell[1][1], cell[1][2],
                    cell[2][0], cell[2][1], cell[2][2]);
                
                writer.printf("Properties=species:S:1:pos:R:3:forces:R:3 energy=%.10f\n", geom.getEnergy());

                for (int j = 0; j < nAtoms; j++) {
                    writer.printf("%s %.8f %.8f %.8f %.8f %.8f %.8f\n",
                        geom.getName(j),
                        geom.getX(j), geom.getY(j), geom.getZ(j),
                        geom.getForceX(j), geom.getForceY(j), geom.getForceZ(j));
                }
            }
        }
    }
}
