/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run.parser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #124 (alignment slice): EXPLICIT reference alignment of a two-series
 * energy comparison (Fermi / VBM / vacuum / user reference). The comparer
 * layer ({@link EnergySeriesComparer}) deliberately never shifts anything;
 * this layer performs the shift ONLY from analyst-supplied reference values
 * and states the applied shift next to every result - an honest overlay, not
 * a hidden shift.
 *
 * <p>Load-bearing honesty points:</p>
 * <ul>
 *   <li>the tool CANNOT know the Fermi level / VBM / vacuum level of either
 *       calculation from a delta CSV - both reference values (eV) are
 *       REQUIRED analyst input; nothing is inferred from the data;</li>
 *   <li>the analyst is ASSERTING that the two references share a comparable
 *       physical origin (e.g. vacuum levels of the same slab polarity and
 *       termination); the analyzer names this assertion verbatim, and a
 *       reference shift beyond {@link #LOUD_ALIGNMENT_THRESHOLD_EV} eV is
 *       flagged LOUDLY as a probable units slip or incomparable reference -
 *       flagged, never hidden, never forbidden;</li>
 *   <li>grid agreement holds by construction (the comparer's same-grid CSV);
 *       cutoff/k-mesh/sampling comparability is NOT visible in a CSV - the
 *       report states that this remains the analyst's burden;</li>
 *   <li>USER mode lands both references at an explicit target value instead
 *       of zero; the aligned DELTAS are identical in all modes (the target
 *       cancels), and the report says so.</li>
 * </ul>
 *
 * <p>Refusal codes: the comparer's SERIES_* codes are propagated verbatim;
 * ALIGN_MODE for a missing/unknown reference mode; ALIGN_VALUE for a
 * non-finite or missing reference/target.</p>
 */
public final class SeriesAlignmentMath {

    /** |shift| beyond this many eV triggers the loud units/incomparability flag. */
    public static final double LOUD_ALIGNMENT_THRESHOLD_EV = 1.0;

    /** The completed, explicitly-provenanced alignment. */
    public static final class AlignedComparison {
        private final String mode;
        private final double referenceEv1;
        private final double referenceEv2;
        private final double targetEv;
        private final double referenceShiftEv;
        private final boolean loudShift;
        private final int rowCount;
        private final int rejectedRows;
        private final String parameterLabel;
        private final String firstSeriesLabel;
        private final String secondSeriesLabel;
        private final double rmsEv;
        private final double meanEv;
        private final double maxAbsEv;
        private final double maxAbsAtParameter;
        private final List<double[]> rows;

        AlignedComparison(String mode, double referenceEv1, double referenceEv2,
                double targetEv, EnergySeriesComparer.SeriesComparison base) {
            this.mode = mode;
            this.referenceEv1 = referenceEv1;
            this.referenceEv2 = referenceEv2;
            this.targetEv = targetEv;
            this.referenceShiftEv = referenceEv2 - referenceEv1;
            this.loudShift = Math.abs(this.referenceShiftEv)
                    > LOUD_ALIGNMENT_THRESHOLD_EV;
            this.rowCount = base.getRowCount();
            this.rejectedRows = base.getRejectedRows();
            this.parameterLabel = base.getParameterLabel();
            this.firstSeriesLabel = base.getFirstSeriesLabel();
            this.secondSeriesLabel = base.getSecondSeriesLabel();
            double landing = "USER".equals(mode) ? targetEv : 0.0;
            double sumSq = 0.0;
            double sum = 0.0;
            double maxAbs = -1.0;
            double maxAt = Double.NaN;
            this.rows = new ArrayList<>();
            for (double[] row : base.getRows()) {
                double aligned1 = row[1] - referenceEv1 + landing;
                double aligned2 = row[2] - referenceEv2 + landing;
                double delta = aligned2 - aligned1;
                sumSq += delta * delta;
                sum += delta;
                if (Math.abs(delta) > maxAbs) {
                    maxAbs = Math.abs(delta);
                    maxAt = row[0];
                }
                this.rows.add(new double[] {row[0], aligned1, aligned2, delta});
            }
            this.rmsEv = this.rows.isEmpty() ? Double.NaN
                    : Math.sqrt(sumSq / this.rows.size());
            this.meanEv = this.rows.isEmpty() ? Double.NaN : sum / this.rows.size();
            this.maxAbsEv = maxAbs;
            this.maxAbsAtParameter = maxAt;
        }

        /** FERMI, VBM, VACUUM or USER. */
        public String getMode() { return this.mode; }
        public double getReferenceEv1() { return this.referenceEv1; }
        public double getReferenceEv2() { return this.referenceEv2; }
        /** USER-mode landing point (0.0 in the zero-anchored modes). */
        public double getTargetEv() { return this.targetEv; }
        /** ref2 - ref1, the EXPLICIT shift difference applied, eV. */
        public double getReferenceShiftEv() { return this.referenceShiftEv; }
        /** True when |shift| exceeds the loud-flag threshold. */
        public boolean isLoudShift() { return this.loudShift; }
        public int getRowCount() { return this.rowCount; }
        public int getRejectedRows() { return this.rejectedRows; }
        public String getParameterLabel() { return this.parameterLabel; }
        public String getFirstSeriesLabel() { return this.firstSeriesLabel; }
        public String getSecondSeriesLabel() { return this.secondSeriesLabel; }
        public double getRmsEv() { return this.rmsEv; }
        public double getMeanEv() { return this.meanEv; }
        public double getMaxAbsEv() { return this.maxAbsEv; }
        public double getMaxAbsAtParameter() { return this.maxAbsAtParameter; }
        /** Rows {parameter, aligned1, aligned2, alignedDelta}, eV. */
        public List<double[]> getRows() { return List.copyOf(this.rows); }
    }

    private SeriesAlignmentMath() {
    }

    /**
     * Aligns a two-series CSV with explicit references. Codes: SERIES_*
     * propagated, ALIGN_MODE, ALIGN_VALUE.
     */
    public static OperationResult<AlignedComparison> align(Path file, String modeText,
            double referenceEv1, double referenceEv2, double targetEv) {
        String mode = modeText == null ? "" : modeText.trim().toUpperCase(
                java.util.Locale.ROOT);
        if (!mode.equals("FERMI") && !mode.equals("VBM") && !mode.equals("VACUUM")
                && !mode.equals("USER")) {
            return OperationResult.failed("ALIGN_MODE",
                    "The reference mode must be one of FERMI, VBM, VACUUM or USER "
                            + "(got '" + (modeText == null ? "" : modeText.trim())
                            + "'). No reference value is ever inferred from the "
                            + "data.",
                    null);
        }
        if (!Double.isFinite(referenceEv1) || !Double.isFinite(referenceEv2)) {
            return OperationResult.failed("ALIGN_VALUE",
                    "Both reference values (series 1 and series 2, eV) must be "
                            + "finite analyst-supplied numbers - the tool cannot "
                            + "know the Fermi/VBM/vacuum level of either "
                            + "calculation and refuses to guess.",
                    null);
        }
        if ("USER".equals(mode) && !Double.isFinite(targetEv)) {
            return OperationResult.failed("ALIGN_VALUE",
                    "USER mode needs a finite target value (the landing point for "
                            + "both references, eV).",
                    null);
        }
        OperationResult<EnergySeriesComparer.SeriesComparison> compared =
                EnergySeriesComparer.compare(file);
        if (!compared.isSuccess() || compared.getValue().isEmpty()) {
            return OperationResult.failed(compared.getCode(), compared.getMessage(),
                    null);
        }
        return OperationResult.success("ALIGN_OK", "Aligned "
                + compared.getValue().get().getRowCount() + " row(s).",
                new AlignedComparison(mode, referenceEv1, referenceEv2, targetEv,
                        compared.getValue().get()));
    }
}
