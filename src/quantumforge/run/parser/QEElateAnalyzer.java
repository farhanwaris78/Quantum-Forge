/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import quantumforge.operation.OperationResult;

/**
 * ELATE-convention elastic tensor analysis (Roadmap: ELATE integration with
 * the thermo_pw elastic channel). The computations port, formula by formula,
 * the public ELATE code (Gaillac & Coudert, MIT licence) file
 * {@code src/ELATE/elastic.py} and the 3D analysis flow of
 * {@code src/ELATE/elate.py} at upstream commit
 * 0627e636a7c97e8678f71aea44d0851455650d3a (fetched 2026-07-21), with the
 * provenance kept in the report rather than vendored prose:
 *
 * <ul>
 *   <li>input validation mirrored: a 6-row matrix (full, upper- or
 *       lower-triangular), 3 rows refused as a 2D material (ELATE's own
 *       TypeError path), braces/pipes stripped, asymmetry norm &gt; 1e-3
 *       refused, a symmetric PART within 1e-3 symmetrized, singular refused;</li>
 *   <li>compliance S = C^-1 and the rank-4 S_ijkl built with ELATE's own
 *       VoigtMat {{0,5,4},{5,1,3},{4,3,2}} and SVoigtCoeff(p,q) =
 *       1/((1+p//3)(1+q//3));</li>
 *   <li>directional Young E(n) = 1/(aaaa:S), linear compressibility printed
 *       as 1000 * (aa:S contract) i.e. TPa^-1 as elate.py prints it, shear
 *       G(n,t) = 1/(4 abab:S) and Poisson nu(n,t) = -(aabb:S)/(aaaa:S) with
 *       dirVec / dirVec2 verbatim;</li>
 *   <li>Voigt/Reuss/Hill averages in ELATE's formula order (KV=(A+2B)/3,
 *       GV=(A-B+3C)/5, KR=1/(3a+6b), GR=5/(4a-4b+3c), Hill means,
 *       E=1/(1/(3G)+1/(9K)), nu=(1-3G/(3K+G))/2) - numerically identical to
 *       QuantumForge's own QETensorAnalyzer (#125) BY TEST, duplicated here
 *       because ELATE's own control flow reports averages BEFORE the
 *       positive-definiteness gate while #125 refuses non-SPD tensors
 *       outright - the ELATE surface must mirror ELATE, not #125;</li>
 *   <li>eigenvalues sorted ascending and the mechanical-stability gate at
 *       lambda_min &lt;= 0 - averages and eigenvalues are always reported,
 *       the spatial-variation extrema are NOT computed for an unstable
 *       crystal, exactly like elate_main_3D prints 'No further analysis
 *       will be performed';</li>
 *   <li>extrema by the same scheme as elastic.minimize/maximize: brute grid
 *       (25x25 for the 2-angle Young/LC, 10x10x10 for the 3-angle
 *       shear/Poisson over [0,pi] ranges, mirroring optimize.brute Ns) plus
 *       a Nelder-Mead refine (mirroring finish=fmin; the scipy internals
 *       differ in the last digits, so test pins use tolerance, stated
 *       honestly - the scheme and ranges are ELATE's own);</li>
 *   <li>plane-cut polar curves for (xy), (xz), (yz) exactly as elate.py
 *       plots them: Young/LC sample elas.X([pi/2, x]), elas.X([x, 0]),
 *       elas.X([x, pi/2]); shear and Poisson bands min/max over chi at each
 *       in-plane angle (this implementation scans chi on a dense 181-point
 *       grid - MORE exhaustive than ELATE's single Powell run from pi/2,
 *       stated rather than hidden).</li>
 * </ul>
 *
 * <p>Units: ELATE itself expects GPa everywhere. thermo_pw prints elastic
 * constants in kbar (its own stdout aid line reads "10 kbar = 1 GPa"), so a
 * KBAR declaration converts by exactly 0.1 - a named, stated factor, never
 * an inference. Every reported modulus is in GPa, every linear
 * compressibility in TPa^-1, Poisson dimensionless. Nothing here executes
 * jobs or writes files.</p>
 */
public final class QEElateAnalyzer {

    /** Maximum input-text size (a 6x6 matrix is tiny; this bounds abuse). */
    public static final int MAX_TEXT_CHARS = 64 * 1024;
    /** ELATE's own asymmetry acceptance norm (elastic.py __init__). */
    public static final double ASYMMETRY_TOLERANCE = 1.0e-3;
    /** Brute-grid mirror of elastic.minimize/maximize for 2-angle problems. */
    public static final int GRID_2D = 25;
    /** Brute-grid mirror for the 3-angle shear/Poisson problems. */
    public static final int GRID_3D = 10;
    /** Dense chi scan resolution for the plane-cut shear/Poisson bands. */
    public static final int BAND_CHI_STEPS = 181;
    /** '10 kbar = 1 GPa' - thermo_pw's own printed conversion aid + SI. */
    public static final double KBAR_TO_GPA = 0.1;

    private QEElateAnalyzer() {
        // Utility
    }

    /** Input-scale declaration (never inferred from magnitude). */
    public enum ElateUnit {
        /** ELATE's native scale (multiplier 1). */
        GPA(1.0),
        /** thermo_pw's printed scale; converts by 0.1 per '10 kbar = 1 GPa'. */
        KBAR(QEElateAnalyzer.KBAR_TO_GPA);

        private final double toGpa;

        ElateUnit(double toGpa) {
            this.toGpa = toGpa;
        }

        /** Multiplier converting the declared scale into GPa. */
        public double getToGpa() { return this.toGpa; }
    }

    /** Polar plane keys mirroring elate.py's three plots. */
    public enum Plane {
        /** (xy): direction dirVec(pi/2, x). */
        XY("(xy) plane"),
        /** (xz): direction dirVec(x, 0). */
        XZ("(xz) plane"),
        /** (yz): direction dirVec(x, pi/2). */
        YZ("(yz) plane");

        private final String label;

        Plane(String label) {
            this.label = label;
        }

        public String getLabel() { return this.label; }
    }

    /** Voigt/Reuss/Hill row of the average-property table (ELATE order). */
    public static final class AverageRow {
        private final String scheme;
        private final double bulkGpa;
        private final double youngGpa;
        private final double shearGpa;
        private final double poisson;

        AverageRow(String scheme, double bulkGpa, double shearGpa) {
            this.scheme = scheme;
            this.bulkGpa = bulkGpa;
            this.shearGpa = shearGpa;
            // ELATE's own derived-column formulas (elate.py averages()).
            this.youngGpa = 1.0 / (1.0 / (3.0 * shearGpa) + 1.0 / (9.0 * bulkGpa));
            this.poisson = (1.0 - 3.0 * shearGpa / (3.0 * bulkGpa + shearGpa)) / 2.0;
        }

        public String getScheme() { return this.scheme; }
        public double getBulkGpa() { return this.bulkGpa; }
        public double getYoungGpa() { return this.youngGpa; }
        public double getShearGpa() { return this.shearGpa; }
        public double getPoisson() { return this.poisson; }
    }

    /** One extremum: value plus direction angles and the cartesian axes. */
    public static final class Extremum {
        private final String quantity;
        private final boolean maximum;
        private final double value;
        private final double theta;
        private final double phi;
        private final double chi;
        private final double[] firstAxis;
        private final double[] secondAxis;

        Extremum(String quantity, boolean maximum, double value,
                 double theta, double phi, double chi) {
            this.quantity = quantity;
            this.maximum = maximum;
            this.value = value;
            this.theta = theta;
            this.phi = phi;
            this.chi = chi;
            this.firstAxis = chiPresent(chi) ? dirVec1(theta, phi, chi) : dirVec(theta, phi);
            this.secondAxis = chiPresent(chi) ? dirVec2(theta, phi, chi) : null;
        }

        private static boolean chiPresent(double chi) {
            return !Double.isNaN(chi);
        }

        public String getQuantity() { return this.quantity; }
        public boolean isMaximum() { return this.maximum; }
        public double getValue() { return this.value; }
        public double getTheta() { return this.theta; }
        public double getPhi() { return this.phi; }
        /** Rotation angle of the second axis; NaN for 2-angle quantities. */
        public double getChi() { return this.chi; }
        /** first axis (ELATE prints dirVec / dirVec1 per quantity). */
        public double[] getFirstAxis() { return this.firstAxis.clone(); }
        /** second axis for shear/Poisson; null for Young/LC. */
        public double[] getSecondAxis() {
            return this.secondAxis == null ? null : this.secondAxis.clone();
        }
    }

    /** min/max over chi at one in-plane angle (the shear/Poisson band). */
    public static final class Band {
        private final double min;
        private final double max;

        Band(double min, double max) {
            this.min = min;
            this.max = max;
        }

        public double getMin() { return this.min; }
        public double getMax() { return this.max; }
    }

    /** The whole ELATE-flavoured analysis result. */
    public static final class ElateReport {
        private final ElateUnit declaredUnit;
        private final double[][] stiffnessGpa;
        private final double[][] compliance;
        private final List<String> notes;
        private final List<AverageRow> averages;
        private final double[] eigenvaluesGpa;
        private final boolean mechanicallyStable;
        private final Extremum minYoung;
        private final Extremum maxYoung;
        private final Extremum minLc;
        private final Extremum maxLc;
        private final Extremum minShear;
        private final Extremum maxShear;
        private final Extremum minPoisson;
        private final Extremum maxPoisson;
        private final String youngAnisotropy;
        private final String lcAnisotropy;
        private final String shearAnisotropy;
        private final String poissonAnisotropy;
        private final double[][][][] smat;

        ElateReport(ElateUnit declaredUnit, double[][] stiffnessGpa, double[][] compliance,
                    double[][][][] smat, List<String> notes, List<AverageRow> averages,
                    double[] eigenvaluesGpa, boolean mechanicallyStable,
                    Extremum minYoung, Extremum maxYoung, Extremum minLc, Extremum maxLc,
                    Extremum minShear, Extremum maxShear,
                    Extremum minPoisson, Extremum maxPoisson,
                    String youngAnisotropy, String lcAnisotropy,
                    String shearAnisotropy, String poissonAnisotropy) {
            this.declaredUnit = declaredUnit;
            this.stiffnessGpa = stiffnessGpa;
            this.compliance = compliance;
            this.smat = smat;
            this.notes = notes;
            this.averages = averages;
            this.eigenvaluesGpa = eigenvaluesGpa;
            this.mechanicallyStable = mechanicallyStable;
            this.minYoung = minYoung;
            this.maxYoung = maxYoung;
            this.minLc = minLc;
            this.maxLc = maxLc;
            this.minShear = minShear;
            this.maxShear = maxShear;
            this.minPoisson = minPoisson;
            this.maxPoisson = maxPoisson;
            this.youngAnisotropy = youngAnisotropy;
            this.lcAnisotropy = lcAnisotropy;
            this.shearAnisotropy = shearAnisotropy;
            this.poissonAnisotropy = poissonAnisotropy;
        }

        /** Unit the caller declared for the input (never inferred). */
        public ElateUnit getDeclaredUnit() { return this.declaredUnit; }
        /** Symmetrized 6x6 stiffness in GPa (ELATE native scale). */
        public double[][] getStiffnessGpa() { return copy6(this.stiffnessGpa); }
        /** 6x6 engineering compliance in GPa^-1 (inverse of the stiffness). */
        public double[][] getCompliance() { return copy6(this.compliance); }
        public List<String> getNotes() { return this.notes; }
        public List<AverageRow> getAverages() { return this.averages; }
        /** The six eigenvalues sorted ascending, in GPa. */
        public double[] getEigenvaluesGpa() { return this.eigenvaluesGpa.clone(); }
        /** lambda_min > 0 (ELATE's positive-definiteness gate). */
        public boolean isMechanicallyStable() { return this.mechanicallyStable; }
        /** min/max Young; null when the stability gate blocked extrema. */
        public Extremum getMinYoung() { return this.minYoung; }
        public Extremum getMaxYoung() { return this.maxYoung; }
        /** min/max linear compressibility (TPa^-1); null when gated. */
        public Extremum getMinLc() { return this.minLc; }
        public Extremum getMaxLc() { return this.maxLc; }
        /** min/max shear; null when gated. */
        public Extremum getMinShear() { return this.minShear; }
        public Extremum getMaxShear() { return this.maxShear; }
        /** min/max Poisson; null when gated. */
        public Extremum getMinPoisson() { return this.minPoisson; }
        public Extremum getMaxPoisson() { return this.maxPoisson; }
        /** Rendered anisotropy text ('2.429', or 'INFINITE' where ELATE prints infinity). */
        public String getYoungAnisotropy() { return this.youngAnisotropy; }
        public String getLcAnisotropy() { return this.lcAnisotropy; }
        public String getShearAnisotropy() { return this.shearAnisotropy; }
        public String getPoissonAnisotropy() { return this.poissonAnisotropy; }

        /** Directional Young modulus in GPa (full rank-4 contraction). */
        public double youngGpa(double theta, double phi) {
            double[] a = dirVec(theta, phi);
            double r = 0.0;
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    for (int k = 0; k < 3; k++) {
                        for (int l = 0; l < 3; l++) {
                            r += a[i] * a[j] * a[k] * a[l] * this.smat[i][j][k][l];
                        }
                    }
                }
            }
            return 1.0 / r; // ELATE Young() verbatim: 1/(aaaa:S)
        }

        /** Directional linear compressibility in TPa^-1 (ELATE's 1000x print). */
        public double lcTPa(double theta, double phi) {
            double[] a = dirVec(theta, phi);
            double r = 0.0;
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    for (int k = 0; k < 3; k++) {
                        r += a[i] * a[j] * this.smat[i][j][k][k];
                    }
                }
            }
            return 1000.0 * r; // ELATE LC() verbatim
        }

        /** Directional shear modulus in GPa for normal n and transverse t=dirVec2. */
        public double shearGpa(double theta, double phi, double chi) {
            double[] a = dirVec(theta, phi);
            double[] b = dirVec2(theta, phi, chi);
            double r = 0.0;
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    for (int k = 0; k < 3; k++) {
                        for (int l = 0; l < 3; l++) {
                            r += a[i] * b[j] * a[k] * b[l] * this.smat[i][j][k][l];
                        }
                    }
                }
            }
            return 1.0 / (4.0 * r); // ELATE shear() verbatim
        }

        /** Directional Poisson ratio (dimensionless; can be negative). */
        public double poisson(double theta, double phi, double chi) {
            double[] a = dirVec(theta, phi);
            double[] b = dirVec2(theta, phi, chi);
            double r1 = 0.0;
            double r2 = 0.0;
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    for (int k = 0; k < 3; k++) {
                        for (int l = 0; l < 3; l++) {
                            r1 += a[i] * a[j] * b[k] * b[l] * this.smat[i][j][k][l];
                            r2 += a[i] * a[j] * a[k] * a[l] * this.smat[i][j][k][l];
                        }
                    }
                }
            }
            return -r1 / r2; // ELATE Poisson() verbatim
        }

        /** In-plane direction at polar angle x, mirroring elate.py's plots. */
        public static double[] planeDirection(Plane plane, double x) {
            switch (plane) {
                case XY: return dirVec(Math.PI / 2.0, x);
                case XZ: return dirVec(x, 0.0);
                case YZ: return dirVec(x, Math.PI / 2.0);
                default: throw new IllegalStateException("unmapped plane " + plane);
            }
        }

        /** Young polar curve over [0, 2pi) with n samples (as elate.py plots). */
        public double[] polarYoung(Plane plane, int n) {
            double[] radii = new double[n];
            for (int s = 0; s < n; s++) {
                double x = 2.0 * Math.PI * s / n;
                double[] a = planeDirection(plane, x);
                double theta = Math.acos(clampUnit(a[2]));
                double phi = Math.atan2(a[1], a[0]);
                radii[s] = youngGpa(theta, phi);
            }
            return radii;
        }

        /** Linear-compressibility polar curve over [0, 2pi), TPa^-1. */
        public double[] polarLc(Plane plane, int n) {
            double[] radii = new double[n];
            for (int s = 0; s < n; s++) {
                double x = 2.0 * Math.PI * s / n;
                double[] a = planeDirection(plane, x);
                double theta = Math.acos(clampUnit(a[2]));
                double phi = Math.atan2(a[1], a[0]);
                radii[s] = lcTPa(theta, phi);
            }
            return radii;
        }

        /** Shear band over the plane: min/max over chi at each polar angle. */
        public Band[] polarShearBand(Plane plane, int n) {
            Band[] bands = new Band[n];
            for (int s = 0; s < n; s++) {
                double x = 2.0 * Math.PI * s / n;
                double[] a = planeDirection(plane, x);
                double theta = Math.acos(clampUnit(a[2]));
                double phi = Math.atan2(a[1], a[0]);
                double min = Double.POSITIVE_INFINITY;
                double max = Double.NEGATIVE_INFINITY;
                for (int c = 0; c < BAND_CHI_STEPS - 1; c++) {
                    double chi = Math.PI * c / (BAND_CHI_STEPS - 2.0);
                    double g = shearGpa(theta, phi, chi);
                    min = Math.min(min, g);
                    max = Math.max(max, g);
                }
                bands[s] = new Band(min, max);
            }
            return bands;
        }

        /** Poisson band over the plane: min/max over chi at each polar angle. */
        public Band[] polarPoissonBand(Plane plane, int n) {
            Band[] bands = new Band[n];
            for (int s = 0; s < n; s++) {
                double x = 2.0 * Math.PI * s / n;
                double[] a = planeDirection(plane, x);
                double theta = Math.acos(clampUnit(a[2]));
                double phi = Math.atan2(a[1], a[0]);
                double min = Double.POSITIVE_INFINITY;
                double max = Double.NEGATIVE_INFINITY;
                for (int c = 0; c < BAND_CHI_STEPS - 1; c++) {
                    double chi = Math.PI * c / (BAND_CHI_STEPS - 2.0);
                    double nu = poisson(theta, phi, chi);
                    min = Math.min(min, nu);
                    max = Math.max(max, nu);
                }
                bands[s] = new Band(min, max);
            }
            return bands;
        }
    }

    /**
     * Full ELATE-style analysis of one matrix text. Verdicts: ELATE_INPUT
     * (null/oversized), ELATE_SHAPE (row/column grammar broken, non-number),
     * ELATE_2D (3 rows - ELATE refuses 2D materials here and so do we),
     * ELATE_ASYMMETRIC (asymmetry norm above ELATE's 1e-3 acceptance),
     * ELATE_SINGULAR (no inverse, ELATE 'matrix is singular'), ELATE_OK.
     */
    public static OperationResult<ElateReport> analyze(String matrixText, ElateUnit unit) {
        if (matrixText == null || unit == null) {
            return OperationResult.failed("ELATE_INPUT",
                    "A matrix text and its declared unit are both required - the unit is"
                            + " never inferred.", null);
        }
        if (matrixText.length() > MAX_TEXT_CHARS) {
            return OperationResult.failed("ELATE_INPUT",
                    "The matrix text exceeds the " + MAX_TEXT_CHARS
                            + "-character bound; refusing an unbounded read.", null);
        }
        String cleaned = matrixText.replace("|", " ").replace("(", " ")
                .replace(")", " ").replace("[", " ").replace("]", " ")
                .replace(",", " ").replace(";", "\n");
        List<String> lines = new ArrayList<>();
        for (String raw : cleaned.split("\n")) {
            if (!raw.trim().isEmpty()) {
                lines.add(raw.trim());
            }
        }
        if (lines.size() == 3) {
            return OperationResult.failed("ELATE_2D",
                    "Three rows: ELATE classifies this as a 2D material (its own TypeError"
                            + " path) - this integration covers the 3D 6x6 flow only.", null);
        }
        if (lines.size() != 6) {
            return OperationResult.failed("ELATE_SHAPE",
                    "Expected six rows (full or triangular 6,5,4,3,2,1 / 1..6 tokens),"
                            + " found " + lines.size() + ": " + shapeMessage(lines), null);
        }
        List<double[]> rows = new ArrayList<>();
        List<Integer> widths = new ArrayList<>();
        for (String line : lines) {
            String[] tokens = line.split("\\s+");
            double[] row = new double[tokens.length];
            for (int t = 0; t < tokens.length; t++) {
                double value = QEThermoPwSeriesParser.parseFortranDouble(tokens[t]);
                if (Double.isNaN(value) || !Double.isFinite(value)) {
                    return OperationResult.failed("ELATE_SHAPE",
                            "Row " + (rows.size() + 1) + " token '" + tokens[t]
                                    + "' is not a finite number - 'not all entries are"
                                    + " numbers' (ELATE's own refusal).", null);
                }
                row[t] = value;
            }
            rows.add(row);
            widths.add(Integer.valueOf(tokens.length));
        }
        double[][] mirrored = mirror(rows, widths);
        if (mirrored == null) {
            return OperationResult.failed("ELATE_SHAPE",
                    "Row widths " + widths + " are not a 6x6 full or triangular matrix"
                            + " (accepted: 6/6/6/6/6/6, 6/5/4/3/2/1, 1/2/3/4/5/6).", null);
        }
        double asymNorm = asymmetryNorm(mirrored);
        List<String> notes = new ArrayList<>();
        if (asymNorm > ASYMMETRY_TOLERANCE) {
            return OperationResult.failed("ELATE_ASYMMETRIC",
                    String.format(Locale.ROOT,
                            "Asymmetry norm %.6g exceeds ELATE's own 1e-3 acceptance"
                                    + " ('should be symmetric, or triangular') - not symmetrized.",
                            asymNorm), null);
        }
        if (asymNorm > 0.0) {
            notes.add(String.format(Locale.ROOT,
                    "mirrored ELATE behaviour: asymmetry norm %.6g within 1e-3 ->"
                            + " tensor symmetrized as 0.5 (C + C^T)", asymNorm));
            mirrored = symmetrize(mirrored);
        }
        double[][] stiffnessGpa = new double[6][6];
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                stiffnessGpa[i][j] = mirrored[i][j] * unit.getToGpa();
            }
        }
        double[][] compliance = invert6OrNull(stiffnessGpa);
        if (compliance == null) {
            return OperationResult.failed("ELATE_SINGULAR",
                    "The stiffness matrix is singular ('matrix is singular' in ELATE) -"
                            + " no compliance, no directional properties are computed.", null);
        }
        if (unit == ElateUnit.KBAR) {
            notes.add("input declared kbar -> GPa by exactly 0.1 ('10 kbar = 1 GPa',"
                    + " thermo_pw's own printed conversion aid; never an inference)");
        }
        double[][][][] smat = buildSmat(compliance);
        List<AverageRow> averages = computeAverages(stiffnessGpa, compliance);
        double[] eigenvalues = eigenvalues6(stiffnessGpa);
        boolean stable = eigenvalues[0] > 0.0;
        if (!stable) {
            notes.add("Stiffness matrix is not positive definite (lambda_min = "
                    + String.format(Locale.ROOT, "%.6g", eigenvalues[0])
                    + " GPa): the crystal is mechanically unstable; averages and eigenvalues"
                    + " are reported and the spatial-variation extrema are NOT computed,"
                    + " mirroring elate_main_3D's 'No further analysis will be performed.'");
        }
        Extremum minYoung = null;
        Extremum maxYoung = null;
        Extremum minLc = null;
        Extremum maxLc = null;
        Extremum minShear = null;
        Extremum maxShear = null;
        Extremum minPoisson = null;
        Extremum maxPoisson = null;
        String youngAnis = null;
        String lcAnis = null;
        String shearAnis = null;
        String poissonAnis = null;
        if (stable) {
            double[][][][] smatRef = smat;
            java.util.function.ToDoubleFunction<double[]> young = x -> youngRaw(smatRef,
                    x[0], x[1]);
            java.util.function.ToDoubleFunction<double[]> lc = x -> lcRaw(smatRef,
                    x[0], x[1]);
            java.util.function.ToDoubleFunction<double[]> shear = x -> shearRaw(smatRef,
                    x[0], x[1], x[2]);
            java.util.function.ToDoubleFunction<double[]> poisson = x -> poissonRaw(smatRef,
                    x[0], x[1], x[2]);
            double[] lo = gridRefine(young, 2, false);
            double[] hi = gridRefine(young, 2, true);
            minYoung = extremum("Young's modulus", false, young.applyAsDouble(lo), lo);
            maxYoung = extremum("Young's modulus", true, young.applyAsDouble(hi), hi);
            double[] llo = gridRefine(lc, 2, false);
            double[] lhi = gridRefine(lc, 2, true);
            minLc = extremum("Linear compressibility", false, lc.applyAsDouble(llo), llo);
            maxLc = extremum("Linear compressibility", true, lc.applyAsDouble(lhi), lhi);
            double[] slo = gridRefine(shear, 3, false);
            double[] shi = gridRefine(shear, 3, true);
            minShear = extremum("Shear modulus", false, shear.applyAsDouble(slo), slo);
            maxShear = extremum("Shear modulus", true, shear.applyAsDouble(shi), shi);
            double[] plo = gridRefine(poisson, 3, false);
            double[] phi3 = gridRefine(poisson, 3, true);
            minPoisson = extremum("Poisson's ratio", false, poisson.applyAsDouble(plo), plo);
            maxPoisson = extremum("Poisson's ratio", true, poisson.applyAsDouble(phi3), phi3);
            youngAnis = formatAnisotropy(maxYoung.getValue(), minYoung.getValue(), "%.4g");
            lcAnis = minLc.getValue() > 0.0
                    ? formatAnisotropy(maxLc.getValue(), minLc.getValue(), "%.4f") : "INFINITE";
            shearAnis = formatAnisotropy(maxShear.getValue(), minShear.getValue(), "%.4g");
            poissonAnis = minPoisson.getValue() * maxPoisson.getValue() > 0.0
                    ? formatAnisotropy(maxPoisson.getValue(), minPoisson.getValue(), "%.4f")
                    : "INFINITE";
        }
        return OperationResult.success("ELATE_OK",
                stable ? "Analysis complete: averages, eigenvalues, spatial extrema and"
                        + " polar curves ready (GPa / TPa^-1 - ELATE native scales)."
                        : "Stability gate tripped: averages and eigenvalues reported;",
                new ElateReport(unit, stiffnessGpa, compliance, smat, notes, averages,
                        eigenvalues, stable, minYoung, maxYoung, minLc, maxLc, minShear,
                        maxShear, minPoisson, maxPoisson, youngAnis, lcAnis, shearAnis,
                        poissonAnis));
    }

    private static Extremum extremum(String name, boolean max, double value, double[] angles) {
        return new Extremum(name, max, value, angles[0], angles[1],
                angles.length > 2 ? angles[2] : Double.NaN);
    }

    private static String formatAnisotropy(double max, double min, String format) {
        return String.format(Locale.ROOT, format, max / min);
    }

    private static String shapeMessage(List<String> lines) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < Math.min(lines.size(), 2); i++) {
            text.append(i == 0 ? "first rows: '" : "' / '")
                    .append(lines.get(i).length() <= 48 ? lines.get(i)
                            : lines.get(i).substring(0, 45) + "...");
        }
        return text.append("'").toString();
    }

    /** Mirrors ELATE's triangular acceptance; null when widths fit no shape. */
    private static double[][] mirror(List<double[]> rows, List<Integer> widths) {
        boolean full = widths.stream().allMatch(w -> w == 6);
        double[][] mat = new double[6][6];
        if (full) {
            for (int i = 0; i < 6; i++) {
                System.arraycopy(rows.get(i), 0, mat[i], 0, 6);
            }
            return mat;
        }
        boolean upper = true;
        for (int i = 0; i < 6; i++) {
            if (widths.get(i) != 6 - i) {
                upper = false;
                break;
            }
        }
        if (upper) {
            for (int i = 0; i < 6; i++) {
                System.arraycopy(rows.get(i), 0, mat[i], i, 6 - i); // upper triangle
            }
            // ELATE: np.triu(C) + np.triu(C, 1).T - reflect the triangle so the
            // downstream symmetry check sees a completed matrix, not zeros.
            for (int i = 0; i < 6; i++) {
                for (int j = i + 1; j < 6; j++) {
                    mat[j][i] = mat[i][j];
                }
            }
            return mat;
        }
        boolean lower = true;
        for (int i = 0; i < 6; i++) {
            if (widths.get(i) != i + 1) {
                lower = false;
                break;
            }
        }
        if (lower) {
            for (int i = 0; i < 6; i++) {
                System.arraycopy(rows.get(i), 0, mat[i], 0, i + 1); // lower triangle
            }
            // ELATE: np.tril(C) + np.tril(C, -1).T - reflect, same reason as above.
            for (int i = 1; i < 6; i++) {
                for (int j = 0; j < i; j++) {
                    mat[j][i] = mat[i][j];
                }
            }
            return mat;
        }
        return null;
    }

    private static double asymmetryNorm(double[][] mat) {
        double sum = 0.0;
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                double d = mat[i][j] - mat[j][i];
                sum += d * d;
            }
        }
        return Math.sqrt(sum);
    }

    private static double[][] symmetrize(double[][] mat) {
        double[][] out = new double[6][6];
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) {
                out[i][j] = 0.5 * (mat[i][j] + mat[j][i]);
            }
        }
        return out;
    }

    /** Gauss-Jordan inversion with partial pivoting; null on singularity. */
    private static double[][] invert6OrNull(double[][] matrix) {
        int n = 6;
        double[][] aug = new double[n][2 * n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(matrix[i], 0, aug[i], 0, n);
            aug[i][n + i] = 1.0;
        }
        for (int column = 0; column < n; column++) {
            int pivotRow = column;
            for (int row = column + 1; row < n; row++) {
                if (Math.abs(aug[row][column]) > Math.abs(aug[pivotRow][column])) {
                    pivotRow = row;
                }
            }
            double[] tmp = aug[column];
            aug[column] = aug[pivotRow];
            aug[pivotRow] = tmp;
            double pivot = aug[column][column];
            if (Math.abs(pivot) < 1.0e-300) {
                return null;
            }
            for (int j = 0; j < 2 * n; j++) {
                aug[column][j] /= pivot;
            }
            for (int row = 0; row < n; row++) {
                if (row == column) {
                    continue;
                }
                double factor = aug[row][column];
                if (factor == 0.0) {
                    continue;
                }
                for (int j = 0; j < 2 * n; j++) {
                    aug[row][j] -= factor * aug[column][j];
                }
            }
        }
        double[][] inverse = new double[n][n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(aug[i], n, inverse[i], 0, n);
        }
        return inverse;
    }

    /** ELATE's own S_ijkl assembly (VoigtMat + SVoigtCoeff). */
    private static final int[][] VOIGT = {{0, 5, 4}, {5, 1, 3}, {4, 3, 2}};

    private static double[][][][] buildSmat(double[][] svoigt) {
        double[][][][] smat = new double[3][3][3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                for (int k = 0; k < 3; k++) {
                    for (int l = 0; l < 3; l++) {
                        int p = VOIGT[i][j];
                        int q = VOIGT[k][l];
                        double coeff = 1.0 / ((1 + p / 3) * (1 + q / 3)); // integer div: ELATE //
                        smat[i][j][k][l] = coeff * svoigt[p][q];
                    }
                }
            }
        }
        return smat;
    }

    private static List<AverageRow> computeAverages(double[][] c, double[][] s) {
        double a = (c[0][0] + c[1][1] + c[2][2]) / 3.0;
        double b = (c[1][2] + c[0][2] + c[0][1]) / 3.0;
        double cShear = (c[3][3] + c[4][4] + c[5][5]) / 3.0;
        double sa = (s[0][0] + s[1][1] + s[2][2]) / 3.0;
        double sb = (s[1][2] + s[0][2] + s[0][1]) / 3.0;
        double sc = (s[3][3] + s[4][4] + s[5][5]) / 3.0;
        double kv = (a + 2.0 * b) / 3.0;
        double gv = (a - b + 3.0 * cShear) / 5.0;
        double kr = 1.0 / (3.0 * sa + 6.0 * sb);
        double gr = 5.0 / (4.0 * sa - 4.0 * sb + 3.0 * sc);
        List<AverageRow> rows = new ArrayList<>();
        rows.add(new AverageRow("Voigt", kv, gv));
        rows.add(new AverageRow("Reuss", kr, gr));
        rows.add(new AverageRow("Hill", (kv + kr) / 2.0, (gv + gr) / 2.0));
        return rows;
    }

    /** Cyclic-Jacobi eigenvalues of the symmetric 6x6, sorted ascending. */
    private static double[] eigenvalues6(double[][] matrix) {
        double[][] a = new double[6][6];
        for (int i = 0; i < 6; i++) {
            System.arraycopy(matrix[i], 0, a[i], 0, 6);
        }
        double scale = 0.0;
        for (int i = 0; i < 6; i++) {
            scale = Math.max(scale, Math.abs(a[i][i]));
        }
        double threshold = (scale > 0.0 ? scale : 1.0) * 1.0e-14;
        for (int sweep = 0; sweep < 100; sweep++) {
            double off = 0.0;
            for (int p = 0; p < 6; p++) {
                for (int q = p + 1; q < 6; q++) {
                    off += a[p][q] * a[p][q];
                }
            }
            if (Math.sqrt(off) < threshold) {
                break;
            }
            for (int p = 0; p < 6; p++) {
                for (int q = p + 1; q < 6; q++) {
                    if (Math.abs(a[p][q]) < threshold) {
                        continue;
                    }
                    double app = a[p][p];
                    double aqq = a[q][q];
                    double apq = a[p][q];
                    double phi = 0.5 * Math.atan2(2.0 * apq, aqq - app);
                    double cs = Math.cos(phi);
                    double sn = Math.sin(phi);
                    for (int i = 0; i < 6; i++) {
                        double aip = a[i][p];
                        double aiq = a[i][q];
                        a[i][p] = cs * aip - sn * aiq;
                        a[i][q] = sn * aip + cs * aiq;
                    }
                    for (int i = 0; i < 6; i++) {
                        double api = a[p][i];
                        double aqi = a[q][i];
                        a[p][i] = cs * api - sn * aqi;
                        a[q][i] = sn * api + cs * aqi;
                    }
                }
            }
        }
        double[] eigenvalues = new double[6];
        for (int i = 0; i < 6; i++) {
            eigenvalues[i] = a[i][i];
        }
        java.util.Arrays.sort(eigenvalues); // ascending, like np.sort in ELATE
        return eigenvalues;
    }

    /** dirVec verbatim from elastic.py. */
    public static double[] dirVec(double theta, double phi) {
        return new double[] {Math.sin(theta) * Math.cos(phi),
                Math.sin(theta) * Math.sin(phi), Math.cos(theta)};
    }

    /** dirVec1 verbatim (first in-plane axis, same as dirVec). */
    public static double[] dirVec1(double theta, double phi, double chi) {
        return dirVec(theta, phi);
    }

    /** dirVec2 verbatim (second axis with the chi rotation). */
    public static double[] dirVec2(double theta, double phi, double chi) {
        return new double[] {
                Math.cos(theta) * Math.cos(phi) * Math.cos(chi) - Math.sin(phi) * Math.sin(chi),
                Math.cos(theta) * Math.sin(phi) * Math.cos(chi) + Math.cos(phi) * Math.sin(chi),
                -Math.sin(theta) * Math.cos(chi)};
    }

    private static double clampUnit(double value) {
        return value < -1.0 ? -1.0 : (value > 1.0 ? 1.0 : value);
    }

    private static double youngRaw(double[][][][] smat, double theta, double phi) {
        double[] a = dirVec(theta, phi);
        double r = 0.0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                for (int k = 0; k < 3; k++) {
                    for (int l = 0; l < 3; l++) {
                        r += a[i] * a[j] * a[k] * a[l] * smat[i][j][k][l];
                    }
                }
            }
        }
        return 1.0 / r;
    }

    private static double lcRaw(double[][][][] smat, double theta, double phi) {
        double[] a = dirVec(theta, phi);
        double r = 0.0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                for (int k = 0; k < 3; k++) {
                    r += a[i] * a[j] * smat[i][j][k][k];
                }
            }
        }
        return 1000.0 * r;
    }

    private static double shearRaw(double[][][][] smat, double theta, double phi,
                                   double chi) {
        double[] a = dirVec(theta, phi);
        double[] b = dirVec2(theta, phi, chi);
        double r = 0.0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                for (int k = 0; k < 3; k++) {
                    for (int l = 0; l < 3; l++) {
                        r += a[i] * b[j] * a[k] * b[l] * smat[i][j][k][l];
                    }
                }
            }
        }
        return 1.0 / (4.0 * r);
    }

    private static double poissonRaw(double[][][][] smat, double theta, double phi,
                                     double chi) {
        double[] a = dirVec(theta, phi);
        double[] b = dirVec2(theta, phi, chi);
        double r1 = 0.0;
        double r2 = 0.0;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                for (int k = 0; k < 3; k++) {
                    for (int l = 0; l < 3; l++) {
                        r1 += a[i] * a[j] * b[k] * b[l] * smat[i][j][k][l];
                        r2 += a[i] * a[j] * a[k] * a[l] * smat[i][j][k][l];
                    }
                }
            }
        }
        return -r1 / r2;
    }

    /**
     * ELATE-scheme extrema: brute grid over [0, pi]^dim (GRID_2D / GRID_3D
     * samples per coordinate, exactly the optimize.brute Ns of
     * elastic.minimize/maximize) then a Nelder-Mead refine (the finish=fmin
     * of the same function; last digits may differ from scipy's internals -
     * stated, tolerance-pinned in tests).
     */
    private static double[] gridRefine(java.util.function.ToDoubleFunction<double[]> func,
                                       int dim, boolean maximize) {
        int grid = dim == 2 ? GRID_2D : GRID_3D;
        double best = maximize ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        double[] bestAngles = new double[dim];
        int[] index = new int[dim];
        int total = dim == 2 ? grid * grid : grid * grid * grid;
        for (int flat = 0; flat < total; flat++) {
            int rest = flat;
            for (int d = dim - 1; d >= 0; d--) {
                index[d] = rest % grid;
                rest /= grid;
            }
            double[] angles = new double[dim];
            for (int d = 0; d < dim; d++) {
                angles[d] = Math.PI * index[d] / (grid - 1.0);
            }
            double value = func.applyAsDouble(angles);
            if ((maximize && value > best) || (!maximize && value < best)) {
                best = value;
                System.arraycopy(angles, 0, bestAngles, 0, dim);
            }
        }
        return nelderMead(func, bestAngles, maximize);
    }

    /** Standard Nelder-Mead (alpha=1 gamma=2 rho=0.5 sigma=0.5), bounded. */
    private static double[] nelderMead(java.util.function.ToDoubleFunction<double[]> func,
                                       double[] start, boolean maximize) {
        int dim = start.length;
        double sign = maximize ? -1.0 : 1.0;
        double[][] simplex = new double[dim + 1][dim];
        double[] values = new double[dim + 1];
        for (int i = 0; i <= dim; i++) {
            System.arraycopy(start, 0, simplex[i], 0, dim);
            if (i > 0) {
                simplex[i][i - 1] += 0.05; // starting simplex step around the grid winner
            }
            values[i] = sign * func.applyAsDouble(simplex[i]);
        }
        for (int iteration = 0; iteration < 500; iteration++) {
            int worst = 0;
            int secondWorst = 0;
            int best = 0;
            for (int i = 1; i <= dim; i++) {
                if (values[i] < values[best]) {
                    best = i;
                }
                if (values[i] > values[worst]) {
                    secondWorst = worst;
                    worst = i;
                } else if (values[i] > values[secondWorst] && i != worst) {
                    secondWorst = i;
                }
            }
            double spread = Math.abs(values[worst] - values[best]);
            if (spread < 1.0e-11 * Math.max(1.0, Math.abs(values[best]))) {
                break;
            }
            double[] centroid = new double[dim];
            for (int i = 0; i <= dim; i++) {
                if (i == worst) {
                    continue;
                }
                for (int d = 0; d < dim; d++) {
                    centroid[d] += simplex[i][d] / dim;
                }
            }
            double[] reflected = new double[dim];
            for (int d = 0; d < dim; d++) {
                reflected[d] = centroid[d] + (centroid[d] - simplex[worst][d]);
            }
            double fr = sign * func.applyAsDouble(reflected);
            if (fr < values[best]) {
                double[] expanded = new double[dim];
                for (int d = 0; d < dim; d++) {
                    expanded[d] = centroid[d] + 2.0 * (reflected[d] - centroid[d]);
                }
                double fe = sign * func.applyAsDouble(expanded);
                if (fe < fr) {
                    System.arraycopy(expanded, 0, simplex[worst], 0, dim);
                    values[worst] = fe;
                } else {
                    System.arraycopy(reflected, 0, simplex[worst], 0, dim);
                    values[worst] = fr;
                }
            } else if (fr < values[secondWorst]) {
                System.arraycopy(reflected, 0, simplex[worst], 0, dim);
                values[worst] = fr;
            } else {
                double[] contracted = new double[dim];
                for (int d = 0; d < dim; d++) {
                    contracted[d] = centroid[d] + 0.5 * (simplex[worst][d] - centroid[d]);
                }
                double fc = sign * func.applyAsDouble(contracted);
                if (fc < values[worst]) {
                    System.arraycopy(contracted, 0, simplex[worst], 0, dim);
                    values[worst] = fc;
                } else {
                    for (int i = 0; i <= dim; i++) {
                        if (i == best) {
                            continue;
                        }
                        for (int d = 0; d < dim; d++) {
                            simplex[i][d] = simplex[best][d]
                                    + 0.5 * (simplex[i][d] - simplex[best][d]);
                        }
                        values[i] = sign * func.applyAsDouble(simplex[i]);
                    }
                }
            }
        }
        int best = 0;
        for (int i = 1; i <= dim; i++) {
            if (values[i] < values[best]) {
                best = i;
            }
        }
        return simplex[best].clone();
    }

    private static double[][] copy6(double[][] matrix) {
        double[][] out = new double[6][6];
        for (int i = 0; i < 6; i++) {
            System.arraycopy(matrix[i], 0, out[i], 0, 6);
        }
        return out;
    }

    /** Value formatter mirroring elate.py's %g prints in the tables. */
    public static String formatValue(double value) {
        return String.format(Locale.ROOT, "%.6g", value);
    }
}
