/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.hpc;

/**
 * An adapter's OWNED array/bulk-submit knowledge (Roadmap #93/#100
 * submit-lane slice): the flag tokens that turn the same
 * {@code submitCommand} into an array submission, the environment variable
 * the scheduler exposes inside each task, and the documentation anchor the
 * tokens were taken from.
 *
 * <p>Per the no-default doctrine, an adapter without OWNED array knowledge
 * reports {@link #isSupported()} == false with the reason spelled out -
 * callers then render the honest per-task submit LOOP, which runs exactly
 * the documented single-job submit once per task and invents no flag. No
 * adapter may return an empty reason or a guessed flag set.</p>
 */
public final class ArraySubmitSpec {

    private final boolean supported;
    private final String envVar;
    private final java.util.function.BiFunction<Integer, Integer, String[]> flagTokens;
    private final String docAnchor;
    private final String unsupportedReason;

    private ArraySubmitSpec(boolean supported, String envVar,
            java.util.function.BiFunction<Integer, Integer, String[]> flagTokens,
            String docAnchor, String unsupportedReason) {
        this.supported = supported;
        this.envVar = envVar;
        this.flagTokens = flagTokens;
        this.docAnchor = docAnchor;
        this.unsupportedReason = unsupportedReason;
    }

    /** Array knowledge owned, with its documentation anchor. */
    public static ArraySubmitSpec supported(String envVar,
            java.util.function.BiFunction<Integer, Integer, String[]> flagTokens,
            String docAnchor) {
        if (envVar == null || envVar.isBlank() || flagTokens == null
                || docAnchor == null || docAnchor.isBlank()) {
            throw new IllegalArgumentException("an owned array spec needs the task env "
                    + "variable, the token renderer AND its documentation anchor");
        }
        return new ArraySubmitSpec(true, envVar, flagTokens, docAnchor, null);
    }

    /** No owned array knowledge - with the reason spelled out, never blurred. */
    public static ArraySubmitSpec unsupported(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("an unsupported array spec must say WHY");
        }
        return new ArraySubmitSpec(false, null, null, null, reason);
    }

    public boolean isSupported() { return this.supported; }

    /** Task-index env variable (supported only). */
    public String getEnvVar() { return this.envVar; }

    /** Documentation anchor for the tokens (supported only). */
    public String getDocAnchor() { return this.docAnchor; }

    /** Why no array form is owned (unsupported only). */
    public String getUnsupportedReason() { return this.unsupportedReason; }

    /**
     * The flag tokens for a {@code from..to} index range (supported only).
     * Bounds are enforced here so callers cannot silently render a degenerate
     * range.
     */
    public String[] renderFlagTokens(int from, int to) {
        if (!this.supported) {
            throw new IllegalStateException("no owned array form: " + this.unsupportedReason);
        }
        if (from < 1 || to < from) {
            throw new IllegalArgumentException("array range must be 1-based and ordered "
                    + "(got " + from + ".." + to + ")");
        }
        String[] tokens = this.flagTokens.apply(Integer.valueOf(from), Integer.valueOf(to));
        if (tokens == null || tokens.length == 0) {
            throw new IllegalStateException("the adapter's array renderer produced no tokens");
        }
        java.util.List.of(tokens).forEach(token -> {
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("the adapter's array renderer produced a "
                        + "blank token");
            }
        });
        return tokens;
    }
}
