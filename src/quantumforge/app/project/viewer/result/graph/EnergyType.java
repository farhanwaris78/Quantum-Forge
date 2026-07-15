/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.viewer.result.graph;

public enum EnergyType {

    TOTAL("ene.TOT"),
    KINETIC("ene.KIN"),
    CONSTANT("ene.TOT+KIN"),
    TEMPERATURE("temp");

    private String symbol;

    private EnergyType(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return this.symbol;
    }
}
