/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.hpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class PjmSchedulerAdapterTest {

    private final PjmSchedulerAdapter adapter = new PjmSchedulerAdapter();

    @Test
    void scriptCarriesTheMinimalHonestPjmDirectives() {
        SchedulerResources resources = SchedulerResources.builder()
                .nodes(2).ntasks(8).cpusPerTask(4).walltime("01:30")
                .partition("small").account("projA").memory("4gb").build();
        String script = adapter.generateScript("qe run", resources,
                List.of(new String[] {"mpirun", "pw.x", "-i", "pw.in"}));
        assertTrue(script.startsWith("#!/usr/bin/env bash"), script);
        assertTrue(script.contains("#PJM -N qe_run"), script);
        assertTrue(script.contains("#PJM -L \"rscgrp=small\""), script);
        assertTrue(script.contains("#PJM -L \"node=2\""), script);
        // HH:MM is normalized to PJM's HH:MM:SS elapse shape.
        assertTrue(script.contains("#PJM -L \"elapse=01:30:00\""), script);
        assertTrue(script.contains("#PJM -j"), script);
        assertTrue(script.contains("export OMP_NUM_THREADS=4"), script);
        assertTrue(script.contains("'pw.x'"), script);
        // The stated omissions: nothing is invented for mpi placement, account
        // or memory - the header comment must say so instead.
        assertFalse(script.contains("--mpi"), script);
        assertFalse(script.contains("projA"), script);
        assertFalse(script.contains("4gb"), script);
        assertTrue(script.contains("no --mpi placement line"), script);
        assertFalse(script.contains("qsub") && !script.contains("pjsub-related"),
                "PJM grammar never leaks a PBS token");
    }

    @Test
    void jobIdParsesThePjsubSubmissionLineAndBareDigits() {
        assertEquals("12345678", adapter.parseJobId(
                "[INFO] PJM 0000 pjsub Job 12345678 submitted.").orElseThrow());
        assertEquals("7712", adapter.parseJobId("7712\n").orElseThrow());
        assertTrue(adapter.parseJobId("no id here").isEmpty());
        assertTrue(adapter.parseJobId("  ").isEmpty());
        assertTrue(adapter.parseJobId(null).isEmpty());
    }

    @Test
    void commandsUseTheRealFujitsuPjmTools() {
        // Fujitsu PJM grammar: pjsub / pjdel / pjstat -S (Fujitsu TCS manual,
        // Kyushu University Genkai job-usage documentation).
        assertEquals("pjdel", adapter.cancelCommand("7712")[0]);
        assertEquals("7712", adapter.cancelCommand("7712")[1]);
        String[] status = adapter.statusCommand("7712");
        assertEquals("pjstat", status[0]);
        assertEquals("-S", status[1]);
        assertEquals("7712", status[2]);
        assertEquals("pjsub", adapter.submitCommand("jobs/run.sh")[0]);
        assertEquals("jobs/run.sh", adapter.submitCommand("jobs/run.sh")[1]);
    }

    @Test
    void jobIdGrammarIsDecimalOnlyAndRefusesSlurmArraySyntax() {
        assertThrows(IllegalArgumentException.class, () -> adapter.cancelCommand(""),
                "blank is nobody's job id");
        assertThrows(IllegalArgumentException.class, () -> adapter.cancelCommand(null));
        assertThrows(IllegalArgumentException.class,
                () -> adapter.cancelCommand("job123"), "free-form ids refuse");
        assertThrows(IllegalArgumentException.class,
                () -> adapter.cancelCommand("7712.srv"),
                "the PBS-style .server suffix is not PJM grammar");
        IllegalArgumentException array = assertThrows(IllegalArgumentException.class,
                () -> adapter.cancelCommand("4521_3"));
        assertTrue(array.getMessage().contains("id_index"), array.getMessage(),
                "the refusal names exactly whose grammar array syntax is");
    }

    @Test
    void submitPathRefusesTraversalAndBlank() {
        assertThrows(IllegalArgumentException.class, () -> adapter.submitCommand("  "));
        assertThrows(IllegalArgumentException.class,
                () -> adapter.submitCommand("../escape.sh"));
    }
}
