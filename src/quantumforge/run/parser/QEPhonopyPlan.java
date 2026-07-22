/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import quantumforge.operation.OperationResult;

/**
 * Builder for phonopy setting files ({@code band.conf} style) and the
 * matching command lines, so a phononic band structure / DOS becomes a
 * fill-in-and-run task rather than a grammar-memory task. The produced text
 * and commands are pinned against three upstream sources
 * (github.com/phonopy/phonopy commit 3a3e0f099da5de2556e75d72ea89b3bb22c8e97e
 * and the phonopy.github.io doc pages):
 *
 * <ul>
 *   <li>the SETTING grammar ({@code settings.py _read_file}): one
 *       {@code TAG = value} per line, case-insensitive tags, {@code #}
 *       comments, Fortran booleans {@code .TRUE.} / {@code .FALSE.}, and
 *       fractional coordinates like {@code 1/2} written verbatim (upstream
 *       confs themselves write {@code BAND = 0 0 0 1/2 1/2 1/2});</li>
 *   <li>the COMMAND surface ({@code phonopy_argparse.py}):
 *       {@code --band}, {@code --band-labels}, {@code --band-connection},
 *       {@code --band-points}, {@code --mesh}, {@code --dos}, {@code --pdos},
 *       {@code -p/--plot}, {@code -s/--save}, {@code --dim}, {@code -c};</li>
 *   <li>the QE RUN FLOW (doc/qe.md):
 *       {@code phonopy-init --qe -d --dim="..." -c <cell.in>} &rarr; pw.x on
 *       every {@code supercell-*.in} &rarr; {@code phonopy-init -f <outs>}
 *       (FORCE_SETS) &rarr; {@code phonopy --qe -p --config band.conf}; the
 *       summary yaml then feeds the user's own
 *       {@code phonopy-load --band "..." --band_labels "..." --band_connection
 *       -p} flow.</li>
 *   <li>the NAC machinery (batch 169): {@code NAC = .TRUE.} conf tag
 *       (settings.py), the doc/qe.md BORN flow (pw.x SCF &rarr; ph.x epsil
 *       &rarr; {@code phonopy-qe-born ... | tee BORN}), and the v4 argparse
 *       removal note for {@code --nac} (verbatim from phonopy_argparse.py;
 *       {@code --nonac} disables, BORN-in-cwd activates automatically).</li>
 * </ul>
 *
 * <p>What is NOT here, deliberately: QuantumForge does not bundle phonopy
 * and never claims these commands were executed - the plan is a PREVIEW of
 * the exact text the user (or their job file) runs, mirroring the
 * XCrySDen-style external-tool doctrine. The band path is expressed as the
 * vertex list (e.g. the user's cubic BCC preset
 * 0 0 0 / 1/2 0 0 / 1/2 1/2 1/2 / 1/2 1/2 0 / 0 0 0 / 1/2 1/2 1/2 with
 * labels G X R M G R), which the builder turns into the canonical
 * comma-separated segment sections of the {@code BAND} tag (upstream writer
 * consumes one label pair per segment, sharing vertices under
 * band_connection - exactly the 5-segments/6-verteces shape the user's own
 * command line #4 encodes).</p>
 */
public final class QEPhonopyPlan {

    private static final Pattern INT_TOKEN = Pattern.compile("^[+-]?\\d+$");
    private static final Pattern DEC_TOKEN = Pattern.compile(
            "^[+-]?(\\d+\\.?\\d*|\\.\\d+)([eEdD][+-]?\\d+)?$");
    private static final Pattern FRAC_TOKEN = Pattern.compile(
            "^[+-]?\\d+/[+-]?\\d+$");

    private QEPhonopyPlan() {
        // Utility
    }

    /** Vertex-tokenised band path (fractions allowed) + run options. */
    public static final class Request {
        private String cellFilename = "pw.in";
        private String confName = "band.conf";
        private final List<int[]> dim = new ArrayList<>(1);
        private final List<String[]> bandVertices = new ArrayList<>();
        private final List<String> bandLabels = new ArrayList<>();
        private boolean bandConnection = true;
        private int bandPoints = 101;
        private boolean dosMode;
        private int[] mesh = {8, 8, 8};
        private final List<String> pdosTokens = new ArrayList<>();
        private boolean tpropMode;
        private double tmin = 0.0;
        private double tmax = 1000.0;
        private double tstep = 10.0;
        private boolean nac;

        /** Unit-cell input file phonopy reads (QE pw input name). */
        public Request cellFilename(String name) {
            this.cellFilename = name;
            return this;
        }

        /** Conf file name used in the phonopy-init/phonopy command previews. */
        public Request confName(String name) {
            this.confName = name;
            return this;
        }

        /** Supercell dimension: 3 ints or the 9-int supercell matrix. */
        public Request supercellDim(int... values) {
            this.dim.clear();
            if (values != null) {
                this.dim.add(values.clone());
            }
            return this;
        }

        /** One band-path vertex as exactly 3 tokens ('0', '0.5', '1/2', ...). */
        public Request bandVertex(String x, String y, String z) {
            this.bandVertices.add(new String[] {x, y, z});
            return this;
        }

        /** Flat label list, one label per VERTEX (user-command style). */
        public Request bandLabels(String... labels) {
            this.bandLabels.clear();
            if (labels != null) {
                for (String label : labels) {
                    this.bandLabels.add(label);
                }
            }
            return this;
        }

        public Request bandConnection(boolean connection) {
            this.bandConnection = connection;
            return this;
        }

        /** BAND_POINTS: samplings per segment (upstream default 51; must be > 1). */
        public Request bandPoints(int points) {
            this.bandPoints = points;
            return this;
        }

        /** Total DOS on the sampling mesh (DOS = .TRUE.). */
        public Request dos(boolean enabled) {
            this.dosMode = enabled;
            return this;
        }

        /** Sampling mesh for DOS/TPROP (three positive ints). */
        public Request mesh(int a, int b, int c) {
            this.mesh = new int[] {a, b, c};
            return this;
        }

        /** Projected DOS spec verbatim, e.g. pdos("1", ",", "2") -> 'PDOS = 1, 2'. */
        public Request pdos(String... atoms) {
            this.pdosTokens.clear();
            if (atoms != null) {
                for (String atom : atoms) {
                    this.pdosTokens.add(atom);
                }
            }
            this.dosMode = true; // PDOS runs the DOS machinery
            return this;
        }

        /** Thermal properties mode with the temperature range (K). */
        public Request tprop(double tmin, double tmax, double tstep) {
            this.tpropMode = true;
            this.tmin = tmin;
            this.tmax = tmax;
            this.tstep = tstep;
            return this;
        }

        /**
         * Non-analytical term correction (LO-TO splitting via a BORN file).
         * True emits {@code NAC = .TRUE.} into the conf (pinned: settings.py
         * line 'confs["nac"] = ".true."') and adds the doc/qe.md NAC run
         * steps (pw.x SCF -&gt; ph.x with epsil -&gt; phonopy-qe-born).
         */
        public Request nac(boolean enable) {
            this.nac = enable;
            return this;
        }
    }

    /** The preview product: conf text, two command sets, warnings, notes. */
    public static final class Plan {
        private final String confText;
        private final List<String> flowCommands;
        private final List<String> loadCommands;
        private final List<String> warnings;
        private final List<String> notes;

        private Plan(String confText, List<String> flowCommands,
                     List<String> loadCommands, List<String> warnings,
                     List<String> notes) {
            this.confText = confText;
            this.flowCommands = flowCommands;
            this.loadCommands = loadCommands;
            this.warnings = warnings;
            this.notes = notes;
        }

        /** The setting-file text (verbatim upstream tag grammar). */
        public String getConfText() { return this.confText; }
        /** The four-step QE -> phonopy run flow (per doc/qe.md). */
        public List<String> getFlowCommands() { return this.flowCommands; }
        /** The phonopy-load one-liner mirroring the user's own command #4. */
        public List<String> getLoadCommands() { return this.loadCommands; }
        /** Honest warnings (label counts, missing mesh for DOS, ...). */
        public List<String> getWarnings() { return this.warnings; }
        /** Stated conventions (units, yaml-load prerequisites, ...). */
        public List<String> getNotes() { return this.notes; }
    }

    /** Builds (and validates) the plan; every issue is enumerated, never thrown. */
    public static OperationResult<Plan> build(Request request) {
        if (request == null) {
            return OperationResult.failed("PHONOPY_PLAN_INPUT", "No plan request.", null);
        }
        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        // ---- cell / names ----
        String cell = request.cellFilename == null ? "" : request.cellFilename.trim();
        if (cell.isEmpty() || cell.contains("\"") || cell.contains(" ")) {
            issues.add("cell filename must be one shell-token without quotes/spaces"
                    + " (given: '" + cell + "')");
        }
        String confName = request.confName == null ? "" : request.confName.trim();
        if (confName.isEmpty() || confName.contains("\"") || confName.contains(" ")) {
            issues.add("conf name must be one shell-token without quotes/spaces"
                    + " (given: '" + confName + "')");
        }

        // ---- DIM ----
        int[] dimValues = request.dim.isEmpty() ? null : request.dim.get(0);
        String dimText = null;
        if (dimValues == null) {
            issues.add("DIM missing: phonopy builds the supercell from 3 ints or the"
                    + " 9-int supercell matrix");
        } else if (dimValues.length == 3) {
            for (int d : dimValues) {
                if (d < 1) {
                    issues.add("DIM diagonal entries must be >= 1 (found " + d + ")");
                }
            }
            dimText = dimValues[0] + " " + dimValues[1] + " " + dimValues[2];
            notes.add("DIM " + dimText + ": a " + dimValues[0] + "x" + dimValues[1]
                    + "x" + dimValues[2] + " supercell of the unit cell");
        } else if (dimValues.length == 9) {
            long det = det3(dimValues);
            if (det == 0) {
                issues.add("the 9-int supercell matrix is singular (det = 0): phonopy"
                        + " cannot build a supercell from it");
            } else {
                notes.add("DIM 9-int matrix, determinant " + det + " (nonzero, so the"
                        + " supercell volume is |det| x the unit cell)");
            }
            StringBuilder flat = new StringBuilder();
            for (int i = 0; i < 9; i++) {
                if (i > 0) {
                    flat.append(' ');
                }
                flat.append(dimValues[i]);
            }
            dimText = flat.toString();
        } else {
            issues.add("DIM accepts 3 or 9 integers (found " + dimValues.length + ")");
        }

        // ---- band path ----
        boolean bandMode = !request.bandVertices.isEmpty();
        List<String[]> vertices = new ArrayList<>();
        if (bandMode) {
            if (request.bandVertices.size() < 2) {
                issues.add("a band path needs at least TWO vertices (found "
                        + request.bandVertices.size() + ")");
            }
            for (int v = 0; v < request.bandVertices.size(); v++) {
                String[] vertex = request.bandVertices.get(v);
                for (int c = 0; c < 3; c++) {
                    String token = vertex[c] == null ? "" : vertex[c].trim();
                    if (!validQToken(token)) {
                        issues.add("vertex " + (v + 1) + " coordinate " + (c + 1)
                                + " ('" + token + "') is not an int, a decimal, or a"
                                + " fraction a/b (phonopy's own accepted grammar,"
                                + " setting-tags doc)");
                    } else if (FRAC_TOKEN.matcher(token).matches()
                            && Integer.parseInt(token.substring(token.indexOf('/') + 1)) == 0) {
                        issues.add("vertex " + (v + 1) + " coordinate " + (c + 1)
                                + " has a zero denominator ('" + token + "')");
                    }
                }
                vertices.add(new String[] {vertex[0].trim(), vertex[1].trim(),
                        vertex[2].trim()});
            }
            if (request.bandPoints <= 1) {
                issues.add("BAND_POINTS must be > 1 (samplings PER SEGMENT; upstream"
                        + " default 51, this builder's default 101)");
            }
            int segments = vertices.size() - 1;
            if (!request.bandLabels.isEmpty()) {
                int expectedVertices = vertices.size();
                if (request.bandLabels.size() != expectedVertices) {
                    warnings.add(request.bandLabels.size() + " labels for "
                            + expectedVertices + " path vertices: under"
                            + " BAND_CONNECTION the upstream yaml writer consumes one"
                            + " label per shared vertex (5 connected segments -> 6"
                            + " labels like your own command); a mismatch here shifts"
                            + " the tick labels - WARNED, not refused, since phonopy"
                            + " itself tolerates it");
                }
            }
        }

        // ---- DOS / PDOS / TPROP machinery needs MESH ----
        boolean meshMode = request.dosMode || request.tpropMode
                || !request.pdosTokens.isEmpty();
        String meshText = null;
        if (meshMode) {
            int[] m = request.mesh;
            if (m == null || m.length != 3) {
                issues.add("MESH needs exactly 3 ints for DOS/PDOS/TPROP");
            } else {
                for (int value : m) {
                    if (value < 1) {
                        issues.add("MESH entries must be >= 1 (found " + value + ")");
                    }
                }
                meshText = m[0] + " " + m[1] + " " + m[2];
            }
            if (!request.pdosTokens.isEmpty() && request.bandVertices.isEmpty()
                    && request.tpropMode) {
                warnings.add("PDOS + TPROP both on the same mesh is valid but doubles"
                        + " the run-mode output set; both are emitted as configured");
            }
        }
        if (request.tpropMode) {
            if (!(request.tstep > 0.0)) {
                issues.add("TSTEP must be > 0 (K)");
            }
            if (request.tmax < request.tmin) {
                issues.add("TMAX < TMIN (" + request.tmax + " < " + request.tmin
                        + "): the temperature range is empty");
            }
        }
        if (!bandMode && !meshMode) {
            issues.add("nothing requested: set a band path (>= 2 vertices), dos(true),"
                    + " pdos(...), or tprop(...)");
        }

        if (!issues.isEmpty()) {
            return OperationResult.failed("PHONOPY_PLAN_INPUT",
                    "phonopy plan refused (" + issues.size() + " issue(s)): "
                            + String.join(" | ", issues), null);
        }

        // ---- conf text (verbatim upstream tag grammar) ----
        StringBuilder conf = new StringBuilder();
        conf.append("# phonopy setting file PREVIEW written by QuantumForge.\n");
        conf.append("# Grammar: one 'TAG = value' per line (case-insensitive), '#'\n");
        conf.append("# comments, boolean .TRUE./.FALSE. - phonopy's own conf format\n");
        conf.append("# (test/cui/phonopy_command/*.conf, settings.py _read_file).\n");
        conf.append("DIM = ").append(dimText).append('\n');
        if (bandMode) {
            // canonical comma-separated SEGMENTS between consecutive vertices
            StringBuilder band = new StringBuilder();
            for (int s = 0; s + 1 < vertices.size(); s++) {
                if (s > 0) {
                    band.append(", ");
                }
                String[] a = vertices.get(s);
                String[] b = vertices.get(s + 1);
                band.append(a[0]).append(' ').append(a[1]).append(' ').append(a[2])
                        .append(' ').append(b[0]).append(' ').append(b[1]).append(' ')
                        .append(b[2]);
            }
            conf.append("BAND = ").append(band).append('\n');
            conf.append("BAND_POINTS = ").append(request.bandPoints).append('\n');
            if (!request.bandLabels.isEmpty()) {
                conf.append("BAND_LABELS = ")
                        .append(String.join(" ", request.bandLabels)).append('\n');
            }
            conf.append("BAND_CONNECTION = ")
                    .append(request.bandConnection ? ".TRUE." : ".FALSE.").append('\n');
        }
        if (request.dosMode || !request.pdosTokens.isEmpty()) {
            conf.append("MESH = ").append(meshText).append('\n');
            conf.append("DOS = .TRUE.\n");
            if (!request.pdosTokens.isEmpty()) {
                conf.append("PDOS = ").append(String.join(" ", request.pdosTokens))
                        .append('\n');
            }
        }
        if (request.tpropMode) {
            if (request.dosMode == false && request.pdosTokens.isEmpty()) {
                conf.append("MESH = ").append(meshText).append('\n');
            }
            conf.append("TPROP = .TRUE.\n");
            conf.append(String.format(Locale.ROOT, "TMIN = %s%n",
                    formatNumber(request.tmin)));
            conf.append(String.format(Locale.ROOT, "TMAX = %s%n",
                    formatNumber(request.tmax)));
            conf.append(String.format(Locale.ROOT, "TSTEP = %s%n",
                    formatNumber(request.tstep)));
        }
        if (request.nac) {
            conf.append("NAC = .TRUE.\n");
        }

        // ---- flow commands (doc/qe.md, verbatim shape) ----
        List<String> flow = new ArrayList<>();
        flow.add("phonopy-init --qe -d --dim=\"" + dimText + "\" -c " + cell
                + "    # step 1: supercell-*.in + phonopy_disp.yaml");
        flow.add("(run pw.x on EVERY supercell-*.in, e.g."
                + " 'pw.x -i disp-001.in |& tee disp-001.out')"
                + "    # step 2: forces are measured by QE, not guessed");
        flow.add("phonopy-init -f disp-001.out disp-002.out ..."
                + "    # step 3: FORCE_SETS (needs phonopy_disp.yaml beside it)");
        int step = 4;
        if (request.nac) {
            // doc/qe.md 'Non-analytical term correction (Optional)', verbatim shape
            String stem = cell.replaceAll("\\.in$", "");
            flow.add("(BORN/NAC step A: SCF on the UNIT cell with a denser k mesh -"
                    + " 'pw.x -i " + stem + ".in |& tee " + stem + ".out'; doc/qe.md"
                    + " carries the verbatim NaCl.in example)");
            flow.add("(BORN/NAC step B: write '" + stem + ".ph.in' as   &inputph /"
                    + " tr2_ph = 1.0d-14, epsil = .true. / 0 0 0   then"
                    + " 'ph.x -i " + stem + ".ph.in |& tee " + stem + ".ph.out'"
                    + " - exact template from doc/qe.md)");
            flow.add("phonopy-qe-born " + stem + ".in " + stem + ".ph.out | tee BORN"
                    + "    # step " + step + ": dielectric + Born charges into the"
                    + " BORN file (upstream helper command); QuantumForge's studio"
                    + " can also BUILD this BORN text from the ph.out (raw values)");
            step++;
        }
        StringBuilder post = new StringBuilder("phonopy --qe -p -s --config ")
                .append(confName);
        flow.add(post.toString()
                + "    # step " + step + ": post-process (-p plots, -s writes"
                + " band.yaml / total_dos.dat / projected_dos.dat /"
                + " thermal_properties.yaml)");

        // ---- phonopy-load one-liner (the user's own command #4 shape) ----
        List<String> load = new ArrayList<>();
        if (bandMode) {
            StringBuilder flat = new StringBuilder();
            for (String[] vertex : vertices) {
                if (flat.length() > 0) {
                    flat.append("  ");
                }
                flat.append(vertex[0]).append(' ').append(vertex[1]).append(' ')
                        .append(vertex[2]);
            }
            StringBuilder cmd = new StringBuilder("phonopy-load --band \"")
                    .append(flat).append('"');
            if (!request.bandLabels.isEmpty()) {
                cmd.append(" --band_labels \"")
                        .append(String.join(" ", request.bandLabels)).append(" \"");
            }
            if (request.bandConnection) {
                cmd.append(" --band_connection");
            }
            cmd.append(" -p");
            load.add(cmd.toString()
                    + "    # live plot from phonopy.yaml (post-process summary)");
            load.add(cmd + " -s    # same, but also writes band.yaml for this viewer");
        }
        if (request.dosMode || !request.pdosTokens.isEmpty()) {
            StringBuilder cmd = new StringBuilder("phonopy-load --dos --mesh \"")
                    .append(meshText).append("\" -p -s");
            if (!request.pdosTokens.isEmpty()) {
                cmd.append(" --pdos \"").append(String.join(" ", request.pdosTokens))
                        .append('"');
            }
            load.add(cmd + "    # writes total_dos.dat (+ projected_dos.dat)");
        }
        if (request.tpropMode) {
            load.add("phonopy-load --tprop --mesh \"" + meshText + "\" --tmin "
                    + formatNumber(request.tmin) + " --tmax "
                    + formatNumber(request.tmax) + " --tstep "
                    + formatNumber(request.tstep)
                    + " -p -s    # writes thermal_properties.yaml");
        }

        notes.add("everything above is a PREVIEW: QuantumForge does not bundle"
                + " phonopy and never claims it ran - paste the lines or hand the conf"
                + " to your own phonopy install");
        notes.add("frequencies land in THz (phonopy's default unit) inside band.yaml"
                + " / the DOS tables that this app's viewer then reads");
        if (request.bandConnection && bandMode) {
            notes.add("BAND kept connected segments (" + (vertices.size() - 1)
                    + " segments sharing " + vertices.size() + " vertices) - the shape"
                    + " your own phonopy-load command encodes");
        }
        if (request.nac) {
            notes.add("NAC = .TRUE. emitted + BORN steps above; HOW NAC activates is"
                    + " version-sensitive, stated not hidden: doc/qe.md still says"
                    + " 'just adding the --nac option', but upstream REMOVED --nac in"
                    + " phonopy v4 - its own argparse message reads: '--nac was"
                    + " removed in phonopy v4. NAC is now enabled automatically when"
                    + " a BORN file is present or nac_params are stored in"
                    + " phonopy.yaml. Use --nonac to disable NAC.' So on v4 the BORN"
                    + " file next to the conf is ENOUGH (load.py: 'BORN is searched"
                    + " in the current directory when is_nac=True', default True -"
                    + " the doc's own run prints: NAC params were read from"
                    + " \"BORN\".); on pre-v4 installs append --nac to the"
                    + " post-process command. Check 'phonopy --version' first");
        }

        Plan plan = new Plan(conf.toString(), List.copyOf(flow), List.copyOf(load),
                List.copyOf(warnings), List.copyOf(notes));
        return OperationResult.success("PHONOPY_PLAN_OK",
                "conf (" + conf.toString().lines().count() + " lines) + "
                        + flow.size() + " flow steps + " + load.size()
                        + " phonopy-load one-liner(s)"
                        + (warnings.isEmpty() ? "" : "; " + warnings.size()
                                + " warning(s) stated"),
                plan);
    }

    /** q-token grammar shared with QEPhonopyGruneisenPlan (same package). */
    static boolean validQToken(String token) {
        return INT_TOKEN.matcher(token).matches() || DEC_TOKEN.matcher(token).matches()
                || FRAC_TOKEN.matcher(token).matches();
    }

    private static long det3(int[] m) {
        return (long) m[0] * ((long) m[4] * m[8] - (long) m[5] * m[7])
                - (long) m[1] * ((long) m[3] * m[8] - (long) m[5] * m[6])
                + (long) m[2] * ((long) m[3] * m[7] - (long) m[4] * m[6]);
    }

    private static String formatNumber(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1.0e15) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%g", value);
    }

    /** The cubic BCC vertex preset of the user's own command #4 (labels G X R M G R). */
    public static Request cubicBccPreset() {
        return new Request()
                .supercellDim(2, 2, 2)
                .bandVertex("0.0", "0.0", "0.0")      // G
                .bandVertex("0.5", "0.0", "0.0")      // X
                .bandVertex("0.5", "0.5", "0.5")      // R
                .bandVertex("0.5", "0.5", "0.0")      // M
                .bandVertex("0.0", "0.0", "0.0")      // G
                .bandVertex("0.5", "0.5", "0.5")      // R
                .bandLabels("G", "X", "R", "M", "G", "R")
                .bandConnection(true)
                .bandPoints(101)
                .mesh(8, 8, 8)
                .dos(true);
    }
}
