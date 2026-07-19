/*
 * Copyright (C) 2025 QuantumForge Team
 */
package quantumforge.neural;

/**
 * Registry of machine-learning potential (MLP) backends known to QuantumForge
 * (Roadmap #136: renamed from the misspelled GNNFroceField).
 *
 * <p>This class is a descriptor registry only: it does not execute Python, run
 * inference, or claim availability of any backend. Whether a descriptor can run
 * must be proven by the model-manifest validation ({@code MlModelManifest},
 * Roadmap #139) and the backend conformance gates (Roadmap #138), not by a name
 * appearing in this list.</p>
 */
public class MLPotentialService {

    public static final String GNN_M3GNET = "M3GNet";
    public static final String GNN_CHGNET = "CHGNet";
    public static final String GNN_MACE = "MACE";
    public static final String GNN_SEVENNET = "SevenNet";
    public static final String GNN_ORB = "ORB";
    public static final String GNN_MATTERSIM = "MatterSim";
    public static final String GNN_FAIRCHEM = "FAIR-Chem";
    public static final String GNN_OCP = "OpenCatalyst";

    public static class MLPotentialDescriptor {
        public final String name;
        public final String pythonPackage;
        public final String modelFile;
        public boolean gpuEnabled;
        public int numGPUs;
        public String[] supportedElements;

        public MLPotentialDescriptor(String name, String pkg, String model) {
            this.name = name;
            this.pythonPackage = pkg;
            this.modelFile = model;
            this.gpuEnabled = false;
            this.numGPUs = 0;
        }
    }

    private MLPotentialDescriptor[] availableFields;

    public MLPotentialService() {
        this.availableFields = new MLPotentialDescriptor[]{
            new MLPotentialDescriptor(GNN_M3GNET, "matgl", "M3GNet-MP-2021.2.8-PES"),
            new MLPotentialDescriptor(GNN_CHGNET, "chgnet", "chgnet-0.3.0"),
            new MLPotentialDescriptor(GNN_MACE, "mace", "MACE-MP-0b"),
            new MLPotentialDescriptor(GNN_SEVENNET, "sevennet", "SevenNet-0"),
            new MLPotentialDescriptor(GNN_ORB, "orb", "orb-v2"),
            new MLPotentialDescriptor(GNN_MATTERSIM, "mattersim", "MatterSim-v1.0.0"),
            new MLPotentialDescriptor(GNN_FAIRCHEM, "fairchem", "eqV2"),
            new MLPotentialDescriptor(GNN_OCP, "ocpmodels", "OC20")
        };
    }

    public MLPotentialDescriptor[] getAvailableFields() { return this.availableFields; }

    public MLPotentialDescriptor getField(String name) {
        for (MLPotentialDescriptor f : this.availableFields) {
            if (f.name.equals(name)) return f;
        }
        return null;
    }

    /**
     * Check if the Python environment has the required package
     */
    public boolean isFieldAvailable(String name) {
        MLPotentialDescriptor field = this.getField(name);
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
