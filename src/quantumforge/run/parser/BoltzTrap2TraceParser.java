/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import quantumforge.project.property.ProjectProperty;

/**
 * Reads BoltzTraP2's human-readable transport tables - the classical
 * {@code .trace} and tensor-full {@code .condtens} files written by
 * {@code BoltzTraP2.io.save_trace} / {@code save_condtens} (Roadmap #109's
 * output side). The column grammar was taken from BoltzTraP2's own writer
 * source (26.3.1):
 *
 * <ul>
 *   <li>{@code .trace}: a single {@code #} header line naming all ten columns
 *       ({@code Ef[Ry] T[K] N[e/uc] DOS(ef) S[V/K] sigma/... RH kappae/... cv
 *       chi}), then whitespace-separated {@code %g} rows of exactly ten
 *       numbers;</li>
 *   <li>{@code .condtens}: a header whose thirty print slots carry only SIX
 *       names ({@code Ef[Ry] T[K] N[e/uc] sigma/... S[V/K] kappae/...} - the
 *       remaining slots are empty strings, so the whitespace-tokenized header
 *       shows six tokens, NOT thirty), then rows of exactly thirty numbers:
 *       Ef, T, N, sigma(3x3 Fortran order), S(3x3 Fortran), kappa(3x3
 *       Fortran). The diagonal components xx/yy/zz sit at offsets 0/4/8 of
 *       each 9-element tensor block.
 *   </ul>
 *
 * <p>File families are therefore decided by the DATA ROW token count, never
 * by the header token count. A 30-token tensor file whose header names
 * {@code RH[m**3/C]} instead of sigma is a {@code .halltens} (Hall tensor)
 * file: it is NOT transport data, so it is refused loudly (kind
 * {@link FileKind#TENSOR_OTHER}), its rows skipped and counted - never
 * quietly re-interpreted as sigma/S/kappa.</p>
 *
 * <p>Units are never invented or silently converted: which scattering model
 * wrote the file is legible ONLY from the header tokens ({@code sigma/tau0},
 * {@code sigma/lambda0}, {@code sigma[...]}), so the parser reports those
 * header tokens verbatim and states the detected scattering-model family as
 * a provenance note (headerless BoltzTraP1-style files report "no header").
 * The custom_tau model's 1e-9 conductivity factor is already baked into the
 * written numbers by the writer; nothing is re-applied here. The one physics
 * constant used is the Rydberg energy 1 Ry = 13.605693122994 eV, named
 * explicitly where applied.</p>
 *
 * <p>Rows with non-finite or non-parseable cells, or with a token count
 * foreign to the locked family, are skipped and COUNTED - never silently
 * healed; fewer than two clean rows is a hard failure for analysis purposes
 * (a single point is not a curve).</p>
 */
public final class BoltzTrap2TraceParser extends LogParser {

    /** 1 Ry in eV (CODATA Rydberg energy, named pinned constant). */
    public static final double RY_TO_EV = 13.605693122994;

    /** Exact number of numeric cells a .trace data row carries. */
    public static final int TRACE_COLUMNS = 10;
    /** Exact number of numeric cells a .condtens data row carries. */
    public static final int TENSOR_COLUMNS = 30;

    /** File family decoded from the per-row token count and header names. */
    public enum FileKind {
        /** .trace: 10 columns, isotropic (trace/3) transport values. */
        TRACE,
        /** .condtens: 30 columns, full 3x3 transport tensors in Fortran order. */
        CONDTENS,
        /**
         * A 30-column tensor file that is not transport (e.g. .halltens,
         * header names RH[m**3/C]): parsed structure recognized, semantics
         * refused - rows are skipped and counted.
         */
        TENSOR_OTHER
    }

    /** One parsed row of a BoltzTraP2 transport table. */
    public static final class TransportRow {
        private final double muRy;
        private final double temperatureK;
        private final double carriersPerCell;
        private final double seebeckVK;
        private final double sigmaOverTau;
        private final double kappaOverTau;
        private final double[] seebeckDiag; // condtens only, else null

        TransportRow(double muRy, double temperatureK, double carriersPerCell,
                double seebeckVK, double sigmaOverTau, double kappaOverTau,
                double[] seebeckDiag) {
            this.muRy = muRy;
            this.temperatureK = temperatureK;
            this.carriersPerCell = carriersPerCell;
            this.seebeckVK = seebeckVK;
            this.sigmaOverTau = sigmaOverTau;
            this.kappaOverTau = kappaOverTau;
            this.seebeckDiag = seebeckDiag;
        }

        /** Chemical potential in Ry, as written (Ef[Ry] column; Ha doubled to Ry by the writer). */
        public double getMuRy() { return this.muRy; }
        /** Same chemical potential in eV via {@link #RY_TO_EV}. */
        public double getMuEv() { return this.muRy * RY_TO_EV; }
        public double getTemperatureK() { return this.temperatureK; }
        /** Carrier count per unit cell as written (N[e/uc], base electrons included). */
        public double getCarriersPerCell() { return this.carriersPerCell; }
        /** Isotropic Seebeck coefficient (trace/3 written by BoltzTraP2), V/K. */
        public double getSeebeckVK() { return this.seebeckVK; }
        /** sigma column in the units the header names (uniform_tau: 1/(ohm m s)). */
        public double getSigmaOverTau() { return this.sigmaOverTau; }
        /** kappa_e column in the units the header names. */
        public double getKappaOverTau() { return this.kappaOverTau; }
        /**
         * For .condtens rows: the diagonal of the Seebeck tensor (xx, yy, zz)
         * pulled out of the Fortran-ordered 3x3 block; null for .trace rows.
         */
        public double[] getSeebeckDiag() {
            return this.seebeckDiag == null ? null : this.seebeckDiag.clone();
        }
    }

    private FileKind kind;
    private String sigmaUnits = "";
    private String kappaUnits = "";
    private String scatteringModel = "";
    private String familyNote = "";
    private boolean headerSeen;
    private boolean headerNamedTransport;
    private boolean headerNamedHall;
    private final List<TransportRow> rows = new ArrayList<>();
    private final LinkedHashSet<Double> temperatures = new LinkedHashSet<>();
    private int skippedRows;

    public BoltzTrap2TraceParser(ProjectProperty property) {
        super(property);
    }

    /** File family locked from the first clean data row; null when no clean row parsed. */
    public FileKind getFileKind() { return this.kind; }
    public List<TransportRow> getRows() { return List.copyOf(this.rows); }
    public LinkedHashSet<Double> getTemperatures() {
        return new LinkedHashSet<>(this.temperatures);
    }
    public int getSkippedRowCount() { return this.skippedRows; }

    /** Verbatim sigma-column unit token from the header (empty when headerless). */
    public String getSigmaUnits() { return this.sigmaUnits; }
    /** Verbatim kappa-column unit token from the header (empty when headerless). */
    public String getKappaUnits() { return this.kappaUnits; }
    /** Scattering-model family legible from the header (may be "unknown"/"no header"). */
    public String getScatteringModel() { return this.scatteringModel; }
    /** One-line provenance note on how the family was decided. */
    public String getFamilyNote() { return this.familyNote; }

    /**
     * Power factor S^2 * sigma (W/(m K^2 s) when sigma is in 1/(ohm m s)) computed
     * per row from the recorded trace-averaged columns. Honest scope: this is
     * the isotropic-average approximation - it is NOT the tensor-consistent
     * tr(S^2.sigma)/3 of the full .condtens tensor pair.
     */
    public static double powerFactor(TransportRow row) {
        double s = row.getSeebeckVK();
        return s * s * row.getSigmaOverTau();
    }

    /** Row with the largest |S| (isotropic) - the screening point, tie-broken by T. */
    public TransportRow maxAbsSeebeck() {
        TransportRow best = null;
        for (TransportRow row : this.rows) {
            if (best == null
                    || Math.abs(row.getSeebeckVK()) > Math.abs(best.getSeebeckVK())
                    || (Math.abs(row.getSeebeckVK()) == Math.abs(best.getSeebeckVK())
                            && row.getTemperatureK() < best.getTemperatureK())) {
                best = row;
            }
        }
        return best;
    }

    @Override
    public void parse(File file) throws IOException {
        this.kind = null;
        this.rows.clear();
        this.temperatures.clear();
        this.sigmaUnits = "";
        this.kappaUnits = "";
        this.scatteringModel = "unknown";
        this.familyNote = "";
        this.skippedRows = 0;
        this.headerSeen = false;
        this.headerNamedTransport = false;
        this.headerNamedHall = false;
        if (file == null || !file.isFile()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith("#")) {
                    if (!this.headerSeen) {
                        readHeader(trimmed.substring(1).trim());
                        this.headerSeen = true;
                    }
                    continue;
                }
                readRow(trimmed);
            }
        }
        if (!this.headerSeen && this.scatteringModel.equals("unknown")) {
            this.scatteringModel = "no header";
        }
        if (this.kind == null && this.skippedRows > 0) {
            this.familyNote = this.familyNote.isEmpty()
                    ? "no clean data row matched the BoltzTraP2 .trace (10) / .condtens (30)"
                            + " column grammar"
                    : this.familyNote;
        }
    }

    private void readHeader(String headerText) {
        String[] tokens = headerText.split("\\s+");
        String joined = " " + String.join(" ", tokens) + " ";
        for (String token : tokens) {
            if (token.startsWith("sigma/") || token.startsWith("sigma[")) {
                this.sigmaUnits = token;
                this.headerNamedTransport = true;
            }
            if (token.startsWith("kappae/") || token.startsWith("kappae[")) {
                if (this.kappaUnits.isEmpty()) {
                    this.kappaUnits = token;
                }
                this.headerNamedTransport = true;
            }
            if (token.startsWith("RH[")) {
                this.headerNamedHall = true;
            }
        }
        if (joined.contains("sigma/lambda0")) {
            this.scatteringModel = "uniform_lambda";
        } else if (joined.contains("sigma/tau0")) {
            this.scatteringModel = "uniform_tau";
        } else if (joined.contains("sigma[")) {
            this.scatteringModel = "custom_tau";
        } else if (this.headerNamedHall && !this.headerNamedTransport) {
            this.scatteringModel = "n/a (Hall tensor file)";
        }
    }

    private void readRow(String line) {
        String[] tokens = line.split("\\s+");
        if (this.kind == null) {
            // family decision: the EXACT column grammar of the two writers;
            // a 30-column file is transport only when the header names sigma
            // (or is absent) - an RH-named header is a Hall tensor file.
            if (tokens.length == TRACE_COLUMNS) {
                this.kind = FileKind.TRACE;
                this.familyNote = this.headerSeen
                        ? "trace family confirmed by 10-column data rows"
                        : "no header: 10-column rows taken as BoltzTraP .trace grammar";
            } else if (tokens.length == TENSOR_COLUMNS) {
                this.kind = this.headerNamedHall && !this.headerNamedTransport
                        ? FileKind.TENSOR_OTHER : FileKind.CONDTENS;
                if (this.kind == FileKind.CONDTENS) {
                    this.familyNote = this.headerSeen
                            ? "condtens family confirmed by 30-column data rows and sigma header"
                            : "no header: 30-column rows taken as BoltzTraP .condtens grammar";
                } else {
                    this.familyNote = "header names RH[...] (Hall tensor), not sigma:"
                            + " a .halltens file is NOT transport data - rows skipped";
                }
            } else {
                this.skippedRows++;
                return;
            }
        }
        int expected = this.kind == FileKind.TRACE ? TRACE_COLUMNS : TENSOR_COLUMNS;
        if (this.kind == FileKind.TENSOR_OTHER || tokens.length != expected) {
            this.skippedRows++;
            return;
        }
        double[] values = new double[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            try {
                values[i] = Double.parseDouble(tokens[i]);
            } catch (NumberFormatException ex) {
                this.skippedRows++;
                return;
            }
            if (Double.isNaN(values[i]) || Double.isInfinite(values[i])) {
                this.skippedRows++;
                return;
            }
        }
        if (this.kind == FileKind.CONDTENS) {
            // Fortran (column-major) 3x3 blocks: sigma cols 3..11, S cols
            // 12..20, kappa cols 21..29; xx, yy, zz sit at offsets 0, 4, 8.
            double[] seebeckDiag = new double[] {values[12], values[16], values[20]};
            double sIso = (values[12] + values[16] + values[20]) / 3.0;
            double sigmaIso = (values[3] + values[7] + values[11]) / 3.0;
            double kappaIso = (values[21] + values[25] + values[29]) / 3.0;
            this.rows.add(new TransportRow(values[0], values[1], values[2],
                    sIso, sigmaIso, kappaIso, seebeckDiag));
        } else {
            // trace columns: Ef[Ry] T[K] N[e/uc] DOS S sigma/tau RH kappa cv chi
            this.rows.add(new TransportRow(values[0], values[1], values[2],
                    values[4], values[5], values[7], null));
        }
        this.temperatures.add(values[1]);
    }

    /**
     * Isotropic Seebeck tensor anisotropy diagnostic for .condtens rows: the
     * min/max/spread of the three diagonal S components of the given row;
     * null for .trace files (no tensor data - never faked).
     */
    public double[] seebeckDiagonalSpread(TransportRow row) {
        double[] diag = row == null ? null : row.getSeebeckDiag();
        if (diag == null) {
            return null;
        }
        double min = Math.min(diag[0], Math.min(diag[1], diag[2]));
        double max = Math.max(diag[0], Math.max(diag[1], diag[2]));
        return new double[] {min, max, max - min};
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT,
                "BoltzTraP2TraceParser[kind=%s, model=%s, rows=%d, skipped=%d]",
                this.kind, this.scatteringModel, this.rows.size(), this.skippedRows);
    }
}
