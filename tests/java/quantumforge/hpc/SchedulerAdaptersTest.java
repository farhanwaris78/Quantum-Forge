/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.hpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SchedulerAdaptersTest {

    @Test
    void registryCoversExactlyTheFourTypedSchedulers() {
        List<SchedulerAdapter> all = SchedulerAdapters.all();
        assertEquals(4, all.size(), "slurm, pbs, pjm, sge - nothing more, nothing less");
        Set<String> names = new HashSet<>();
        for (SchedulerAdapter adapter : all) {
            names.add(adapter.name());
        }
        assertEquals(Set.of("slurm", "pbs", "pjm", "sge"), names);
    }

    @Test
    void forNameIsTrimmedCaseInsensitiveAndNeverDefaults() {
        assertEquals("slurm", SchedulerAdapters.forName(" SLURM ").orElseThrow().name());
        assertEquals("pjm", SchedulerAdapters.forName("Pjm").orElseThrow().name());
        assertTrue(SchedulerAdapters.forName("").isEmpty(),
                "blank must NOT silently resolve to a default adapter");
        assertTrue(SchedulerAdapters.forName(null).isEmpty());
        assertTrue(SchedulerAdapters.forName("  ").isEmpty());
        assertTrue(SchedulerAdapters.forName("torque").isEmpty(),
                "a free-form scheduler name never reaches a cancel command");
    }

    @Test
    void supportedNamesListsTheTypedSet() {
        String names = SchedulerAdapters.supportedNames();
        assertTrue(names.contains("slurm"), names);
        assertTrue(names.contains("pbs"), names);
        assertTrue(names.contains("pjm"), names);
        assertTrue(names.contains("sge"), names);
    }

    @Test
    void adaptersOwnDistinctGrammarPerScheduler() {
        // Cross-grammar drift probe (batch 126 core): the same string must be
        // judged per SCHEDULER, not per shared regex.
        SchedulerAdapter slurm = SchedulerAdapters.forName("slurm").orElseThrow();
        SchedulerAdapter pbs = SchedulerAdapters.forName("pbs").orElseThrow();
        SchedulerAdapter pjm = SchedulerAdapters.forName("pjm").orElseThrow();
        SchedulerAdapter sge = SchedulerAdapters.forName("sge").orElseThrow();
        slurm.cancelCommand("4521_3");            // array task: SLURM-only grammar
        assertThrowsFor(pbs, "4521_3");
        assertThrowsFor(pjm, "4521_3");
        assertThrowsFor(sge, "4521_3");
        pbs.cancelCommand("9156.hpc02");          // .server suffix: PBS-only grammar
        assertThrowsFor(pjm, "9156.hpc02");
        assertThrowsFor(sge, "9156.hpc02");
        assertThrowsFor(slurm, "9156.hpc02");
        slurm.cancelCommand("42");                // plain decimal: everyone
        pbs.cancelCommand("42");
        pjm.cancelCommand("42");
        sge.cancelCommand("42");
    }

    private static void assertThrowsFor(SchedulerAdapter adapter, String id) {
        try {
            adapter.cancelCommand(id);
            org.junit.jupiter.api.Assertions.fail(
                    adapter.name() + " must refuse '" + id + "' per its own grammar");
        } catch (IllegalArgumentException expected) {
            // refusal is the grammar verdict
        }
    }
}
