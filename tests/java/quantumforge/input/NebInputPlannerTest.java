/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.input.NebInputPlanner.NebDraft;
import quantumforge.operation.OperationResult;

class NebInputPlannerTest {

    @Test
    void validDraftRendersNamelistAndExplicitImageChecklist() {
        OperationResult<NebDraft> result = NebInputPlanner.validate(
                7, 300, "Broyden", "Highest", 0.1, 0.5, 1.0, 0.05);
        assertTrue(result.isSuccess(), result.toString());
        NebDraft draft = result.getValue().orElseThrow();
        assertEquals("broyden", draft.getOptScheme(), "typed enums normalize lowercase");
        assertEquals("highest", draft.getCiScheme());
        String text = draft.draft();
        assertTrue(text.contains("&PATH\n"), text);
        assertTrue(text.contains("restart_mode  = 'from_scratch'\n"), text);
        assertTrue(text.contains("string_method = 'neb'\n"), text);
        assertTrue(text.contains("nstep_path    = 300\n"), text);
        assertTrue(text.contains("opt_scheme    = 'broyden'\n"), text);
        assertTrue(text.contains("CI_scheme     = 'highest'\n"), text);
        assertTrue(text.contains("k_max         = 0.500000\n"), text);
        assertTrue(text.contains("k_min         = 0.100000\n"), text);
        assertTrue(text.contains("ds            = 1.000000\n"), text);
        assertTrue(text.contains("path_thr      = 0.050000\n"), text);
        assertTrue(text.contains("/\n"), text);
        assertTrue(text.contains("num_of_images = 7"), text);
        assertTrue(text.contains("NOT"), text);

        String checklist = draft.checklist();
        assertTrue(checklist.contains("image 1 = FIRST end point (fixed)"), checklist);
        assertTrue(checklist.contains("image 2 = intermediate"), checklist);
        assertTrue(checklist.contains("image 6 = intermediate"), checklist);
        assertTrue(checklist.contains("image 7 = LAST end point (fixed)"), checklist);
        assertFalse(checklist.contains("image 8 ="), checklist + " | " + "the checklist ends exactly at num_of_images");
    }

    @Test
    void countAndStepBoundsRefuse() {
        assertEquals("NEB_IMAGES", NebInputPlanner.validate(
                1, 100, "sd", "no-ci", 0.1, 0.5, 1.0, 0.05).getCode(),
                "one end point is not a path");
        assertEquals("NEB_IMAGES", NebInputPlanner.validate(
                65, 100, "sd", "no-ci", 0.1, 0.5, 1.0, 0.05).getCode());
        assertEquals("NEB_NSTEP", NebInputPlanner.validate(
                3, 0, "sd", "no-ci", 0.1, 0.5, 1.0, 0.05).getCode());
        assertEquals("NEB_NSTEP", NebInputPlanner.validate(
                3, 10001, "sd", "no-ci", 0.1, 0.5, 1.0, 0.05).getCode());
    }

    @Test
    void optimizerAndCiEnumsRefuseFreeForm() {
        assertEquals("NEB_OPT", NebInputPlanner.validate(
                3, 100, "cg", "no-ci", 0.1, 0.5, 1.0, 0.05).getCode(),
                "free-form optimizer strings refuse, they are not echoed");
        assertEquals("NEB_CI", NebInputPlanner.validate(
                3, 100, "sd", "guess", 0.1, 0.5, 1.0, 0.05).getCode());
        OperationResult<NebDraft> manual = NebInputPlanner.validate(
                5, 100, "sd", "manual", 0.1, 0.5, 1.0, 0.05);
        assertFalse(manual.isSuccess());
        assertEquals("NEB_CI", manual.getCode(),
                "'manual' refuses ACTIONABLY - blind indexing would be ceremonial");
        assertTrue(manual.getMessage().contains("highest"),
                "the refusal names the usable alternatives");
    }

    @Test
    void ciNeedsAnInteriorImage() {
        OperationResult<NebDraft> noInterior = NebInputPlanner.validate(
                2, 100, "sd", "highest", 0.1, 0.5, 1.0, 0.05);
        assertFalse(noInterior.isSuccess(),
                "with 2 images every image is an end point - climbing is impossible");
        assertEquals("NEB_CI", noInterior.getCode());
        OperationResult<NebDraft> fine = NebInputPlanner.validate(
                2, 100, "sd", "no-ci", 0.1, 0.5, 1.0, 0.05);
        assertTrue(fine.isSuccess(), "no-CI with 2 images is permitted");
    }

    @Test
    void springsThresholdAndStepAreFailClosed() {
        assertEquals("NEB_K", NebInputPlanner.validate(
                3, 100, "sd", "no-ci", Double.NaN, 0.5, 1.0, 0.05).getCode(),
                "no invented default for the elastic constants");
        assertEquals("NEB_K", NebInputPlanner.validate(
                3, 100, "sd", "no-ci", -0.1, 0.5, 1.0, 0.05).getCode());
        OperationResult<NebDraft> inverted = NebInputPlanner.validate(
                3, 100, "sd", "no-ci", 0.5, 0.1, 1.0, 0.05);
        assertFalse(inverted.isSuccess());
        assertEquals("NEB_K", inverted.getCode(),
                "k_min > k_max inverts the spring ladder - refused, not swapped");
        assertEquals("NEB_DS", NebInputPlanner.validate(
                3, 100, "sd", "no-ci", 0.1, 0.5, 0.0, 0.05).getCode());
        assertEquals("NEB_DS", NebInputPlanner.validate(
                3, 100, "sd", "no-ci", 0.1, 0.5, 5000.0, 0.05).getCode());
        assertEquals("NEB_PATH_THR", NebInputPlanner.validate(
                3, 100, "sd", "no-ci", 0.1, 0.5, 1.0, Double.POSITIVE_INFINITY).getCode());
    }
}
