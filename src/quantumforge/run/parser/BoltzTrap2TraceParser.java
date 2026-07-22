/* Copyright (C) 2025-2026 QuantumForge Development Team.
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
 * Reads BoltzTraP2's human-readable transport tables written by
 * {@code BoltzTraP2.io.save_trace} / {@code save_condtens} /
 * {@code save_halltens}, plus the yiwang62 "BoltzTraP2Y" fork extensions
 * (gitlab.com/yiwang62/BoltzTraP2 branch 20210126, the readthedocs
 * "BoltzTraP2Y" documentation's own Edit-on-GitLab link):
 * {@code io.save_trace}'s optional {@code cv_x}/{@code S_x}/{@code N_x}
 * columns ("changed by Yi Wang, 09/24/2020") and
 * {@code ioEXT.save_traceEXT}'s 23-column {@code .dope.trace}. The column
 * grammars are pinned from the writers' own format strings (fork sources,
 * branch 20210126):
 *
 * <ul>
 *   <li>{@code .trace}: a single {@code #} header line naming ten columns
 *       ({@code Ef[Ry] T[K] N[e/uc] DOS(ef)[1/(Ha*uc)] S[V/K] sigma/...
 *       RH[m**3/C] kappae/... cv chi}), then rows of exactly ten numbers
 *       ({@code {:>12.8g} {:>9g}} + 8x {@code {:>25g}}); the fork's
 *       {@code cv_x} variant carries thirteen numbers under a 13-name
 *       header ({@code cv_x[J/(mole-atom*K)] S_x(V/K) N_x(e/cm^3)});
 *   <li>{@code .dope.trace}: {@code save_traceEXT}'s 23 columns
 *       ({@code {:>12.8g} {:>9g}} + 21x {@code {:>25.12g}}) whose FIRST
 *       header token is {@code mu-Ef[eV]} (not {@code Ef[Ry]}; the writer
 *       prints {@code (mu-efermi)/Volt}), then the ten base quantities
 *       plus {@code cv_x, S_x, N_x, L(W*ohm/K**2), L0_h, L0_e, M_h, M_e,
 *       N_h, N_e, deltaE(eV), tau(s), tau_1(s)};
 *   <li>{@code .condtens}: a 30-slot header whose six NON-empty names are
 *       {@code Ef[Ry] T[K] N[e/uc] sigma/... S[V/K] kappae/...}, then rows
 *       of exactly thirty numbers: Ef, T, N, sigma(3x3 Fortran order),
 *       S(3x3 Fortran), kappa(3x3 Fortran). The diagonal xx/yy/zz sit at
 *       offsets 0/4/8 of each 9-element tensor block;
 *   <li>{@code .halltens}: the same 30-slot shell whose only named fourth
 *       token is {@code RH[m**3/C]}; rows carry the rank-3 Hall tensor
 *       raveled in Fortran order (index {@code i + 3j + 9k}). Since the
 *       writer was pinned verbatim at batch 172 this file is now parsed AS
 *       Hall data (its own declared semantics) - the batch-109 posture
 *       that skipped it as {@link FileKind#TENSOR_OTHER} is superseded;
 *       TENSOR_OTHER remains in the enum for compatibility and for any
 *       genuinely unnamed 30-column dialect.
 *   </ul>
 *
 * <p>Family decisions are taken from the DATA ROW token count cross-
 * certified by header names. A 13- or 23-column table whose header does
 * NOT certify the Yi-Wang units ({@code cv_x/S_x/N_x} or
 * {@code mu-Ef[eV]}) is REFUSED line by line (skipped and counted, with
 * the reason stated) - unit honesty is never guessed from column count
 * alone. The first-column unit note ({@code Ef[Ry]} vs
 * {@code mu-Ef[eV]}) is exposed via {@link #getColumnOneNote()} so
 * consumers never double-convert.</p>
 *
 * <p>Units are never invented or silently converted: the scattering model
 * is legible ONLY from the header tokens ({@code sigma/tau0},
 * {@code sigma/lambda0}, {@code sigma[...]}), reported verbatim; the
 * custom_tau 1e-9 conductivity factor is already baked into the written
 * numbers by the writer and nothing is re-applied here. The one physics
 * constant used is 1 Ry = 13.605693122994 eV (CODATA Rydberg, named where
 * applied).</p>
 *
 * <p>Rows with non-finite or non-parseable cells, or with a token count
 * foreign to the locked family, are skipped and COUNTED - never silently
 * healed; fewer than two clean rows is a hard failure for analysis
 * purposes (a single point is not a curve).</p>
 */
public final class BoltzTrap2TraceParser extends LogParser {

    /** 1 Ry in eV (CODATA Rydberg energy, named pinned constant). */
    public static final double RY_TO_EV = 13.605693122994;

    /** Exact number of numeric cells a .trace data row carries. */
    public static final int TRACE_COLUMNS = 10;
    /** Yi-Wang .trace variant with cv_x, S_x, N_x appended (save_trace cv_x branch). */
    public static final int TRACE_X_COLUMNS = 13;
    /** save_traceEXT .dope.trace row width (mu-Ef[eV] + 22 more columns). */
    public static final int DOPE_EXT_COLUMNS = 23;
    /** Exact number of numeric cells a .condtens/.halltens data row carries. */
    public static final int TENSOR_COLUMNS = 30;

    // ---- trace-family column indices (0-based) shared by the 10/13/23-col kinds ----
    /** Column of mu - as written: Ef[Ry], or mu-Ef[eV] in the fork's .dope.trace. */
    public static final int COL_MU = 0;
    /** DOS(ef)[1/(Ha*uc)] per the header token. */
    public static final int COL_DOS_EF = 3;
    /** S[V/K] column (trace-average written by the writer). */
    public static final int COL_SEEBECK = 4;
    /** sigma/tau0 (or /lambda0 or absolute) column - units from the header. */
    public static final int COL_SIGMA = 5;
    /** RH[m**3/C] column (even-permutation average, per save_trace). */
    public static final int COL_RH = 6;
    /** kappae column - units from the header. */
    public static final int COL_KAPPA = 7;
    /** cv[J/(mole-atom*K)] column (mole of ATOM in the fork, stated in save_trace). */
    public static final int COL_CV = 8;
    /** First Yi-Wang extension column: cv_x (13- and 23-col kinds only). */
    public static final int COL_CV_X = 10;
    /** Yi-Wang S_x(V/K) (13- and 23-col kinds only). */
    public static final int COL_S_X = 11;
    /** Yi-Wang N_x(e/cm^3) (13- and 23-col kinds only). */
    public static final int COL_N_X = 12;

    /** File family decoded from the per-row token count and header names. */
    public enum FileKind {
        /** .trace: 10 columns, isotropic (trace/3) transport values. */
        TRACE,
        /**
         * Fork .trace with the Yi-Wang cv_x/S_x/N_x triple appended:
         * 13 columns, header certificates required.
         */
        TRACE_DOPE_X,
        /**
         * Fork .dope.trace (save_traceEXT): 23 columns, first column
         * certified as mu-Ef[eV] by the header.
         */
        TRACE_DOPE_EXT,
        /** .condtens: 30 columns, full 3x3 transport tensors in Fortran order. */
        CONDTENS,
        /**
         * .halltens: 30 columns, rank-3 Hall tensor (Fortran ravel
         * i + 3j + 9k), parsed as Hall data since the save_halltens writer
         * was pinned verbatim (batch 172).
         */
        HALLTENS,
        /**
         * Batch-109 residual: a 30-column tensor table whose semantics the
         * header does not certify. Before batch 172 every .halltens took
         * this branch (rows skipped); kept for genuinely unknowable
         * headers - nothing is ever re-interpreted silently.
         */
        TENSOR_OTHER
    }

    /** One parsed row of the trace-family kinds (10-, 13-, and 23-column). */
    public static final class TransportRow {
        private final double columnOne;
        private final boolean columnOneEv;
        private final double temperatureK;
        private final double carriersPerCell;
        private final double seebeckVK;
        private final double sigmaOverTau;
        private final double kappaOverTau;
        private final double[] seebeckDiag; // condtens only, else null
        private final double[] fullRow;     // every written cell, verbatim
        private final double[] extras;      // cv_x/S_x/N_x for the 13/23-col kinds

        TransportRow(double muRy, double temperatureK, double carriersPerCell,
                double seebeckVK, double sigmaOverTau, double kappaOverTau,
                double[] seebeckDiag) {
            this(muRy, false, temperatureK, carriersPerCell, seebeckVK,
                    sigmaOverTau, kappaOverTau, seebeckDiag, null, null);
        }

        TransportRow(double columnOne, boolean columnOneEv, double temperatureK,
                double carriersPerCell, double seebeckVK, double sigmaOverTau,
                double kappaOverTau, double[] seebeckDiag, double[] fullRow,
                double[] extras) {
            this.columnOne = columnOne;
            this.columnOneEv = columnOneEv;
            this.temperatureK = temperatureK;
            this.carriersPerCell = carriersPerCell;
            this.seebeckVK = seebeckVK;
            this.sigmaOverTau = sigmaOverTau;
            this.kappaOverTau = kappaOverTau;
            this.seebeckDiag = seebeckDiag;
            this.fullRow = fullRow;
            this.extras = extras;
        }

        /**
         * Chemical potential column AS WRITTEN: Ef[Ry] for .trace/.condtens
         * (Ha doubled to Ry by the writer); mu-Ef[eV] for the fork's
         * .dope.trace. The parser's {@link #getColumnOneNote()} names which.
         */
        public double getMuRy() { return this.columnOne; }
        /** Whether the file itself writes the first column already in eV. */
        public boolean isColumnOneEv() { return this.columnOneEv; }
        /** First column in eV: verbatim for .dope.trace, Ry*RY_TO_EV otherwise. */
        public double getMuEv() {
            return this.columnOneEv ? this.columnOne : this.columnOne * RY_TO_EV;
        }
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
        /** Every numeric cell of the row exactly as written (never null after batch 172). */
        public double[] getFullRow() {
            return this.fullRow == null ? null : this.fullRow.clone();
        }
        /**
         * 13/23-column kinds: the Yi-Wang extension cells - {cv_x, S_x, N_x}
         * for the 13-column kind; {cv_x, S_x, N_x, L_x, L0_h, L0_e, M_h,
         * M_e, N_h, N_e, deltaE(eV), tau(s), tau_1(s)} for the 23-column
         * .dope.trace (save_traceEXT's own print order, verbatim); null
         * otherwise.
         */
        public double[] getExtras() {
            return this.extras == null ? null : this.extras.clone();
        }
    }

    /** One parsed .halltens row: (T, N) header trio plus the rank-3 Hall tensor. */
    public static final class HallRow {
        private final double muRy;
        private final double temperatureK;
        private final double carriersPerCell;
        private final double[] rh27;

        HallRow(double muRy, double temperatureK, double carriersPerCell,
                double[] rh27) {
            this.muRy = muRy;
            this.temperatureK = temperatureK;
            this.carriersPerCell = carriersPerCell;
            this.rh27 = rh27.clone();
        }

        /** Chemical potential as written (Ef[Ry]; Ha doubled by the writer). */
        public double getMuRy() { return this.muRy; }
        public double getMuEv() { return this.muRy * RY_TO_EV; }
        public double getTemperatureK() { return this.temperatureK; }
        public double getCarriersPerCell() { return this.carriersPerCell; }
        /** All 27 tensor cells, Fortran-raveled ({@code i + 3j + 9k}). */
        public double[] getRhTensor() { return this.rh27.clone(); }
        /** Component RH_{ijk} with 0-based indices (Fortran ravel i + 3j + 9k). */
        public double getRhComponent(int i, int j, int k) {
            if (i < 0 || i > 2 || j < 0 || j > 2 || k < 0 || k > 2) {
                throw new IllegalArgumentException(
                        "Hall indices must be 0..2 (upstream needs exactly three)");
            }
            return this.rh27[i + 3 * j + 9 * k];
        }
        /**
         * save_trace's own scalar estimate: the mean over the even
         * permutations (0,1,2) + (2,0,1) + (1,2,0) - pinned verbatim from
         * the upstream 'ohall' expression.
         */
        public double getRhEvenPermutationAverage() {
            return (this.rh27[21] + this.rh27[11] + this.rh27[7]) / 3.0;
        }
    }

    private FileKind kind;
    private String sigmaUnits = "";
    private String kappaUnits = "";
    private String scatteringModel = "";
    private String familyNote = "";
    private String columnOneNote = "Ef[Ry] as written";
    private boolean headerSeen;
    private boolean headerNamedTransport;
    private boolean headerNamedHall;
    private boolean headerCertifiedTraceX;
    private boolean headerCertifiedDopeExt;
    private boolean uncertifiedNoted;
    private final List<TransportRow> rows = new ArrayList<>();
    private final List<HallRow> hallRows = new ArrayList<>();
    private final LinkedHashSet<Double> temperatures = new LinkedHashSet<>();
    private int skippedRows;

    public BoltzTrap2TraceParser(ProjectProperty property) {
        super(property);
    }

    /** File family locked from the first clean data row; null when no clean row parsed. */
    public FileKind getFileKind() { return this.kind; }
    public List<TransportRow> getRows() { return List.copyOf(this.rows); }
    /** .halltens rows (only populated when the kind is HALLTENS). */
    public List<HallRow> getHallRows() { return List.copyOf(this.hallRows); }
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
    /** First-column unit semantics verbatim from the header ('Ef[Ry]' or 'mu-Ef[eV]'). */
    public String getColumnOneNote() { return this.columnOneNote; }

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

    /** Largest |RH even-permutation average| over the .halltens rows (null when none). */
    public Double maxAbsHallAverage() {
        Double best = null;
        for (HallRow row : this.hallRows) {
            double v = Math.abs(row.getRhEvenPermutationAverage());
            if (best == null || v > best) {
                best = v;
            }
        }
        return best;
    }

    @Override
    public void parse(File file) throws IOException {
        this.kind = null;
        this.rows.clear();
        this.hallRows.clear();
        this.temperatures.clear();
        this.sigmaUnits = "";
        this.kappaUnits = "";
        this.scatteringModel = "unknown";
        this.familyNote = "";
        this.columnOneNote = "Ef[Ry] as written";
        this.skippedRows = 0;
        this.headerSeen = false;
        this.headerNamedTransport = false;
        this.headerNamedHall = false;
        this.headerCertifiedTraceX = false;
        this.headerCertifiedDopeExt = false;
        this.uncertifiedNoted = false;
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
        if (!this.headerSeen && "unknown".equals(this.scatteringModel)) {
            this.scatteringModel = "no header";
        }
        if (this.kind == null && this.skippedRows > 0 && this.familyNote.isEmpty()) {
            this.familyNote = "no clean data row matched the BoltzTraP2 column"
                    + " grammar (10 .trace / 13 trace+cv_x / 23 .dope.trace / 30 tensor)";
        }
    }

    private void readHeader(String headerText) {
        String[] tokens = headerText.split("\\s+");
        String joined = " " + String.join(" ", tokens) + " ";
        boolean certCvX = false;
        boolean certSX = false;
        boolean certNX = false;
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
            if (token.startsWith("cv_x[")) {
                certCvX = true;
            }
            if (token.startsWith("S_x(")) {
                certSX = true;
            }
            if (token.startsWith("N_x(")) {
                certNX = true;
            }
            if (token.equals("mu-Ef[eV]")) {
                this.headerCertifiedDopeExt = true;
                this.columnOneNote = "mu-Ef[eV] as written (fork .dope.trace)";
            }
        }
        this.headerCertifiedTraceX = certCvX && certSX && certNX;
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
            // family decision: the EXACT column grammar of the writers,
            // cross-certified by the headers. 13/23-column rows are refused
            // unless the header carries the Yi-Wang unit tokens.
            if (tokens.length == TRACE_COLUMNS) {
                this.kind = FileKind.TRACE;
                this.familyNote = this.headerSeen
                        ? "trace family confirmed by 10-column data rows"
                        : "no header: 10-column rows taken as BoltzTraP .trace grammar";
            } else if (tokens.length == TRACE_X_COLUMNS) {
                if (this.headerCertifiedTraceX) {
                    this.kind = FileKind.TRACE_DOPE_X;
                    this.familyNote = "13-column rows certified by the header's"
                            + " cv_x/S_x/N_x tokens (Yi Wang 09/24/2020 cv_x writer branch)";
                } else {
                    noteUncertified(13);
                    this.skippedRows++;
                    return;
                }
            } else if (tokens.length == DOPE_EXT_COLUMNS) {
                if (this.headerCertifiedDopeExt) {
                    this.kind = FileKind.TRACE_DOPE_EXT;
                    this.familyNote = "23-column rows certified by the header's"
                            + " mu-Ef[eV] token (save_traceEXT .dope.trace writer)";
                } else {
                    noteUncertified(23);
                    this.skippedRows++;
                    return;
                }
            } else if (tokens.length == TENSOR_COLUMNS) {
                if (this.headerNamedHall && !this.headerNamedTransport) {
                    this.kind = FileKind.HALLTENS;
                    this.familyNote = "header names RH[m**3/C] only: the rank-3 Hall"
                            + " tensor is parsed AS Hall data (save_halltens pinned"
                            + " verbatim at batch 172)";
                } else {
                    this.kind = FileKind.CONDTENS;
                    this.familyNote = this.headerSeen
                            ? "condtens family confirmed by 30-column data rows and"
                                    + " sigma header"
                            : "no header: 30-column rows taken as BoltzTraP"
                                    + " .condtens grammar";
                }
            } else {
                this.skippedRows++;
                return;
            }
        }
        int expected = switch (this.kind) {
            case TRACE -> TRACE_COLUMNS;
            case TRACE_DOPE_X -> TRACE_X_COLUMNS;
            case TRACE_DOPE_EXT -> DOPE_EXT_COLUMNS;
            case CONDTENS, HALLTENS, TENSOR_OTHER -> TENSOR_COLUMNS;
        };
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
        switch (this.kind) {
            case CONDTENS -> {
                // Fortran (column-major) 3x3 blocks: sigma cols 3..11,
                // S cols 12..20, kappa cols 21..29; xx, yy, zz at 0, 4, 8.
                double[] seebeckDiag = new double[] {values[12], values[16], values[20]};
                double sIso = (values[12] + values[16] + values[20]) / 3.0;
                double sigmaIso = (values[3] + values[7] + values[11]) / 3.0;
                double kappaIso = (values[21] + values[25] + values[29]) / 3.0;
                this.rows.add(new TransportRow(values[0], false, values[1], values[2],
                        sIso, sigmaIso, kappaIso, seebeckDiag, values, null));
            }
            case HALLTENS -> {
                // cols 3..29: rank-3 Hall tensor, Fortran ravel i + 3j + 9k
                double[] rh = new double[27];
                System.arraycopy(values, 3, rh, 0, 27);
                this.hallRows.add(new HallRow(values[0], values[1], values[2], rh));
            }
            case TRACE_DOPE_X -> {
                double[] extras = new double[] {values[COL_CV_X], values[COL_S_X],
                        values[COL_N_X]};
                this.rows.add(new TransportRow(values[0], false, values[1], values[2],
                        values[COL_SEEBECK], values[COL_SIGMA], values[COL_KAPPA],
                        null, values, extras));
            }
            case TRACE_DOPE_EXT -> {
                double[] extras = new double[tokens.length - 10];
                System.arraycopy(values, 10, extras, 0, extras.length);
                this.rows.add(new TransportRow(values[0], true, values[1], values[2],
                        values[COL_SEEBECK], values[COL_SIGMA], values[COL_KAPPA],
                        null, values, extras));
            }
            default -> {
                // trace columns: Ef[Ry] T[K] N[e/uc] DOS S sigma/tau RH kappa cv chi
                this.rows.add(new TransportRow(values[0], false, values[1], values[2],
                        values[COL_SEEBECK], values[COL_SIGMA], values[COL_KAPPA],
                        null, values, null));
            }
        }
        this.temperatures.add(values[1]);
    }

    private void noteUncertified(int width) {
        if (!this.uncertifiedNoted) {
            this.familyNote = width + "-column rows seen but the header does not"
                    + " certify the Yi-Wang units (cv_x/S_x/N_x for 13, mu-Ef[eV]"
                    + " for 23) - every such row skipped, unit honesty never guessed";
            this.uncertifiedNoted = true;
        }
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
                "BoltzTrap2TraceParser[kind=%s, model=%s, rows=%d, hall=%d, skipped=%d]",
                this.kind, this.scatteringModel, this.rows.size(),
                this.hallRows.size(), this.skippedRows);
    }
}
