/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.editor;

import java.io.IOException;

import quantumforge.app.QEFXAppComponent;
import quantumforge.app.QEFXAppController;

public abstract class QEFXEditorComponent<T extends QEFXAppController> extends QEFXAppComponent<T> {

    public QEFXEditorComponent(String fileFXML, T controller) throws IOException {
        super(fileFXML, controller);
    }

    public abstract void notifyEditorOpened();

}
