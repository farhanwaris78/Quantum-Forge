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

public class MaterialUnit {

    private String cif;

    private String material_id;

    private MaterialUnit() {
        this.cif = null;
        this.material_id = null;
    }

    public String getCif() {
        return this.cif;
    }

    public String getMaterialId() {
        return this.material_id;
    }

}
