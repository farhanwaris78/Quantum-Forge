/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.hpc.PbsSchedulerAdapter;
import quantumforge.hpc.PjmSchedulerAdapter;
import quantumforge.hpc.SchedulerAdapter;
import quantumforge.hpc.SchedulerAdapters;
import quantumforge.hpc.SgeSchedulerAdapter;
import quantumforge.hpc.SlurmSchedulerAdapter;
import quantumforge.operation.OperationResult;

/**
 * Batch-144 single-owner fix: the SSH-server-to-adapter resolution has ONE
 * owner, it delegates to the registry, and the old silent slurm default for
 * 'none'/pjm/unknown is gone - every refusal names where the honest fix
 * lives (the SSH dialog's Job Scheduler chooser).
 */
class SSHServerSchedulerTest {

    private static SSHServer server(String type) {
        SSHServer server = new SSHServer("cluster-a");
        if (type != null) {
            server.setSchedulerType(type);
        }
        return server;
    }

    @Test
    void everyRegistrySchedulerResolvesFirstClass() {
        assertTrue(SSHServerScheduler.resolveAdapter(server(SSHServer.SCHEDULER_SLURM))
                .getValue().orElseThrow() instanceof SlurmSchedulerAdapter);
        assertTrue(SSHServerScheduler.resolveAdapter(server(SSHServer.SCHEDULER_PBS))
                .getValue().orElseThrow() instanceof PbsSchedulerAdapter);
        assertTrue(SSHServerScheduler.resolveAdapter(server(SSHServer.SCHEDULER_SGE))
                .getValue().orElseThrow() instanceof SgeSchedulerAdapter);
        OperationResult<SchedulerAdapter> pjm =
                SSHServerScheduler.resolveAdapter(server(SSHServer.SCHEDULER_PJM));
        assertTrue(pjm.isSuccess(), pjm.toString());
        assertEquals("SSH_SCHEDULER_OK", pjm.getCode());
        assertTrue(pjm.getValue().orElseThrow() instanceof PjmSchedulerAdapter,
                "pjm is a first-class SSHServer constant - resolving it to slurm "
                        + "(the old private copy's behaviour) was the bug");
    }

    @Test
    void vernacularCaseAndPaddingNormalizeThroughTheRegistry() {
        assertTrue(SSHServerScheduler.resolveAdapter(server(" Slurm "))
                .getValue().orElseThrow() instanceof SlurmSchedulerAdapter);
        assertTrue(SSHServerScheduler.resolveAdapter(server("PJM"))
                .getValue().orElseThrow() instanceof PjmSchedulerAdapter);
    }

    @Test
    void theOutOfTheBoxNoneIsUnsetNotASilentSlurm() {
        freshEntryRefuses(SSHServer.SCHEDULER_NONE, "'none'");
    }

    @Test
    void aBlankDeclarationIsUnsetToo() {
        freshEntryRefuses("  ", "'<blank>'");
    }

    private static void freshEntryRefuses(String type, String rendered) {
        SSHServer server = server(type);
        OperationResult<SchedulerAdapter> refused =
                SSHServerScheduler.resolveAdapter(server);
        assertFalse(refused.isSuccess());
        assertEquals("SSH_SCHEDULER_UNSET", refused.getCode());
        assertTrue(refused.getMessage().contains("cluster-a")
                        && refused.getMessage().contains(rendered),
                "the refusal names the entry and what it declares: "
                        + refused.getMessage());
        assertTrue(refused.getMessage().contains("Job Scheduler chooser")
                        && refused.getMessage().contains(SchedulerAdapters.supportedNames()),
                "the honest fix (the batch-144 SSH-dialog chooser) and the supported set "
                        + "ride the message: " + refused.getMessage());
        assertTrue(refused.getMessage().contains("no default is ever picked"),
                refused.getMessage());
    }

    @Test
    void anUnknownSchedulerIsNamedNeverGuessed() {
        OperationResult<SchedulerAdapter> refused =
                SSHServerScheduler.resolveAdapter(server("lsf"));
        assertFalse(refused.isSuccess());
        assertEquals("SSH_SCHEDULER_UNKNOWN", refused.getCode());
        assertTrue(refused.getMessage().contains("'lsf'")
                        && refused.getMessage().contains(SchedulerAdapters.supportedNames()),
                refused.getMessage());
        assertTrue(refused.getMessage().contains("no default is ever picked"),
                refused.getMessage());
    }

    @Test
    void aNullServerFailsLoudly() {
        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> SSHServerScheduler.resolveAdapter(null));
        assertTrue(thrown.getMessage().contains("server is required"),
                thrown.getMessage());
    }
}
