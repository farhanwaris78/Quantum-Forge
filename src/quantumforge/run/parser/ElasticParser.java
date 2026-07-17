/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.run.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import quantumforge.project.property.ProjectProperty;

/**
 * Parser for thermo_pw elastic constant output.
 */
public class ElasticParser extends LogParser {

    private double[][] cij; // 6x6 matrix
    private QEElasticStabilityValidator.StabilityResult stabilityResult;

    public ElasticParser(ProjectProperty property) {
        super(property);
        this.cij = new double[6][6];
        this.stabilityResult = null;
    }

    @Override
    public void parse(File file) throws IOException {
        if (file == null || !file.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Elastic Constant Matrix")) {
                    for (int i = 0; i < 6; i++) {
                        line = reader.readLine();
                        if (line == null) break;
                        String[] parts = line.trim().split("\\s+");
                        for (int j = 0; j < Math.min(parts.length, 6); j++) {
                            try {
                                cij[i][j] = Double.parseDouble(parts[j]);
                            } catch (NumberFormatException e) {}
                        }
                    }
                }
            }
        }

        // Validate mechanical stability of the parsed tensor
        this.stabilityResult = QEElasticStabilityValidator.validateStability(this.cij);
    }

    public double[][] getCij() {
        return cij;
    }

    public QEElasticStabilityValidator.StabilityResult getStabilityResult() {
        return this.stabilityResult;
    }
}
