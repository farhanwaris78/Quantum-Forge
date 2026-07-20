/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import java.util.Locale;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #50 (draft slice): typed, fail-closed {@code &PATH} namelist draft
 * for neb.x. This planner owns ONLY the namelist arithmetic - the image
 * machinery (interpolated first/intermediate/last ATOMIC_POSITIONS blocks),
 * the engine-level {@code num_of_images} partitioning, the NEB stage parser,
 * and the movie remain the #50 editor/runtime depth, and the render says so.
 *
 * <p>Owned grammar and semantic rules:</p>
 * <ul>
 *   <li>{@code num_of_images}: 2..64, end-points INCLUDED (neb.x counts the
 *       end points as images) (NEB_IMAGES);</li>
 *   <li>{@code nstep_path}: 1..10000 path-optimization steps (NEB_NSTEP);</li>
 *   <li>{@code opt_scheme}: TYPED enum sd/broyden/broyden2/quick-min/lbfgs -
 *       free-form strings refuse (NEB_OPT);</li>
 *   <li>{@code CI_scheme}: no-CI / highest / spin only. {@code 'manual'} is
 *       REFUSED ACTIONABLY in this slice (manual image indexing needs the
 *       editor that can see image energies - picking an index blindly would
 *       be ceremonial) (NEB_CI). Any CI choice requires num_of_images &gt;= 3,
 *       because climbing needs an INTERIOR image and with 2 images every
 *       image is an end point (NEB_CI);</li>
 *   <li>{@code k_min} / {@code k_max}: required finite &gt; 0 elastic
 *       constants in a.u. with OUR ordering rule k_min &lt;= k_max - an
 *       inverted spring ladder inverts which images get stiffened and refuses
 *       rather than writing a subtly wrong namelist (NEB_K);</li>
 *   <li>{@code ds} (optimization step, a.u.): required finite &gt; 0,
 *       capped at 1000 (NEB_DS); {@code path_thr} (path convergence
 *       threshold, a.u.): required finite &gt; 0 (NEB_PATH_THR). No keyword
 *       has an invented default - blank refuses at the GUI.</li>
 * </ul>
 *
 * <p>Keyword semantics follow the QE neb.x documentation; units are a.u.
 * (QE Rydberg atomic units) and are STATED, never converted away.</p>
 */
public final class NebInputPlanner {

    public static final int MAX_IMAGES = 64;
    public static final int MAX_NSTEP = 10000;
    public static final double MAX_DS = 1000.0;

    private NebInputPlanner() {
    }

    /** One validated &PATH draft. */
    public static final class NebDraft {
        private final int numOfImages;
        private final int nstepPath;
        private final String optScheme;
        private final String ciScheme;
        private final double kMin;
        private final double kMax;
        private final double ds;
        private final double pathThr;

        NebDraft(int numOfImages, int nstepPath, String optScheme, String ciScheme,
                double kMin, double kMax, double ds, double pathThr) {
            this.numOfImages = numOfImages;
            this.nstepPath = nstepPath;
            this.optScheme = optScheme;
            this.ciScheme = ciScheme;
            this.kMin = kMin;
            this.kMax = kMax;
            this.ds = ds;
            this.pathThr = pathThr;
        }

        public int getNumOfImages() { return this.numOfImages; }
        public int getNstepPath() { return this.nstepPath; }
        public String getOptScheme() { return this.optScheme; }
        public String getCiScheme() { return this.ciScheme; }
        public double getKMin() { return this.kMin; }
        public double getKMax() { return this.kMax; }
        public double getDs() { return this.ds; }
        public double getPathThr() { return this.pathThr; }

        /** The &PATH namelist plus the honesty checklist around it. */
        public String draft() {
            StringBuilder text = new StringBuilder();
            text.append("# QuantumForge neb.x &PATH draft (Roadmap #50, draft slice)\n");
            text.append("# REVIEW before use. Units are a.u. (QE Rydberg atomic "
                    + "units).\n");
            text.append("&PATH\n");
            text.append("   restart_mode  = 'from_scratch'\n");
            text.append("   string_method = 'neb'\n");
            text.append(String.format(Locale.ROOT, "   nstep_path    = %d%n",
                    this.nstepPath));
            text.append(String.format(Locale.ROOT, "   opt_scheme    = '%s'%n",
                    this.optScheme));
            text.append(String.format(Locale.ROOT, "   CI_scheme     = '%s'%n",
                    this.ciScheme));
            text.append(String.format(Locale.ROOT, "   k_max         = %.6f%n", this.kMax));
            text.append(String.format(Locale.ROOT, "   k_min         = %.6f%n", this.kMin));
            text.append(String.format(Locale.ROOT, "   ds            = %.6f%n", this.ds));
            text.append(String.format(Locale.ROOT, "   path_thr      = %.6f%n", this.pathThr));
            text.append("/\n");
            text.append("# num_of_images = ").append(this.numOfImages)
                    .append("  (end points INCLUDED - this count must match the "
                            + "-nimage engine partitioning AND the image blocks below;\n");
            text.append("#  QuantumForge does NOT generate intermediate images in this "
                    + "slice - see checklist.)\n");
            return text.toString();
        }

        /** The numbered-image checklist - images can never reorder silently. */
        public String checklist() {
            StringBuilder text = new StringBuilder();
            text.append("Image checklist (numbering is explicit; nothing reorders "
                    + "silently):\n");
            text.append("  image 1 = FIRST end point (fixed)\n");
            for (int img = 2; img < this.numOfImages; img++) {
                text.append("  image ").append(img).append(" = intermediate - supplied "
                        + "by the #50 editor interpolation, NOT by this draft\n");
            }
            if (this.numOfImages > 1) {
                text.append("  image ").append(this.numOfImages)
                        .append(" = LAST end point (fixed)\n");
            }
            return text.toString();
        }
    }

    /** Validates one draft. Codes: NEB_IMAGES/NSTEP/OPT/CI/K/DS/PATH_THR. */
    public static OperationResult<NebDraft> validate(int numOfImages, int nstepPath,
            String optSchemeText, String ciSchemeText, double kMin, double kMax,
            double ds, double pathThr) {
        if (numOfImages < 2 || numOfImages > MAX_IMAGES) {
            return OperationResult.failed("NEB_IMAGES",
                    "num_of_images must be 2.." + MAX_IMAGES + " (end points INCLUDED by "
                            + "neb.x; got " + numOfImages + ").",
                    null);
        }
        if (nstepPath < 1 || nstepPath > MAX_NSTEP) {
            return OperationResult.failed("NEB_NSTEP",
                    "nstep_path must be 1.." + MAX_NSTEP + " (got " + nstepPath + ").",
                    null);
        }
        String opt = optSchemeText == null ? "" : optSchemeText.trim()
                .toLowerCase(Locale.ROOT);
        if (!(opt.equals("sd") || opt.equals("broyden") || opt.equals("broyden2")
                || opt.equals("quick-min") || opt.equals("lbfgs"))) {
            return OperationResult.failed("NEB_OPT",
                    "opt_scheme is TYPED: sd/broyden/broyden2/quick-min/lbfgs (got '"
                            + opt + "') - free-form optimizer strings refuse.",
                    null);
        }
        String ci = ciSchemeText == null ? "" : ciSchemeText.trim()
                .toLowerCase(Locale.ROOT);
        if (ci.equals("manual")) {
            return OperationResult.failed("NEB_CI",
                    "CI_scheme='manual' needs the editor that can SEE image energies - "
                            + "picking a climbing index blindly would be ceremonial. "
                            + "Refused actionably: choose 'highest', 'spin' or 'no-CI' "
                            + "(manual indexing lands with the #50 editor slice).",
                    null);
        }
        if (!(ci.equals("no-ci") || ci.equals("highest") || ci.equals("spin"))) {
            return OperationResult.failed("NEB_CI",
                    "CI_scheme is TYPED: no-CI/highest/spin (got '" + ci
                            + "'; 'manual' refused actionably - see message above "
                            + "family).",
                    null);
        }
        if (!ci.equals("no-ci") && numOfImages < 3) {
            return OperationResult.failed("NEB_CI",
                    "CI_scheme='" + ci + "' needs an INTERIOR image: with num_of_images="
                            + numOfImages + " every image is an end point. Raise "
                            + "num_of_images to >= 3 or pick 'no-CI'.",
                    null);
        }
        if (!finitePositive(kMin) || !finitePositive(kMax)) {
            return OperationResult.failed("NEB_K",
                    "k_min and k_max are required finite > 0 elastic constants in a.u. - "
                            + "there is no honest default, so blank refuses (got k_min="
                            + kMin + ", k_max=" + kMax + ").",
                    null);
        }
        if (kMin > kMax) {
            return OperationResult.failed("NEB_K",
                    "k_min > k_max inverts the spring ladder (images meant to be "
                            + "stiffened would soften). OUR ordering rule refuses k_min="
                            + kMin + " > k_max=" + kMax + " rather than writing a subtly "
                            + "wrong namelist.",
                    null);
        }
        if (!finitePositive(ds) || ds > MAX_DS) {
            return OperationResult.failed("NEB_DS",
                    "ds (optimization step, a.u.) must be finite > 0 and <= " + MAX_DS
                            + " (got " + ds + "); blank refuses - no invented default.",
                    null);
        }
        if (!finitePositive(pathThr)) {
            return OperationResult.failed("NEB_PATH_THR",
                    "path_thr (path convergence threshold, a.u.) is required finite > 0 "
                            + "(got " + pathThr + "); blank refuses.",
                    null);
        }
        return OperationResult.success("NEB_OK", "&PATH draft validated.",
                new NebDraft(numOfImages, nstepPath, opt, ci, kMin, kMax, ds, pathThr));
    }

    private static boolean finitePositive(double value) {
        return Double.isFinite(value) && value > 0.0;
    }
}
