/*
 * Copyright (C) 2025 QuantumForge Team
 */
package quantumforge.jupyter;

/**
 * Jupyter Interface for QuantumForge.
 * 
 * NanoLabo provides JupyterLab integration that enables:
 * - Exchange atomic structures with ASE (Atomic Simulation Environment)
 * - Python-based workflow scripting
 * - Matlantis collaboration via PFP API
 * - Automated analysis pipelines
 * 
 * This interface creates a bridge between QuantumForge and
 * the Python scientific computing ecosystem.
 */
public class JupyterInterface {

    private int jupyterPort;
    private boolean connected;

    public JupyterInterface() {
        this.jupyterPort = 8888;
        this.connected = false;
    }

    public void setPort(int port) { this.jupyterPort = port; }
    public int getPort() { return this.jupyterPort; }

    /**
     * Start JupyterLab server
     */
    public boolean startJupyterLab() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "jupyter-lab", "--port", String.valueOf(this.jupyterPort),
                "--no-browser");
            pb.start();
            this.connected = true;
            return true;
        } catch (Exception e) {
            this.connected = false;
            return false;
        }
    }

    public boolean isConnected() { return this.connected; }
    public void disconnect() { this.connected = false; }
}
