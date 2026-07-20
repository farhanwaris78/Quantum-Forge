/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.ssh;

import java.util.Locale;
import java.util.Optional;

import quantumforge.hpc.SchedulerAdapter;
import quantumforge.hpc.SchedulerAdapters;
import quantumforge.operation.OperationResult;

/**
 * Roadmap #93/#96 (batch-144 single-owner fix): THE one resolution path from
 * a configured {@link SSHServer} to its {@link SchedulerAdapter}.
 *
 * <p>Why this owner exists: the whole-code review found
 * {@code SSHJob.resolveAdapter()} carrying a PRIVATE copy of the
 * server-scheduler mapping that silently returned the SLURM adapter for
 * everything except exact {@code pbs}/{@code sge} - so a server set to
 * {@code pjm} (a defined constant since the adapter landed!) or, worse, the
 * out-of-the-box {@code none} that every GUI-created server keeps, would
 * still submit sbatch scripts to a cluster that may not speak SLURM. The
 * registry's no-default doctrine (batch 126) and the site-domain
 * canonicalization (batch 134) existed precisely to forbid that; the legacy
 * server path simply had not been enrolled. This class is the enrollment:
 * the ONLY mapping from server-declared scheduler text to a registry
 * adapter, used by the fixed {@code SSHJob}, by the batch-144 GUI
 * array-submit dialogue, and by anything else that must never invent a
 * scheduler.</p>
 *
 * <p>Honesty rules, all fail-closed:</p>
 * <ul>
 *   <li>the registry ({@link SchedulerAdapters#forName}) is the single owner
 *       of the canonical name set and of every id grammar - this resolver
 *       only normalizes vernacular case/space and delegates;</li>
 *   <li>a BLANK or {@code none} declaration is SSH_SCHEDULER_UNSET - the
 *       message says where the honest fix lives (the SSH configuration
 *       dialog's Job Scheduler chooser, batch 144) and that no default is
 *       ever picked;</li>
 *   <li>any OTHER unknown name is SSH_SCHEDULER_UNKNOWN, naming the raw
 *       value and the live supported set - a cluster's scheduler is never
 *       guessed from nothing.</li>
 * </ul>
 */
public final class SSHServerScheduler {

    private SSHServerScheduler() {
    }

    /**
     * Resolve the server's declared scheduler through the registry. Codes:
     * SSH_SCHEDULER_OK on success; SSH_SCHEDULER_UNSET for blank/none;
     * SSH_SCHEDULER_UNKNOWN otherwise.
     */
    public static OperationResult<SchedulerAdapter> resolveAdapter(SSHServer server) {
        if (server == null) {
            throw new NullPointerException("server is required - its declared "
                    + "scheduler is the only input this resolver reads");
        }
        String raw = server.getSchedulerType();
        String norm = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (norm.isEmpty() || SSHServer.SCHEDULER_NONE.equals(norm)) {
            return OperationResult.failed("SSH_SCHEDULER_UNSET",
                    "SSH server '" + server.getTitle() + "' declares scheduler '"
                            + (norm.isEmpty() ? "<blank>" : norm) + "' - no scheduler "
                            + "is recorded on this entry. Set one in the SSH "
                            + "configuration dialog (the Job Scheduler chooser lists "
                            + SchedulerAdapters.supportedNames() + "); no default is "
                            + "ever picked, so nothing was submitted.",
                    null);
        }
        Optional<SchedulerAdapter> adapter = SchedulerAdapters.forName(norm);
        if (adapter.isEmpty()) {
            return OperationResult.failed("SSH_SCHEDULER_UNKNOWN",
                    "SSH server '" + server.getTitle() + "' declares unknown scheduler '"
                            + raw + "' - supported: " + SchedulerAdapters.supportedNames()
                            + ". Fix the entry in the SSH configuration dialog; no "
                            + "default is ever picked, so nothing was submitted.",
                    null);
        }
        return OperationResult.success("SSH_SCHEDULER_OK",
                "Server '" + server.getTitle() + "' resolves to the '" + adapter.get().name()
                        + "' adapter through the registry (never a GUI-side copy).",
                adapter.get());
    }
}
