/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.builder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import quantumforge.operation.OperationResult;

/**
 * Parser for the compact ionic-constraint specification consumed by the GUI
 * constraint preview (Roadmap #80 data layer).
 *
 * <p>Grammar (whitespace tolerant): {@code index[: flags]} terms separated by
 * {@code ';'}, where an index is a 1-based atom number or inclusive range
 * {@code N-M} and the flags are exactly three digits of {@code 0}/{@code 1}
 * (QE {@code if_pos} semantics: 1 = free, 0 = frozen). Examples:</p>
 *
 * <pre>
 *   1:000              freeze atom 1 completely
 *   2-4:110            atoms 2, 3, 4 move along z only
 *   1:000; 3-5:010     two terms
 * </pre>
 *
 * <p>The parser is fail-closed: syntax errors, out-of-range indices, reversed
 * ranges, malformed flag strings, and duplicated atom indices are all blocking
 * errors. Nothing here edits a cell or an input; this is a validated data
 * object only.</p>
 */
public final class QEConstraintSpec {

    /** QE INPUT_PW documentation for the if_pos flags. */
    public static final String DOCS = "https://www.quantum-espresso.org/Doc/INPUT_PW.html";

    private static final Pattern TERM = Pattern.compile(
            "^(\\d+)(?:-(\\d+))?\\s*:\\s*([01]{3})$");

    /** One validated assignment: 0-based atom index plus its three if_pos flags. */
    public static final class Entry {
        private final int atomIndex0;
        private final int ifX;
        private final int ifY;
        private final int ifZ;

        public Entry(int atomIndex0, int ifX, int ifY, int ifZ) {
            this.atomIndex0 = atomIndex0;
            this.ifX = ifX;
            this.ifY = ifY;
            this.ifZ = ifZ;
        }

        /** 0-based atom index (matches the builder-manager convention). */
        public int getAtomIndex0() { return this.atomIndex0; }
        public int getIfX() { return this.ifX; }
        public int getIfY() { return this.ifY; }
        public int getIfZ() { return this.ifZ; }

        /** True when at least one axis is frozen. */
        public boolean isFrozen() {
            return this.ifX == 0 || this.ifY == 0 || this.ifZ == 0;
        }
    }

    private final List<Entry> entries;

    private QEConstraintSpec(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    public List<Entry> getEntries() { return this.entries; }

    /** Number of atoms with at least one frozen axis. */
    public int frozenCount() {
        int count = 0;
        for (Entry entry : this.entries) {
            if (entry.isFrozen()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Validates a specification against an atom count. Fails closed on any
     * syntax, range, or duplication problem; never returns a partial spec.
     */
    public static OperationResult<QEConstraintSpec> parse(String spec, int atomCount) {
        if (spec == null || spec.isBlank()) {
            return OperationResult.failed("CONSTRAINT_SPEC_EMPTY",
                    "The constraint specification is empty; supply terms such as "
                            + "\"1:000; 2-4:110\" (1-based indices, three 0/1 flags).", null);
        }
        if (atomCount <= 0) {
            return OperationResult.failed("CONSPEC_ATOMS",
                    "The project cell has no atoms to constrain.", null);
        }
        List<Entry> entries = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        String[] terms = spec.split(";");
        for (String rawTerm : terms) {
            String term = rawTerm.trim();
            if (term.isEmpty()) {
                continue;
            }
            Matcher matcher = TERM.matcher(term);
            if (!matcher.matches()) {
                return OperationResult.failed("CONSTRAINT_SPEC_SYNTAX",
                        "Cannot parse the term \"" + term + "\". Expected N:fff or N-M:fff "
                                + "with flags f in {0,1} (QE if_pos: 1 free, 0 frozen). DOCS: "
                                + DOCS, null);
            }
            int low;
            int high;
            int ifX;
            int ifY;
            int ifZ;
            try {
                low = Integer.parseInt(matcher.group(1));
                high = matcher.group(2) == null ? low : Integer.parseInt(matcher.group(2));
                String flags = matcher.group(3);
                ifX = flags.charAt(0) - '0';
                ifY = flags.charAt(1) - '0';
                ifZ = flags.charAt(2) - '0';
            } catch (NumberFormatException ex) {
                return OperationResult.failed("CONSTRAINT_SPEC_SYNTAX",
                        "The term \"" + term + "\" has a non-integer atom index.", null);
            }
            if (low < 1 || high < low || high > atomCount) {
                return OperationResult.failed("CONSTRAINT_SPEC_RANGE",
                        String.format(Locale.ROOT,
                                "The range %d-%d in \"%s\" is outside the atom list (1..%d) "
                                        + "or reversed.", low, high, term, atomCount), null);
            }
            for (int atom = low; atom <= high; atom++) {
                if (!seen.add(atom)) {
                    return OperationResult.failed("CONSTRAINT_SPEC_DUPLICATE",
                            "Atom " + atom + " is constrained by more than one term; the "
                                    + "specification must be unambiguous.", null);
                }
                entries.add(new Entry(atom - 1, ifX, ifY, ifZ));
            }
        }
        if (entries.isEmpty()) {
            return OperationResult.failed("CONSTRAINT_SPEC_EMPTY",
                    "The constraint specification has no terms after parsing.", null);
        }
        entries.sort((a, b) -> Integer.compare(a.getAtomIndex0(), b.getAtomIndex0()));
        return OperationResult.success("CONSTRAINT_SPEC_OK",
                "Specification validated: " + entries.size() + " atom constraint(s), "
                        + "no duplicates, all indices inside 1.." + atomCount + ".",
                new QEConstraintSpec(entries));
    }
}
