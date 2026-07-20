/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import quantumforge.operation.OperationResult;
import quantumforge.project.property.ProjectProperty;

/**
 * Roadmap #47 (curve slice): analytic band-gap classification from parsed
 * plotband/bands.x {@code .dat.gnu} band curves, driven by an EXPLICIT
 * valence-band count and an EXPLICIT metallicity tolerance - the two numbers
 * the data itself cannot supply.
 *
 * <p>Definitions (stated so the verdict is auditable): for equal k-grids
 * across curves, VBM = max over k of the {@code nV}-th curve, CBM = min over
 * k of the {@code (nV+1)}-th curve, gap = CBM - VBM. Verdicts:</p>
 * <ul>
 *   <li>gap &lt; -tolerance: METALLIC_OVERLAP - bands cross, OR the valence
 *       count is wrong for a partial-occupation (alkali-like) system; both
 *       possibilities are printed, never resolved silently;</li>
 *   <li>|gap| &lt;= tolerance: DEGENERATE_WITHIN_TOLERANCE - the data does
 *       not decide metal vs gap at this tolerance and says so;</li>
 *   <li>gap &gt; tolerance: GAP, direct iff |k(VBM) - k(CBM)| &lt;= kTolerance,
 *       else indirect (both k-locations printed).</li>
 * </ul>
 *
 * <p>Load-bearing honesty: the valence count is analyst input (refused when
 * unset or when it leaves no conduction band); a curve set with UNEQUAL
 * k-grids is refused rather than interpolated (interpolation would invent
 * band values); spin channels come as separate files (one file = one
 * verdict); energies are in the file's own unit (QE plotband convention eV)
 * with nothing re-scaled; the k(VBM)/k(CBM) used for directness come from
 * the SAMPLED k points only - a finer mesh can move them and the text says
 * so. Occupation-number-based detection from the pw.x log remains the other
 * #47 half.</p>
 *
 * <p>Refusal codes: GAPMATH_IO, GAPMATH_EMPTY, GAPMATH_VALENCE,
 * GAPMATH_GRID, GAPMATH_VALUE.</p>
 */
public final class BandGapBandMath {

    /** Verdict vocabulary. */
    public enum Verdict {
        GAP_DIRECT, GAP_INDIRECT, METALLIC_OVERLAP, DEGENERATE_WITHIN_TOLERANCE
    }

    /** The completed classification. */
    public static final class GapClassification {
        private final int valenceBands;
        private final int bandCount;
        private final double toleranceEv;
        private final double kTolerance;
        private final double vbmEv;
        private final double vbmAtK;
        private final double cbmEv;
        private final double cbmAtK;
        private final double gapEv;
        private final Verdict verdict;

        GapClassification(int valenceBands, int bandCount, double toleranceEv,
                double kTolerance, double vbmEv, double vbmAtK, double cbmEv,
                double cbmAtK, Verdict verdict) {
            this.valenceBands = valenceBands;
            this.bandCount = bandCount;
            this.toleranceEv = toleranceEv;
            this.kTolerance = kTolerance;
            this.vbmEv = vbmEv;
            this.vbmAtK = vbmAtK;
            this.cbmEv = cbmEv;
            this.cbmAtK = cbmAtK;
            this.gapEv = cbmEv - vbmEv;
            this.verdict = verdict;
        }

        public int getValenceBands() { return this.valenceBands; }
        public int getBandCount() { return this.bandCount; }
        public double getToleranceEv() { return this.toleranceEv; }
        public double getKTolerance() { return this.kTolerance; }
        /** VBM = max over sampled k of curve nV, in file units. */
        public double getVbmEv() { return this.vbmEv; }
        public double getVbmAtK() { return this.vbmAtK; }
        /** CBM = min over sampled k of curve nV+1, in file units. */
        public double getCbmEv() { return this.cbmEv; }
        public double getCbmAtK() { return this.cbmAtK; }
        public double getGapEv() { return this.gapEv; }
        public Verdict getVerdict() { return this.verdict; }
    }

    private BandGapBandMath() {
    }

    /**
     * Classifies one band file. Codes: GAPMATH_IO, GAPMATH_EMPTY,
     * GAPMATH_VALENCE, GAPMATH_GRID, GAPMATH_VALUE.
     */
    public static OperationResult<GapClassification> classify(Path file,
            int valenceBands, double toleranceEv, double kTolerance) {
        if (file == null || !Files.isRegularFile(file)) {
            return OperationResult.failed("GAPMATH_IO",
                    "The bands .dat.gnu file does not exist.", null);
        }
        if (!Double.isFinite(toleranceEv) || toleranceEv < 0.0) {
            return OperationResult.failed("GAPMATH_VALUE",
                    "The metallicity tolerance must be a finite, non-negative eV "
                            + "value supplied by the analyst.",
                    null);
        }
        if (!Double.isFinite(kTolerance) || kTolerance <= 0.0) {
            return OperationResult.failed("GAPMATH_VALUE",
                    "The k-location tolerance for directness must be finite and "
                            + "strictly positive.",
                    null);
        }
        QEBandsDataParser parser = new QEBandsDataParser(new ProjectProperty());
        try {
            parser.parse(file.toFile());
        } catch (IOException | RuntimeException ex) {
            return OperationResult.failed("GAPMATH_IO",
                    "Parsing the bands file failed: " + ex.getMessage(), null);
        }
        if (parser.getBands().isEmpty()) {
            return OperationResult.failed("GAPMATH_EMPTY",
                    "No band curve was parsed from " + file.getFileName() + ".",
                    null);
        }
        int bandCount = parser.getBands().size();
        if (valenceBands <= 0 || valenceBands >= bandCount) {
            return OperationResult.failed("GAPMATH_VALENCE",
                    "The valence-band count must leave at least one conduction "
                            + "band: 1 <= nV < " + bandCount + " here (got "
                            + valenceBands + "). The count is analyst input - "
                            + "it is never guessed from the data.",
                    null);
        }
        double[] kReference = parser.getBands().get(0).getKDistance();
        for (int idx = 1; idx < bandCount; idx += 1) {
            double[] kValues = parser.getBands().get(idx).getKDistance();
            if (kValues.length != kReference.length) {
                return OperationResult.failed("GAPMATH_GRID",
                        "Curve " + (idx + 1) + " holds " + kValues.length
                                + " k-point(s) but curve 1 holds "
                                + kReference.length + "; unequal k-grids are "
                                + "refused rather than interpolated.",
                        null);
            }
            for (int point = 0; point < kValues.length; point += 1) {
                if (kValues[point] != kReference[point]) {
                    return OperationResult.failed("GAPMATH_GRID",
                            "Curve " + (idx + 1) + " disagrees with curve 1 on "
                                    + "k-point " + (point + 1) + "; unequal "
                                    + "k-grids are refused rather than "
                                    + "interpolated.",
                            null);
                }
            }
        }
        double[] valence = parser.getBands().get(valenceBands - 1).getEnergyEv();
        double[] conduction = parser.getBands().get(valenceBands).getEnergyEv();
        if (valence.length == 0 || conduction.length == 0) {
            return OperationResult.failed("GAPMATH_EMPTY",
                    "The valence or conduction curve parsed empty.", null);
        }
        double vbm = valence[0];
        double vbmAtK = parser.getBands().get(valenceBands - 1).getKDistance()[0];
        for (int point = 1; point < valence.length; point += 1) {
            if (valence[point] > vbm) {
                vbm = valence[point];
                vbmAtK = parser.getBands().get(valenceBands - 1)
                        .getKDistance()[point];
            }
        }
        double cbm = conduction[0];
        double cbmAtK = parser.getBands().get(valenceBands).getKDistance()[0];
        for (int point = 1; point < conduction.length; point += 1) {
            if (conduction[point] < cbm) {
                cbm = conduction[point];
                cbmAtK = parser.getBands().get(valenceBands).getKDistance()[point];
            }
        }
        double gap = cbm - vbm;
        Verdict verdict;
        if (gap < -toleranceEv) {
            verdict = Verdict.METALLIC_OVERLAP;
        } else if (Math.abs(gap) <= toleranceEv) {
            verdict = Verdict.DEGENERATE_WITHIN_TOLERANCE;
        } else {
            verdict = Math.abs(vbmAtK - cbmAtK) <= kTolerance
                    ? Verdict.GAP_DIRECT : Verdict.GAP_INDIRECT;
        }
        return OperationResult.success("GAPMATH_OK", "Classified with gap "
                + gap + ".", new GapClassification(valenceBands, bandCount,
                toleranceEv, kTolerance, vbm, vbmAtK, cbm, cbmAtK, verdict));
    }
}
