/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.hpc;

import java.util.List;
import java.util.Optional;

/**
 * Scheduler-specific script generation and job-id parsing.
 */
public interface SchedulerAdapter {

    String name();

    /**
     * Generate a batch script body (shebang + directives + body commands).
     */
    String generateScript(String jobName, SchedulerResources resources, List<String[]> commands);

    /**
     * Parse a job id from submit command stdout/stderr.
     */
    Optional<String> parseJobId(String submitOutput);

    /**
     * Cancel command tokens (argument array, not shell string).
     */
    String[] cancelCommand(String schedulerJobId);

    /**
     * Status/query command tokens.
     */
    String[] statusCommand(String schedulerJobId);

    /**
     * Submit command tokens given a local script path that will be staged.
     */
    String[] submitCommand(String remoteScriptPath);
}
