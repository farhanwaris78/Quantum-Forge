/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import quantumforge.input.schema.QEThermoPwSchema.Entry;
import quantumforge.input.schema.QEThermoPwSchema.Type;

class QEThermoPwSchemaTest {

    @Test
    void minedCountsAndAnchorEntriesMatchTheFortranDeclaration() {
        // thermo_readin.f90 NAMELIST / input_thermo / ... (commit-provenance in
        // QEThermoPwSchemaData): 232 unique keywords, `what` leading.
        assertEquals(232, QEThermoPwSchema.entryCount());
        Entry first = QEThermoPwSchema.entries().get(0);
        assertEquals("what", first.getName());
        assertEquals(Type.CHARACTER, first.getType());
        assertEquals("' '", first.getDefaultText());

        Entry tmin = QEThermoPwSchema.lookup("tmin").orElseThrow();
        assertEquals(Type.REAL, tmin.getType(), "1.0_DP infers REAL (kind suffix is literal)");
        assertEquals("1.0_DP", tmin.getDefaultText());
        assertTrue(tmin.getGroups().contains("temperature and pressure"),
                tmin.getGroups().toString());

        Entry ltau = QEThermoPwSchema.lookup("ltau_from_file").orElseThrow();
        assertTrue(ltau.getGroups().contains("mur_lc") && ltau.getGroups().contains("mur_lc_t"),
                "declared under two groups, both kept in order: " + ltau.getGroups());
    }

    @Test
    void lookupIsCaseInsensitiveAndStripsArrayIndexHonest() {
        assertTrue(QEThermoPwSchema.lookup("CELldM_PH").isPresent());
        assertTrue(QEThermoPwSchema.lookup("temp_plot(3)").isPresent(),
                "array index stripped to the base keyword");
        assertTrue(QEThermoPwSchema.lookup("not_a_thermo_keyword").isEmpty());
        assertTrue(QEThermoPwSchema.lookup(null).isEmpty());
    }

    @Test
    void whatHardSetMatchesTheDispatchArms() {
        List<String> values = QEThermoPwSchema.whatAcceptedValues();
        assertEquals(30, values.size());
        for (String expected : List.of("scf", "scf_disp", "mur_lc", "mur_lc_t",
                "elastic_constants_geo", "piezoelectric_tensor_geo", "polarization_geo",
                "scf_magnetoelectric_tensor", "mur_lc_magnetic_susceptibility")) {
            assertTrue(values.contains(expected), expected + " missing: " + values);
        }
        assertFalse(values.contains("scf_elastic"), "prefix matches are NOT accepted");
        assertFalse(values.contains("SCF"), "Fortran string equality is case-significant");
    }

    @Test
    void groupNavigationIsAdvisoryAndVerbatim() {
        List<Entry> murLc = QEThermoPwSchema.entriesGroupedUnder("mur_lc");
        assertFalse(murLc.isEmpty());
        assertTrue(murLc.stream().anyMatch(e -> e.getName().equals("ngeo")),
                "the mur_lc-cluster keywords surface under their code group");
        assertTrue(QEThermoPwSchema.entriesGroupedUnder("no_such_what").isEmpty(),
                "unknown what groups to nothing - never fabricated");
        assertFalse(QEThermoPwSchema.consistencyFacts().isEmpty(),
                "the verbatim consistency fact lines ride along");
    }
}
