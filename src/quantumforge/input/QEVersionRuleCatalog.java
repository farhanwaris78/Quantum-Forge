/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import quantumforge.input.namelist.QEValue;

/**
 * Version-aware INPUT_PW keyword catalog - the curated first slice (Roadmap
 * #22): a CONFIDENT, READABLE snapshot of the &CONTROL/&SYSTEM keywords whose
 * status is uniform across the QE 7.2-7.5 window. The honesty contract:
 *
 * <ul>
 *   <li>this slice is curated by hand, NOT generated from the upstream docs,
 *       so it covers ~25 keywords of the hundreds INPUT_PW defines;</li>
 *   <li>ABSENCE of a keyword from the catalog is reported as
 *       NOT-IN-CURATED - it is never judged invalid (that would fabricate
 *       authority the slice does not have);</li>
 *   <li>a value outside a curated allowed-set is a WARNING to verify against
 *       the exact minor-version docs ({@link QEKeywordHelp#INPUT_PW_URL}),
 *       never a fabricated rejection;</li>
 *   <li>REMOVED entries are the true action items: keywords that 7.2-7.5 no
 *       longer documents (the classic: wf_collect, whose I/O role became
 *       automatic in the 7.x window).</li>
 * </ul>
 *
 * The #22 completeness work LANDED in batch 150: the machine-generated,
 * per-minor-version mined schema (QE 7.2-7.6, {@code quantumforge.input.schema.
 * QENamelistSchema}). This curated slice stays as the readable baseline; new
 * audits should consume the mined schema.
 */
public final class QEVersionRuleCatalog {

    /** Minor versions this curated window covers (status uniform within it). */
    public static final List<String> SUPPORTED_VERSIONS =
            List.of("7.2", "7.3", "7.4", "7.5");
    /** Scope text repeated on every report for honesty. */
    public static final String WINDOW_NOTE =
            "curated INPUT_PW snapshot, uniform across QE 7.2-7.5";

    /** Per-keyword audit verdict. */
    public enum AuditVerdict {
        /** Documented in the window and (when typed) inside the curated values. */
        OK,
        /** Documented, but the verbatim value is outside the curated set. */
        VALUE_WARNING,
        /** No longer documented in the window - the actionable removals. */
        REMOVED_KEYWORD,
        /** Outside the curated slice - reported, deliberately not judged. */
        NOT_IN_CURATED
    }

    /** One curated keyword rule. */
    public static final class Rule {
        private final String namelist;
        private final String keyword;
        private final boolean removed;
        private final List<String> allowedValues; // null = free-form in this slice
        private final String note;

        Rule(String namelist, String keyword, boolean removed, List<String> allowedValues,
                String note) {
            this.namelist = namelist;
            this.keyword = keyword;
            this.removed = removed;
            this.allowedValues = allowedValues;
            this.note = note;
        }

        public String getNamelist() { return this.namelist; }
        public String getKeyword() { return this.keyword; }
        public boolean isRemoved() { return this.removed; }
        public List<String> getAllowedValues() { return this.allowedValues; }
        public String getNote() { return this.note; }
        public String getDocsUrl() { return QEKeywordHelp.INPUT_PW_URL; }

        /** Allowed-set membership on the quote-stripped, lower-case value. */
        public boolean allows(String rawValue) {
            if (this.allowedValues == null) {
                return true;
            }
            return this.allowedValues.contains(normalizeForComparison(rawValue));
        }
    }

    /** One audited live keyword. */
    public static final class AuditEntry {
        private final String namelist;
        private final String keyword;
        private final String valueEcho;
        private final AuditVerdict verdict;
        private final String note;

        AuditEntry(String namelist, String keyword, String valueEcho,
                AuditVerdict verdict, String note) {
            this.namelist = namelist;
            this.keyword = keyword;
            this.valueEcho = valueEcho;
            this.verdict = verdict;
            this.note = note;
        }

        public String getNamelist() { return this.namelist; }
        public String getKeyword() { return this.keyword; }
        public String getValueEcho() { return this.valueEcho; }
        public AuditVerdict getVerdict() { return this.verdict; }
        public String getNote() { return this.note; }
    }

    private static final List<Rule> RULES = buildRules();

    private QEVersionRuleCatalog() { }

    /** The curated rules, immutable order (CONTROL first, then SYSTEM). */
    public static List<Rule> listRules() {
        return List.copyOf(RULES);
    }

    /** Finds the rule for a (namelist, keyword); keyword matching is case-free. */
    public static Optional<Rule> find(String namelist, String keyword) {
        if (namelist == null || keyword == null) {
            return Optional.empty();
        }
        String wanted = keyword.trim().toLowerCase(Locale.ROOT);
        for (Rule rule : RULES) {
            if (rule.getNamelist().equalsIgnoreCase(namelist.trim())
                    && rule.getKeyword().toLowerCase(Locale.ROOT).equals(wanted)) {
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }

    public static boolean isSupportedVersion(String version) {
        return version != null && SUPPORTED_VERSIONS.contains(version.trim());
    }

    /**
     * Audits one live keyword value. The value echo is verbatim (quotes kept)
     * so the report shows what the input really says; membership checks use
     * the quote-stripped lower-case form.
     */
    public static AuditEntry audit(String namelist, QEValue value) {
        String keyword = value.getName();
        String echo;
        try {
            echo = value.getCharacterValue();
        } catch (RuntimeException ex) {
            echo = value.toString();
        }
        Optional<Rule> rule = find(namelist, keyword);
        if (rule.isEmpty()) {
            return new AuditEntry(namelist, keyword, echo, AuditVerdict.NOT_IN_CURATED,
                    "outside the " + WINDOW_NOTE + " - reported, deliberately NOT judged");
        }
        Rule found = rule.get();
        if (found.isRemoved()) {
            return new AuditEntry(namelist, keyword, echo, AuditVerdict.REMOVED_KEYWORD,
                    found.getNote() + " (docs: " + found.getDocsUrl() + ")");
        }
        if (!found.allows(echo)) {
            return new AuditEntry(namelist, keyword, echo, AuditVerdict.VALUE_WARNING,
                    "the verbatim value is outside the curated " + found.getNamelist()
                            + "." + found.getKeyword() + " set " + found.getAllowedValues()
                            + " - verify against the exact minor-version docs (docs: "
                            + found.getDocsUrl() + ")");
        }
        return new AuditEntry(namelist, keyword, echo, AuditVerdict.OK, found.getNote());
    }

    /** Quote-stripping, lower-case normalisation used for membership checks. */
    public static String normalizeForComparison(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.length() >= 2 && ((trimmed.startsWith("'") && trimmed.endsWith("'"))
                || (trimmed.startsWith("\"") && trimmed.endsWith("\"")))) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.trim().toLowerCase(Locale.ROOT);
    }

    private static List<Rule> buildRules() {
        List<Rule> rules = new ArrayList<>();
        // &CONTROL
        rules.add(new Rule("CONTROL", "calculation", false,
                List.of("scf", "nscf", "bands", "relax", "md", "vc-relax", "vc-md"),
                "pw.x driver; 'cp' is NOT a valid pw.x calculation (the dedicated cp.x "
                        + "executable is the tool - the CP_INPUT_DRAFT kind audits that)"));
        rules.add(new Rule("CONTROL", "title", false, null,
                "free-form run label echoed throughout the output"));
        rules.add(new Rule("CONTROL", "verbosity", false,
                List.of("high", "low", "debug"),
                "INPUT_PW 7.2-7.5 documents low/high (+ debug for developers)"));
        rules.add(new Rule("CONTROL", "restart_mode", false,
                List.of("from_scratch", "restart"), "restart routing for the run"));
        rules.add(new Rule("CONTROL", "wf_collect", true, null,
                "REMOVED across the 7.x window: wavefunction collection across pools is "
                        + "handled automatically; INPUT_PW 7.2-7.5 no longer documents the "
                        + "keyword - delete it"));
        rules.add(new Rule("CONTROL", "disk_io", false,
                List.of("none", "minimal", "low", "medium", "high"),
                "the pre-7.0 values 'now'/'default' are gone from the window"));
        rules.add(new Rule("CONTROL", "max_seconds", false, null,
                "wall-time budget in seconds for a clean stop"));
        rules.add(new Rule("CONTROL", "etot_conv_thr", false, null,
                "total-energy convergence threshold (Ry)"));
        rules.add(new Rule("CONTROL", "forc_conv_thr", false, null,
                "force convergence threshold (Ry/bohr) for relax/vc-relax"));
        rules.add(new Rule("CONTROL", "nstep", false, null,
                "ionic/electronic step budget"));
        rules.add(new Rule("CONTROL", "tstress", false,
                List.of(".true.", ".false."), "print the stress tensor"));
        rules.add(new Rule("CONTROL", "tprnfor", false,
                List.of(".true.", ".false."), "print the forces"));
        rules.add(new Rule("CONTROL", "prefix", false, null,
                "output file stem; must match any post-processing code's input"));
        rules.add(new Rule("CONTROL", "outdir", false, null,
                "scratch directory; keep it fast/local per the HPC guidance"));
        rules.add(new Rule("CONTROL", "pseudo_dir", false, null,
                "pseudopotential search path"));
        rules.add(new Rule("CONTROL", "dt", false, null,
                "time step in Rydberg atomic units for md"));
        // &SYSTEM
        rules.add(new Rule("SYSTEM", "ibrav", false, null,
                "Bravais-lattice index (0..14 plus negative specials); the exact index "
                        + "set is version-stable but not value-checked in this slice"));
        rules.add(new Rule("SYSTEM", "nat", false, null, "atom count"));
        rules.add(new Rule("SYSTEM", "ntyp", false, null, "species count"));
        rules.add(new Rule("SYSTEM", "nbnd", false, null,
                "explicit band count - REQUIRED for Wannier work (W90_WIN_DRAFT kind)"));
        rules.add(new Rule("SYSTEM", "ecutwfc", false, null,
                "wavefunction kinetic cutoff (Ry) - converge it explicitly"));
        rules.add(new Rule("SYSTEM", "ecutrho", false, null,
                "charge-density kinetic cutoff (Ry); >= 4x ecutwfc for NC, more for US/PAW"));
        rules.add(new Rule("SYSTEM", "occupations", false,
                List.of("smearing", "tetrahedra", "fixed", "from_input"),
                "metals need smearing+tetrahedra-free runs; insulators 'fixed'"));
        rules.add(new Rule("SYSTEM", "smearing", false,
                List.of("gaussian", "gauss", "m-p", "mp", "methfessel-paxton",
                        "marzari-vanderbilt", "marzari", "cold", "m-v", "mv",
                        "fermi-dirac", "f-d", "fd"),
                "curated alias set from INPUT_PW 7.2-7.5 (cold == marzari-vanderbilt)"));
        rules.add(new Rule("SYSTEM", "degauss", false, null,
                "smearing width (Ry) - must be converged, not defaulted"));
        rules.add(new Rule("SYSTEM", "nspin", false,
                List.of("1", "2", "4"),
                "1 unpolarized / 2 collinear / 4 non-collinear"));
        rules.add(new Rule("SYSTEM", "nosym", false,
                List.of(".true.", ".false."),
                "REQUIRED .true. for Wannier nscf meshes (pw2wannier90.x rejects reduced "
                        + "sets)"));
        rules.add(new Rule("SYSTEM", "noinv", false,
                List.of(".true.", ".false."), "no complex conjugation symmetry on k and -k"));
        rules.add(new Rule("SYSTEM", "tot_charge", false, null,
                "charged-cell total charge; a charged cell needs the correction schemes, "
                        + "converge with care"));
        rules.add(new Rule("SYSTEM", "tot_magnetization", false, null,
                "total spin polarization constraint for collinear runs"));
        rules.add(new Rule("SYSTEM", "assume_isolated", false,
                List.of("none", "pbc", "vacuum", "mt", "martyna-tuckerman", "esm"),
                "the ESM_SLAB_CHECK kind audits the esm branches of this keyword"));
        rules.add(new Rule("SYSTEM", "esm_bc", false,
                List.of("pbc", "bc1", "bc2", "bc3"),
                "ESM boundary condition; pbc disables the open-circuit work function"));
        rules.add(new Rule("SYSTEM", "esm_w", false, null,
                "ESM field offset (Ry) for charged slabs"));
        rules.add(new Rule("SYSTEM", "esm_efield", false, null,
                "ESM applied field (Ry/a.u.)"));
        rules.add(new Rule("SYSTEM", "ecutvcut", false, null,
                "smooth Coulomb cutoff for 2D vacuum corrections"));
        return List.copyOf(rules);
    }
}
