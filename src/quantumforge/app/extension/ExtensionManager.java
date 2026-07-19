/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.app.extension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import quantumforge.app.extension.vasp.VASPExtension;
import quantumforge.app.extension.castep.CASTEPExtension;
import quantumforge.app.extension.phonopy.PhonopyExtension;
import quantumforge.app.extension.boltztrap2.BoltzTraP2Extension;
import quantumforge.app.extension.thermopw.ThermoPwExtension;
import quantumforge.app.extension.ml.MLPotentialExtension;

public class ExtensionManager {
    private static ExtensionManager instance = null;
    private List<SoftwareExtension> extensions;

    private ExtensionManager() {
        extensions = new ArrayList<>();
        extensions.add(new VASPExtension());
        extensions.add(new CASTEPExtension());
        extensions.add(new PhonopyExtension());
        extensions.add(new BoltzTraP2Extension());
        extensions.add(new ThermoPwExtension());
        extensions.add(new MLPotentialExtension());
        // Only complete, validated extensions may be returned to production UI
        // code. The source tree retains several historical editor sketches, but
        // exposing them would imply that an executable form is a workflow.
        extensions.removeIf(extension -> !extension.isAvailable());
        extensions = Collections.unmodifiableList(extensions);
    }

    public static ExtensionManager getInstance() {
        if (instance == null) {
            instance = new ExtensionManager();
        }
        return instance;
    }

    /**
     * Returns only production-ready extensions. Experimental source sketches
     * are deliberately excluded until their capability is independently
     * validated end-to-end.
     */
    public List<SoftwareExtension> getExtensions() {
        return extensions;
    }
}
