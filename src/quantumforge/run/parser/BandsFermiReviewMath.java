/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import quantumforge.operation.OperationResult;
import quantumforge.project.property.ProjectProperty;

/**
 * Roadmap #46 (Fermi-reference slice): explicit-Fermi review of a
 * plotband/bands.x {@code .dat.gnu} band file. The reference is REQUIRED
 * analyst input - the review never invents a Fermi level from the data. The
 * points already parsed by {@link QEBandsDataParser#parseWithFermi} are
 * reduced to honest, fully-labeled statistics: per-band shifted min/max, the
 * count of bands straddling E - E_F = 0 with an EXACTLY-zero crossing
 * tolerance (stated as a point-sampled metallicity indicator, NOT the full
 * #47 occupation/gap classification), plus the occupied-side maximum and
 * empty-side minimum when both exist.
 *
 * <p>Load-bearing honesty:</p>
 * <ul>
 *   <li>QE writes spin channels as SEPARATE band files; this review covers
 *       exactly one file and says so - reviewing channel A never implies
 *       channel B;</li>
 *   <li>energies are assumed in the SAME unit as the supplied Fermi value
 *       (the QE plotband convention is eV; nothing is re-scaled or guessed);</li>
 *   <li>the parser's skipped-row diagnostics are surfaced CAPPED with the
 *       total count - sampling hides nothing;</li>
 *   <li>the naive VBM-side/CBM-side pair is labelled point-sampled with a
 *       zero crossing tolerance - the valence-count-based detector with a
 *       metallicity tolerance is the #47 slice, not this review.</li>
 * </ul>
 *
 * <p>Refusal codes: BANDS_REVIEW_IO, BANDS_REVIEW_FERMI, BANDS_REVIEW_EMPTY.</p>
 */
public final class BandsFermiReviewMath {

    /** Parser diagnostics surfaced per review (total count always shown). */
    public static final int MAX_LISTED_DIAGNOSTICS = 20;

    /** Per-band shifted statistics. */
    public static final class BandStats {
        private final int index;
        private final int points;
        private final double minShiftedEv;
        private final double maxShiftedEv;

        BandStats(int index, int points, double minShiftedEv, double maxShiftedEv) {
            this.index = index;
            this.points = points;
            this.minShiftedEv = minShiftedEv;
            this.maxShiftedEv = maxShiftedEv;
        }

        /** 1-based band index in file order. */
        public int getIndex() { return this.index; }
        public int getPoints() { return this.points; }
        public double getMinShiftedEv() { return this.minShiftedEv; }
        public double getMaxShiftedEv() { return this.maxShiftedEv; }
        /** min < 0 < max with EXACTLY-zero tolerance, both directions counted. */
        public boolean crossesZero() {
            return this.minShiftedEv < 0.0 && this.maxShiftedEv > 0.0;
        }
    }

    /** The completed review. */
    public static final class BandsReview {
        private final double fermiEv;
        private final int bandCount;
        private final long totalPoints;
        private final double kMin;
        private final double kMax;
        private final List<BandStats> perBand;
        private final int crossingCount;
        private final double vbmSideEv;
        private final double cbmSideEv;
        private final List<String> diagnostics;
        private final int diagnosticsTotal;

        BandsReview(double fermiEv, List<BandStats> perBand, double kMin, double kMax,
                double vbmSideEv, double cbmSideEv, List<String> diagnostics,
                int diagnosticsTotal) {
            this.fermiEv = fermiEv;
            this.perBand = new ArrayList<>(perBand);
            this.bandCount = perBand.size();
            long points = 0;
            int crossings = 0;
            for (BandStats stats : perBand) {
                points += stats.getPoints();
                if (stats.crossesZero()) {
                    crossings += 1;
                }
            }
            this.totalPoints = points;
            this.kMin = kMin;
            this.kMax = kMax;
            this.crossingCount = crossings;
            this.vbmSideEv = vbmSideEv;
            this.cbmSideEv = cbmSideEv;
            this.diagnostics = new ArrayList<>(diagnostics);
            this.diagnosticsTotal = diagnosticsTotal;
        }

        /** The analyst-supplied reference actually applied, eV. */
        public double getFermiEv() { return this.fermiEv; }
        public int getBandCount() { return this.bandCount; }
        public long getTotalPoints() { return this.totalPoints; }
        public double getKMin() { return this.kMin; }
        public double getKMax() { return this.kMax; }
        public List<BandStats> getPerBand() { return List.copyOf(this.perBand); }
        /** Bands straddling E_F with an exactly-zero tolerance. */
        public int getCrossingCount() { return this.crossingCount; }
        /** Max shifted energy <= 0 across all bands (NaN when none). */
        public double getVbmSideEv() { return this.vbmSideEv; }
        /** Min shifted energy > 0 across all bands (NaN when none). */
        public double getCbmSideEv() { return this.cbmSideEv; }
        /** cbmSide - vbmSide when both exist, else NaN; point-sampled only. */
        public double getNaiveGapEv() {
            return Double.isNaN(this.vbmSideEv) || Double.isNaN(this.cbmSideEv)
                    ? Double.NaN : this.cbmSideEv - this.vbmSideEv;
        }
        /** Up to {@link #MAX_LISTED_DIAGNOSTICS} parser diagnostics. */
        public List<String> getDiagnostics() { return List.copyOf(this.diagnostics); }
        /** Total parser diagnostics (capped list may hide the tail; count does not). */
        public int getDiagnosticsTotal() { return this.diagnosticsTotal; }
    }

    private BandsFermiReviewMath() {
    }

    /**
     * Parses and reviews one .dat.gnu file against the explicit Fermi value.
     * Codes: BANDS_REVIEW_IO, BANDS_REVIEW_FERMI, BANDS_REVIEW_EMPTY.
     */
    public static OperationResult<BandsReview> review(Path file, double fermiEv) {
        if (file == null || !Files.isRegularFile(file)) {
            return OperationResult.failed("BANDS_REVIEW_IO",
                    "The bands .dat.gnu file does not exist.", null);
        }
        if (!Double.isFinite(fermiEv)) {
            return OperationResult.failed("BANDS_REVIEW_FERMI",
                    "A finite analyst-supplied Fermi reference (eV) is required; "
                            + "the review never infers one from the data.",
                    null);
        }
        QEBandsDataParser parser = new QEBandsDataParser(new ProjectProperty());
        try {
            parser.parseWithFermi(file.toFile(), fermiEv);
        } catch (IOException | RuntimeException ex) {
            return OperationResult.failed("BANDS_REVIEW_IO",
                    "Parsing the bands file failed: " + ex.getMessage(), null);
        }
        List<QEBandsDataParser.Band> bands = parser.getBands();
        if (bands.isEmpty()) {
            return OperationResult.failed("BANDS_REVIEW_EMPTY",
                    "No band curve was parsed from " + file.getFileName()
                            + " (parser diagnostics: " + parser.getDiagnostics().size()
                            + "). Nothing was fabricated.",
                    null);
        }
        List<BandStats> perBand = new ArrayList<>();
        double kMin = Double.POSITIVE_INFINITY;
        double kMax = Double.NEGATIVE_INFINITY;
        double vbmSide = Double.NaN;
        double cbmSide = Double.NaN;
        for (int idx = 0; idx < bands.size(); idx += 1) {
            QEBandsDataParser.Band band = bands.get(idx);
            double[] kValues = band.getKDistance();
            double[] energies = band.getEnergyEv();
            if (energies.length == 0) {
                continue;
            }
            double minE = energies[0];
            double maxE = energies[0];
            for (int point = 0; point < energies.length; point += 1) {
                minE = Math.min(minE, energies[point]);
                maxE = Math.max(maxE, energies[point]);
                kMin = Math.min(kMin, kValues[point]);
                kMax = Math.max(kMax, kValues[point]);
                if (energies[point] <= 0.0) {
                    vbmSide = Double.isNaN(vbmSide) ? energies[point]
                            : Math.max(vbmSide, energies[point]);
                } else {
                    cbmSide = Double.isNaN(cbmSide) ? energies[point]
                            : Math.min(cbmSide, energies[point]);
                }
            }
            perBand.add(new BandStats(idx + 1, energies.length, minE, maxE));
        }
        if (perBand.isEmpty()) {
            return OperationResult.failed("BANDS_REVIEW_EMPTY",
                    "Every band curve in " + file.getFileName() + " parsed empty.",
                    null);
        }
        List<String> listedDiagnostics = new ArrayList<>();
        List<String> allDiagnostics = parser.getDiagnostics();
        for (int idx = 0; idx < allDiagnostics.size()
                && idx < MAX_LISTED_DIAGNOSTICS; idx += 1) {
            listedDiagnostics.add(allDiagnostics.get(idx));
        }
        return OperationResult.success("BANDS_REVIEW_OK",
                "Reviewed " + perBand.size() + " band(s).",
                new BandsReview(fermiEv, perBand, kMin, kMax, vbmSide, cbmSide,
                        listedDiagnostics, allDiagnostics.size()));
    }
}
