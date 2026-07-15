/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.com.font;

import quantumforge.com.env.Environments;

public final class FontTools {

    private FontTools() {
        // NOP
    }

    public static String getBlackFont() {
        if (Environments.isLinux()) {
            return "Noto Sans CJK JP Black";

        } else {
            return "Arial Black";
        }
    }

    public static String getRomanFont() {
        if (Environments.isLinux()) {
            //return "Nimbus Roman No9 L";
            return "FreeSerif";

        } else {
            return "Times New Roman";
        }
    }

}
