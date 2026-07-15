/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.com.keys;

import javafx.scene.input.KeyCode;
import quantumforge.com.env.Environments;

public final class KeyNames {

    private KeyNames() {
        // NOP
    }

    public static String getShortcut() {
        return getShortcut(null);
    }

    public static String getShortcut(KeyCode keyCode) {
        String comb = Environments.isMac() ? "Command" : "Ctrl";
        String name = keyCode == null ? null : keyCode.getName();
        if (name == null) {
            return comb;
        } else {
            return comb + "+" + name;
        }
    }

    public static String getShortcutShift() {
        return getShortcutShift(null);
    }

    public static String getShortcutShift(KeyCode keyCode) {
        String comb = Environments.isMac() ? "Command+Shift" : "Ctrl+Shift";
        String name = keyCode == null ? null : keyCode.getName();
        if (name == null) {
            return comb;
        } else {
            return comb + "+" + name;
        }
    }

    public static String getAlt() {
        return getAlt(null);
    }

    public static String getAlt(KeyCode keyCode) {
        String comb = Environments.isMac() ? "Option" : "Alt";
        String name = keyCode == null ? null : keyCode.getName();
        if (name == null) {
            return comb;
        } else {
            return comb + "+" + name;
        }
    }
}
