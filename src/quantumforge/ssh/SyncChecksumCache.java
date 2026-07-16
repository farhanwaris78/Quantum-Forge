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
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import quantumforge.com.file.AtomicFileWriter;

/**
 * Local SHA-256 cache for selective result sync.
 *
 * <p>If a local file already matches the recorded checksum, re-download can be
 * skipped. Remote size/mtime are optional hints only.</p>
 */
public final class SyncChecksumCache {

    private final Path file;
    private final Map<String, String> checksums = new LinkedHashMap<>();

    public SyncChecksumCache(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    public void load() throws IOException {
        this.checksums.clear();
        if (!Files.isRegularFile(this.file)) {
            return;
        }
        for (String line : Files.readAllLines(this.file, StandardCharsets.UTF_8)) {
            if (line == null || line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int space = line.indexOf(' ');
            if (space <= 0) {
                continue;
            }
            String hash = line.substring(0, space).trim().toLowerCase();
            String path = line.substring(space + 1).trim();
            if (hash.matches("[0-9a-f]{64}") && !path.isEmpty()) {
                this.checksums.put(path, hash);
            }
        }
    }

    public void save() throws IOException {
        Path parent = this.file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        StringBuilder out = new StringBuilder();
        out.append("# QuantumForge selective-sync checksum cache\n");
        for (Map.Entry<String, String> entry : this.checksums.entrySet()) {
            out.append(entry.getValue()).append(' ').append(entry.getKey()).append('\n');
        }
        AtomicFileWriter.writeUtf8(this.file, out.toString());
    }

    public boolean isUpToDate(Path localFile, String relativePath) throws IOException {
        if (localFile == null || !Files.isRegularFile(localFile) || relativePath == null) {
            return false;
        }
        String expected = this.checksums.get(relativePath.replace('\\', '/'));
        if (expected == null) {
            return false;
        }
        return expected.equals(sha256(localFile));
    }

    public void record(Path localFile, String relativePath) throws IOException {
        if (localFile == null || !Files.isRegularFile(localFile) || relativePath == null) {
            return;
        }
        this.checksums.put(relativePath.replace('\\', '/'), sha256(localFile));
    }

    public void remove(String relativePath) {
        if (relativePath != null) {
            this.checksums.remove(relativePath.replace('\\', '/'));
        }
    }

    public int size() {
        return this.checksums.size();
    }

    public static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Files.readAllBytes(file));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("SHA-256 unavailable", ex);
        }
    }
}
