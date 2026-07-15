/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.atoms.design;

public enum AtomsStyle {

    BALL_STICK(0, "Ball & Stick"),

    BALL(1, "Ball"),

    STICK(2, "Stick");

    private int id;

    private String label;

    private AtomsStyle(int id, String label) {
        this.id = id;
        this.label = label;
    }

    public int getId() {
        return this.id;
    }

    public static AtomsStyle getInstance(int id) {
        AtomsStyle[] atomsStyles = values();
        if (atomsStyles == null || atomsStyles.length < 1) {
            return null;
        }

        for (AtomsStyle atomsStyle : atomsStyles) {
            if (atomsStyle != null && id == atomsStyle.getId()) {
                return atomsStyle;
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return this.label;
    }
}
