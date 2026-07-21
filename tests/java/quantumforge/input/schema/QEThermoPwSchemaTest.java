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
        // window union 234 (master column alone = 232; two tag-only keywords
        // were removed from the NAMELIST before master), `what` leading.
        assertEquals(234, QEThermoPwSchema.entryCount(), "7-column window union, pinned");
        assertEquals(List.of("2.0.0", "2.0.1", "2.0.2", "2.0.3", "2.1.0", "2.1.1", "master"),
                QEThermoPwSchema.VERSIONS, "thermo_pw-tag-indexed window, never QE-paired");
        assertEquals(0x7F, QEThermoPwSchema.ALL_VERSIONS_MASK);
        Entry first = QEThermoPwSchema.entries().get(0);
        assertEquals("what", first.getName());
        assertEquals(Type.CHARACTER, first.getType());
        assertEquals("' '", first.getDefaultText());
        assertEquals(0x7F, first.getVersionMask(), "what is declared in every version");

        Entry tmin = QEThermoPwSchema.lookup("tmin").orElseThrow();
        assertEquals(Type.REAL, tmin.getType(), "1.0_DP infers REAL (kind suffix is literal)");
        assertEquals("1.0_DP", tmin.getDefaultText());
        assertTrue(tmin.getGroups().contains("temperature and pressure"),
                tmin.getGroups().toString());

        Entry ltau = QEThermoPwSchema.lookup("ltau_from_file").orElseThrow();
        assertTrue(ltau.getGroups().contains("mur_lc") && ltau.getGroups().contains("mur_lc_t"),
                "declared under two groups, both kept in order: " + ltau.getGroups());
        assertEquals(0x40, ltau.getVersionMask(),
                "ltau_from_file exists ONLY at the master column");
        assertFalse(ltau.presentIn("2.1.1"));
        assertTrue(ltau.presentIn("master"));
    }

    @Test
    void presenceMasksCarryTheRealDrift() {
        // added at 2.1.0 (the gruneisen block):
        Entry gen = QEThermoPwSchema.lookup("lgruneisen_gen").orElseThrow();
        assertEquals(0x70, gen.getVersionMask(), "2.1.0, 2.1.1, master - pinned");
        assertFalse(gen.presentIn("2.0.3"));
        assertTrue(gen.presentIn("2.1.0"));
        // removed from the NAMELIST before master:
        Entry epsilon0 = QEThermoPwSchema.lookup("epsilon_0").orElseThrow();
        assertEquals(0x3F, epsilon0.getVersionMask(), "tag-only keyword, pinned");
        assertFalse(epsilon0.presentIn("master"));
        assertTrue(epsilon0.presentIn("2.1.1"));
        assertEquals("2.1.1", QEThermoPwSchema.lastPresentLabel(0x3F));
        // 202 keywords stable across the whole window:
        long stable = QEThermoPwSchema.entries().stream()
                .filter(e -> e.getVersionMask() == 0x7F).count();
        assertEquals(202, stable, "stable-everywhere keyword count, pinned");
    }

    @Test
    void defaultAndTypeDriftIsVerbatimPerTag() {
        Entry oldEc = QEThermoPwSchema.lookup("old_ec").orElseThrow();
        assertEquals(0x7F, oldEc.getVersionMask());
        assertEquals(Type.INTEGER, oldEc.getType());
        assertEquals("0", oldEc.getDefaultText(), "newest reading since 2.0.1");
        assertEquals(Type.LOGICAL, oldEc.typeAt("2.0.0"),
                "at 2.0.0 the procedural default was .TRUE.");
        assertEquals(".TRUE.", oldEc.defaultAt("2.0.0"));
        assertEquals(Type.INTEGER, oldEc.typeAt("2.0.1"));
        assertEquals("0", oldEc.defaultAt("2.0.1"));
        assertNullOutsideWindow(oldEc);

        Entry epsilon0 = QEThermoPwSchema.lookup("epsilon_0").orElseThrow();
        assertEquals(Type.REAL, epsilon0.typeAt("2.0.3"), "0.0_DP at the tags");
        assertEquals("0.0_DP", epsilon0.defaultAt("2.0.3"));
        assertTrue(epsilon0.getDriftByVersion().containsKey("2.0.0"),
                "the per-tag drift rows are kept verbatim");
    }

    private static void assertNullOutsideWindow(Entry entry) {
        org.junit.jupiter.api.Assertions.assertNull(entry.defaultAt("1.9.0"));
        org.junit.jupiter.api.Assertions.assertNull(entry.defaultAt(null));
        org.junit.jupiter.api.Assertions.assertEquals(Type.UNKNOWN, entry.typeAt("9.9.9"));
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
        assertEquals(30, values.size(), "window union of part-1 dispatch arms");
        for (String expected : List.of("scf", "scf_disp", "mur_lc", "mur_lc_t",
                "elastic_constants_geo", "piezoelectric_tensor_geo", "polarization_geo",
                "scf_magnetoelectric_tensor", "mur_lc_magnetic_susceptibility")) {
            assertTrue(values.contains(expected), expected + " missing: " + values);
        }
        assertFalse(values.contains("scf_elastic"), "prefix matches are NOT accepted");
        assertFalse(values.contains("SCF"), "Fortran string equality is case-significant");
        // per-version presence: 22 stable values + 8 magnetic additions at master
        assertEquals(0x7F, QEThermoPwSchema.whatMask("mur_lc_t"));
        assertEquals(0x40, QEThermoPwSchema.whatMask("scf_magnetoelectric_tensor"));
        assertTrue(QEThermoPwSchema.whatPresentIn("mur_lc_t", "2.0.0"));
        assertFalse(QEThermoPwSchema.whatPresentIn("scf_magnetoelectric_tensor", "2.1.1"),
                "the 8 magnetic what values dispatch only at the master column");
        assertTrue(QEThermoPwSchema.whatPresentIn("scf_magnetoelectric_tensor", "master"));
        assertEquals(0, QEThermoPwSchema.whatMask("nonsense"));
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

    @Test
    void consistencyFactsAreVersionMasked() {
        // gruneisen consistency rules exist only from 2.1.0 on:
        assertTrue(QEThermoPwSchema.factsForVersion("2.1.0").stream()
                .anyMatch(f -> f.contains("lgruneisen_gen requires lmurn=.FALSE.")),
                QEThermoPwSchema.factsForVersion("2.1.0").toString());
        assertTrue(QEThermoPwSchema.factsForVersion("2.0.3").stream()
                .noneMatch(f -> f.contains("lgruneisen_gen")),
                "2.0.3 predates the gruneisen block");
        // the GPU-bound many_k rule was removed at 2.1.0 (mask 0x0F), while
        // the nproc=npool many_k rule is stable across the whole window:
        assertTrue(QEThermoPwSchema.factsForVersion("2.0.3").stream()
                .anyMatch(f -> f.contains("many_k requires as many GPU as CPU")),
                "2.0.x still carries the GPU-bound many_k rule");
        assertTrue(QEThermoPwSchema.factsForVersion("master").stream()
                .noneMatch(f -> f.contains("requires as many GPU as CPU")),
                "the GPU-bound rule was removed at 2.1.0");
        assertTrue(QEThermoPwSchema.factsForVersion("master").stream()
                .anyMatch(f -> f.contains("many_k requires nproc=npool")),
                "the nproc=npool rule survives everywhere (0x7F)");
        assertEquals(0x0F, QEThermoPwSchema.maskedFacts().stream()
                .filter(f -> f.getText().contains("requires as many GPU as CPU"))
                .findFirst().orElseThrow().getVersionMask(), "2.0.0..2.0.3 only, pinned");
        // master view keeps the gruneisen rule:
        assertTrue(QEThermoPwSchema.consistencyFacts().stream()
                .anyMatch(f -> f.contains("lgruneisen_gen requires what=''mur_lc_t''")),
                "master-visible facts include the what-binding rule");
    }
}
