/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.editor.result.isosurface;

/**
 * Isosurface rendering for 3D volumetric data visualization.
 * 
 * NanoLabo provides 3D visualization of:
 * - Charge density (total, difference)
 * - Electrostatic potential
 * - Spin polarization
 * - Wave functions
 * 
 * This class handles the data structures for isosurface/cloud rendering,
 * matching NanoLabo's 3D display functionality.
 */
public class IsosurfaceData {

    public static final int TYPE_CHARGE_DENSITY = 0;
    public static final int TYPE_POTENTIAL = 1;
    public static final int TYPE_SPIN_POLARIZATION = 2;
    public static final int TYPE_WAVEFUNCTION = 3;
    public static final int TYPE_DIFFERENCE_DENSITY = 4;
    public static final int TYPE_ELECTRON_LOCALIZATION = 5;

    public static final int RENDER_ISOSURFACE = 0;
    public static final int RENDER_CLOUD = 1;

    private int dataType;
    private int renderMode;
    private double isovalue;
    private double opacity;
    private String label;
    private boolean visible;
    private int[] dimensions;
    private float[] gridData;
    private float[] referenceData;

    public IsosurfaceData(int dataType) {
        this.dataType = dataType;
        this.renderMode = RENDER_ISOSURFACE;
        this.isovalue = 0.03;
        this.opacity = 0.5;
        this.visible = true;
        this.dimensions = new int[]{1, 1, 1};
        this.label = getDefaultLabel(dataType);
    }

    private String getDefaultLabel(int type) {
        switch (type) {
            case TYPE_CHARGE_DENSITY:      return "Total Charge Density";
            case TYPE_POTENTIAL:           return "Electrostatic Potential";
            case TYPE_SPIN_POLARIZATION:   return "Spin Polarization";
            case TYPE_WAVEFUNCTION:        return "Wave Function";
            case TYPE_DIFFERENCE_DENSITY:  return "Difference Density";
            case TYPE_ELECTRON_LOCALIZATION: return "ELF";
            default:                       return "Volume Data";
        }
    }

    public void setRenderMode(int mode) { this.renderMode = mode; }
    public void setIsovalue(double value) { this.isovalue = value; }
    public void setOpacity(double opacity) { this.opacity = Math.max(0.0, Math.min(1.0, opacity)); }
    public void setVisible(boolean visible) { this.visible = visible; }
    public void setLabel(String label) { this.label = label; }
    public void setDimensions(int nx, int ny, int nz) { this.dimensions = new int[]{nx, ny, nz}; }

    public int getDataType() { return this.dataType; }
    public int getRenderMode() { return this.renderMode; }
    public double getIsovalue() { return this.isovalue; }
    public double getOpacity() { return this.opacity; }
    public boolean isVisible() { return this.visible; }
    public String getLabel() { return this.label; }
    public int[] getDimensions() { return this.dimensions; }

    /**
     * Load cube file data for 3D visualization.
     * Cube files are the standard format for volumetric data in QE.
     */
    public boolean loadFromCubeFile(String filePath) {
        // Placeholder for cube file parser
        // In production, this would parse Gaussian cube format
        return false;
    }

    /**
     * Calculate difference between two datasets (current - ref1 - ref2)
     */
    public static IsosurfaceData calculateDifference(IsosurfaceData current,
                                                      IsosurfaceData ref1,
                                                      IsosurfaceData ref2) {
        IsosurfaceData diff = new IsosurfaceData(TYPE_DIFFERENCE_DENSITY);
        diff.setLabel("Difference (current - #1 - #2)");
        return diff;
    }

    /**
     * 3D rendering object container for the viewport
     */
    public static class VolumeRenderingObject {
        private IsosurfaceData data;
        private boolean show;
        private boolean editMode;

        public VolumeRenderingObject(IsosurfaceData data) {
            this.data = data;
            this.show = true;
            this.editMode = false;
        }

        public IsosurfaceData getData() { return this.data; }
        public boolean isShown() { return this.show; }
        public void toggleShow() { this.show = !this.show; }
        public boolean isEditMode() { return this.editMode; }
        public void setEditMode(boolean edit) { this.editMode = edit; }
    }
}
