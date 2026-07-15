/*
 * Copyright (C) 2025 QuantumForge Team
 */

package quantumforge.app.extension;

import java.util.ArrayList;
import java.util.List;
import quantumforge.app.extension.vasp.VASPExtension;

public class ExtensionManager {
    private static ExtensionManager instance = null;
    private List<SoftwareExtension> extensions;

    private ExtensionManager() {
        extensions = new ArrayList<>();
        extensions.add(new VASPExtension());
        // Add others here
    }

    public static ExtensionManager getInstance() {
        if (instance == null) {
            instance = new ExtensionManager();
        }
        return instance;
    }

    public List<SoftwareExtension> getExtensions() {
        return extensions;
    }
}
