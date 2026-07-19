/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.viewer;

import quantumforge.com.graphic.svg.SVGLibrary.SVGData;

public class ViewerItemSet {

    private ViewerItem atomsViewerItem;
    private ViewerItem inputFileItem;
    private ViewerItem modelerItem;
    private ViewerItem saveFileItem;
    private ViewerItem saveAsFileItem;
    private ViewerItem designerItem;
    private ViewerItem screenShotItem;
    private ViewerItem runItem;
    private ViewerItem resultItem;
    private ViewerItem exportItem;
    private ViewerItem recoverItem;
    private ViewerItem xcrysdenItem;
    private ViewerItem exportWorkflowItem;
    private ViewerItem validateInputItem;
    private ViewerItem diagnoseLogItem;
    private ViewerItem bandGapItem;
    private ViewerItem finalGeometryItem;
    private ViewerItem pdosItem;
    private ViewerItem phononItem;
    private ViewerItem spectraItem;

    public ViewerItemSet() {
        this.atomsViewerItem = new ViewerItem(SVGData.ATOMS, "Show atoms");
        this.inputFileItem = new ViewerItem(SVGData.INPUTFILE, "Input-file");
        this.modelerItem = new ViewerItem(SVGData.TOOL, "Modeler");
        this.saveFileItem = new ViewerItem(SVGData.SAVE, "Save");
        this.saveAsFileItem = new ViewerItem(SVGData.SAVE, "Save as ...");
        this.recoverItem = new ViewerItem(SVGData.UNDO, "Recover autosave ...");
        this.designerItem = new ViewerItem(SVGData.COLORS, "Designer");
        this.screenShotItem = new ViewerItem(SVGData.CAMERA, "Screen-shot");
        this.runItem = new ViewerItem(SVGData.RUN, "Run");
        this.resultItem = new ViewerItem(SVGData.RESULT, "Result");
        this.exportItem = new ViewerItem(SVGData.EXPORT, "Export structure");
        this.exportWorkflowItem = new ViewerItem(SVGData.FILE, "Export workflow script");
        this.validateInputItem = new ViewerItem(SVGData.TOOL, "Validate QE input");
        this.diagnoseLogItem = new ViewerItem(SVGData.RESULT, "Diagnose QE log");
        this.bandGapItem = new ViewerItem(SVGData.RESULT, "Analyze band gap from QE log");
        this.finalGeometryItem = new ViewerItem(SVGData.ATOMS, "Preview final geometry");
        this.pdosItem = new ViewerItem(SVGData.RESULT, "Inspect projected DOS");
        this.phononItem = new ViewerItem(SVGData.RESULT, "Inspect phonon frequencies");
        this.spectraItem = new ViewerItem(SVGData.RESULT, "Inspect Raman / IR modes");
        this.xcrysdenItem = new ViewerItem(SVGData.CRYSTAL, "Open in XCrySDen");
    }

    public ViewerItem[] getItems() {
        return new ViewerItem[] {
                this.atomsViewerItem,
                this.inputFileItem,
                this.modelerItem,
                this.saveFileItem,
                this.saveAsFileItem,
                this.recoverItem,
                this.designerItem,
                this.screenShotItem,
                this.runItem,
                this.resultItem,
                this.exportItem,
                this.validateInputItem,
                this.diagnoseLogItem,
                this.bandGapItem,
                this.finalGeometryItem,
                this.pdosItem,
                this.phononItem,
                this.spectraItem,
                this.exportWorkflowItem,
                this.xcrysdenItem
        };
    }

    public ViewerItem getAtomsViewerItem() {
        return this.atomsViewerItem;
    }

    public ViewerItem getInputFileItem() {
        return this.inputFileItem;
    }

    public ViewerItem getModelerItem() {
        return this.modelerItem;
    }

    public ViewerItem getSaveFileItem() {
        return this.saveFileItem;
    }

public ViewerItem getSaveAsFileItem() {
        return this.saveAsFileItem;
    }

    public ViewerItem getRecoverItem() {
        return this.recoverItem;
    }

    public ViewerItem getDesignerItem() {
        return this.designerItem;
    }

    public ViewerItem getScreenShotItem() {
        return this.screenShotItem;
    }

    public ViewerItem getRunItem() {
        return this.runItem;
    }

    public ViewerItem getResultItem() {
        return this.resultItem;
    }

    public ViewerItem getExportItem() {
        return this.exportItem;
    }

    public ViewerItem getExportWorkflowItem() {
        return this.exportWorkflowItem;
    }

    public ViewerItem getValidateInputItem() {
        return this.validateInputItem;
    }

    public ViewerItem getDiagnoseLogItem() {
        return this.diagnoseLogItem;
    }

    public ViewerItem getBandGapItem() {
        return this.bandGapItem;
    }

    public ViewerItem getFinalGeometryItem() {
        return this.finalGeometryItem;
    }

    public ViewerItem getPdosItem() {
        return this.pdosItem;
    }

    public ViewerItem getPhononItem() {
        return this.phononItem;
    }

    public ViewerItem getSpectraItem() {
        return this.spectraItem;
    }

    public ViewerItem getXcrysdenItem() {
        return this.xcrysdenItem;
    }
}
