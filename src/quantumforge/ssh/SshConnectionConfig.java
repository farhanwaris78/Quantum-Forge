/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.ssh;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Immutable SSH connection parameters.
 *
 * <p>Passwords are never stored here as durable fields; callers may supply a
 * session-only char[] to the transport.</p>
 */
public final class SshConnectionConfig {

    private final String host;
    private final int port;
    private final String user;
    private final Path privateKeyPath;
    private final Path knownHostsPath;
    private final Duration connectTimeout;
    private final boolean acceptNewHostKeys;

    private SshConnectionConfig(Builder builder) {
        this.host = Objects.requireNonNull(builder.host, "host").trim();
        if (this.host.isEmpty()) {
            throw new IllegalArgumentException("host is empty");
        }
        this.port = builder.port <= 0 ? 22 : builder.port;
        this.user = Objects.requireNonNull(builder.user, "user").trim();
        if (this.user.isEmpty()) {
            throw new IllegalArgumentException("user is empty");
        }
        this.privateKeyPath = builder.privateKeyPath;
        this.knownHostsPath = Objects.requireNonNull(builder.knownHostsPath, "knownHostsPath");
        this.connectTimeout = builder.connectTimeout == null
                ? Duration.ofSeconds(15) : builder.connectTimeout;
        this.acceptNewHostKeys = builder.acceptNewHostKeys;
    }

    public String getHost() { return this.host; }
    public int getPort() { return this.port; }
    public String getUser() { return this.user; }
    public Path getPrivateKeyPath() { return this.privateKeyPath; }
    public Path getKnownHostsPath() { return this.knownHostsPath; }
    public Duration getConnectTimeout() { return this.connectTimeout; }
    public boolean isAcceptNewHostKeys() { return this.acceptNewHostKeys; }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String host;
        private int port = 22;
        private String user;
        private Path privateKeyPath;
        private Path knownHostsPath;
        private Duration connectTimeout = Duration.ofSeconds(15);
        private boolean acceptNewHostKeys = false;

        public Builder host(String host) { this.host = host; return this; }
        public Builder port(int port) { this.port = port; return this; }
        public Builder user(String user) { this.user = user; return this; }
        public Builder privateKeyPath(Path privateKeyPath) {
            this.privateKeyPath = privateKeyPath; return this;
        }
        public Builder knownHostsPath(Path knownHostsPath) {
            this.knownHostsPath = knownHostsPath; return this;
        }
        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout; return this;
        }
        /** When false (default), unknown hosts are rejected. */
        public Builder acceptNewHostKeys(boolean accept) {
            this.acceptNewHostKeys = accept; return this;
        }
        public SshConnectionConfig build() {
            return new SshConnectionConfig(this);
        }
    }
}
