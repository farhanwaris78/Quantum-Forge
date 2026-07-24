/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.ver;

public interface Version {

    public static final String VERSION = "2.0.3";

    /**
     * Legacy major/minor value retained for binary compatibility only.
     * Semantic versions such as "2.0.0" are not valid Java doubles; new code
     * must compare {@link #VERSION} as a string/semantic version instead.
     */
    @Deprecated
    public static final double VERSION_NUMBER = 2.0d;

    public static final String VERSION_NAME = "QuantumForge";

    public static final String SUPPORTED_QE_VERSION = "7.5";

    public static final String SUPPORTED_THERMO_PW_VERSION = "2.1.1";

}
