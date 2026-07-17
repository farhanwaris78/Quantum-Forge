/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Tracks the complete, immutable historical audit chain of atomic operations 
 * (database load, supercell expansion, slab cuts, defect insertion, adsorption) 
 * applied to a structure, enabling exact mathematical reconstruction (Roadmap #90).
 */
public final class QEStructureProvenanceGraph {

    public static final class ProvenanceNode {
        private final String operation; // LOAD, SUPERCELL, SLAB, DEFECT, ADSORPTION, MOIRE
        private final String description;
        private final double[][] matrix; // Optional 3x3 transformation matrix
        private final String sourceInfo; // Filename, database ID (e.g., MP-149)
        private final long timestamp;

        public ProvenanceNode(String operation, String description, double[][] matrix, String sourceInfo) {
            this.operation = Objects.requireNonNull(operation, "operation").toUpperCase();
            this.description = description == null ? "" : description.trim();
            this.matrix = matrix != null ? copy3(matrix) : null;
            this.sourceInfo = sourceInfo == null ? "" : sourceInfo.trim();
            this.timestamp = System.currentTimeMillis();
        }

        public String getOperation() { return this.operation; }
        public String getDescription() { return this.description; }
        public double[][] getMatrix() { return this.matrix != null ? copy3(this.matrix) : null; }
        public String getSourceInfo() { return this.sourceInfo; }
        public long getTimestamp() { return this.timestamp; }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("[%s] %s", operation, description));
            if (!sourceInfo.isEmpty()) {
                sb.append(" | Source: ").append(sourceInfo);
            }
            return sb.toString();
        }

        private static double[][] copy3(double[][] src) {
            double[][] out = new double[3][3];
            for (int i = 0; i < 3; i++) {
                System.arraycopy(src[i], 0, out[i], 0, 3);
            }
            return out;
        }
    }

    private final List<ProvenanceNode> nodes = new ArrayList<>();

    public QEStructureProvenanceGraph() {
        // Empty graph
    }

    public void addNode(String operation, String description, double[][] matrix, String sourceInfo) {
        this.nodes.add(new ProvenanceNode(operation, description, matrix, sourceInfo));
    }

    public List<ProvenanceNode> getNodes() { return List.copyOf(this.nodes); }
    public int size() { return this.nodes.size(); }

    /**
     * Generates a beautifully formatted, structured Markdown audit report
     * representing the complete reconstruction provenance.
     */
    public String generateHistoryReport() {
        if (this.nodes.isEmpty()) {
            return "Structure Provenance: No history has been recorded yet.";
        }

        StringBuilder sb = new StringBuilder("Crystalline Structure Provenance Audit Trail\n");
        sb.append("============================================\n");
        for (int i = 0; i < this.nodes.size(); i++) {
            ProvenanceNode node = this.nodes.get(i);
            sb.append(String.format("Step %d: [%s]\n", i + 1, node.getOperation()));
            sb.append("  - Action: ").append(node.getDescription()).append("\n");
            if (!node.getSourceInfo().isEmpty()) {
                sb.append("  - Source: ").append(node.getSourceInfo()).append("\n");
            }
            if (node.getMatrix() != null) {
                sb.append("  - Transformation Matrix:\n");
                double[][] m = node.getMatrix();
                sb.append(String.format("      [ %7.4f  %7.4f  %7.4f ]\n", m[0][0], m[0][1], m[0][2]));
                sb.append(String.format("      [ %7.4f  %7.4f  %7.4f ]\n", m[1][0], m[1][1], m[1][2]));
                sb.append(String.format("      [ %7.4f  %7.4f  %7.4f ]\n", m[2][0], m[2][1], m[2][2]));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Serializes the entire provenance history graph to JSON.
     */
    public String toJson() {
        return new GsonBuilder().setPrettyPrinting().create().toJson(this);
    }

    /**
     * Deserializes a provenance history graph from JSON.
     */
    public static QEStructureProvenanceGraph fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new QEStructureProvenanceGraph();
        }
        return new Gson().fromJson(json, QEStructureProvenanceGraph.class);
    }
}
