/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.project.viewer.result.special;

/**
 * Local Work Function (WF) Mapping tool.
 * WF(x,y) = V_vacuum - E_fermi - V_planar_avg(x,y)
 */
public class WorkFunctionMapper {

    private double feremiEnergy;
    private double vacuumLevel;

    public WorkFunctionMapper(double fermi, double vacuum) {
        this.feremiEnergy = fermi;
        this.vacuumLevel = vacuum;
    }

    public double getWorkFunction() {
        return vacuumLevel - feremiEnergy;
    }

    /**
     * Map work function over a 2D surface (e.g. for heterogeneous surfaces)
     */
    public double[][] generateMap(int nx, int ny) {
        double[][] map = new double[nx][ny];
        double baseWF = getWorkFunction();
        for(int i=0; i<nx; i++) {
            for(int j=0; j<ny; j++) {
                map[i][j] = baseWF + (Math.random() - 0.5) * 0.1; // Placeholder for local variations
            }
        }
        return map;
    }
}
