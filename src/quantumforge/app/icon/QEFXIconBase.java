/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.icon;

import java.io.File;
import java.text.SimpleDateFormat;

public abstract class QEFXIconBase<T> extends QEFXIcon {

    protected static final double ICON_SCALE = 0.55;

    protected T content;

    public QEFXIconBase(T content) {
        super();

        if (content == null) {
            throw new IllegalArgumentException("content is null.");
        }

        if (content instanceof String) {
            if (((String) content).trim().isEmpty()) {
                throw new IllegalArgumentException("content is empty.");
            }
        }

        this.content = content;
    }

    public T getContent() {
        return this.content;
    }

    protected String getFileDetail(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        File file = new File(path);
        try {
            if (!file.exists()) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }

        Long timeStamp = 0L;
        try {
            timeStamp = file.lastModified();
        } catch (Exception e) {
            timeStamp = 0L;
        }

        SimpleDateFormat dataFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        String date = dataFormat.format(timeStamp);

        String caption = "Path: " + path + System.lineSeparator() + "Date: " + date;
        return caption;
    }
}
