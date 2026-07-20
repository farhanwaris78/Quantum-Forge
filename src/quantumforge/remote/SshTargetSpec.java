/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.remote;

import java.util.regex.Pattern;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #91 (config-draft slice): fail-closed validator and renderer for
 * an SSH target specification, emitted as a standard {@code ssh_config}
 * Host stanza. NO connection is attempted and no SSH library is bundled -
 * the "real SSH layer" session handling is runtime depth.
 *
 * <p>Honesty centerpieces:</p>
 * <ul>
 *   <li>password auth is STRUCTURALLY ABSENT - the spec has no password
 *       field at all; the rendered stanza pins
 *       {@code PasswordAuthentication no} and
 *       {@code IdentitiesOnly yes}, so the draft steers to public-key auth
 *       or refuses to exist;</li>
 *   <li>every field is validated against an owned grammar - nothing is
 *       interpolated into a config (or later, a command line) unchecked;
 *       the identity-file path rejects whitespace, quotes, {@code $} and
 *       backticks outright (expansion/injection guard) rather than trying
 *       to out-quoting a config parser;</li>
 *   <li>the stanza is a DRAFT for review: key-agent setup, host-key
 *       verification (known_hosts pinning), proxy/bastion chains and the
 *       actual SSH session library (JSch/sshj decision) are the remaining
 *       #91 depth - stated in the emitted comments.</li>
 * </ul>
 *
 * <p>Refusal codes: SSH_HOST, SSH_PORT, SSH_USER, SSH_ALIAS, SSH_KEY_PATH.</p>
 */
public final class SshTargetSpec {

    /** Owned grammars. */
    private static final Pattern HOSTNAME = Pattern.compile(
            "[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?");
    private static final Pattern USERNAME = Pattern.compile(
            "[a-z_][a-z0-9_-]{0,31}");
    private static final Pattern ALIAS = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,63}");

    /** The validated target. */
    public static final class SshTarget {
        private final String alias;
        private final String hostName;
        private final String user;
        private final int port;
        private final String identityFile;

        SshTarget(String alias, String hostName, String user, int port,
                String identityFile) {
            this.alias = alias;
            this.hostName = hostName;
            this.user = user;
            this.port = port;
            this.identityFile = identityFile;
        }

        public String getAlias() { return this.alias; }
        public String getHostName() { return this.hostName; }
        public String getUser() { return this.user; }
        public int getPort() { return this.port; }
        /** Empty when no explicit identity file was supplied. */
        public String getIdentityFile() { return this.identityFile; }

        /** Renders the ssh_config stanza with the honesty comments inline. */
        public String stanza() {
            StringBuilder text = new StringBuilder();
            text.append("# QuantumForge SSH draft (Roadmap #91) - REVIEW before use;\n");
            text.append("# password auth is disabled BY DESIGN, host-key pinning\n");
            text.append("# (known_hosts), bastion/proxy chains and the session\n");
            text.append("# library are runtime depth.\n");
            text.append("Host ").append(this.alias).append('\n');
            text.append("    HostName ").append(this.hostName).append('\n');
            text.append("    User ").append(this.user).append('\n');
            text.append("    Port ").append(this.port).append('\n');
            if (!this.identityFile.isEmpty()) {
                text.append("    IdentityFile ").append(this.identityFile).append('\n');
                text.append("    IdentitiesOnly yes\n");
            } else {
                text.append("    # IdentityFile unset: the agent/default keys will be "
                        + "offered - set one deliberately.\n");
                text.append("    IdentitiesOnly no\n");
            }
            text.append("    PasswordAuthentication no\n");
            text.append("    BatchMode yes\n");
            return text.toString();
        }
    }

    private SshTargetSpec() {
    }

    /**
     * Validates a target. Codes: SSH_HOST, SSH_PORT, SSH_USER, SSH_ALIAS,
     * SSH_KEY_PATH.
     */
    public static OperationResult<SshTarget> validate(String alias, String hostName,
            String user, int port, String identityFile) {
        if (alias == null || !ALIAS.matcher(alias.trim()).matches()) {
            return OperationResult.failed("SSH_ALIAS",
                    "The Host alias must match [A-Za-z0-9][A-Za-z0-9_-]{0,63} (got '"
                            + alias + "').",
                    null);
        }
        if (hostName == null || !HOSTNAME.matcher(hostName.trim()).matches()) {
            return OperationResult.failed("SSH_HOST",
                    "The HostName must be a hostname/IP shape (letters, digits, "
                            + "'.', '-'; got '" + hostName + "').",
                    null);
        }
        if (user == null || !USERNAME.matcher(user.trim()).matches()) {
            return OperationResult.failed("SSH_USER",
                    "The User must match POSIX logname rules "
                            + "([a-z_][a-z0-9_-]{0,31}; got '" + user + "').",
                    null);
        }
        if (port <= 0 || port > 65535) {
            return OperationResult.failed("SSH_PORT",
                    "The port must lie in 1..65535 (got " + port + ").", null);
        }
        String keyPath = identityFile == null ? "" : identityFile.trim();
        if (!keyPath.isEmpty()) {
            for (int idx = 0; idx < keyPath.length(); idx += 1) {
                char ch = keyPath.charAt(idx);
                if (Character.isWhitespace(ch) || ch == '"' || ch == '\''
                        || ch == '$' || ch == '`' || ch == ';' || ch == '|') {
                    return OperationResult.failed("SSH_KEY_PATH",
                            "The identity-file path contains a whitespace, quote or "
                                    + "expansion character at column " + (idx + 1)
                                    + " - refused rather than quoted around.",
                            null);
                }
            }
        }
        return OperationResult.success("SSH_OK", "Target validated.",
                new SshTarget(alias.trim(), hostName.trim(), user.trim(), port,
                        keyPath));
    }
}
