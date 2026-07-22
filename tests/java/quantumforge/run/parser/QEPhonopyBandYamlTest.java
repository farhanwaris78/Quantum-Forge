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
import quantumforge.run.parser.QEPhonopyBandYaml.BandYaml;
import quantumforge.run.parser.QEPhonopyBandYaml.Segment;

/**
 * Pins for {@link QEPhonopyBandYaml}: the band.yaml grammar as written by
 * upstream {@code BandStructure._write_yaml} (phonopy commit
 * 3a3e0f099da5de2556e75d72ea89b3bb22c8e97e). The embedded documents keep the
 * writer's exact line shapes ({@code nqpoint: %-7d}, {@code - q-position: [
 * %12.7f, ... ]}, {@code   distance: %12.7f}, {@code   - # k} +
 * {@code     frequency: %15.10f}); only the numbers themselves are
 * constructed small for the test. Segment distances use STRICTLY resetting
 * runs so the distance-reset fallback boundary is exercised exactly where
 * phonopy-bandplot would look for it.
 */
class QEPhonopyBandYamlTest {

    /** Constructed band.yaml: rows 0-3 run A (0.0..0.3), rows 4-5 run B (0.0..0.132),
     *  row 6+ run C - header-segment variant claims [4, rows-4]. */
    private static String doc(int rows, boolean headerSegments, boolean labels) {
        StringBuilder yaml = new StringBuilder();
        yaml.append("nqpoint: ").append(String.format("%-7d",
                java.lang.Math.max(rows, 4))).append('\n');
        yaml.append("npath:   2      \n");
        if (headerSegments) {
            yaml.append("segment_nqpoint:\n- 4\n- ").append(rows - 4).append('\n');
        }
        if (labels) {
            yaml.append("labels:\n- [ 'G', 'X' ]\n- [ 'X', 'M' ]\n");
        }
        yaml.append("reciprocal_lattice:\n"
                + "- [  0.31111111,  0.00000000,  0.00000000 ] # a*\n"
                + "- [  0.00000000,  0.31111111,  0.00000000 ] # b*\n"
                + "- [  0.00000000,  0.00000000,  0.31111111 ] # c*\n");
        yaml.append("natom:   1    \n");
        yaml.append("\nphonon:\n\n");
        double[][] freqs = {
                {0.0, 4.5, 4.5}, {0.0, 4.6, 4.6}, {0.0, 4.7, 4.7}, {0.0, 4.8, 4.8},
                {0.3, 0.4, -0.5}, {0.2, 4.9, -0.2}, {0.0, 4.4, 4.4}, {0.0, 4.3, 4.3}};
        double[] dists = {0.0, 0.1, 0.2, 0.3, 0.0, 0.132, 0.0, 0.0876543};
        for (int i = 0; i < rows; i++) {
            yaml.append(String.format(java.util.Locale.ROOT,
                    "- q-position: [ %12.7f, %12.7f, %12.7f ]\n",
                    i < 4 ? i * 0.1666667 : 0.5, i < 4 ? 0.0
                            : 0.0833333 * (i - 3), 0.0));
            yaml.append(String.format(java.util.Locale.ROOT, "  distance: %12.7f\n",
                    dists[i]));
            yaml.append("  band:\n");
            double[] sorted = freqs[i].clone();
            java.util.Arrays.sort(sorted);
            for (int k = 0; k < sorted.length; k++) {
                yaml.append("  - # ").append(k + 1).append('\n');
                yaml.append(String.format(java.util.Locale.ROOT,
                        "    frequency: %15.10f\n", sorted[k]));
            }
            yaml.append('\n');
        }
        return yaml.toString();
    }

    @Test
    void testFullDocumentWithHeaderSegmentsAndLabels() {
        OperationResult<BandYaml> result = QEPhonopyBandYaml.parseText(
                doc(6, true, true), "band.yaml");
        assertTrue(result.isSuccess(), () -> result.getMessage());
        assertEquals("PHONOPY_BAND_OK", result.getCode());
        BandYaml yaml = result.getValue().orElseThrow();
        assertEquals(6, yaml.getNqpoint());
        assertEquals(Integer.valueOf(2), yaml.getNpath());
        assertEquals(3, yaml.getBandCount());
        assertEquals(2, yaml.getSegments().size());
        Segment first = yaml.getSegments().get(0);
        assertEquals(4, first.getRows().size());
        assertEquals("G", first.getStartLabel());
        assertEquals("X", first.getEndLabel());
        assertEquals(0.0, first.getStartDistance(), 1e-12);
        assertEquals(0.3, first.getEndDistance(), 1e-9);
        Segment second = yaml.getSegments().get(1);
        assertEquals(2, second.getRows().size());
        assertEquals("X", second.getStartLabel());
        assertEquals("M", second.getEndLabel());
        assertEquals(0.132, yaml.getTotalDistance(), 1e-9);
        assertEquals(-0.5, yaml.getMinFrequency(), 1e-12);
        assertEquals(4.9, yaml.getMaxFrequency(), 1e-12);
        assertEquals(2, yaml.getNegativeFrequencyCount(),
                "negative values = imaginary modes, counted and named");
        assertEquals(0, yaml.getPartialRowsHeld());
        assertTrue(yaml.getFrequencyUnitNote().contains("THz"),
                "the unit is STATED as the phonopy default, never asserted");
        assertTrue(yaml.getSegmentMethod().contains("segment_nqpoint"));
        assertTrue(first.getRows().get(3).getQPosition()[0] > 0.49,
                "fourth row of segment 1 is the X point");
        assertTrue(QEPhonopyBandYaml.describe(yaml).contains("imaginary"));
    }

    @Test
    void testDistanceResetFallbackWhenHeaderSegmentsAbsent() {
        OperationResult<BandYaml> result = QEPhonopyBandYaml.parseText(
                doc(7, false, true), "band.yaml");
        assertTrue(result.isSuccess(), () -> result.getMessage());
        BandYaml yaml = result.getValue().orElseThrow();
        assertEquals(3, yaml.getSegments().size(),
                "three strictly resetting distance runs are phonopy-bandplot's own"
                        + " boundary signal");
        assertEquals(4, yaml.getSegments().get(0).getRows().size());
        assertEquals(2, yaml.getSegments().get(1).getRows().size());
        assertEquals(1, yaml.getSegments().get(2).getRows().size());
        assertNull(yaml.getSegments().get(2).getStartLabel(),
                "the two label pairs cover segments 1-2; segment 3 stays honestly"
                        + " unlabeled");
        assertTrue(yaml.getSegmentMethod().contains("distance-reset"));
        assertEquals(Integer.valueOf(2), yaml.getNpath(),
                "npath is kept as the HEADER's claim (2), never rewritten by the"
                        + " detected 3");
    }

    @Test
    void testLivePartialTrailingRowHeldBack() {
        String full = doc(6, true, true);
        // a live write mid-way through the last row's frequency list
        String partial = full.replace("    frequency:    4.9000000000\n", "");
        OperationResult<BandYaml> result = QEPhonopyBandYaml.parseText(partial,
                "band.yaml");
        assertTrue(result.isSuccess(), () -> result.getMessage());
        BandYaml yaml = result.getValue().orElseThrow();
        assertEquals(1, yaml.getPartialRowsHeld(),
                "only the TRAILING short band list is held back");
        assertEquals(5, yaml.getParsedRowCount());
        assertEquals(1, yaml.getSegments().get(1).getRows().size());
    }

    @Test
    void testPartialWhileLaterPathsStillWriting() {
        String full = doc(6, true, true);
        String anchor = String.format(java.util.Locale.ROOT,
                "- q-position: [ %12.7f", 0.5);
        int cut = full.indexOf(anchor);
        assertTrue(cut > 0, "the row-5 anchor exists in the constructed document");
        String writing = full.substring(0, cut);
        OperationResult<BandYaml> result = QEPhonopyBandYaml.parseText(writing,
                "band.yaml");
        assertFalse(result.isSuccess());
        assertEquals("PHONOPY_BAND_PARTIAL", result.getCode(),
                "4 of 6 promised rows parsed, no trailing partial - still writing");
    }

    @Test
    void testHeaderAndShapeRefusals() {
        assertEquals("PHONOPY_BAND_INPUT", QEPhonopyBandYaml.parseText(null, "x")
                .getCode());
        OperationResult<BandYaml> header = QEPhonopyBandYaml.parseText(
                "thermal_properties:\n- temperature: 0\n", "thermal.yaml");
        assertFalse(header.isSuccess());
        assertEquals("PHONOPY_BAND_HEADER", header.getCode());

        String noPhonon = doc(6, true, true);
        noPhonon = noPhonon.substring(0, noPhonon.indexOf("\nphonon:"));
        OperationResult<BandYaml> partial = QEPhonopyBandYaml.parseText(noPhonon,
                "band.yaml");
        assertEquals("PHONOPY_BAND_PARTIAL", partial.getCode(),
                "header ok, no phonon: block yet - a live run is writing it");

        String badFreq = doc(6, true, true).replace(
                "    frequency:    4.6000000000\n", "    frequency: nan\n");
        OperationResult<BandYaml> shape = QEPhonopyBandYaml.parseText(badFreq,
                "band.yaml");
        assertFalse(shape.isSuccess());
        assertEquals("PHONOPY_BAND_SHAPE", shape.getCode(),
                "a nan frequency row is corrupt, never skipped");

        // non-uniform band count mid-file is NOT a partial - it is corrupt
        String nonUniform = doc(6, true, true).replaceFirst(
                java.util.regex.Pattern.quote(
                        "  - # 3\n    frequency:    4.6000000000\n"),
                java.util.regex.Matcher.quoteReplacement(""));
        OperationResult<BandYaml> nonUniformResult = QEPhonopyBandYaml.parseText(
                nonUniform, "band.yaml");
        assertFalse(nonUniformResult.isSuccess());
        assertEquals("PHONOPY_BAND_SHAPE", nonUniformResult.getCode());

        String distanceAnchor = String.format(java.util.Locale.ROOT,
                "  distance: %12.7f\n", 0.1);
        String noDistance = doc(6, true, true).replace(distanceAnchor, "");
        assertFalse(noDistance.equals(doc(6, true, true)),
                "the distance-anchor splice actually removed a row");
        OperationResult<BandYaml> noDistanceResult = QEPhonopyBandYaml.parseText(
                noDistance, "band.yaml");
        assertFalse(noDistanceResult.isSuccess());
        assertEquals("PHONOPY_BAND_SHAPE", noDistanceResult.getCode());
    }

    @Test
    void testGroupVelocityAndEigenvectorPayloadCountedNotStored() {
        String full = doc(6, true, true);
        String anchor = "    frequency:    0.0000000000\n";
        int at = full.indexOf(anchor);
        String withPayload = full.substring(0, at + anchor.length())
                + "    group_velocity: [   0.0000000,   0.0000000,   0.0000000 ]\n"
                + "    eigenvector:\n"
                + "    - # atom 1\n"
                + "      - [  1.00000000000000,  0.00000000000000 ]\n"
                + "      - [  0.00000000000000,  0.00000000000000 ]\n"
                + "      - [  0.00000000000000,  0.00000000000000 ]\n"
                + full.substring(at + anchor.length());
        OperationResult<BandYaml> result = QEPhonopyBandYaml.parseText(withPayload,
                "band.yaml");
        assertTrue(result.isSuccess(), () -> result.getMessage());
        BandYaml yaml = result.getValue().orElseThrow();
        assertEquals(1, yaml.getGroupVelocityEntries());
        assertEquals(1, yaml.getEigenvectorEntries());
        assertEquals(6, yaml.getParsedRowCount(),
                "the eigenvector payload never breaks row assembly");
        assertEquals(-0.5, yaml.getMinFrequency(), 1e-12);
    }
}
