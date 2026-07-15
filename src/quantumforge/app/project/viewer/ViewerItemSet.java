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

    public ViewerItemSet() {
        this.atomsViewerItem = new ViewerItem(SVGData.ATOMS, "Show atoms");
        this.inputFileItem = new ViewerItem(SVGData.INPUTFILE, "Input-file");
        this.modelerItem = new ViewerItem(SVGData.TOOL, "Modeler");
        this.saveFileItem = new ViewerItem(SVGData.SAVE, "Save");
        this.saveAsFileItem = new ViewerItem(SVGData.SAVE, "Save as ...");
        this.designerItem = new ViewerItem(SVGData.COLORS, "Designer");
        this.screenShotItem = new ViewerItem(SVGData.CAMERA, "Screen-shot");
        this.runItem = new ViewerItem(SVGData.RUN, "Run");
        this.resultItem = new ViewerItem(SVGData.RESULT, "Result");
        this.exportItem = new ViewerItem(SVGData.EXPORT, "Export");
    }

    public ViewerItem[] getItems() {
        return new ViewerItem[] {
                this.atomsViewerItem,
                this.inputFileItem,
                this.modelerItem,
                this.saveFileItem,
                this.saveAsFileItem,
                this.designerItem,
                this.screenShotItem,
                this.runItem,
                this.resultItem,
                this.exportItem
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
}
