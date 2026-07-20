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

    /**
     * Absence verdict: true ONLY when the status query's stderr carries this
     * scheduler's DOCUMENTED "job not found" needle. The default is
     * deliberately FALSE: without documented knowledge, a non-zero status exit
     * or an empty output is NEVER a verdict that the job is gone (a transport
     * failure, a missing scheduler binary or a permission error all print the
     * same nothing). Only adapters that override with a pinned, documented
     * needle may declare absence.
     *
     * @param stderrText the status command's stderr (trimmed, maybe empty)
     * @return true only on the documented absence needle
     */
    default boolean isJobAbsent(String stderrText) {
        return false;
    }

    /**
     * OWNED array/bulk-submit knowledge (Roadmap #93/#100 submit-lane slice).
     * The default is deliberately {@link ArraySubmitSpec#unsupported(String)}
     * with its reason spelled out: per the no-default doctrine, an adapter
     * that does not own array semantics must NEVER invent a flag - callers
     * fall back to the honest per-task submit loop of the SAME documented
     * {@link #submitCommand(String)}.
     */
    default ArraySubmitSpec arraySubmitSpec() {
        return ArraySubmitSpec.unsupported("this adapter owns no array-submit "
                + "form - the per-task loop runs the documented submitCommand "
                + "once per task instead");
    }
}
