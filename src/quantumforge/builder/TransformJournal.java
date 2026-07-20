/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.builder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #90 (format + integrity slice): append-only structure-provenance
 * journal. Every builder transformation is recorded as one canonical line
 * holding the source identifier, the operation name, the optional 3x3
 * transform matrix (row-major, exact round-trip doubles), ordered
 * {@code key=value} parameters, and the hash-chained parent link - the data
 * model from which any generated structure can later be reconstructed
 * exactly. Deterministic by design: entries carry a 1-based sequence
 * number, NEVER a wall-clock timestamp.
 *
 * <p>Canonical entry (one line, header {@code # qf-journal v1} first):</p>
 * <pre>seq|sourceId|operation|m00,m01,...,m22 or -|k=v;... or -|parentHash|entryHash</pre>
 *
 * <p>{@code entryHash} = SHA-256 hex of the five fields + parent hash;
 * {@code parentHash} of the first entry is {@code GENESIS}. Parsing a
 * journal VERIFIES the sequence, the parent linkage and every hash - a
 * tampered or reordered journal is refused, never "repaired".</p>
 *
 * <p>Honesty boundary: this slice delivers the format, the chain integrity
 * and the review. The acceptance criterion (replay reconstruction) needs
 * the writer wired into every builder transform path - that wiring is the
 * remaining #90 depth; the format preserves exactly what replay needs.</p>
 *
 * <p>Refusal codes: JOURNAL_IO, JOURNAL_TOO_LARGE, JOURNAL_EMPTY,
 * JOURNAL_SYNTAX, JOURNAL_SEQ, JOURNAL_VALUE, JOURNAL_HASH.</p>
 */
public final class TransformJournal {

    /** Mandatory header line. */
    public static final String HEADER = "# qf-journal v1";
    /** Parent hash of the first entry. */
    public static final String GENESIS = "GENESIS";
    /** Caps. */
    public static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;
    public static final int MAX_ENTRIES = 100_000;
    public static final int MAX_FIELD = 512;

    /** One immutable provenance entry. */
    public static final class JournalEntry {
        private final int seq;
        private final String sourceId;
        private final String operation;
        private final double[] matrix;
        private final List<String> parameters;
        private final String parentHash;
        private final String entryHash;

        JournalEntry(int seq, String sourceId, String operation, double[] matrix,
                List<String> parameters, String parentHash) {
            this.seq = seq;
            this.sourceId = sourceId;
            this.operation = operation;
            this.matrix = matrix == null ? null : matrix.clone();
            this.parameters = List.copyOf(parameters);
            this.parentHash = parentHash;
            this.entryHash = sha256Hex(canonicalBody(seq, sourceId, operation,
                    matrix, parameters, parentHash));
        }

        public int getSeq() { return this.seq; }
        public String getSourceId() { return this.sourceId; }
        public String getOperation() { return this.operation; }
        /** Row-major 3x3 matrix, or null when the operation carries none. */
        public double[] getMatrix() { return this.matrix == null ? null : this.matrix.clone(); }
        public List<String> getParameters() { return List.copyOf(this.parameters); }
        public String getParentHash() { return this.parentHash; }
        public String getEntryHash() { return this.entryHash; }
    }

    private final List<JournalEntry> entries = new ArrayList<>();

    /** Holds a parsed/verified journal. */
    public static final class JournalSummary {
        private final List<JournalEntry> entries;

        JournalSummary(List<JournalEntry> entries) {
            this.entries = new ArrayList<>(entries);
        }

        public List<JournalEntry> getEntries() { return List.copyOf(this.entries); }
        public int getEntryCount() { return this.entries.size(); }
        /** Entries carrying a transform matrix. */
        public int getMatrixCount() {
            int count = 0;
            for (JournalEntry entry : this.entries) {
                if (entry.getMatrix() != null) {
                    count += 1;
                }
            }
            return count;
        }
    }

    public TransformJournal() {
    }

    public int getEntryCount() { return this.entries.size(); }
    public List<JournalEntry> getEntries() {
        List<JournalEntry> copy = new ArrayList<>();
        for (JournalEntry entry : this.entries) {
            copy.add(entry);
        }
        return copy;
    }

    /**
     * Appends an entry. Fields containing | ; or line breaks are rejected -
     * the canonical format depends on them (IllegalArgumentException).
     */
    public JournalEntry append(String sourceId, String operation, double[] matrix,
            List<String> parameters) {
        checkField(sourceId, "source id");
        checkField(operation, "operation");
        if (sourceId.isEmpty() || operation.isEmpty()) {
            throw new IllegalArgumentException(
                    "source id and operation must be non-empty");
        }
        if (matrix != null && matrix.length != 9) {
            throw new IllegalArgumentException(
                    "the transform matrix must hold exactly 9 values (row-major 3x3)");
        }
        if (matrix != null) {
            for (double value : matrix) {
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException(
                            "the transform matrix must hold finite values only");
                }
            }
        }
        List<String> params = parameters == null ? List.of() : parameters;
        for (String param : params) {
            checkField(param, "parameter");
            if (!param.contains("=") || param.startsWith("=")) {
                throw new IllegalArgumentException(
                        "parameters must be k=v pairs: '" + param + "'");
            }
        }
        if (this.entries.size() >= MAX_ENTRIES) {
            throw new IllegalStateException("journal cap " + MAX_ENTRIES
                    + " entries reached");
        }
        String parent = this.entries.isEmpty() ? GENESIS
                : this.entries.get(this.entries.size() - 1).getEntryHash();
        JournalEntry entry = new JournalEntry(this.entries.size() + 1, sourceId,
                operation, matrix, params, parent);
        this.entries.add(entry);
        return entry;
    }

    /** Renders the canonical journal text (header + one line per entry). */
    public String render() {
        StringBuilder text = new StringBuilder();
        text.append(HEADER).append('\n');
        for (JournalEntry entry : this.entries) {
            text.append(entryLine(entry)).append('\n');
        }
        return text.toString();
    }

    /**
     * Parses and VERIFIES a journal file. Codes: JOURNAL_IO,
     * JOURNAL_TOO_LARGE, JOURNAL_EMPTY, JOURNAL_SYNTAX, JOURNAL_SEQ,
     * JOURNAL_VALUE, JOURNAL_HASH.
     */
    public static OperationResult<JournalSummary> verify(Path file) {
        if (file == null || !Files.exists(file)) {
            return OperationResult.failed("JOURNAL_IO",
                    "The provenance journal does not exist.", null);
        }
        long size;
        try {
            size = Files.size(file);
        } catch (IOException ex) {
            return OperationResult.failed("JOURNAL_IO",
                    "Could not stat the journal: " + ex.getMessage(), null);
        }
        if (size > MAX_FILE_BYTES) {
            return OperationResult.failed("JOURNAL_TOO_LARGE",
                    "The journal is " + size + " bytes; the cap is "
                            + MAX_FILE_BYTES + ".",
                    null);
        }
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return OperationResult.failed("JOURNAL_IO",
                    "Reading the journal failed: " + ex.getMessage(), null);
        }
        return parse(text);
    }

    /** Package-visible verifier for tests; same codes as {@link #verify}. */
    static OperationResult<JournalSummary> parse(String text) {
        if (text == null || text.isBlank()) {
            return OperationResult.failed("JOURNAL_EMPTY",
                    "The journal is empty.", null);
        }
        String[] lines = text.split("\n", -1);
        int headerLine = -1;
        for (int idx = 0; idx < lines.length; idx += 1) {
            String trimmed = lines[idx].trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (headerLine < 0) {
                if (!trimmed.equals(HEADER)) {
                    return OperationResult.failed("JOURNAL_SYNTAX",
                            "The first non-blank line must be the header '"
                                    + HEADER + "' (got '" + abbreviate(trimmed) + "').",
                            null);
                }
                headerLine = idx;
                continue;
            }
        }
        if (headerLine < 0) {
            return OperationResult.failed("JOURNAL_EMPTY",
                    "The journal holds whitespace only.", null);
        }
        List<JournalEntry> entries = new ArrayList<>();
        String expectedParent = GENESIS;
        for (int idx = headerLine + 1; idx < lines.length; idx += 1) {
            String trimmed = lines[idx].trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (entries.size() >= MAX_ENTRIES) {
                return OperationResult.failed("JOURNAL_TOO_LARGE",
                        "More than " + MAX_ENTRIES + " entries.", null);
            }
            String[] fields = trimmed.split("\\|", -1);
            if (fields.length != 7) {
                return OperationResult.failed("JOURNAL_SYNTAX",
                        "Entry line " + (idx + 1) + " holds " + fields.length
                                + " |-separated field(s); exactly 7 are required.",
                        null);
            }
            int seq;
            try {
                seq = Integer.parseInt(fields[0]);
            } catch (NumberFormatException ex) {
                return OperationResult.failed("JOURNAL_VALUE",
                        "Entry line " + (idx + 1) + " has a non-integer sequence '"
                                + fields[0] + "'.",
                        null);
            }
            if (seq != entries.size() + 1) {
                return OperationResult.failed("JOURNAL_SEQ",
                        "Entry " + seq + " on line " + (idx + 1) + " breaks the "
                                + "1-based sequence (expected " + (entries.size() + 1)
                                + "); reordered journals are refused, not repaired.",
                        null);
            }
            double[] matrix = null;
            if (!fields[3].equals("-")) {
                String[] cells = fields[3].split(",", -1);
                if (cells.length != 9) {
                    return OperationResult.failed("JOURNAL_VALUE",
                            "Entry " + seq + " carries " + cells.length
                                    + " matrix value(s); exactly 9 are required.",
                            null);
                }
                matrix = new double[9];
                for (int cell = 0; cell < 9; cell += 1) {
                    try {
                        matrix[cell] = Double.parseDouble(cells[cell]);
                    } catch (NumberFormatException ex) {
                        return OperationResult.failed("JOURNAL_VALUE",
                                "Entry " + seq + " has a non-numeric matrix value '"
                                        + cells[cell] + "'.",
                                null);
                    }
                    if (!Double.isFinite(matrix[cell])) {
                        return OperationResult.failed("JOURNAL_VALUE",
                                "Entry " + seq + " has a non-finite matrix value.",
                                null);
                    }
                }
            }
            List<String> params = new ArrayList<>();
            if (!fields[4].equals("-")) {
                for (String param : fields[4].split(";", -1)) {
                    if (!param.contains("=") || param.startsWith("=")) {
                        return OperationResult.failed("JOURNAL_VALUE",
                                "Entry " + seq + " carries a parameter that is not "
                                        + "a k=v pair: '" + param + "'.",
                                null);
                    }
                    params.add(param);
                }
            }
            String parentHash = fields[5];
            if (!parentHash.equals(expectedParent)) {
                return OperationResult.failed("JOURNAL_HASH",
                        "Entry " + seq + " chains to parent '" + abbreviate(parentHash)
                                + "' but the previous entry's hash is '"
                                + abbreviate(expectedParent)
                                + "'; the chain is broken.",
                        null);
            }
            JournalEntry rebuilt = new JournalEntry(seq, fields[1], fields[2], matrix,
                    params, parentHash);
            if (!rebuilt.getEntryHash().equals(fields[6])) {
                return OperationResult.failed("JOURNAL_HASH",
                        "Entry " + seq + " hashes to '" + abbreviate(rebuilt.getEntryHash())
                                + "' but the line records '" + abbreviate(fields[6])
                                + "'; a tampered journal is refused, not repaired.",
                        null);
            }
            entries.add(rebuilt);
            expectedParent = rebuilt.getEntryHash();
        }
        if (entries.isEmpty()) {
            return OperationResult.failed("JOURNAL_EMPTY",
                    "The journal has a header but no entries.", null);
        }
        return OperationResult.success("JOURNAL_OK",
                "Verified " + entries.size() + " entries.", new JournalSummary(entries));
    }

    private static String entryLine(JournalEntry entry) {
        return canonicalBody(entry.getSeq(), entry.getSourceId(), entry.getOperation(),
                entry.getMatrix(), entry.getParameters(), entry.getParentHash())
                + "|" + entry.getEntryHash();
    }

    private static String canonicalBody(int seq, String sourceId, String operation,
            double[] matrix, List<String> parameters, String parentHash) {
        StringBuilder body = new StringBuilder();
        body.append(seq).append('|').append(sourceId).append('|').append(operation)
                .append('|');
        if (matrix == null) {
            body.append('-');
        } else {
            for (int idx = 0; idx < 9; idx += 1) {
                if (idx > 0) {
                    body.append(',');
                }
                body.append(Double.toString(matrix[idx]));
            }
        }
        body.append('|');
        if (parameters.isEmpty()) {
            body.append('-');
        } else {
            for (int idx = 0; idx < parameters.size(); idx += 1) {
                if (idx > 0) {
                    body.append(';');
                }
                body.append(parameters.get(idx));
            }
        }
        body.append('|').append(parentHash);
        return body.toString();
    }

    private static void checkField(String value, String what) {
        if (value == null || value.length() > MAX_FIELD || value.contains("|")
                || value.contains(";") || value.contains("\n") || value.contains("\r")) {
            throw new IllegalArgumentException("The " + what
                    + " must be a non-null value up to " + MAX_FIELD
                    + " chars with no '|', ';' or line breaks");
        }
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "(null)";
        }
        return value.length() <= 24 ? value
                : value.substring(0, 24) + "...(" + value.length() + " chars)";
    }

    static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the JDK", ex);
        }
    }
}
