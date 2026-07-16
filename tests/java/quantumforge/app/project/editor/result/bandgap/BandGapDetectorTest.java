package quantumforge.app.project.editor.result.bandgap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BandGapDetectorTest {
    @Test
    void detectsDirectAndIndirectIntegerFilledGaps() {
        BandGapDetector direct = new BandGapDetector();
        direct.detectFromBands(new double[][] {
                {-2.0, -1.0, -1.5},
                {-0.7, -0.2, -0.5},
                { 1.0,  0.8,  1.2}
        }, null, 4, 0.3);
        assertEquals(BandGapDetector.Classification.GAPPED, direct.getClassification());
        assertEquals(1.0, direct.getBandGap(), 1.0e-12);
        assertTrue(direct.isDirectKnown());
        assertTrue(direct.isDirect());

        BandGapDetector indirect = new BandGapDetector();
        indirect.detectFromBands(new double[][] {
                {-2.0, -1.0, -1.5},
                {-0.1, -0.5, -0.8},
                { 1.1,  0.9,  0.4}
        }, null, 4, 0.1);
        assertEquals(0.5, indirect.getBandGap(), 1.0e-12);
        assertFalse(indirect.isDirect());
        assertEquals(0, indirect.getVBMKpointIndex());
        assertEquals(2, indirect.getCBMKpointIndex());
    }

    @Test
    void partialOccupationIsMetallic() {
        BandGapDetector detector = new BandGapDetector();
        detector.detectFromBandsAndOccupations(
                new double[][] {{-1.0, -0.5}, {0.0, 0.2}, {1.0, 1.2}},
                new double[][] {{2.0, 2.0}, {0.4, 0.2}, {0.0, 0.0}},
                0.1, 2.0, 1.0e-3, 0.01);
        assertTrue(detector.isMetal());
        assertEquals(0.0, detector.getBandGap(), 0.0);
        assertTrue(detector.getDiagnostic().contains("partially occupied"));
    }

    @Test
    void dosGapUsesThresholdCrossingsAndDoesNotInventDirectness() {
        BandGapDetector detector = new BandGapDetector();
        detector.detectFromDOS(
                new double[] {-2.0, -1.0, -0.5, 0.0, 0.5, 1.0, 2.0},
                new double[] { 1.0,  1.0,  0.0, 0.0, 0.0, 1.0, 1.0},
                0.0, 0.1);
        assertEquals(BandGapDetector.Classification.GAPPED, detector.getClassification());
        assertEquals(1.1, detector.getBandGap(), 1.0e-12);
        assertFalse(detector.isDirectKnown());
        assertTrue(detector.getDescription().startsWith("DOS-estimated"));
    }

    @Test
    void malformedBandDataFailsExplicitly() {
        BandGapDetector detector = new BandGapDetector();
        assertThrows(IllegalArgumentException.class,
                () -> detector.detectFromBands(new double[][] {{1.0}, {2.0, 3.0}}, null, 2, 0.0));
        detector.detectFromBands(new double[][] {{-1.0}, {1.0}}, null, 3, 0.0);
        assertEquals(BandGapDetector.Classification.UNKNOWN, detector.getClassification());
    }
}
