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
 *   <li>the stanza is a DRAFT for review: key-agent setup, proxy/bastion
 *       chains and multi-hop are the remaining #91 depth. The session
 *       transport now exists (JschSshTransport); {@code toConnectionConfig}
 *       bridges this draft to its typed config with known_hosts mandatory
 *       and accept-on-first-use structurally off.</li>
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

        /**
         * Compiles this validated target to the runtime connection config used
         * by {@code JschSshTransport} (Roadmap #91, bridge slice). Rules, all
         * fail-closed: an identity file is REQUIRED (this build's transport has
         * no agent support - SSH_IDENTITY_MISSING); a known_hosts path is
         * REQUIRED (fail-closed host keys - SSH_KNOWN_HOSTS); acceptNewHostKeys
         * is ALWAYS false in the compiled config (unknown-host acceptance is
         * never enabled silently); a null timeout takes the builder's
         * documented 15 s default. NO connection is attempted from this build.
         */
        public OperationResult<quantumforge.ssh.SshConnectionConfig> toConnectionConfig(
                String knownHostsPathText, java.time.Duration connectTimeout) {
            if (this.identityFile.isEmpty()) {
                return OperationResult.failed("SSH_IDENTITY_MISSING",
                        "this draft relies on agent/default keys, which this build's"
                                + " runtime transport does not offer - set an explicit"
                                + " identity file before compiling to a live config.",
                        null);
            }
            String knownHosts = knownHostsPathText == null ? ""
                    : knownHostsPathText.trim();
            if (knownHosts.isEmpty()) {
                return OperationResult.failed("SSH_KNOWN_HOSTS",
                        "fail-closed host keys require a known_hosts path at compile"
                                + " time - there is no accept-on-first-use in a compiled"
                                + " config.", null);
            }
            quantumforge.ssh.SshConnectionConfig.Builder builder =
                    quantumforge.ssh.SshConnectionConfig.builder()
                            .host(this.hostName).port(this.port).user(this.user)
                            .privateKeyPath(java.nio.file.Path.of(this.identityFile))
                            .knownHostsPath(java.nio.file.Path.of(knownHosts))
                            .acceptNewHostKeys(false);
            if (connectTimeout != null) {
                builder.connectTimeout(connectTimeout);
            }
            return OperationResult.success("SSH_BRIDGE_OK",
                    "Compiled for review: " + this.user + "@" + this.hostName + ":"
                            + this.port + " with identity " + this.identityFile
                            + "; acceptNewHostKeys=false"
                            + (connectTimeout == null
                                    ? "; timeout = builder default 15 s"
                                    : ("; timeout = " + connectTimeout.toMillis() + " ms"))
                            + ". NO connection is attempted from this build.",
                    builder.build());
        }

        /** Renders the ssh_config stanza with the honesty comments inline. */
        public String stanza() {
            StringBuilder text = new StringBuilder();
            text.append("# QuantumForge SSH draft (Roadmap #91) - REVIEW before use;\n");
            text.append("# password auth is disabled BY DESIGN; host-key pinning\n");
            text.append("# (known_hosts) is mandatory in any compiled config and\n");
            text.append("# bastion/proxy chains remain runtime depth. NO connection\n");
            text.append("# is attempted from this build.\n");
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
