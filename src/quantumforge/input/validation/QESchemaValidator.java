/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.input.validation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import quantumforge.input.QEInput;
import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValue;
import quantumforge.input.schema.QENamelistSchema.Kind;

/**
 * Thin QEInput adapter of {@link QESchemaAudit} (Roadmap #22 completeness):
 * extracts the live namelist keyword pairs out of a resolved {@link QEInput}
 * and audits them against the machine-mined QE grammar window (7.2 .. 7.6).
 * Supplements - never replaces - the structural {@link QEInputValidator}.
 *
 * <p>Scope honesty (stated in the class docs because reports inherit it):</p>
 * <ul>
 *   <li>only the eight namelist surfaces {@code QEInput.listNamelistKeys()}
 *       exposes (CONTROL, SYSTEM, ELECTRONS, IONS, CELL, DOS, PROJWFC, BANDS)
 *       are reachable - a deck carrying &amp;FCP/&amp;RISM/&amp;WANNIER extras
 *       is out of this adapter's reach until QEInput exposes those keys; the
 *       pair-level {@link QESchemaAudit#validatePairs} core covers them;</li>
 *   <li>the audit is the pw.x ({@link Kind#PW}) grammar - ph.x/hp.x decks use
 *       that same core with their own Kind and raw texts;</li>
 *   <li>values audit in their STORED (already unquoted) form, which is exactly
 *       the comparison {@link QESchemaAudit} performs after stripping matching
 *       quotes - case remains significant, like the Fortran SELECT CASE;</li>
 *   <li>cards (ATOMIC_POSITIONS, K_POINTS...) stay with the structural
 *       validator - this layer adjudicates namelist keywords only.</li>
 * </ul>
 */
public final class QESchemaValidator {

    private final QESchemaAudit audit = new QESchemaAudit();

    /**
     * Audits every namelist keyword the input carries against the mined pw.x
     * grammar of the requested version.
     *
     * @param input   the resolved input; null and namelist-less inputs audit to
     *                an EMPTY issue list rather than a fabricated verdict (the
     *                structural validator owns INPUT_NULL/SYSTEM_MISSING)
     * @param version a supported minor version label ("7.2" .. "7.6"); anything
     *                else is refused loudly ({@link IllegalArgumentException}),
     *                never re-interpreted
     */
    public List<ValidationIssue> validate(QEInput input, String version) {
        Objects.requireNonNull(version, "version");
        if (input == null) {
            return List.of();
        }
        Map<String, Map<String, String>> pairs = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String namelistKey : QEInput.listNamelistKeys()) {
            QENamelist namelist = input.getNamelist(namelistKey);
            if (namelist == null || namelist.numValues() == 0) {
                continue;
            }
            Map<String, String> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (QEValue value : namelist.listQEValues()) {
                if (value != null && value.getName() != null) {
                    out.put(value.getName(), value.getCharacterValue());
                }
            }
            if (!out.isEmpty()) {
                pairs.put(namelistKey, out);
            }
        }
        return this.audit.validatePairs(Kind.PW, version, pairs);
    }
}
