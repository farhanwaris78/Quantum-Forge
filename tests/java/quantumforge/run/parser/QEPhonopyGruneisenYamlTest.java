/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.run.parser.QEPhonopyGruneisenYaml.GammaQ;
import quantumforge.run.parser.QEPhonopyGruneisenYaml.GruneisenYaml;

/**
 * Pins {@link QEPhonopyGruneisenYaml} against the upstream writers
 * (gruneisen/band_structure.py and gruneisen/mesh.py _write_yaml @ 3a3e0f09)
 * - format-verbatim fixtures incl. the '%15.10f' rows, the nested
 * 'path:'/'- nqpoint:' band layout, the 'mesh: [...]'/'multiplicity:' mesh
 * layout, plus the doc-stated doctrines (3-volume finite difference,
 * Gamma-divergence plot caveat, unordered band-mode frequencies).
 */
class QEPhonopyGruneisenYamlTest {

    private static String bandYaml() {
        return "nqpoint: 6      \n"
                + "npath:   2      \n"
                + "segment_nqpoint:\n"
                + "- 3\n"
                + "- 3\n"
                + "labels:\n"
                + "- [ 'G', 'X' ]\n"
                + "- [ 'X', 'M' ]\n"
                + "reciprocal_lattice:\n"
                + "- [   0.17948718,   0.00000000,   0.00000000 ] # a*\n"
                + "- [   0.00000000,   0.17948718,   0.00000000 ] # b*\n"
                + "- [   0.00000000,   0.00000000,   0.17948718 ] # c*\n"
                + "natom: 2      \n"
                + " 1.0\n"
                + "   5.6903014761756712 0 0\n"
                + "path:\n"
                + "\n"
                + "- nqpoint: 3\n"
                + "  phonon:\n"
                + "  - q-position: [  0.0000000,  0.0000000,  0.0000000 ]\n"
                + "    distance:   0.0000000\n"
                + "    band:\n"
                + "    - # 1\n"
                + "      gruneisen:   -12.3456789012\n"
                + "      frequency:    -0.0234567890\n"
                + "    - # 2\n"
                + "      gruneisen:     1.2345678901\n"
                + "      frequency:     4.5678901234\n"
                + "  - q-position: [  0.2500000,  0.0000000,  0.0000000 ]\n"
                + "    distance:   0.2500000\n"
                + "    band:\n"
                + "    - # 1\n"
                + "      gruneisen:     1.5555555555\n"
                + "      frequency:     5.0000000000\n"
                + "    - # 2\n"
                + "      gruneisen:     0.6666666666\n"
                + "      frequency:     6.0000000000\n"
                + "  - q-position: [  0.5000000,  0.0000000,  0.0000000 ]\n"
                + "    distance:   0.5000000\n"
                + "    band:\n"
                + "    - # 1\n"
                + "      gruneisen:     1.1111111111\n"
                + "      frequency:     5.5000000000\n"
                + "    - # 2\n"
                + "      gruneisen:     0.2222222222\n"
                + "      frequency:     6.5000000000\n"
                + "- nqpoint: 2\n"
                + "  phonon:\n"
                + "  - q-position: [  0.5000000,  0.0000000,  0.0000000 ]\n"
                + "    distance:   0.5000000\n"
                + "    band:\n"
                + "    - # 1\n"
                + "      gruneisen:     2.0000000000\n"
                + "      frequency:     7.0000000000\n"
                + "    - # 2\n"
                + "      gruneisen:     0.1000000000\n"
                + "      frequency:     8.0000000000\n";
    }

    private static String meshYaml() {
        return "mesh: [     4,     4,     4 ]\n"
                + "nqpoint: 2\n"
                + "reciprocal_lattice:\n"
                + "- [   0.17948718,   0.00000000,   0.00000000 ] # a*\n"
                + "- [   0.00000000,   0.17948718,   0.00000000 ] # b*\n"
                + "- [   0.00000000,   0.00000000,   0.17948718 ] # c*\n"
                + "natom:   2      \n"
                + " 1.0\n"
                + "phonon:\n"
                + "- q-position: [  0.1250000,  0.1250000,  0.1250000 ]\n"
                + "  multiplicity: 2\n"
                + "  band:\n"
                + "  - # 1\n"
                + "    gruneisen:     1.2345678901\n"
                + "    frequency:     4.5678901234\n"
                + "  - # 2\n"
                + "    gruneisen:    -0.5000000000\n"
                + "    frequency:     5.1111111111\n"
                + "- q-position: [  0.0000000,  0.0000000,  0.0000000 ]\n"
                + "  multiplicity: 1\n"
                + "  band:\n"
                + "  - # 1\n"
                + "    gruneisen:    99.0000000000\n"
                + "    frequency:    -0.0000000001\n"
                + "  - # 2\n"
                + "    gruneisen:     1.0000000000\n"
                + "    frequency:     6.2222222222\n";
    }

    @Test
    void bandModeFullDocumentVerbatim() {
        OperationResult<GruneisenYaml> result =
                QEPhonopyGruneisenYaml.parseText(bandYaml(), "gruneisen.yaml");
        assertTrue(result.isSuccess(), () -> result.getMessage());
        assertEquals("PHONOPY_GRUNEISEN_OK", result.getCode());
        GruneisenYaml yaml = result.getValue().orElseThrow();
        assertEquals(QEPhonopyGruneisenYaml.Mode.BAND, yaml.getMode());
        assertEquals(Integer.valueOf(6), yaml.getHeaderNqpoint());
        assertEquals(Integer.valueOf(2), yaml.getHeaderNpath());
        assertEquals(2, yaml.getSegments().size());
        assertEquals(3, yaml.getSegments().get(0).getDeclaredNqpoint());
        assertEquals(3, yaml.getSegments().get(0).getRows().size());
        // declared 2 vs parsed 1 in segment 2: REPORTED, never guessed
        assertEquals(2, yaml.getSegments().get(1).getDeclaredNqpoint());
        assertEquals(1, yaml.getSegments().get(1).getRows().size());
        assertTrue(yaml.getNotes().stream().anyMatch(n
                -> n.contains("segment 2") && n.contains("REPORTED")));
        // header nqpoint 6 vs 4 parsed: reported verbatim
        assertTrue(yaml.getNotes().stream().anyMatch(n
                -> n.contains("nqpoint: 6") && n.contains("4")));
        // labels verbatim
        assertEquals("G", yaml.getLabels().get(0)[0]);
        assertEquals("X", yaml.getLabels().get(0)[1]);
        assertEquals("M", yaml.getLabels().get(1)[1]);
        assertEquals("G", yaml.getSegments().get(0).getStartLabel());
        assertEquals("X", yaml.getSegments().get(0).getEndLabel());
        // values verbatim
        GammaQ first = yaml.getSegments().get(0).getRows().get(0);
        assertEquals(-12.3456789012, first.getBands().get(0).getGruneisen(), 1e-15);
        assertEquals("-12.3456789012", first.getBands().get(0).getGruneisenText());
        assertEquals(-0.0234567890, first.getBands().get(0).getFrequency(), 1e-15);
        assertTrue(first.getBands().get(0).isImaginaryFrequency());
        assertEquals(2, yaml.getBandCount());
        assertEquals(1, yaml.getNegativeGammaCount());
        double[] extent = yaml.gammaExtent();
        assertEquals(-12.3456789012, extent[0], 1e-15);
        assertEquals(2.0, extent[1], 1e-15);
        // Gamma-point doctrine in the verdict
        assertEquals(1, yaml.getGammaPointRowCount());
        assertTrue(result.getMessage().contains("Gamma-point"));
        // the doc's band-crossing + 3-volume statements ride along
        assertTrue(yaml.getNotes().stream().anyMatch(n
                -> n.contains("may NOT be ordered")));
        assertTrue(yaml.getNotes().stream().anyMatch(n
                -> n.contains("THREE volumes")));
    }

    @Test
    void meshModeFullDocumentVerbatim() {
        OperationResult<GruneisenYaml> result =
                QEPhonopyGruneisenYaml.parseText(meshYaml(), "gruneisen_mesh.yaml");
        assertTrue(result.isSuccess(), () -> result.getMessage());
        GruneisenYaml yaml = result.getValue().orElseThrow();
        assertEquals(QEPhonopyGruneisenYaml.Mode.MESH, yaml.getMode());
        assertEquals(4, yaml.getMesh()[0]);
        assertEquals(2, yaml.getMeshRows().size());
        assertEquals(Integer.valueOf(2), yaml.getHeaderNqpoint());
        GammaQ first = yaml.getMeshRows().get(0);
        assertEquals(0.125, first.getQ()[0], 1e-12);
        assertEquals(Integer.valueOf(2), first.getMultiplicity());
        assertNull(first.getDistance());
        assertEquals(-0.5, first.getBands().get(1).getGruneisen(), 1e-15);
        // multiplicities 2+1=3 vs mesh product 64: DIFFERS - reported, never corrected
        assertTrue(yaml.getNotes().stream().anyMatch(n
                -> n.contains("sum 3") && n.contains("64") && n.contains("DIFFERS")),
                yaml.getNotes().toString());
        // the second row is the Gamma point (with a diverging-looking gamma)
        assertEquals(1, yaml.getGammaPointRowCount());
        assertTrue(result.getMessage().contains("Gamma-point"));
        assertTrue(yaml.getMeshRows().get(1).getBands().get(0)
                .isImaginaryFrequency()); // -0.0000000001 < 0
    }

    @Test
    void modeDecidedByContentNotName() {
        // mesh grammar inside the BAND-mode file name: content wins
        OperationResult<GruneisenYaml> renamed =
                QEPhonopyGruneisenYaml.parseText(meshYaml(), "gruneisen.yaml");
        assertTrue(renamed.isSuccess());
        assertEquals(QEPhonopyGruneisenYaml.Mode.MESH,
                renamed.getValue().orElseThrow().getMode());
        assertTrue(renamed.getValue().orElseThrow().getNotes().stream()
                .anyMatch(n -> n.contains("file name plays no part")));

        // BOTH markers -> SHAPE (no upstream writer emits that)
        OperationResult<GruneisenYaml> both = QEPhonopyGruneisenYaml.parseText(
                "mesh: [     4,     4,     4 ]\npath:\n", "x.yaml");
        assertFalse(both.isSuccess());
        assertEquals("PHONOPY_GRUNEISEN_SHAPE", both.getCode());

        // neither marker -> HEADER refusal
        OperationResult<GruneisenYaml> neither = QEPhonopyGruneisenYaml.parseText(
                "nqpoint: 5\nfoo: bar\n", "x.yaml");
        assertFalse(neither.isSuccess());
        assertEquals("PHONOPY_GRUNEISEN_HEADER", neither.getCode());
        assertEquals("PHONOPY_GRUNEISEN_EMPTY",
                QEPhonopyGruneisenYaml.parseText(" \n \n", "e").getCode());
    }

    @Test
    void shapeRefusalsNeverSkipped() {
        // malformed q row mid-file
        String bad = bandYaml().replace(
                "  - q-position: [  0.2500000,  0.0000000,  0.0000000 ]",
                "  - q-position: garbage");
        assertEquals("PHONOPY_GRUNEISEN_SHAPE",
                QEPhonopyGruneisenYaml.parseText(bad, "b").getCode());
        // non-uniform band count across rows
        String nonUniform = bandYaml().replace(
                "    - # 2\n      gruneisen:     0.6666666666\n"
                + "      frequency:     6.0000000000\n", "");
        OperationResult<GruneisenYaml> nu =
                QEPhonopyGruneisenYaml.parseText(nonUniform, "nu");
        assertFalse(nu.isSuccess());
        assertEquals("PHONOPY_GRUNEISEN_SHAPE", nu.getCode());
        assertTrue(nu.getMessage().contains("uniform"));
        // band entry without its pair
        String broken = bandYaml().replace(
                "      frequency:     4.5678901234\n", "      omega: 1\n");
        assertEquals("PHONOPY_GRUNEISEN_SHAPE",
                QEPhonopyGruneisenYaml.parseText(broken, "br").getCode());
        // mesh row missing multiplicity
        String noMult = meshYaml().replace("  multiplicity: 2\n", "");
        assertEquals("PHONOPY_GRUNEISEN_SHAPE",
                QEPhonopyGruneisenYaml.parseText(noMult, "nm").getCode());
        // 'path:' with zero complete segments
        assertEquals("PHONOPY_GRUNEISEN_PARTIAL",
                QEPhonopyGruneisenYaml.parseText("nqpoint: 1\npath:\n", "p").getCode());
    }

    @Test
    void livePartialTrailingEntryHeld() {
        String live = bandYaml()
                + "  - q-position: [  0.7500000,  0.0000000,  0.0000000 ]\n"
                + "    distance:   0.7500000\n"
                + "    band:\n"
                + "    - # 1\n"
                + "      gruneisen:     1.0";
        OperationResult<GruneisenYaml> result =
                QEPhonopyGruneisenYaml.parseText(live, "live");
        assertTrue(result.isSuccess(), () -> result.getMessage());
        GruneisenYaml yaml = result.getValue().orElseThrow();
        assertEquals(1, yaml.getPartialRowsHeld());
        assertEquals(4, yaml.flatRows().size());
        assertTrue(yaml.getNotes().stream().anyMatch(n -> n.contains("held back")));
        assertTrue(result.getMessage().contains("1 trailing partial held"));
    }

    @Test
    void describeRendersTheCensus() {
        GruneisenYaml yaml = QEPhonopyGruneisenYaml.parseText(bandYaml(), "g")
                .getValue().orElseThrow();
        String text = QEPhonopyGruneisenYaml.describe(yaml);
        assertTrue(text.contains("mode: BAND"));
        assertTrue(text.contains("gamma extent: [-12.345679, 2.000000]"));
        assertTrue(text.contains("negative gamma entries: 1"));
        GruneisenYaml mesh = QEPhonopyGruneisenYaml.parseText(meshYaml(), "m")
                .getValue().orElseThrow();
        assertTrue(QEPhonopyGruneisenYaml.describe(mesh).contains("mesh 4x4x4"));
    }
}
