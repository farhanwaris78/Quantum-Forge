/*
 * Copyright (C) 2025 QuantumForge Team
 */
package quantumforge.neural;

/**
 * Graph Neural Network (GNN) Force Field interface.
 * 
 * NanoLabo supports multiple GNN force fields for MD simulations:
 * - M3GNet (MatGL) - Universal GNN potential
 * - CHGNet - Crystal Hamiltonian Graph Neural Network
 * - MACE - Multi-Atomic Cluster Expansion
 * - SevenNet - Multi-GPU compatible GNN
 * - ORB - Orbital-based GNN
 * - MatterSim - Microsoft MatterSim
 * - FAIR-Chem (eqV2) - Facebook AI Research
 * - Open Catalyst Project (legacy)
 * 
 * These enable:
 * - Universal force field MD without DFT training
 * - Structural optimization at GNN level
 * - Property prediction
 * - Fine-tuning for specific systems
 */
public class GNNFroceField {

    public static final String GNN_M3GNET = "M3GNet";
    public static final String GNN_CHGNET = "CHGNet";
    public static final String GNN_MACE = "MACE";
    public static final String GNN_SEVENNET = "SevenNet";
    public static final String GNN_ORB = "ORB";
    public static final String GNN_MATTERSIM = "MatterSim";
    public static final String GNN_FAIRCHEM = "FAIR-Chem";
    public static final String GNN_OCP = "OpenCatalyst";

    public static class GNNField {
        public final String name;
        public final String pythonPackage;
        public final String modelFile;
        public boolean gpuEnabled;
        public int numGPUs;
        public String[] supportedElements;

        public GNNField(String name, String pkg, String model) {
            this.name = name;
            this.pythonPackage = pkg;
            this.modelFile = model;
            this.gpuEnabled = false;
            this.numGPUs = 0;
        }
    }

    private GNNField[] availableFields;

    public GNNFroceField() {
        this.availableFields = new GNNField[]{
            new GNNField(GNN_M3GNET, "matgl", "M3GNet-MP-2021.2.8-PES"),
            new GNNField(GNN_CHGNET, "chgnet", "chgnet-0.3.0"),
            new GNNField(GNN_MACE, "mace", "MACE-MP-0b"),
            new GNNField(GNN_SEVENNET, "sevennet", "SevenNet-0"),
            new GNNField(GNN_ORB, "orb", "orb-v2"),
            new GNNField(GNN_MATTERSIM, "mattersim", "MatterSim-v1.0.0"),
            new GNNField(GNN_FAIRCHEM, "fairchem", "eqV2"),
            new GNNField(GNN_OCP, "ocpmodels", "OC20")
        };
    }

    public GNNField[] getAvailableFields() { return this.availableFields; }

    public GNNField getField(String name) {
        for (GNNField f : this.availableFields) {
            if (f.name.equals(name)) return f;
        }
        return null;
    }

    /**
     * Check if the Python environment has the required package
     */
    public boolean isFieldAvailable(String name) {
        GNNField field = this.getField(name);
        if (field == null) return false;

        try {
            ProcessBuilder pb = new ProcessBuilder("python3", "-c",
                "import " + field.pythonPackage + "; print('ok')");
            Process p = pb.start();
            int exitCode = p.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
