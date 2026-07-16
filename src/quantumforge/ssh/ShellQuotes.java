/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.ssh;

/**
 * POSIX single-quote escaping for remote shell scripts.
 *
 * <p>Never concatenate untrusted tokens into remote scripts without this.</p>
 */
public final class ShellQuotes {

    private ShellQuotes() {
        // Utility.
    }

    public static String single(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    public static String join(String... tokens) {
        if (tokens == null || tokens.length == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) {
                out.append(' ');
            }
            out.append(single(tokens[i]));
        }
        return out.toString();
    }
}
