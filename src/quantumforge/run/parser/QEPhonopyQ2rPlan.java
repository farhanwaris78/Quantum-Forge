/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import java.util.ArrayList;
import java.util.List;

import quantumforge.operation.OperationResult;

/**
 * Builder for the QE-native route into phonopy force constants: the
 * pw.x -&gt; ph.x (ldisp) -&gt; q2r.x -&gt; PH_Q2R flow, marked
 * "**Experimental**" in the upstream doc. Pinned read-only against
 * (github.com/phonopy/phonopy commit
 * 3a3e0f099da5de2556e75d72ea89b3bb22c8e97e):
 *
 * <ul>
 *   <li>doc/qe.md's q2r section: the verbatim NaCl.ph.in template
 *       ({@code &inputph tr2_ph=1.0d-16, prefix='NaCl', ldisp=.true.,
 *       nq1=4, nq2=4, nq3=4, amass(1)=..., amass(2)=..., outdir='.',
 *       fildyn='NaCl.dyn' /}), the per-q {@code start_q/last_q} independence
 *       note, and the Gamma-swap doctrine: epsil=.false. Gamma-only deck
 *       ({@code 0.0 0.0 0.0} q line) whose NaCl.dyn REPLACES NaCl.dyn1
 *       ("These are unnecessary for phonopy", doc verbatim);</li>
 *   <li>example/NaCl-QE-q2r: the verbatim q2r.in template
 *       ({@code &input fildyn='NaCl.dyn', zasr='simple', flfrc='NaCl.fc' /})
 *       and the README's verbatim command sequence incl.
 *       {@code cp NaCl.dyn1 NaCl.dyn1.bak} / {@code cp NaCl.dyn NaCl.dyn1}
 *       and the NAC-BORN route
 *       {@code python make_born_q2r.py NaCl.in NaCl.fc > BORN};</li>
 *   <li>the doc's own honesty markers, quoted verbatim: "**Experimental**",
 *       and "A parser of {@code q2r.x} output is implemented experimentally.
 *       Currently command-line user interface is not prepared." - which is
 *       WHY the flow ends in the make_fc_q2r.py script form;</li>
 *   <li>doc post-processing:
 *       {@code python make_fc_q2r.py NaCl.in NaCl.fc} &rarr;
 *       {@code phonopy_params_q2r.yaml} &rarr;
 *       {@code phonopy phonopy_params_q2r.yaml --band="0 0 0  1/2 0 0
 *       1/2 1/2 0  0 0 0  1/2 1/2 1/2" -p} (doc) /
 *       {@code phonopy-load phonopy_params_q2r.yaml --band auto -p}
 *       (README).</li>
 * </ul>
 *
 * <p>PREVIEW doctrine identical to QEPhonopyPlan: QuantumForge never runs
 * these lines; it emits the exact templates with the user's own prefix /
 * fildyn / flfrc / nq grid / masses substituted.</p>
 */
public final class QEPhonopyQ2rPlan {

    private QEPhonopyQ2rPlan() {
        // Utility
    }

    /** q2r-route request (prefix + grid + masses + band path). */
    public static final class Request {
        private String prefix = "NaCl";
        private String cellFilename = "NaCl.in";
        private String fildyn = "NaCl.dyn";
        private String flfrc = "NaCl.fc";
        private String zasr = "simple";
        private int[] nq = {4, 4, 4};
        private final List<String> masses = new ArrayList<>();
        private boolean doBand = true;
        private final List<String[]> bandVertices = new ArrayList<>();

        /** pw/ph prefix (the doc example uses 'NaCl'). */
        public Request prefix(String prefix) { this.prefix = prefix; return this; }

        /** The pw input deck name for the make_fc_q2r call (doc: NaCl.in). */
        public Request cellFilename(String name) { this.cellFilename = name; return this; }

        /** fildyn stem for ph.x and q2r.in (doc: 'NaCl.dyn'). */
        public Request fildyn(String fildyn) { this.fildyn = fildyn; return this; }

        /** flfrc output name for q2r.in (doc: 'NaCl.fc'). */
        public Request flfrc(String flfrc) { this.flfrc = flfrc; return this; }

        /** zasr acoustic-sum-rule option (example: 'simple'). */
        public Request zasr(String zasr) { this.zasr = zasr; return this; }

        /** The ldisp q grid nq1 nq2 nq3 (== q2r supercell dims). */
        public Request nq(int nq1, int nq2, int nq3) {
            this.nq = new int[] {nq1, nq2, nq3};
            return this;
        }

        /** One mass per species, in the ldisp deck's amass(i)= order. */
        public Request amass(String... values) {
            this.masses.clear();
            if (values != null) {
                for (String v : values) {
                    this.masses.add(v);
                }
            }
            return this;
        }

        /** Band path vertices for the final phonopy band command (same grammar). */
        public Request bandVertex(String x, String y, String z) {
            this.bandVertices.add(new String[] {x, y, z});
            return this;
        }

        /** False suppresses the band command (yaml build only). */
        public Request band(boolean on) { this.doBand = on; return this; }
    }

    /** The preview product: flow steps + notes. */
    public static final class Plan {
        private final List<String> steps;
        private final List<String> warnings;
        private final List<String> notes;

        private Plan(List<String> steps, List<String> warnings, List<String> notes) {
            this.steps = steps;
            this.warnings = warnings;
            this.notes = notes;
        }

        /** The ordered flow (pw.x -> ph.x -> swap -> q2r.x -> phonopy). */
        public List<String> getSteps() { return this.steps; }

        public List<String> getWarnings() { return this.warnings; }

        public List<String> getNotes() { return this.notes; }
    }

    /** Builds the experimental q2r flow preview, or an enumerated refusal. */
    public static OperationResult<Plan> build(Request request) {
        if (request == null) {
            return OperationResult.failed("PHONOPY_Q2R_PLAN_INPUT",
                    "No q2r plan request.", null);
        }
        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        String prefix = oneToken(request.prefix, "prefix", issues);
        String cell = oneToken(request.cellFilename, "cell filename", issues);
        String fildyn = oneToken(request.fildyn, "fildyn stem", issues);
        String flfrc = oneToken(request.flfrc, "flfrc name", issues);
        String zasr = oneToken(request.zasr, "zasr", issues);
        for (int n : request.nq) {
            if (n < 1) {
                issues.add("nq entries must be >= 1 (found " + n + ")");
            }
        }
        if (request.nq[0] * request.nq[1] * request.nq[2] > 100000) {
            warnings.add("nq grid " + request.nq[0] + "x" + request.nq[1] + "x"
                    + request.nq[2] + " is unusually fine for a q2r route (the"
                    + " doc example uses 4x4x4 / 8x8x8); emitted as given");
        }
        for (int m = 0; m < request.masses.size(); m++) {
            String mass = request.masses.get(m).trim();
            try {
                double v = Double.parseDouble(mass);
                if (!(v > 0.0)) {
                    issues.add("amass(" + (m + 1) + ") must be > 0: '" + mass + "'");
                }
            } catch (NumberFormatException e) {
                issues.add("amass(" + (m + 1) + ") is not plain decimal text: '"
                        + mass + "'");
            }
        }
        if (request.masses.isEmpty()) {
            warnings.add("no amass(i)= entries configured - the doc's ldisp template"
                    + " carries them; ph.x needs correct masses for the dynamical"
                    + " matrix");
        }
        List<String[]> vertices = new ArrayList<>();
        if (request.doBand) {
            if (request.bandVertices.size() < 2) {
                issues.add("band() needs at least TWO vertices (for the final"
                        + " phonopy ... --band command)");
            }
            for (int v = 0; v < request.bandVertices.size(); v++) {
                String[] vertex = request.bandVertices.get(v);
                for (int c = 0; c < 3; c++) {
                    String token = vertex[c] == null ? "" : vertex[c].trim();
                    if (!QEPhonopyPlan.validQToken(token)) {
                        issues.add("vertex " + (v + 1) + " coordinate " + (c + 1)
                                + " ('" + token + "') is not an int, a decimal, or a"
                                + " fraction a/b");
                    }
                }
                vertices.add(new String[] {vertex[0].trim(), vertex[1].trim(),
                        vertex[2].trim()});
            }
        }
        if (!issues.isEmpty()) {
            return OperationResult.failed("PHONOPY_Q2R_PLAN_INPUT",
                    "q2r plan refused (" + issues.size() + " issue(s)): "
                            + String.join(" | ", issues), null);
        }

        List<String> steps = new ArrayList<>();
        steps.add("# **Experimental** route (doc/qe.md marker, verbatim) - QE-native"
                + " force constants for phonopy via q2r.x");
        steps.add("pw.x -i " + cell + " |& tee " + prefix + ".out"
                + "    # step 1: SCF (the doc's NaCl.in template carries"
                + " tprnfor/tstress and conv_thr=1.0d-12)");
        StringBuilder ph = new StringBuilder("## " + prefix + ".ph.in (ldisp deck,"
                + " doc template verbatim shape):\n"
                + " &inputph\n"
                + "  tr2_ph=1.0d-16,\n"
                + "  prefix='" + prefix + "',\n"
                + "  ldisp=.true.,\n"
                + "  nq1=" + request.nq[0] + ", nq2=" + request.nq[1] + ", nq3="
                + request.nq[2] + "\n");
        for (int m = 0; m < request.masses.size(); m++) {
            ph.append("  amass(").append(m + 1).append(")=")
                    .append(request.masses.get(m).trim()).append(",\n");
        }
        ph.append("  outdir='.',\n"
                + "  fildyn='" + fildyn + "',\n"
                + " /");
        steps.add(ph.toString());
        steps.add("ph.x -i " + prefix + ".ph.in |& tee " + prefix + ".ph.out"
                + "    # step 2: one run covers the whole ldisp grid; per-q"
                + " independence via start_q=N last_q=N (doc: same template with"
                + " two extra lines)");
        StringBuilder gamma = new StringBuilder("## " + prefix + ".ph-gamma.in"
                + " (Gamma-only deck for the SWAP, doc verbatim shape):\n"
                + " &inputph\n"
                + "  tr2_ph=1.0d-16,\n"
                + "  prefix='" + prefix + "',\n"
                + "  epsil=.false.,\n");
        for (int m = 0; m < request.masses.size(); m++) {
            gamma.append("  amass(").append(m + 1).append(")=")
                    .append(request.masses.get(m).trim()).append(",\n");
        }
        gamma.append("  outdir='.',\n"
                + "  fildyn='" + fildyn + "',\n"
                + " /\n"
                + "0.0 0.0 0.0");
        steps.add(gamma.toString());
        steps.add("ph.x -i " + prefix + ".ph-gamma.in |& tee " + prefix
                + ".ph-gamma.out    # step 3: epsil=.false. Gamma-only -> a"
                + " dielectric/BEC-free " + fildyn);
        steps.add("cp " + fildyn + "1 " + fildyn + "1.bak"
                + " && cp " + fildyn + " " + fildyn + "1"
                + "    # step 4: THE SWAP - 'These are unnecessary for phonopy'"
                + " (doc verbatim); README's exact commands mirrored");
        steps.add("## q2r.in (example template verbatim):\n"
                + " &input\n"
                + "   fildyn='" + fildyn + "', zasr='" + zasr + "', flfrc='" + flfrc
                + "'\n"
                + " /");
        steps.add("q2r.x < q2r.in |& tee q2r.out"
                + "    # step 5: real-space force constants (unit note: Ry/au^2) ->"
                + " " + flfrc);
        steps.add("python make_fc_q2r.py " + cell + " " + flfrc
                + "    # step 6: phonopy's experimental PH_Q2R parser ->"
                + " phonopy_params_q2r.yaml ('Currently command-line user interface"
                + " is not prepared.' - doc verbatim, hence the script form)");
        StringBuilder band = new StringBuilder();
        for (String[] vertex : vertices) {
            if (band.length() > 0) {
                band.append("  ");
            }
            band.append(vertex[0]).append(' ').append(vertex[1]).append(' ')
                    .append(vertex[2]);
        }
        if (request.doBand) {
            steps.add("phonopy phonopy_params_q2r.yaml --band=\"" + band + "\" -p"
                    + "    # step 7: the doc's own post-process form");
            steps.add("phonopy-load phonopy_params_q2r.yaml --band auto -p"
                    + "    # README's alternative (--band auto requires seekpath;"
                    + " the explicit string works everywhere)");
        }
        steps.add("# BORN when you deliberately kept the NAC dyn: per README - run"
                + " q2r.x WITHOUT the swap (keep " + fildyn + "1.bak as " + fildyn
                + "1), then 'python make_born_q2r.py " + cell + " " + flfrc + " >"
                + " BORN' - or open the " + flfrc + " in QuantumForge's phonopy"
                + " studio and EXTRACT BORN from its NAC block (raw, NOT"
                + " symmetrized - upstream's script symmetrizes)");

        notes.add("the NAC doctrine, verbatim doc/qe.md: for insulators QE writes"
                + " dielectric + Born charges into the Gamma dyn file under"
                + " ldisp; q2r then applies QE's own NAC to the real-space"
                + " constants - 'this force constants are partially corrected by"
                + " QE's implemented NAC method. Phonopy needs uncorrected force"
                + " constants.' (example README verbatim), hence the epsil=.false."
                + " swap");
        notes.add("q2r file grammar is community-documented (pw_forum links quoted"
                + " in phonopy's parser): QuantumForge reads it with"
                + " QEPhonopyQ2rFc, pinned against the real 18,489-line"
                + " example/NaCl-QE-q2r/NaCl.fc (36 blocks x 512, order census"
                + " 18432/18432)");
        notes.add("everything above is a PREVIEW: QuantumForge does not run pw.x,"
                + " ph.x, q2r.x, or python for you - paste the lines into your own"
                + " terminal / job file");

        return OperationResult.success("PHONOPY_Q2R_PLAN_OK",
                steps.size() + " flow step(s) (Experimental route)" + (
                        warnings.isEmpty() ? "" : "; " + warnings.size()
                                + " warning(s) stated"),
                new Plan(List.copyOf(steps), List.copyOf(warnings),
                        List.copyOf(notes)));
    }

    private static String oneToken(String value, String name, List<String> issues) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty() || v.contains("\"") || v.contains(" ") || v.contains("'")) {
            issues.add(name + " must be one shell-token without quotes/spaces"
                    + " (given: '" + v + "')");
        }
        return v;
    }

    /** The doc's own NaCl example preset (4x4x4 grid, NaCl band path). */
    public static Request naclPreset() {
        return new Request()
                .prefix("NaCl").cellFilename("NaCl.in").fildyn("NaCl.dyn")
                .flfrc("NaCl.fc").nq(4, 4, 4)
                .amass("22.98976928", "35.453")
                .bandVertex("0", "0", "0")
                .bandVertex("1/2", "0", "0")
                .bandVertex("1/2", "1/2", "0")
                .bandVertex("0", "0", "0")
                .bandVertex("1/2", "1/2", "1/2");
    }
}
