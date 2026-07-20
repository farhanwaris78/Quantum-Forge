/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import quantumforge.operation.OperationResult;

/**
 * Deterministic QE error knowledge base - the curated first slice (Roadmap
 * #31): a small set of verbatim needles from pw.x QE 7.x failure output,
 * each mapped to a cause and deterministic checks (no free-form/LLM
 * guessing). Rules that keep it honest:
 *
 * <ul>
 *   <li>matching is substring matching on whole lines - conservative, and the
 *       quoted line is echoed verbatim so the user can judge;</li>
 *   <li>the generic "Error in routine" signature is evaluated LAST, so the
 *       specific curated signatures always win attribution on the same line;</li>
 *   <li>at most {@link #MAX_QUOTES_PER_SIGNATURE} verbatim quotes per
 *       signature are kept (pools repeat errors); the suppressed count is
 *       reported, never hidden;</li>
 *   <li>NO hint that absence of a match means a healthy run - the no-hit
 *       report says so explicitly.</li>
 * </ul>
 *
 * A versioned, source-linked corpus built from real failure reports is the
 * remaining #31 completeness work.
 */
public final class QEErrorSignatureCatalog {

    /** Total hit cap across one log (spam containment for huge outputs). */
    public static final int MAX_HITS = 50;
    /** Verbatim quotes kept per signature. */
    public static final int MAX_QUOTES_PER_SIGNATURE = 3;
    /** Bounded log read; larger logs must be truncated/split by the caller. */
    public static final long MAX_LOG_BYTES = 16L * 1024 * 1024;
    /** Quoted-line display cap. */
    public static final int QUOTE_CAP = 160;
    /** Stable upstream troubleshooting docs used by every signature here. */
    public static final String USER_GUIDE_URL =
            "https://www.quantum-espresso.org/Doc/user_guide.pdf";

    /** Match severity. */
    public enum Severity {
        ERROR,
        WARNING,
        /** Not an error at runtime, but worth surfacing deterministically. */
        INFO
    }

    /** One curated signature. */
    public static final class Signature {
        private final String id;
        private final String needle;
        private final Severity severity;
        private final String cause;
        private final String checks;

        Signature(String id, String needle, Severity severity, String cause,
                String checks) {
            this.id = id;
            this.needle = needle;
            this.severity = severity;
            this.cause = cause;
            this.checks = checks;
        }

        public String getId() { return this.id; }
        public String getNeedle() { return this.needle; }
        public Severity getSeverity() { return this.severity; }
        public String getCause() { return this.cause; }
        public String getChecks() { return this.checks; }
        public String getDocsUrl() { return USER_GUIDE_URL; }
    }

    /** One matched line. */
    public static final class Hit {
        private final Signature signature;
        private final long lineNumber; // 1-based
        private final String quotedLine;

        Hit(Signature signature, long lineNumber, String quotedLine) {
            this.signature = signature;
            this.lineNumber = lineNumber;
            this.quotedLine = quotedLine;
        }

        public Signature getSignature() { return this.signature; }
        public long getLineNumber() { return this.lineNumber; }
        public String getQuotedLine() { return this.quotedLine; }
    }

    /** Scan outcome: the kept hits plus per-signature suppression counts. */
    public static final class ScanResult {
        private final long lineCount;
        private final List<Hit> hits;
        private final Map<String, Long> totalMatchesPerSignature;

        ScanResult(long lineCount, List<Hit> hits,
                Map<String, Long> totalMatchesPerSignature) {
            this.lineCount = lineCount;
            this.hits = hits;
            this.totalMatchesPerSignature = totalMatchesPerSignature;
        }

        public long getLineCount() { return this.lineCount; }
        public List<Hit> getHits() { return List.copyOf(this.hits); }

        /** Total matches per signature id (including suppressed repeats). */
        public long totalMatches(String signatureId) {
            return this.totalMatchesPerSignature.getOrDefault(signatureId,
                    Long.valueOf(0L)).longValue();
        }

        /** Matched signature ids in first-occurrence order. */
        public List<String> matchedSignatureIds() {
            return List.copyOf(this.totalMatchesPerSignature.keySet());
        }

        public long distinctSignatures() {
            return this.totalMatchesPerSignature.size();
        }

        public boolean isEmpty() { return this.hits.isEmpty(); }
    }

    private static final List<Signature> SIGNATURES = buildSignatures();

    private QEErrorSignatureCatalog() { }

    /** The curated signatures in evaluation order (generic fallback last). */
    public static List<Signature> listSignatures() {
        return List.copyOf(SIGNATURES);
    }

    /** Scans log text line-by-line; deterministic and allocation-bounded. */
    public static ScanResult scanText(String text) {
        List<Hit> hits = new ArrayList<>();
        Map<String, Long> totals = new LinkedHashMap<>();
        Map<String, Long> quotes = new LinkedHashMap<>();
        if (text == null) {
            return new ScanResult(0L, hits, totals);
        }
        // A single trailing newline terminates the last line; it is not an 8th line.
        String normalized = text.endsWith("\n")
                ? text.substring(0, text.length() - 1) : text;
        long lineNumber = 0L;
        for (String line : normalized.split("\r\n|\n|\r", -1)) {
            lineNumber += 1L;
            for (Signature signature : SIGNATURES) {
                if (!line.contains(signature.getNeedle())) {
                    continue;
                }
                totals.merge(signature.getId(), 1L, Long::sum);
                long quotedSoFar = quotes.getOrDefault(signature.getId(),
                        Long.valueOf(0L)).longValue();
                if (quotedSoFar < MAX_QUOTES_PER_SIGNATURE && hits.size() < MAX_HITS) {
                    String trimmed = line.trim();
                    String quoted = trimmed.length() > QUOTE_CAP
                            ? trimmed.substring(0, QUOTE_CAP) : trimmed;
                    hits.add(new Hit(signature, lineNumber, quoted));
                    quotes.merge(signature.getId(), 1L, Long::sum);
                }
                break; // one signature attribution per line, order decides
            }
        }
        return new ScanResult(lineNumber, hits, totals);
    }

    /**
     * Reads and scans a log file. Codes: ERROR_IO (unreadable), ERROR_TOO_LARGE
     * (beyond the bounded read - pass the CRASH/tail file instead).
     */
    public static OperationResult<ScanResult> scanPath(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return OperationResult.failed("ERROR_IO", "No readable log file at: " + path,
                    null);
        }
        try {
            long size = Files.size(path);
            if (size > MAX_LOG_BYTES) {
                return OperationResult.failed("ERROR_TOO_LARGE",
                        "The log is " + size + " bytes, beyond the " + MAX_LOG_BYTES
                                + "-byte bounded read; pass the CRASH file or a truncated "
                                + "tail. (Sampling the middle of a huge log would hide "
                                + "errors - refused.)",
                        null);
            }
            return OperationResult.success("ERROR_SCAN_OK", "Scanned.",
                    scanText(Files.readString(path, StandardCharsets.UTF_8)));
        } catch (IOException ex) {
            return OperationResult.failed("ERROR_IO", "Reading the log failed: "
                    + ex.getMessage(), null);
        }
    }

    private static List<Signature> buildSignatures() {
        List<Signature> signatures = new ArrayList<>();
        signatures.add(new Signature("scf-not-converged", "convergence NOT achieved",
                Severity.ERROR,
                "Self-consistency did not converge within electron_maxstep.",
                "Lower mixing_beta stepwise (0.3-0.5, then 0.1 for stubborn metals); "
                        + "check smearing scheme/degauss for metals; try "
                        + "diagonalization='cg' or 'ppcg'; inspect the last accuracy "
                        + "trend with the SCF_CONVERGENCE kind before any rerun."));
        signatures.add(new Signature("charge-wrong", "charge is wrong",
                Severity.ERROR,
                "Occupied states overflowed the band count (too few electrons/bands "
                        + "for the requested occupations).",
                "Raise nbnd; verify nat/ntyp and the ATOMIC_SPECIES cards match the "
                        + "pseudo files; for tot_magnetization or from_input occupations "
                        + "re-read the INPUT_PW occupation rules; metals need smearing."));
        signatures.add(new Signature("out-of-memory",
                "not enough memory allocated for", Severity.ERROR,
                "An allocation exceeded available process memory.",
                "Distribute across more nodes (not more threads per rank); split pools "
                        + "so each pool holds fewer FFT planes; set -nd/-nt; check the "
                        + "site memory-per-core profile; validate with a smaller "
                        + "cutoff/mesh first."));
        signatures.add(new Signature("xml-data-file", "Error opening xml data file",
                Severity.ERROR,
                "Post-processing cannot find the SCF save data (prefix/outdir "
                        + "mismatch, moved scratch, or the SCF never finished).",
                "Keep prefix and outdir/ESPRESSO_TMPDIR identical between the SCF and "
                        + "the post-processing run; confirm <prefix>.save/data-file-schema."
                        + "xml exists; rerun the SCF if the save is absent."));
        signatures.add(new Signature("cholesky", "problems computing cholesky",
                Severity.ERROR,
                "Singular overlap in the diagonalization subspace - almost always an "
                        + "ill-posed structure (overlapping/duplicate atoms) or a bad "
                        + "starting wavefunction.",
                "Check for duplicate/too-close atoms (GEOMETRY_MEASURE kind) and cell "
                        + "degeneracy; retry with smaller mixing_beta; inspect fractional "
                        + "coordinates for wrap duplicates after builder transforms."));
        signatures.add(new Signature("pool-divide",
                "is not a multiple of number of pools", Severity.ERROR,
                "The -nk pool count does not divide the k-point set QE is "
                        + "distributing.",
                "Pick -nk from exact divisor arithmetic: the MPI_POOLS_ADVISOR kind lists "
                        + "every admissible pool count for your mesh and ranks; note "
                        + "symmetry reduces the distributed count below the full mesh - "
                        + "re-verify with a short dry run."));
        signatures.add(new Signature("bfgs-history-converged",
                "bfgs history already converged", Severity.INFO,
                "The BFGS state satisfied the convergence criteria from its own history "
                        + "(the run finished because of history, not a fresh step).",
                "Not a failure. If tighter forces matter, increase forc_conv_thr "
                        + "strictness and let relax continue; verify with the "
                        + "GEOMETRY_CONVERGENCE kind."));
        // Generic fallback LAST: anything QE reports as an error routine.
        signatures.add(new Signature("generic-error-routine", "Error in routine",
                Severity.ERROR,
                "pw.x reported an error routine; the quoted line names it.",
                "Read the routine name and the following lines - they carry the reason; "
                        + "then check INPUT_PW for every keyword you changed last; if the "
                        + "message is a keyword complaint, the QE_VERSION_CHECK kind "
                        + "audits the input against the curated 7.2-7.5 window."));
        return List.copyOf(signatures);
    }
}
