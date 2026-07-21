/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.input.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import quantumforge.input.schema.QENamelistSchema.AcceptedValue;
import quantumforge.input.schema.QENamelistSchema.Entry;
import quantumforge.input.schema.QENamelistSchema.Kind;

/**
 * Batch 150 pins of the machine-mined QE grammar table. Every pin here was
 * checked against the ground-truth grammar files of qe-7.2 .. qe-7.6 during
 * generation (see the sha256 provenance header of QESchemaData); a pin breaks
 * only when the schema is regenerated against different QE sources - exactly
 * the alarm this test is for.
 */
class QENamelistSchemaTest {

    @Test
    void entryCountMatchesTheMinedTable() {
        assertEquals(572, QENamelistSchema.entryCount());
        assertEquals(459, QENamelistSchema.entries(Kind.PW).size());
        assertEquals(80, QENamelistSchema.entries(Kind.PH).size());
        assertEquals(33, QENamelistSchema.entries(Kind.HP).size());
    }

    @Test
    void versionWindowIsPinnedToSevenTwoThroughSevenSix() {
        assertEquals(List.of("7.2", "7.3", "7.4", "7.5", "7.6"), QENamelistSchema.VERSIONS);
        assertEquals(3, QENamelistSchema.indexOfVersion("7.5"));
        assertEquals(-1, QENamelistSchema.indexOfVersion("7.7"));
        assertEquals(-1, QENamelistSchema.indexOfVersion(""));
        assertEquals(-1, QENamelistSchema.indexOfVersion(null));
    }

    @Test
    void programParsingIsStrictAndHonest() {
        assertEquals(Kind.PW, Kind.parse("pw").orElseThrow());
        assertEquals(Kind.PW, Kind.parse(" pw.X ").orElseThrow());
        assertEquals(Kind.PH, Kind.parse("PH").orElseThrow());
        assertEquals(Kind.HP, Kind.parse("hp.x").orElseThrow());
        assertTrue(Kind.parse("cp").isEmpty(), "cp.x is not mined in this window");
        assertTrue(Kind.parse(null).isEmpty());
        assertEquals("https://www.quantum-espresso.org/Doc/INPUT_PW.html",
                Kind.PW.getDocsUrl());
    }

    @Test
    void entriesFilteredByVersionMatchTheTags() {
        assertEquals(454, QENamelistSchema.entries(Kind.PW, "7.2").size());
        assertEquals(454, QENamelistSchema.entries(Kind.PW, "7.3").size());
        assertEquals(456, QENamelistSchema.entries(Kind.PW, "7.4").size());
        assertEquals(457, QENamelistSchema.entries(Kind.PW, "7.5").size());
        assertEquals(458, QENamelistSchema.entries(Kind.PW, "7.6").size());
        assertEquals(75, QENamelistSchema.entries(Kind.PH, "7.2").size());
        assertEquals(79, QENamelistSchema.entries(Kind.PH, "7.6").size());
        assertEquals(32, QENamelistSchema.entries(Kind.HP, "7.2").size());
        assertEquals(33, QENamelistSchema.entries(Kind.HP, "7.5").size());
    }

    @Test
    void calculationEnumIsMinedHardAndVerbatimFromTheProgramSwitch() {
        Entry calc = QENamelistSchema.lookup(Kind.PW, "Calculation").orElseThrow();
        assertEquals("CONTROL", calc.getNamelist());
        assertEquals(QENamelistSchema.Type.CHARACTER, calc.getType());
        assertEquals("'scf'", calc.getDefaultText());
        assertFalse(calc.isArray());
        assertEquals(List.of("scf", "ensemble", "nscf", "bands", "relax", "md",
                "vc-relax", "vc-md"), calc.getAcceptedValues(),
                "verified per tag: the exact arm literals of the pw.x calculation switch");
        for (AcceptedValue value : calc.getAcceptedValueDetails()) {
            assertEquals(QENamelistSchema.ALL_VERSIONS_MASK, value.getVersionMask(),
                    "calculation='" + value.getLiteral() + "' is accepted in all of 7.2..7.6");
        }
        assertTrue(calc.acceptsHardValue("'vc-MD'".toLowerCase()));
        assertTrue(calc.acceptsHardValue("\"nscf\""), "matching quotes strip before comparing");
        assertFalse(calc.acceptsHardValue("'SCF'"),
                "uppercase is not folded - the Fortran switch is exact match");
    }

    @Test
    void hardSetsDriftBetweenVersionsExactlyLikeTheTags() {
        Entry diag = QENamelistSchema.lookup(Kind.PW, "DIAGONALIZATION").orElseThrow();
        List<String> at72 = diag.getAcceptedValuesIn("7.2");
        List<String> at75 = diag.getAcceptedValuesIn("7.5");
        List<String> at76 = diag.getAcceptedValuesIn("7.6");
        assertFalse(at72.contains("PPCG") || at72.contains("ParO"),
                "verified per tag: the uppercase aliases do not exist before 7.5");
        assertTrue(at75.contains("PPCG") && at75.contains("ParO"),
                "verified per tag: the ppcg/paro uppercase aliases exist at 7.5");
        assertTrue(at76.contains("PPCG") && at76.contains("ParO"),
                "verified per tag: QE 7.6 keeps the same ppcg/paro uppercase arms");
        assertFalse(at72.contains("direct"), "'direct' joins the switch only at 7.6");
        assertTrue(at76.contains("direct"));
        assertTrue(diag.acceptsHardValueIn("'direct'", "7.6"));
        assertFalse(diag.acceptsHardValueIn("'direct'", "7.2"),
                "an arm absent from the 7.2 switch must reject for 7.2");
    }

    @Test
    void softSwitchesCarryToleratedSetsInsteadOfHardOnes() {
        Entry diskIo = QENamelistSchema.lookup(Kind.PW, "disk_io").orElseThrow();
        assertTrue(diskIo.getAcceptedValues().isEmpty(),
                "disk_io's CASE DEFAULT silently remaps - no hard ground truth exists");
        assertEquals(List.of("high", "medium", "low", "nowf", "none", "minimal"),
                diskIo.getDocumentedValues(),
                "soft literals first, then .def options, de-duplicated");
        assertTrue(diskIo.inDocumentedValues("'nowf'"));
        assertFalse(diskIo.inDocumentedValues("'banana'"));

        Entry verbosity = QENamelistSchema.lookup(Kind.PW, "verbosity").orElseThrow();
        assertTrue(verbosity.getAcceptedValues().isEmpty());
        assertTrue(verbosity.getDocumentedValues().contains("high"));

        Entry findAtpert = QENamelistSchema.lookup(Kind.HP, "find_atpert").orElseThrow();
        assertTrue(findAtpert.getAcceptedValues().isEmpty(),
                "hp.x carries no SELECT CASE ground truth in this window - stated, not invented");
        assertEquals(List.of("1", "2", "3", "4"), findAtpert.getDocumentedValues());
    }

    @Test
    void smearingAndOccupationsRideSetOccupationsGroundTruth() {
        Entry occupations = QENamelistSchema.lookup(Kind.PW, "occupations").orElseThrow();
        assertEquals(List.of("fixed", "smearing", "tetrahedra", "tetrahedra_lin",
                "tetrahedra-lin", "tetrahedra_opt", "tetrahedra-opt", "from_input"),
                occupations.getAcceptedValues(),
                "the literal arms of set_occupations.f90, hyphen aliases included");

        Entry smearing = QENamelistSchema.lookup(Kind.PW, "smearing").orElseThrow();
        assertTrue(smearing.getAcceptedValues().contains("methfessel-paxton"));
        assertTrue(smearing.getAcceptedValues().contains("cold"),
                "the marzari-vanderbilt 'cold' alias is mined from the same switch");
        assertTrue(smearing.getAcceptedValues().contains("F-D"),
                "case variants are legal Fortran spellings the switch really lists");
    }

    @Test
    void versionDriftFactsMatchTheTags() {
        Entry noMetq0 = QENamelistSchema.lookup(Kind.HP, "no_metq0").orElseThrow();
        assertEquals("7.5", noMetq0.addedIn());
        assertEquals("7.5-7.6", noMetq0.versionRange());
        assertFalse(noMetq0.presentIn("7.4"));

        Entry swl = QENamelistSchema.lookup(Kind.PW, "symmetry_with_labels").orElseThrow();
        assertEquals("7.4", swl.addedIn());
        assertEquals("7.4-7.6", swl.versionRange());

        Entry ppcgIter = QENamelistSchema.lookup(Kind.PW, "diago_ppcg_maxiter").orElseThrow();
        assertEquals("7.2-7.4", ppcgIter.versionRange());
        assertEquals("7.4", ppcgIter.lastPresentIn());
        assertFalse(ppcgIter.presentIn("7.5"));

        Entry exxType = QENamelistSchema.lookup(Kind.PW, "exx_type").orElseThrow();
        assertEquals("7.6", exxType.addedIn());
        assertEquals("7.6", exxType.versionRange());

        Entry kx = QENamelistSchema.lookup(Kind.PH, "kx").orElseThrow();
        assertEquals("7.3", kx.addedIn());

        Entry alwaysOn = QENamelistSchema.lookup(Kind.PW, "ibrav").orElseThrow();
        assertEquals("7.2-7.6", alwaysOn.versionRange());
    }

    @Test
    void requiredAndDefaultsMatchTheGrammar() {
        for (String required : new String[] {"ibrav", "nat", "ntyp", "ecutwfc",
                "gcscf_mu", "fcp_mu", "nsolv"}) {
            assertTrue(QENamelistSchema.lookup(Kind.PW, required).orElseThrow().isRequired(),
                    required + " is status{REQUIRED} in the .def grammar");
        }
        Entry ahcNbnd = QENamelistSchema.lookup(Kind.PH, "ahc_nbnd").orElseThrow();
        assertTrue(ahcNbnd.isRequired());

        Entry ecutwfc = QENamelistSchema.lookup(Kind.PW, "ecutwfc").orElseThrow();
        assertEquals(QENamelistSchema.Type.REAL, ecutwfc.getType());
        assertEquals("4 * @ref ecutwfc",
                QENamelistSchema.lookup(Kind.PW, "ecutrho").orElseThrow().getDefaultText(),
                "the expression default survives 7.6's '-kind expr' grammar modifier");
        assertEquals("[7.2-7.5: 1.D-6; 7.6: 1e-06]",
                QENamelistSchema.lookup(Kind.PW, "conv_thr").orElseThrow().getDefaultText(),
                "the conv_thr default notation really drifts at 7.6");
        assertEquals(
                QENamelistSchema.lookup(Kind.PW, "conv_thr").orElseThrow().getDescription(),
                "the declared default text differs between the mined QE versions");
    }

    @Test
    void arrayMetadataAndIndexStrippingWork() {
        Entry hubbardU = QENamelistSchema.lookup(Kind.PW, "Hubbard_U").orElseThrow();
        assertTrue(hubbardU.isArray());
        assertEquals("nsx", hubbardU.getArrayDims());
        assertEquals("SYSTEM", hubbardU.getNamelist());

        assertTrue(QENamelistSchema.lookup(Kind.PW, "hubbard_u(2)").isPresent());
        assertTrue(QENamelistSchema.lookup(Kind.PW, "HUBBARD_u( 2 , 3 )").isPresent(),
                "multi-index suffixes strip to the same base keyword");
        assertEquals("hubbard_u", QENamelistSchema.baseKeyword("Hubbard_U(2)"));
        assertNull(QENamelistSchema.baseKeyword("hubbard_u(2"),
                "a broken index suffix is null, never thrown against");
        assertNull(QENamelistSchema.baseKeyword("   "));
    }

    @Test
    void unknownKeywordsStayUnknown() {
        assertTrue(QENamelistSchema.lookup(Kind.PW, "garbage-zz").isEmpty());
        assertTrue(QENamelistSchema.lookup(Kind.PW, "mixing_drum").isEmpty(),
                "not declared anywhere in the 7.2-7.6 grammar (later-release only)");
        assertTrue(QENamelistSchema.lookup(Kind.PW, "tr2_ph").isEmpty(),
                "ph.x keywords are not pw.x keywords");
        assertTrue(QENamelistSchema.lookup(Kind.PH, "tr2_ph").isPresent());
        assertTrue(QENamelistSchema.lookup(Kind.PW, null).isEmpty());
    }

    @Test
    void namelistAndOrderingApisStayConsistent() {
        List<Entry> system = QENamelistSchema.namelist(Kind.PW, "system");
        assertFalse(system.isEmpty());
        for (Entry entry : system) {
            assertEquals("SYSTEM", entry.getNamelist());
            assertEquals(Kind.PW, entry.getKind());
        }
        assertEquals(80, QENamelistSchema.namelist(Kind.PH, "INPUTPH").size());
        assertTrue(QENamelistSchema.namelist(Kind.PW, "WANNIER_AC").stream()
                .map(Entry::getName).collect(Collectors.toList()).contains("plot_wannier"));
        assertThrows(NullPointerException.class, () -> QENamelistSchema.entries(null));
        assertThrows(NullPointerException.class,
                () -> QENamelistSchema.entries(Kind.PW, null));
    }
}
