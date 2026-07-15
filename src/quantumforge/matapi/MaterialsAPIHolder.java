/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.matapi;

import quantumforge.com.life.Life;

public class MaterialsAPIHolder {

    private static MaterialsAPIHolder instance = null;

    protected static MaterialsAPIHolder getInstance() {
        if (instance == null) {
            instance = new MaterialsAPIHolder();
        }

        return instance;
    }

    private MaterialsAPILoader loader;

    private MaterialsAPIHolder() {
        this.loader = null;

        Life.getInstance().addOnDead(() -> this.deleteLoader());
    }

    protected synchronized void setLoader(MaterialsAPILoader loader) {
        this.loader = loader;
    }

    protected synchronized void deleteLoader() {
        if (this.loader != null) {
            if (this.loader.isAlive()) {
                this.loader.setToBeDead();
            }

            this.loader = null;
        }
    }
}
