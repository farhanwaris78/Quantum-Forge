/* Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import quantumforge.operation.OperationResult;

/**
 * PREVIEW builder for the BoltzTraP2 {@code btp2} command flow, pinned
 * read-only against:
 *
 * <ul>
 *   <li><b>gitlab.com/yiwang62/BoltzTraP2 branch 20210126</b> (the
 *       "BoltzTraP2Y" fork, per the readthedocs documentation's own
 *       Edit-on-GitLab link): {@code BoltzTraP2/interface.py}'s
 *       parse_arguments subcommand wiring with the verbatim argparse help
 *       strings, parse_interpolate/integrate/dope/plotbands/plot/describe/
 *       fermisurface, and io.py/ioEXT.py's save_* writers (the Yi-Wang
 *       09/24/2020 additions named as such);</li>
 *   <li><b>boltztrap2y.readthedocs.io</b> (the tutorial page): prerequisites
 *       (Python &gt;= 3.5, NumPy, SciPy, matplotlib, spglib, NetCDF4, ASE;
 *       optional pyFFTW/colorama/VTK; a C++ compiler + Python headers only
 *       for a source build; Cython NOT required), {@code pip install
 *       BoltzTraP2} / {@code python setup.py install} /
 *       {@code pytest -v tests};</li>
 *   <li><b>gitlab.com/sousaw/BoltzTraP2 wiki tutorial</b> (the upstream one
 *       pointed at by that page): the LiZnSb example commands
 *       ({@code btp2 -vv interpolate -m 3 data/LiZnSb},
 *       {@code btp2 -vv integrate interpolation.bt2 300:500:1},
 *       {@code btp2 plot -u -c '["xx", "zz"]' -s 50 interpolation.btj S},
 *       {@code btp2 fermisurface interpolation.bt2 -0.05},
 *       {@code xzcat interpolation.bt2 | more}, the plotbands Python-list
 *       kpath with None segment breaks);</li>
 *   <li><b>sousaw/BoltzTraP2 branch public</b>: the Quantum ESPRESSO route -
 *       {@code ESPRESSOLoader} scans the directory's *.xml for a
 *       {@code schemaLocation} beginning
 *       {@code http://www.quantum-espresso.org/ns/qes/qes-1.0}; loaders are
 *       tried last-registered-first so a directory holding such an XML
 *       (a pw.x {@code prefix.save/} outdir, QE >= 6.0 schema) is detected
 *       as ESPRESSO automatically.</li>
 * </ul>
 *
 * <p>PREVIEW doctrine: QuantumForge never runs btp2; it emits the exact
 * command lines with the user's own substitutions, mirrors upstream's own
 * validation refusals ('zero-width energy window', 'the energy window must
 * bracket the Fermi level', 'refusing to interpolate to a sparser grid',
 * 'all temperatures must be positive', 'empty temperature specification',
 * 'The reference for the chemical potential is electroneutrality.'), and
 * states which artifacts stay enumerated-not-parsed (.bt2/.btj are
 * xz-compressed JSON).</p>
 */
public final class BoltzTrap2Btp2Plan {

    private BoltzTrap2Btp2Plan() {
        // Utility
    }

    /** Valid plot quantities, verbatim from parse_plot's tensors + scalars. */
    public static final String[] PLOT_TENSORS = {
            "sigma", "S", "kappae", "L", "PF", "RH"};
    public static final String[] PLOT_SCALARS = {"cv", "n", "DOS"};

    /** btp2 flow request (interpolate -> integrate/dope -> plot family). */
    public static final class Request {
        private String directory = "data/LiZnSb";
        private String output = "interpolation.bt2";
        private int verbose = 2;
        private int nworkers = 1;
        private Integer multiplier = 3;
        private Integer kpoints;
        private boolean derivatives;
        private double emin = -0.2;
        private double emax = 0.2;
        private boolean windowExplicit;
        private boolean absolute;
        private boolean runIntegrate = true;
        private String temperature = "300:500:1";
        private Integer bins;
        private boolean uniformLambda;
        private Double scissorEv;
        private boolean runDope;
        private String dopingLevels = "";
        private String doscar;
        private Integer nedos;
        private Integer smooth;
        private Double ugauss;
        private boolean runPlotBands;
        private int nkpointsPerSegment = 50;
        private final List<String[]> kpathVertices = new ArrayList<>();
        private boolean runPlot;
        private String quantity = "S";
        private final List<String> components = new ArrayList<>();
        private boolean abscissaMu = true;
        private int subsample = 50;
        private boolean runDescribe = true;
        private boolean runFermisurface;
        private String fermisurfaceMu = "";

        /** DFT input directory (QE outdir holding a qes-1.0 XML, or wien2k/vasp/...). */
        public Request directory(String directory) { this.directory = directory; return this; }
        /** interpolate -o output file (default interpolation.bt2). */
        public Request output(String output) { this.output = output; return this; }
        /** -v count (0..3; the tutorial uses 2). */
        public Request verbose(int verbose) { this.verbose = verbose; return this; }
        /** -n processes. */
        public Request nworkers(int n) { this.nworkers = n; return this; }
        /** interpolate -m multiplier (exclusive with kpoints, one REQUIRED). */
        public Request multiplier(Integer m) { this.multiplier = m; this.kpoints = null; return this; }
        /** interpolate -k kpoints (exclusive with multiplier, one REQUIRED). */
        public Request kpoints(Integer k) { this.kpoints = k; this.multiplier = null; return this; }
        /** interpolate -d (use band derivatives). */
        public Request derivatives(boolean on) { this.derivatives = on; return this; }
        /** interpolate -e/-E energy window (Ha; relative unless absolute()). */
        public Request window(double emin, double emax) {
            this.emin = emin;
            this.emax = emax;
            this.windowExplicit = true;
            return this;
        }
        /** interpolate -a: -e/-E are absolute. */
        public Request absolute(boolean on) { this.absolute = on; return this; }
        /** Emit the integrate step. */
        public Request integrate(boolean on) { this.runIntegrate = on; return this; }
        /** integrate temperatures: array_argument grammar (floats, min:max:step, commas). */
        public Request temperature(String spec) { this.temperature = spec; return this; }
        /** integrate -b DOS bins (null = let the code handle it, upstream default). */
        public Request bins(Integer bins) { this.bins = bins; return this; }
        /** scattering model: uniform_tau (default) or uniform_lambda (-l). */
        public Request uniformLambda(boolean on) { this.uniformLambda = on; return this; }
        /** integrate -s scissor gap in eV. */
        public Request scissorEv(Double gapEv) { this.scissorEv = gapEv; return this; }
        /** Emit the fork's dope step (fixed carrier concentrations). */
        public Request dope(boolean on) { this.runDope = on; return this; }
        /** dope doping levels [1/cm^3], array_argument grammar (signs allowed: holes). */
        public Request dopingLevels(String spec) { this.dopingLevels = spec; return this; }
        /** dope -dos: DOSCAR path for the fork's DOS renorm (optional). */
        public Request doscar(String path) { this.doscar = path; return this; }
        /** dope -nd NEDOS (fork; upstream default 20000). */
        public Request nedos(Integer n) { this.nedos = n; return this; }
        /** dope -sm savgol box (fork; upstream default 0). */
        public Request smooth(Integer n) { this.smooth = n; return this; }
        /** dope -ng ugauss (fork; upstream default 2000). */
        public Request ugauss(Double g) { this.ugauss = g; return this; }
        /** Emit plotbands. */
        public Request plotBands(boolean on) { this.runPlotBands = on; return this; }
        /** plotbands -k points PER segment (upstream default 50). */
        public Request nkpointsPerSegment(int n) { this.nkpointsPerSegment = n; return this; }
        /** One kpath vertex (x y z tokens, ints/decimals/fractions); "None" starts a break. */
        public Request kpathVertex(String x, String y, String z) {
            this.kpathVertices.add(new String[] {x, y, z});
            return this;
        }
        /** Insert a path break (the upstream 'None' separator). */
        public Request kpathBreak() {
            this.kpathVertices.add(new String[] {"None", "None", "None"});
            return this;
        }
        /** Emit the plot step. */
        public Request plot(boolean on) { this.runPlot = on; return this; }
        /** plot quantity: cv, n, DOS, sigma, S, kappae, L, PF, RH. */
        public Request quantity(String q) { this.quantity = q; return this; }
        /** plot -c component (xx/yy/zz for rank-2, xyz for RH, or 'scalar'). */
        public Request component(String c) { this.components.add(c); return this; }
        /** Clear all accumulated -c components (e.g. before re-driving validation). */
        public Request componentReset() { this.components.clear(); return this; }
        /** plot independent variable: mu (-u, default) or temperature (-T). */
        public Request abscissaMu(boolean mu) { this.abscissaMu = mu; return this; }
        /** plot -s subsample of the OTHER variable (upstream example uses 50). */
        public Request subsample(int n) { this.subsample = n; return this; }
        /** Emit the describe step. */
        public Request describe(boolean on) { this.runDescribe = on; return this; }
        /** Emit fermisurface (needs the VTK python package upstream). */
        public Request fermisurface(boolean on, String muHa) {
            this.runFermisurface = on;
            this.fermisurfaceMu = muHa;
            return this;
        }
    }

    /** The preview product: flow steps + warnings + honesty notes. */
    public static final class Plan {
        private final List<String> steps;
        private final List<String> warnings;
        private final List<String> notes;

        private Plan(List<String> steps, List<String> warnings, List<String> notes) {
            this.steps = steps;
            this.warnings = warnings;
            this.notes = notes;
        }

        /** The ordered command lines + comments (never executed by QuantumForge). */
        public List<String> getSteps() { return this.steps; }
        public List<String> getWarnings() { return this.warnings; }
        public List<String> getNotes() { return this.notes; }
    }

    /** Builds the btp2 flow preview, or an enumerated refusal. */
    public static OperationResult<Plan> build(Request request) {
        if (request == null) {
            return OperationResult.failed("BOLTZTRAP2_PLAN_INPUT",
                    "No btp2 plan request.", null);
        }
        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        String directory = oneToken(request.directory, "directory", issues);
        String output = oneToken(request.output, "output file", issues);
        if (request.verbose < 0 || request.verbose > 3) {
            issues.add("verbose count must be 0..3 (upstream set_logging_level"
                    + " raises beyond that)");
        }
        if (request.nworkers < 1) {
            issues.add("-n nworkers must be a positive integer (upstream's own"
                    + " argparse type)");
        }
        if (request.multiplier == null && request.kpoints == null) {
            issues.add("interpolate needs exactly ONE of -m multiplier / -k kpoints"
                    + " (upstream's mutually-exclusive group is REQUIRED)");
        }
        if (request.multiplier != null && request.multiplier < 1) {
            issues.add("-m multiplier must be a positive integer");
        }
        if (request.kpoints != null && request.kpoints < 1) {
            issues.add("-k kpoints must be a positive integer");
        }
        if (!(request.emin < request.emax)) {
            issues.add("energy window emin >= emax - upstream lexits"
                    + " 'zero-width energy window'");
        }
        if (request.temperature == null || request.temperature.trim().isEmpty()) {
            if (request.runIntegrate || request.runDope) {
                issues.add("empty temperature specification (upstream's own refusal)");
            }
        } else {
            List<Double> temps = parseArrayArgument(request.temperature, "temperature",
                    issues);
            if ((request.runIntegrate || request.runDope) && temps != null) {
                for (double t : temps) {
                    if (t <= 0.0) {
                        issues.add("all temperatures must be positive"
                                + " (upstream parse_integrate refusal)");
                        break;
                    }
                }
            }
        }
        if (request.bins != null && request.bins < 1) {
            issues.add("-b bins must be a positive integer when given");
        }
        if (request.scissorEv != null && request.scissorEv <= 0.0) {
            issues.add("-s scissor must be positive (eV, upstream's argparse type)");
        }
        if (request.runDope) {
            if (request.dopingLevels == null || request.dopingLevels.trim().isEmpty()) {
                issues.add("dope needs doping levels [1/cm^3] (array_argument:"
                        + " floats, min:max:step, comma lists)");
            } else {
                parseArrayArgument(request.dopingLevels, "doping_level", issues);
            }
            if (request.nedos != null && request.nedos < 1) {
                issues.add("-nd NEDOS must be a positive integer (fork default 20000)");
            }
            if (request.smooth != null && request.smooth < 0) {
                issues.add("-sm smooth must be >= 0 (fork savgol box size)");
            }
            if (request.ugauss != null && request.ugauss <= 0.0) {
                issues.add("-ng ugauss must be positive (fork default 2000)");
            }
            if (request.doscar != null) {
                oneToken(request.doscar, "DOSCAR path", issues);
            }
        }
        if (request.runPlotBands) {
            if (request.nkpointsPerSegment < 1) {
                issues.add("plotbands -k must be a positive integer (default 50 per"
                        + " segment)");
            }
            validateKpath(request, issues);
        }
        boolean tensorQuantity = false;
        if (request.runPlot) {
            String q = request.quantity == null ? "" : request.quantity.trim();
            boolean scalar = false;
            for (String t : PLOT_TENSORS) {
                if (t.equals(q)) {
                    tensorQuantity = true;
                }
            }
            for (String s : PLOT_SCALARS) {
                if (s.equals(q)) {
                    scalar = true;
                }
            }
            if (!tensorQuantity && !scalar) {
                issues.add("plot quantity '" + q + "' is not one of cv, n, DOS, sigma,"
                        + " S, kappae, L, PF, RH (parse_plot's own list)");
            }
            if (tensorQuantity && request.components.isEmpty()) {
                issues.add(q + " is a tensor but no components have been specified"
                        + " (upstream lexit; use e.g. \"xx\", \"yy\")");
            }
            for (String c : request.components) {
                String comp = c == null ? "" : c.trim();
                if (!"scalar".equals(comp) && !comp.matches("[xyz]{2,3}")) {
                    issues.add("component '" + comp + "' cannot be parsed:"
                            + " strings of length 2 or 3 made of x/y/z, or 'scalar'"
                            + " (upstream components_argument)");
                } else if ("RH".equals(q) && !"scalar".equals(comp)
                        && comp.length() != 3) {
                    issues.add("component specifications for the Hall tensor need"
                            + " three indices (upstream lexit) - got '" + comp + "'");
                } else if (tensorQuantity && !"RH".equals(q) && !"scalar".equals(comp)
                        && comp.length() != 2) {
                    issues.add("component specifications for " + q + " need two"
                            + " indices (upstream lexit) - got '" + comp + "'");
                }
            }
            if (request.subsample < 1) {
                issues.add("plot -s subsample must be >= 1");
            }
        }
        if (request.runDescribe && !output.endsWith(".bt2")) {
            warnings.add("describe reads a .bt2 (interpolation) or .btj (integration)"
                    + " file; the emitted step uses '" + output + "'");
        }
        if (request.runFermisurface) {
            try {
                Double.parseDouble(request.fermisurfaceMu.trim());
            } catch (NumberFormatException ex) {
                issues.add("fermisurface mu must be a plain number (Ha; the tutorial"
                        + " uses -0.05)");
            }
        }
        if (request.multiplier != null && request.multiplier > 10) {
            warnings.add("-m " + request.multiplier + " is a very dense interpolation"
                    + " (tutorial uses 3, QE users often 20): emitted as given, but"
                    + " expect a long fitde3D run");
        }
        if (request.bins == null && (request.runIntegrate || request.runDope)) {
            warnings.add("no -b bins given - upstream lets the code handle it"
                    + " automatically (parse_integrate's own default)");
        }
        if (!issues.isEmpty()) {
            return OperationResult.failed("BOLTZTRAP2_PLAN_INPUT",
                    "btp2 plan refused (" + issues.size() + " issue(s)): "
                            + String.join(" | ", issues), null);
        }

        // ============================== steps ==============================
        List<String> steps = new ArrayList<>();
        steps.add("# BoltzTraP2 btp2 flow PREVIEW - pinned vs gitlab.com/yiwang62/"
                + "BoltzTraP2 branch 20210126 (BoltzTraP2Y fork) + the sousaw wiki"
                + " tutorial; QuantumForge NEVER runs these lines");
        steps.add("# install (readthedocs tutorial page, verbatim): pip install"
                + " BoltzTraP2   # needs Python>=3.5 + NumPy/SciPy/matplotlib/spglib/"
                + "NetCDF4/ASE; a C++ compiler + Python headers only for a source"
                + " build; Cython NOT required");
        steps.add("# source-build route (same page): python setup.py install"
                + "   # or setup.py develop; then pytest -v tests; optional extras:"
                + " pyFFTW, colorama, VTK (VTK gates the fermisurface subcommand)");
        StringBuilder interp = new StringBuilder("btp2 ");
        if (request.verbose > 0) {
            interp.append("-").append("v".repeat(request.verbose)).append(' ');
        }
        if (request.nworkers != 1) {
            interp.append("-n ").append(request.nworkers).append(' ');
        }
        interp.append("interpolate ");
        if (request.derivatives) {
            interp.append("-d ");
        }
        if (request.windowExplicit || request.absolute) {
            interp.append("-e ").append(trim(request.emin)).append(" -E ")
                    .append(trim(request.emax)).append(' ');
            if (request.absolute) {
                interp.append("-a ");
            }
        }
        interp.append(request.multiplier != null
                ? "-m " + request.multiplier : "-k " + request.kpoints);
        interp.append(' ').append(directory);
        if (!"interpolation.bt2".equals(output)) {
            interp.append(" -o ").append(output);
        }
        interp.append("    # step 1: interpolate; writes ").append(output)
                .append(" (xz-JSON; QuantumForge enumerates it, never parses it)");
        steps.add(interp.toString());
        steps.add("xzcat " + output + " | more    # or: xzcat " + output
                + " | jq . -C | less -r   (the wiki's own inspection lines)");
        if (request.runIntegrate) {
            StringBuilder integ = new StringBuilder("btp2 integrate ");
            if (request.bins != null) {
                integ.append("-b ").append(request.bins).append(' ');
            }
            integ.append(request.uniformLambda ? "-l " : "-t ");
            if (request.scissorEv != null) {
                integ.append("-s ").append(trim(request.scissorEv)).append(' ');
            }
            integ.append(output).append(' ').append(request.temperature.trim());
            String base = stripExt(output);
            integ.append("    # step 2: writes ").append(base)
                    .append(".trace + ").append(base).append(".condtens + ")
                    .append(base).append(".halltens + ").append(base)
                    .append(".btj; epilog verbatim: 'The reference for the chemical"
                            + " potential is electroneutrality.'");
            steps.add(integ.toString());
        }
        if (request.runDope) {
            StringBuilder dope = new StringBuilder("btp2 dope ")
                    .append(request.uniformLambda ? "-l " : "-t ");
            if (request.bins != null) {
                dope.append("-b ").append(request.bins).append(' ');
            }
            if (request.scissorEv != null) {
                dope.append("-s ").append(trim(request.scissorEv)).append(' ');
            }
            if (request.doscar != null) {
                dope.append("-dos ").append(request.doscar.trim()).append(' ');
            }
            if (request.nedos != null) {
                dope.append("-nd ").append(request.nedos).append(' ');
            }
            if (request.smooth != null) {
                dope.append("-sm ").append(request.smooth).append(' ');
            }
            if (request.ugauss != null) {
                dope.append("-ng ").append(trim(request.ugauss)).append(' ');
            }
            dope.append(output).append(' ').append(request.temperature.trim())
                    .append(' ').append(request.dopingLevels.trim());
            String base = stripExt(output);
            dope.append("    # fork's dope step (Yi Wang 09/24/2020): writes ")
                    .append(base).append(".dope.trace + ").append(base)
                    .append(".dope.condtens + ").append(base).append(".dope.halltens")
                    .append(" + ").append(base).append(".dope.dos + ").append(base)
                    .append(".dope.vvdos (+ _raw renames when a DOSCAR remesh runs)");
            steps.add(dope.toString());
        }
        if (request.runPlotBands) {
            steps.add("btp2 plotbands " + (request.nkpointsPerSegment != 50
                    ? "-k " + request.nkpointsPerSegment + " " : "")
                    + output + " \"" + buildKpathString(request) + "\""
                    + "    # step X: interpolated bands along the Python-list path"
                    + " (50 points/segment default; energies plotted relative to"
                    + " data.fermi)");
        }
        if (request.runPlot && request.runIntegrate) {
            StringBuilder plot = new StringBuilder("btp2 plot ")
                    .append(request.abscissaMu ? "-u " : "-T ");
            if (!request.components.isEmpty()) {
                plot.append("-c '").append(pythonList(request.components))
                        .append("' ");
            }
            plot.append("-s ").append(request.subsample).append(' ')
                    .append(stripExt(output)).append(".btj ")
                    .append(request.quantity.trim());
            if ("L".equals(request.quantity.trim())) {
                plot.append("    # L quantity: upstream draws the reference Lorenz"
                        + " line L0 = 2.44e-8");
            }
            if (request.abscissaMu) {
                plot.append("    # abscissa: mu - fermi in Ha (upstream's own axis)");
            }
            steps.add(plot.toString());
        }
        if (request.runDescribe) {
            steps.add("btp2 describe " + (request.runIntegrate
                    ? stripExt(output) + ".btj" : output)
                    + "    # prints Compound + metadata from the xz-JSON");
        }
        if (request.runFermisurface) {
            steps.add("btp2 fermisurface " + output + " "
                    + request.fermisurfaceMu.trim()
                    + "    # needs the VTK python package (else the subcommand is"
                    + " ABSENT - upstream gates registration on the import); keys"
                    + " verbatim: r reset camera / w wireframe / e exit");
        }
        steps.add("# QuantumForge studio handoff: open " + stripExt(output)
                + ".trace / .condtens / .halltens / .dope.trace / .dope.dos /"
                + " .dope.vvdos in QuantumForge's BoltzTraP2 studio (WATCH: 2s"
                + " signature polling; OPEN: explicit pick); .bt2/.btj stay"
                + " enumerated-not-parsed (xz-JSON)");

        // ============================== notes ==============================
        notes.add("prerequisites (readthedocs page, verbatim list): Python >= 3.5,"
                + " NumPy, SciPy, matplotlib, spglib, NetCDF4, ASE; recommended"
                + " extras pyFFTW / colorama / VTK; Cython is NOT required for a"
                + " regular compilation");
        notes.add("Quantum ESPRESSO route (sousaw public branch ESPRESSOLoader):"
                + " point the interpolate DIRECTORY at a pw.x outdir containing a"
                + " data-file XML whose schemaLocation starts"
                + " http://www.quantum-espresso.org/ns/qes/qes-1.0 (a prefix.save/"
                + " folder, QE >= 6.0); loaders are tried last-registered-first so"
                + " QE wins when present, and multiple XMLs warn 'using the first"
                + " one' - state this on cluster scripts");
        notes.add("likelihood-of-confusion honest split: the CLI grammar + writers"
                + " above are pinned from the yiwang62 fork branch 20210126 (the"
                + " user's link #1; its cv_x/S_x/N_x/.dope.* extensions are the"
                + " Yi-Wang 09/24/2020 additions, named as such); the QE XML loader itself"
                + " is pinned from the sousaw public branch - keep btp2 updated"
                + " (pip install -U BoltzTraP2) so both grammars coexist");
        notes.add("upstream's own refusals you may still hit at runtime, quoted"
                + " verbatim: 'refusing to interpolate to a sparser grid' (-m/-k too"
                + " small vs the DFT grid), 'the energy window must bracket the Fermi"
                + " level' (-e/-E misplaced), 'the energy window is too narrow'"
                + " (integrate), 'minimum and maximum possible concentrations at"
                + " T = ... K: ...' (dope range check)");
        notes.add("everything above is a PREVIEW: QuantumForge does not run btp2,"
                + " does not install Python packages, and never writes these files"
                + " itself - paste the lines into your own terminal / job script");

        return OperationResult.success("BOLTZTRAP2_PLAN_OK",
                steps.size() + " flow step(s) (btp2 preview)" + (
                        warnings.isEmpty() ? "" : "; " + warnings.size()
                                + " warning(s) stated"),
                new Plan(List.copyOf(steps), List.copyOf(warnings),
                        List.copyOf(notes)));
    }

    /** The wiki tutorial's own LiZnSb example, verbatim. */
    public static Request liznsbPreset() {
        return new Request()
                .directory("data/LiZnSb").multiplier(3).verbose(2)
                .temperature("300:500:1")
                .plotBands(true)
                .kpathVertex("0.0", "0.0", "0.0")
                .kpathVertex("0.5", "0.0", "0.0")
                .kpathVertex("0.5", "0.5", "0.0")
                .plot(true).quantity("S").component("xx").component("zz")
                .abscissaMu(true).subsample(50)
                .fermisurface(false, "");
    }

    /** The QE-flavored preset: pw.x outdir + the community -m 20 recipe. */
    public static Request qePreset(String outdir) {
        return new Request()
                .directory(outdir == null || outdir.isBlank() ? "./si.save" : outdir)
                .multiplier(20).verbose(2).nworkers(4)
                .temperature("300:1001:100");
    }

    private static String buildKpathString(Request request) {
        StringBuilder kpath = new StringBuilder("[");
        boolean firstElem = true;
        List<String[]> vertices = request.kpathVertices;
        for (int i = 0; i < vertices.size(); i++) {
            String[] v = vertices.get(i);
            if ("None".equals(v[0])) {
                if (!firstElem) {
                    kpath.append(", ");
                }
                kpath.append("None");
                firstElem = false;
                continue;
            }
            if (!firstElem) {
                kpath.append(", ");
            }
            kpath.append('[').append(v[0].trim())
                    .append(", ").append(v[1].trim())
                    .append(", ").append(v[2].trim())
                    .append(']');
            firstElem = false;
        }
        kpath.append(']');
        return kpath.toString();
    }

    private static void validateKpath(Request request, List<String> issues) {
        List<String[]> vertices = request.kpathVertices;
        if (vertices.isEmpty()) {
            issues.add("plotbands needs a kpath (Python list syntax; None separates"
                    + " segments - e.g. [[0.0, 0.0, 0.0], [0.5, 0.0, 0.0]])");
            return;
        }
        int segmentSize = 0;
        for (int i = 0; i < vertices.size(); i++) {
            String[] v = vertices.get(i);
            if ("None".equals(v[0])) {
                if (segmentSize < 2) {
                    issues.add("every kpath segment needs at least 2 vertices"
                            + " (upstream: 'the path cannot be interpreted as a set"
                            + " of N x 3 arrays (with N >= 2')");
                    return;
                }
                segmentSize = 0;
                continue;
            }
            for (String token : v) {
                if (!token.trim().matches(
                        "[-+]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][-+]?\\d+)?")) {
                    issues.add("kpath vertex token '" + token + "' is not a Python"
                            + " number literal (ast.literal_eval upstream; ints/"
                            + " decimals like the tutorial's 0.0 and 0.5)");
                    return;
                }
            }
            segmentSize++;
        }
        if (segmentSize < 2 && !vertices.isEmpty()) {
            issues.add("every kpath segment needs at least 2 vertices (upstream:"
                    + " N x 3 arrays with N >= 2)");
        }
    }

    /** Mirrors upstream array_argument: floats, min:max:step, comma lists. */
    static List<Double> parseArrayArgument(String spec, String what,
            List<String> issues) {
        // upstream array_argument: split on ',', each field is a float or a
        // min:max:step range fed to np.arange (STOP EXCLUDED, the sign of the
        // step picks the direction), then np.unique (sorted, deduplicated).
        java.util.TreeSet<Double> values = new java.util.TreeSet<>();
        for (String field : spec.split(",")) {
            String f = field.trim();
            if (f.isEmpty()) {
                continue;
            }
            String[] parts = f.split(":");
            try {
                if (parts.length == 1) {
                    values.add(Double.parseDouble(f));
                } else if (parts.length == 3) {
                    double start = Double.parseDouble(parts[0].trim());
                    double end = Double.parseDouble(parts[1].trim());
                    double step = Double.parseDouble(parts[2].trim());
                    if (step == 0.0) {
                        issues.add(what + ": range '" + f + "' has a zero step -"
                                + " np.arange would crash upstream"
                                + " (ZeroDivisionError), refusing instead");
                        return null;
                    }
                    // np.arange yields NOTHING when the step sign cannot reach
                    // the stop (e.g. 300:100:50); that is numpy semantics, and
                    // upstream then dies on the empty check below.
                    int added = 0;
                    for (double v = start;
                            (step > 0.0 ? v < end : v > end) && added < 100000;
                            v += step, added++) {
                        values.add(v);
                    }
                } else {
                    issues.add(what + ": '" + f
                            + "' cannot be parsed as a number or a range"
                            + " (upstream array_argument)");
                    return null;
                }
            } catch (NumberFormatException ex) {
                issues.add(what + ": '" + f + "' cannot be parsed as a number of a"
                        + " range (upstream array_argument, verbatim phrase)");
                return null;
            }
        }
        if (values.isEmpty()) {
            issues.add("empty " + what + " specification (upstream refusal)");
            return null;
        }
        return new ArrayList<>(values);
    }

    private static String oneToken(String value, String name, List<String> issues) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty() || v.contains("\"") || v.contains(" ") || v.contains("'")) {
            issues.add(name + " must be one shell-token without quotes/spaces"
                    + " (given: '" + v + "')");
        }
        return v;
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    private static String pythonList(List<String> items) {
        StringBuilder list = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                list.append(", ");
            }
            list.append('"').append(items.get(i).trim()).append('"');
        }
        return list.append(']').toString();
    }

    private static String trim(double value) {
        // %.10g keeps up to 10 significant digits (like the upstream floats) but
        // pads trailing zeros; strip them so the emitted CLI stays human-typed
        // (-0.3, not -0.3000000000).
        String s = String.format(Locale.ROOT, "%.10g", value);
        if (s.indexOf('.') >= 0 && s.indexOf('e') < 0 && s.indexOf('E') < 0) {
            while (s.endsWith("0")) {
                s = s.substring(0, s.length() - 1);
            }
            if (s.endsWith(".")) {
                s = s.substring(0, s.length() - 1);
            }
        }
        return s;
    }
}
