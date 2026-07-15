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

public abstract class MaterialData {

    public static MaterialData getInstance(String matID) {
        return MaterialData.getInstance(matID, null);
    }

    public static MaterialData getInstance(String matID, String apiKey) {

        MaterialData matData = null;
        if (apiKey != null && (!apiKey.trim().isEmpty())) {
            matData = MaterialAllData.getInstance(matID, apiKey);
        }

        if (matData == null) {
            matData = MaterialCIF.getInstance(matID);
        }

        return matData;
    }

    protected MaterialData() {
        // NOP
    }

    public abstract String getCIF();

}
