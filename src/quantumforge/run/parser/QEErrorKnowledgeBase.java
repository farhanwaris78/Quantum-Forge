/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Deterministic signatures for common Quantum ESPRESSO failures.
 *
 * <p>Suggestions are rule-based and testable; they deliberately do not invent
 * numerical fixes. Expand only with real log excerpts and official-doc links.</p>
 */
public final class QEErrorKnowledgeBase {

    public static final class Signature {
        private final String id;
        private final Pattern pattern;
        private final String summary;
        private final String likelyCause;
        private final String suggestedChecks;
        private final String documentationUrl;

        public Signature(String id, String regex, String summary, String likelyCause,
                         String suggestedChecks, String documentationUrl) {
            this.id = Objects.requireNonNull(id);
            this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            this.summary = summary;
            this.likelyCause = likelyCause;
            this.suggestedChecks = suggestedChecks;
            this.documentationUrl = documentationUrl;
        }

        public String getId() { return this.id; }
        public String getSummary() { return this.summary; }
        public String getLikelyCause() { return this.likelyCause; }
        public String getSuggestedChecks() { return this.suggestedChecks; }
        public String getDocumentationUrl() { return this.documentationUrl; }

        public boolean matches(String text) {
            return text != null && this.pattern.matcher(text).find();
        }
    }

    public static final class Diagnosis {
        private final Signature signature;
        private final String excerpt;

        public Diagnosis(Signature signature, String excerpt) {
            this.signature = signature;
            this.excerpt = excerpt == null ? "" : excerpt;
        }

        public Signature getSignature() { return this.signature; }
        public String getExcerpt() { return this.excerpt; }

        @Override
        public String toString() {
            return "[" + this.signature.getId() + "] " + this.signature.getSummary()
                    + " | cause: " + this.signature.getLikelyCause()
                    + " | check: " + this.signature.getSuggestedChecks();
        }
    }

    private static final List<Signature> SIGNATURES = List.of(
            new Signature("MISSING_PSEUDO",
                    "Error in routine read_pseudopot|file .*\\.UPF not found|cannot open file .*\\.UPF",
                    "Pseudopotential file is missing or unreadable.",
                    "Wrong pseudo path, typo in ATOMIC_SPECIES, or pseudo not installed.",
                    "Confirm Menu→Path pseudo directory, file names, and that the UPF exists and is readable.",
                    "https://www.quantum-espresso.org/Doc/INPUT_PW.html#idm458"),
            new Signature("SCF_NOT_CONVERGED",
                    "convergence NOT achieved|electrons: convergence NOT achieved after",
                    "Electronic SCF did not converge within the allowed iterations.",
                    "Too tight thresholds, bad mixing, metallic occupations, or inadequate cutoffs/k-mesh.",
                    "Inspect energy oscillation, raise electron_maxstep, adjust mixing_beta, check smearing for metals.",
                    "https://www.quantum-espresso.org/Doc/INPUT_PW.html#idm879"),
            new Signature("FFT_GRID_MISMATCH",
                    "FFT grid incompatible|some G-vectors not found|dense grid not commensurate",
                    "FFT / plane-wave grid incompatibility.",
                    "Inconsistent ecutrho/ecutwfc or restart with a different cutoff.",
                    "Delete scratch .save if restarting with new cutoffs; ensure ecutrho ≥ 4× ecutwfc for US/PAW.",
                    "https://www.quantum-espresso.org/Doc/INPUT_PW.html#idm545"),
            new Signature("CHARGE_NOT_NEUTRAL",
                    "charge is wrong|the system is not charge neutral|unexpected charge",
                    "Charge neutrality or electron-count problem.",
                    "Wrong tot_charge, occupations, or nspin/starting_magnetization combination.",
                    "Verify valence electron count from pseudopotentials and SYSTEM.tot_charge/nbnd.",
                    "https://www.quantum-espresso.org/Doc/INPUT_PW.html#idm590"),
            new Signature("MPI_ABORT",
                    "MPI_Abort|application called MPI_Abort|FORTRAN STOP",
                    "Process aborted (often after an earlier QE error).",
                    "Earlier fatal error, resource exhaustion, or MPI environment mismatch.",
                    "Read the first ERROR message above the abort; match MPI family between mpirun and QE build.",
                    "https://www.quantum-espresso.org/Doc/user_guide/"),
            new Signature("DISK_FULL_OR_IO",
                    "No space left on device|cannot write|I/O error|error opening",
                    "Filesystem I/O failure.",
                    "Full disk, permission error, or invalid outdir/wfcdir.",
                    "Check free space and write permissions for outdir and the project directory.",
                    "https://www.quantum-espresso.org/Doc/INPUT_PW.html#idm214"),
            new Signature("SYMMETRY_PROBLEM",
                    "symmetry operation|Error in routine checkallsym|not orthogonal",
                    "Symmetry detection or lattice inconsistency.",
                    "Noisy coordinates, wrong ibrav, or conflicting CELL_PARAMETERS.",
                    "Increase atomic coordinate precision, set nosym=.true. only after understanding implications.",
                    "https://www.quantum-espresso.org/Doc/INPUT_PW.html#idm671"),
            new Signature("MEMORY",
                    "allocation failure|cannot allocate|out of memory|killed",
                    "Memory allocation failure.",
                    "System too large for available RAM/MPI ranks or excessive nbnd/ecut.",
                    "Reduce ecut/k-mesh, increase ranks, or use disk_io/wf_collect carefully.",
                    "https://www.quantum-espresso.org/Doc/user_guide/")
    );

    private QEErrorKnowledgeBase() {
        // Utility.
    }

    public static List<Signature> signatures() {
        return SIGNATURES;
    }

    public static List<Diagnosis> diagnose(String logText) {
        if (logText == null || logText.isBlank()) {
            return List.of();
        }
        String text = logText;
        List<Diagnosis> hits = new ArrayList<>();
        for (Signature signature : SIGNATURES) {
            if (signature.matches(text)) {
                hits.add(new Diagnosis(signature, excerpt(text, signature)));
            }
        }
        return Collections.unmodifiableList(hits);
    }

    public static List<Diagnosis> diagnoseLines(Iterable<String> lines) {
        if (lines == null) {
            return List.of();
        }
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (line != null) {
                builder.append(line).append('\n');
            }
        }
        return diagnose(builder.toString());
    }

    private static String excerpt(String text, Signature signature) {
        String lower = text.toLowerCase(Locale.ROOT);
        // Keep a short surrounding window around the first match-ish keyword from the id.
        int idx = lower.indexOf("error");
        if (idx < 0) {
            idx = 0;
        }
        int start = Math.max(0, idx - 80);
        int end = Math.min(text.length(), idx + 200);
        return text.substring(start, end).replace('\n', ' ').trim();
    }
}
