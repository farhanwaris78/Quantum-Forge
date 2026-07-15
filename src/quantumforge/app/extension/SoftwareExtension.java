/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.extension;

import javafx.scene.Node;

/**
 * Interface for external software extensions.
 */
public interface SoftwareExtension {
    String getName();
    String getVersion();
    Node getEditorGUI();
    Node getResultGUI();
    boolean isAvailable();
}
