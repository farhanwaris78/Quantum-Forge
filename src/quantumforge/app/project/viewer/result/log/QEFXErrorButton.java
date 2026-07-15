/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.viewer.result.log;

import java.io.File;

import quantumforge.app.project.QEFXProjectController;
import quantumforge.app.project.viewer.result.QEFXResultButtonWrapper;
import quantumforge.project.Project;

public class QEFXErrorButton extends QEFXLogButton {

    private static final String BUTTON_TITLE = "ERR";
    private static final String BUTTON_FONT_COLOR = "-fx-text-fill: ivory";
    private static final String BUTTON_BACKGROUND = "-fx-background-color: derive(deepskyblue, -24.0%)";

    public static QEFXResultButtonWrapper<QEFXErrorButton> getWrapper(QEFXProjectController projectController, Project project, String ext) {
        if (projectController == null) {
            return null;
        }

        String dirPath = project == null ? null : project.getDirectoryPath();
        String fileName = project == null ? null : project.getErrFileName(ext);

        File file = null;
        if (dirPath != null && fileName != null) {
            file = new File(dirPath, fileName);
        }

        try {
            if (file != null && file.isFile() && (file.length() > 0L)) {
                final File file_ = file;
                return () -> new QEFXErrorButton(projectController, file_, ext);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private QEFXErrorButton(QEFXProjectController projectController, File file, String ext) {
        super(projectController, BUTTON_TITLE, "." + ext, file);

        this.setIconStyle(BUTTON_BACKGROUND);
        this.setLabelStyle(BUTTON_FONT_COLOR);
    }
}
