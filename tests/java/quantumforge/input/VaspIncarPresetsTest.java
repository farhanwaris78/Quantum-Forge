/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import quantumforge.input.validation.VaspIncarDeck;
import quantumforge.input.validation.VaspIncarDeckAudit;
import quantumforge.input.validation.VaspKpointsDeck;
import quantumforge.input.validation.ValidationIssue;
import quantumforge.input.validation.ValidationSeverity;
import quantumforge.operation.OperationResult;

/**
 * Batch-173 pins for {@link VaspIncarPresets}: every preset is assembled
 * ONLY from the tier-1 schema window, every preset audits clean against the
 * batch's own audit (no ERROR, no WARNING - INFO advisories such as the
 * ENCUT manual-pin burden are deliberate), and every companion mesh is a
 * legal KPOINTS grammar citizen.
 */
class VaspIncarPresetsTest {

    @Test
    void everyKeyHasALabelAndText() {
        assertEquals(6, VaspIncarPresets.KEYS.size());
        for (String key : VaspIncarPresets.KEYS) {
            assertFalse(VaspIncarPresets.labelOf(key).isBlank(), key);
            String incar = VaspIncarPresets.buildIncar(key);
            assertTrue(incar.contains("INCAR preset"),
                    key + " misses the honest header");
            assertTrue(incar.contains("# ENCUT ="),
                    key + " must carry (never auto-set) the ENCUT burden: "
                            + incar);
            assertFalse(incar.contains("\nENCUT ="),
                    key + " must NOT auto-set ENCUT (POTCAR-derived default)");
            assertFalse(VaspIncarPresets.companionText(key).isBlank(), key);
        }
        assertThrows(IllegalArgumentException.class,
                () -> VaspIncarPresets.buildIncar("NOPE"));
        assertThrows(IllegalArgumentException.class,
                () -> VaspIncarPresets.companionMeshes("NOPE"));
        assertThrows(IllegalArgumentException.class,
                () -> VaspIncarPresets.labelOf("NOPE"));
    }

    @Test
    void everyPresetAuditsWithoutErrorsOrWarnings() {
        for (String key : VaspIncarPresets.KEYS) {
            List<ValidationIssue> issues = VaspIncarDeckAudit.auditDeckText(
                    VaspIncarPresets.buildIncar(key));
            assertFalse(issues.stream().anyMatch(issue -> issue.getSeverity()
                            == ValidationSeverity.ERROR),
                    key + " preset trips an ERROR finding: " + issues);
            assertFalse(issues.stream().anyMatch(issue -> issue.getSeverity()
                            == ValidationSeverity.WARNING),
                    key + " preset trips a WARNING finding: " + issues);
        }
    }

    @Test
    void mdPresetCarriesBothCrashClassPins() {
        String md = VaspIncarPresets.buildIncar("MD");
        assertTrue(md.contains("IBRION = 0"));
        assertTrue(md.contains("POTIM  = 1.0"),
                "POTIM has NO default for IBRION=0 - VASP crashes without it");
        assertTrue(md.contains("NSW    = 500"),
                "NSW has to be supplied for MD - VASP exits without it");
        assertTrue(md.contains("crashes immediately"),
                "the wiki verbatim crash quote travels in the template");
    }

    @Test
    void hse06PresetIsTheDocumentedHybridRecipe() {
        String hse = VaspIncarPresets.buildIncar("HSE06_BANDS");
        assertTrue(hse.contains("LHFCALC = .TRUE."));
        assertTrue(hse.contains("GGA     = PE"),
                "HFSCREEN requires the GGA=PE/PS/CA family, wiki verbatim");
        assertTrue(hse.contains("HFSCREEN = 0.2"),
                "0.2 A^-1 selects the HSE06 recipe per the wiki");
        assertTrue(hse.contains("AEXX    = 0.25"));
        assertTrue(hse.contains("ALGO    = Damped"),
                "the wiki recommends Damped/All; Fast is 'not properly"
                        + " supported'");
        assertTrue(hse.contains("regular mesh must always be provided"),
                "the line-mode prohibition for hybrids is quoted verbatim");
    }

    @Test
    void dftPlusUPresetFollowsTheUdJGuidance() {
        String plus = VaspIncarPresets.buildIncar("DFTPLUSU");
        assertTrue(plus.contains("LDAU     = .TRUE."));
        assertTrue(plus.contains("LDAUTYPE = 2"));
        assertTrue(plus.contains("LDAUL    = 2 -1"));
        assertTrue(plus.contains("LDAUU    = 4.0 0.0"));
        assertTrue(plus.contains("LMAXMIX  = 4"),
                "the wiki's d-electron LMAXMIX advice is honored");
        assertTrue(plus.contains("EXAMPLE VALUE"),
                "U/J values are marked as examples, never physics laws");
    }

    @Test
    void companionMeshesAreGrammarCitizens() {
        for (String key : VaspIncarPresets.KEYS) {
            List<VaspKpointsDeck> meshes = VaspIncarPresets.companionMeshes(key);
            assertFalse(meshes.isEmpty(), key);
            for (VaspKpointsDeck mesh : meshes) {
                OperationResult<VaspKpointsDeck> back = VaspKpointsDeck.parse(
                        mesh.toKpointsText());
                assertTrue(back.isSuccess(),
                        key + " companion must round-trip: " + back.getMessage());
                assertEquals(mesh.getMode(), back.getValue().orElseThrow()
                        .getMode());
            }
        }
        // the line-mode companion exists ONLY where the wiki allows it
        List<VaspKpointsDeck> hybrid = VaspIncarPresets.companionMeshes(
                "HSE06_BANDS");
        assertTrue(hybrid.stream().noneMatch(mesh -> mesh.getMode()
                        == VaspKpointsDeck.Mode.LINE_MODE),
                "hybrids: 'a regular mesh must always be provided' (wiki)");
        List<VaspKpointsDeck> dosBands = VaspIncarPresets.companionMeshes(
                "DOS_BANDS");
        assertTrue(dosBands.stream().anyMatch(mesh -> mesh.getMode()
                        == VaspKpointsDeck.Mode.LINE_MODE));
        VaspKpointsDeck path = dosBands.stream().filter(mesh -> mesh.getMode()
                        == VaspKpointsDeck.Mode.LINE_MODE).findFirst()
                .orElseThrow();
        assertEquals(6, path.getVertices().size(),
                "the fcc G-X-W-G path has 3 segments = 6 vertices");
        assertEquals(40, path.getPointsPerSegment());
    }

    @Test
    void presetsParseIntoSaneStatements() {
        VaspIncarDeck scf = VaspIncarDeck.parse(
                VaspIncarPresets.buildIncar("SCF")).getValue().orElseThrow();
        assertTrue(scf.first("ISMEAR").isPresent());
        assertTrue(scf.first("ENCUT").isEmpty(),
                "ENCUT is the operator's burden, never generated");
        assertEquals("Accurate", scf.first("PREC").orElseThrow().getRawValue());
        assertTrue(VaspIncarPresets.selfAuditReport("SCF").contains(
                        "ISTART = 0"),
                "the workbench self-audit echoes the deck census");
    }
}
