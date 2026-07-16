/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.ssh;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import quantumforge.com.file.AtomicFileWriter;
import quantumforge.com.log.AppLog;

/**
 * Minimal known_hosts store for host-key fingerprints.
 *
 * <p>Default policy is fail-closed: unknown hosts are rejected until the user
 * explicitly accepts a fingerprint. Changed keys are always rejected.</p>
 */
public final class KnownHostsStore {

    public enum Decision {
        ACCEPT,
        REJECT_UNKNOWN,
        REJECT_CHANGED
    }

    public static final class Entry {
        private final String host;
        private final int port;
        private final String keyType;
        private final String fingerprintSha256;

        public Entry(String host, int port, String keyType, String fingerprintSha256) {
            this.host = Objects.requireNonNull(host).trim().toLowerCase(Locale.ROOT);
            this.port = port <= 0 ? 22 : port;
            this.keyType = keyType == null ? "" : keyType.trim();
            this.fingerprintSha256 = Objects.requireNonNull(fingerprintSha256)
                    .trim().toLowerCase(Locale.ROOT);
        }

        public String getHost() { return this.host; }
        public int getPort() { return this.port; }
        public String getKeyType() { return this.keyType; }
        public String getFingerprintSha256() { return this.fingerprintSha256; }

        String key() {
            return this.host + "|" + this.port + "|" + this.keyType.toLowerCase(Locale.ROOT);
        }
    }

    private final Path path;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public KnownHostsStore(Path path) {
        this.path = Objects.requireNonNull(path, "path");
    }

    public Path getPath() {
        return this.path;
    }

    public void load() throws IOException {
        this.entries.clear();
        if (!Files.isRegularFile(this.path)) {
            return;
        }
        List<String> lines = Files.readAllLines(this.path, StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            // format: host port keyType sha256
            String[] parts = trimmed.split("\\s+");
            if (parts.length < 4) {
                continue;
            }
            try {
                Entry entry = new Entry(parts[0], Integer.parseInt(parts[1]), parts[2], parts[3]);
                this.entries.put(entry.key(), entry);
            } catch (NumberFormatException ignored) {
                AppLog.warn("known_hosts", "Skipping malformed line: " + trimmed);
            }
        }
    }

    public void save() throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("# QuantumForge known_hosts fingerprints (SHA-256)\n");
        out.append("# host port keyType sha256\n");
        for (Entry entry : this.entries.values()) {
            out.append(entry.getHost()).append(' ')
                    .append(entry.getPort()).append(' ')
                    .append(entry.getKeyType()).append(' ')
                    .append(entry.getFingerprintSha256()).append('\n');
        }
        AtomicFileWriter.writeUtf8(this.path, out.toString());
    }

    public Optional<Entry> find(String host, int port, String keyType) {
        return Optional.ofNullable(this.entries.get(keyOf(host, port, keyType)));
    }

    public Decision verify(String host, int port, String keyType, String fingerprintSha256) {
        Entry incoming = new Entry(host, port, keyType, fingerprintSha256);
        Entry known = this.entries.get(keyOf(host, port, keyType));
        if (known == null) {
            return Decision.REJECT_UNKNOWN;
        }
        if (!known.getFingerprintSha256().equals(incoming.getFingerprintSha256())) {
            return Decision.REJECT_CHANGED;
        }
        return Decision.ACCEPT;
    }

    public void accept(String host, int port, String keyType, String fingerprintSha256)
            throws IOException {
        Entry entry = new Entry(host, port, keyType, fingerprintSha256);
        this.entries.put(entry.key(), entry);
        save();
        AppLog.info("known_hosts", "Accepted host key for " + host + ":" + port
                + " (" + keyType + " " + fingerprintSha256 + ")");
    }

    public List<Entry> list() {
        return new ArrayList<>(this.entries.values());
    }

    public static String fingerprintSha256(byte[] hostKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(hostKey);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public static String fingerprintSha256FromBase64(String base64Key) {
        byte[] raw = Base64.getDecoder().decode(base64Key.replaceAll("\\s+", ""));
        return fingerprintSha256(raw);
    }

    private static String keyOf(String host, int port, String keyType) {
        return new Entry(host, port, keyType, "x").key();
    }
}
