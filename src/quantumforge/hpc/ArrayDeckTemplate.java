/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.hpc;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #100 (deck-templating slice): rewrites EXACTLY ONE line of a QE
 * input deck - the swept keyword's assignment - so the
 * {@link ArraySweepPlanner} array renders each task's value into a real deck
 * instead of handing the user a REQUIRED-EDIT comment to do it by hand.
 *
 * <p>Ownership boundaries (fail-closed, stated, never coerced):</p>
 * <ul>
 *   <li>the template is raw deck TEXT (the user's project input as edited);
 *       this product does NOT repurpose the GUI-coupled {@code QEInput}
 *       object model - it owns one small line grammar:
 *       a non-comment line of the shape {@code keyword = value[,]} with an
 *       optional trailing {@code !} comment. QE's full namelist forte
 *       (multi-line values, continuation, case folding) is deliberately out
 *       of scope and anything outside the grammar REFUSES;</li>
 *   <li>the swept keyword must appear EXACTLY ONCE as such an assignment
 *       outside comment ({@code !}) lines (DECK_KEYWORD): zero occurrences
 *       means every task would run the SAME deck - that is not a sweep and
 *       refuses rather than submitting silence; more than one is ambiguous
 *       about which line is the sweep point and refuses rather than picking
 *       the first;</li>
 *   <li>the keyword's existing value span must be non-blank (DECK_VALUE):
 *       there is nothing to stand in for otherwise;</li>
 *   <li>the substitution point is OUR placeholder token
 *       {@value #PLACEHOLDER} - a template that already contains it refuses
 *       (DECK_PLACEHOLDER: a collision would substitute twice). The token is
 *       alphanumeric-plus-underscore by construction, so it is literal-safe
 *       for any downstream shell/sed render step;</li>
 *   <li>a template longer than {@value #MAX_TEMPLATE_CHARS} chars refuses
 *       (DECK_TEMPLATE): a deck is a few KB - the bound keeps a plan from
 *       dumping a binary payload into a review artifact;</li>
 *   <li>per-task rendering substitutes {@link Double#toString(double)} -
 *       the EXACT, lossless rendering of the planner's single-rounding value;
 *       no precision model is invented and no locale separator can leak into
 *       a Fortran value.</li>
 * </ul>
 *
 * <p>Scope honesty: this renders per-task deck TEXT only. The sbatch preview,
 * the task manifest, the submission itself and every scheduler line remain
 * the #00/#93 surfaces - nothing here submits or writes to disk.</p>
 */
public final class ArrayDeckTemplate {

    /** Substitution token, ours alone; literal-safe for downstream renders. */
    public static final String PLACEHOLDER = "QF_ARRAY_VALUE";
    /** Owned template size bound, stated. */
    public static final int MAX_TEMPLATE_CHARS = 65536;

    private ArrayDeckTemplate() {
    }

    /** One validated deck template bound to a sweep plan. */
    public static final class DeckTemplate {
        private final String keyword;
        private final List<Double> values;
        private final String template;      // placeholder form
        private final String lineBefore;    // swept line as found
        private final String lineAfter;     // swept line with the placeholder

        DeckTemplate(String keyword, List<Double> values, String template,
                String lineBefore, String lineAfter) {
            this.keyword = keyword;
            this.values = values;
            this.template = template;
            this.lineBefore = lineBefore;
            this.lineAfter = lineAfter;
        }

        public String getKeyword() { return this.keyword; }
        public String getTemplateText() { return this.template; }
        public String getLineBefore() { return this.lineBefore; }
        public String getLineAfter() { return this.lineAfter; }
        public int getTaskCount() { return this.values.size(); }

        /**
         * Render the deck for a 1-based task index with the EXACT planned
         * value. Out-of-range indices refuse loudly - rendering the wrong
         * task's value silently would poison a whole array directory.
         */
        public String renderTaskDeck(int taskIndex) {
            if (taskIndex < 1 || taskIndex > this.values.size()) {
                throw new IndexOutOfBoundsException(
                        "task " + taskIndex + " is outside 1.." + this.values.size()
                                + " - the per-task index mapping must stay exact");
            }
            // Double.toString is exact and lossless; matches the JSONL manifest.
            return this.template.replace(PLACEHOLDER,
                    Double.toString(this.values.get(taskIndex - 1)));
        }

        /** The swept line as it will read for a 1-based task index. */
        public String renderTaskLine(int taskIndex) {
            if (taskIndex < 1 || taskIndex > this.values.size()) {
                throw new IndexOutOfBoundsException(
                        "task " + taskIndex + " is outside 1.." + this.values.size());
            }
            return this.lineAfter.replace(PLACEHOLDER,
                    Double.toString(this.values.get(taskIndex - 1)));
        }
    }

    /**
     * Validate a deck text against a sweep plan. Codes: DECK_TEMPLATE,
     * DECK_PLACEHOLDER, DECK_KEYWORD, DECK_VALUE.
     */
    public static OperationResult<DeckTemplate> validate(ArraySweepPlanner.SweepPlan plan,
            String templateText) {
        if (plan == null) {
            throw new NullPointerException("plan is required - the template is bound "
                    + "to one sweep's keyword and values");
        }
        String template = templateText == null ? "" : templateText;
        if (template.isBlank()) {
            return OperationResult.failed("DECK_TEMPLATE",
                    "the deck template is empty - there is no deck to sweep.", null);
        }
        if (template.length() > MAX_TEMPLATE_CHARS) {
            return OperationResult.failed("DECK_TEMPLATE",
                    "the deck template exceeds " + MAX_TEMPLATE_CHARS + " chars (got "
                            + template.length() + ") - a deck is a few KB; this bound"
                            + " keeps a plan from carrying a binary payload.", null);
        }
        if (template.contains(PLACEHOLDER)) {
            return OperationResult.failed("DECK_PLACEHOLDER",
                    "the template already contains our substitution token " + PLACEHOLDER
                            + " - a collision would substitute into two unlabeled points."
                            + " Remove it; the token is written by this templater only.",
                    null);
        }
        String keyword = plan.getKeyword();
        Pattern assignment = Pattern.compile(
                "^(\\s*" + Pattern.quote(keyword) + "\\s*=)(.*)$",
                Pattern.CASE_INSENSITIVE);
        String[] lines = template.split("\n", -1);
        int matched = -1;
        int count = 0;
        boolean commentedOnly = false;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            boolean comment = trimmed.startsWith("!");
            Matcher matcher = assignment.matcher(lines[i]);
            if (!matcher.matches()) {
                continue;
            }
            if (comment) {
                commentedOnly = true;
                continue;
            }
            count++;
            matched = i;
        }
        if (count == 0) {
            return OperationResult.failed("DECK_KEYWORD",
                    commentedOnly
                            ? "the swept keyword '" + keyword + "' appears only inside QE"
                            + " comment lines - the array would run the SAME deck "
                            + plan.getValues().size() + " times, which is not a sweep. "
                            + "Uncomment the assignment you want swept."
                            : "the swept keyword '" + keyword + "' never appears as an "
                            + "assignment - the array would run the SAME deck "
                            + plan.getValues().size() + " times, which is not a sweep.",
                    null);
        }
        if (count > 1) {
            return OperationResult.failed("DECK_KEYWORD",
                    "the swept keyword '" + keyword + "' is assigned on " + count
                            + " non-comment lines - one sweep rewrites exactly ONE line. "
                            + "Comment out the duplicates so the sweep point is unambiguous.",
                    null);
        }
        String line = lines[matched];
        int eq = line.indexOf('=');
        String rest = line.substring(eq + 1);
        int valueEnd = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == ',' || c == '!') {
                valueEnd = i;
                break;
            }
        }
        String value = rest.substring(0, valueEnd).trim();
        if (value.isEmpty()) {
            return OperationResult.failed("DECK_VALUE",
                    "the swept keyword line '" + line + "' has no value to stand in for - "
                            + "give it the deck's current setting; the templater replaces "
                            + "exactly that span.", null);
        }
        String prefix = line.substring(0, eq + 1);
        String tail = rest.substring(valueEnd);
        String after = prefix + " " + PLACEHOLDER + tail;
        lines[matched] = after;
        StringBuilder rewritten = new StringBuilder(template.length() + PLACEHOLDER.length());
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                rewritten.append('\n');
            }
            rewritten.append(lines[i]);
        }
        return OperationResult.success("DECK_TEMPLATE_OK",
                "Deck template validated: one sweep point ('" + line.trim()
                        + "' -> '" + after.trim() + "'), " + plan.getValues().size()
                        + " task(s), exact per-task values only.",
                new DeckTemplate(keyword, plan.getValues(), rewritten.toString(),
                        line.trim(), after.trim()));
    }
}
