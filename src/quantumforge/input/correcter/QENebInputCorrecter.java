/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.input.correcter;

import quantumforge.input.QEInput;
import quantumforge.input.namelist.QENamelist;

/**
 * Correcter for Nudged Elastic Band (NEB) path calculations (neb.x),
 * automating the configuration of Climbing Image (CI) schemes to resolve
 * exact transition state saddle points (Roadmap #50).
 */
public final class QENebInputCorrecter extends QEInputCorrecter {

    private boolean climbingImageEnabled;
    private boolean autoScheme; // True for 'Auto', False for 'Manual'
    private int manualClimbingImageIndex; // 1-based index of user-defined climbing image

    public QENebInputCorrecter(QEInput input) {
        super(input);
        this.climbingImageEnabled = true;
        this.autoScheme = true;
        this.manualClimbingImageIndex = 1;
    }

    public void setClimbingImageEnabled(boolean enabled) {
        this.climbingImageEnabled = enabled;
    }

    public void setAutoScheme(boolean auto) {
        this.autoScheme = auto;
    }

    public void setManualClimbingImageIndex(int index) {
        this.manualClimbingImageIndex = Math.max(1, index);
    }

    @Override
    public void correctInput() {
        QENamelist pathNml = this.input.getNamelist("PATH");
        if (pathNml == null) {
            // If PATH namelist is missing, we create it dynamically on the input
            try {
                java.lang.reflect.Method m = this.input.getClass().getSuperclass().getDeclaredMethod("setupNamelist", String.class, quantumforge.input.QEInputReader.class);
                m.setAccessible(true);
                m.invoke(this.input, "PATH", null);
                pathNml = this.input.getNamelist("PATH");
            } catch (Exception e) {
                // Fallback: we write directly to control
                pathNml = this.nmlControl;
            }
        }

        if (pathNml == null) {
            return;
        }

        pathNml.setValue("string_method = 'neb'");

        if (this.climbingImageEnabled) {
            if (this.autoScheme) {
                pathNml.setValue("CI_scheme = 'Auto'");
                pathNml.removeValue("climbing_image");
            } else {
                pathNml.setValue("CI_scheme = 'Manual'");
                pathNml.setValue("climbing_image = " + this.manualClimbingImageIndex);
            }
        } else {
            pathNml.setValue("CI_scheme = 'No-Climbing'");
            pathNml.removeValue("climbing_image");
        }
    }
}
