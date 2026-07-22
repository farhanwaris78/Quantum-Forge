/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.export;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * RO-Crate metadata draft (Roadmap #135, partial).
 *
 * <p>Builds a deterministic {@code ro-crate-metadata.json} (RO-Crate 1.1
 * JSON-LD skeleton) describing project artifacts that already exist on disk:
 * each included file is listed with its byte size and a SHA-256 checksum so the
 * crate can validate payload identity later. Files over the inclusion bound, or
 * files that disappear mid-scan, are skipped with an explicit reason - never
 * hashed partially and never silently dropped. Payload copying/packaging,
 * license declarations and full author metadata remain future work; this crate
 * is a metadata draft to review, not a publication bundle.</p>
 */
public final class RoCrateExporter {

    /** Maximum per-file size included in the draft (hashed ciphertext). */
    public static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;
    private static final int HASH_BUFFER = 1 << 16;

    private RoCrateExporter() {
        // Utility
    }

    /** One artifact included in the crate. */
    public static final class CrateEntry {
        private final String relativePath;
        private final long bytes;
        private final String sha256;
        private final String mediaType;

        private CrateEntry(String relativePath, long bytes, String sha256, String mediaType) {
            this.relativePath = relativePath;
            this.bytes = bytes;
            this.sha256 = sha256;
            this.mediaType = mediaType;
        }

        public String getRelativePath() { return this.relativePath; }
        public long getBytes() { return this.bytes; }
        public String getSha256() { return this.sha256; }
        public String getMediaType() { return this.mediaType; }
    }

    /** The composed draft: JSON text plus bookkeeping of what was included/skipped. */
    public static final class CrateDraft {
        private final String projectName;
        private final String json;
        private final List<CrateEntry> entries;
        private final List<String> skipped;

        private CrateDraft(String projectName, String json, List<CrateEntry> entries,
                           List<String> skipped) {
            this.projectName = projectName;
            this.json = json;
            this.entries = List.copyOf(entries);
            this.skipped = List.copyOf(skipped);
        }

        /** The resolved project name embedded in the metadata (never re-derived later). */
        public String getProjectName() { return this.projectName; }
        public String getJson() { return this.json; }
        public List<CrateEntry> getEntries() { return this.entries; }
        public List<String> getSkipped() { return this.skipped; }
    }

    /**
     * One explicitly declared crate author/creator (Roadmap #135 pack layer).
     * Names are typed by the operator (or read from an explicit profile field);
     * the packer never scrapes environment variables, git config, or system
     * properties to invent authorship.
     */
    public static final class CrateAuthor {
        private final String name;
        private final String identifier;

        public CrateAuthor(String name, String identifier) {
            String trimmed = name == null ? "" : name.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("A crate author needs a non-blank name;"
                        + " omit the author instead of shipping a placeholder.");
            }
            this.name = trimmed;
            this.identifier = identifier == null || identifier.isBlank() ? null : identifier.trim();
        }

        public String getName() { return this.name; }
        public String getIdentifier() { return this.identifier; }
    }

    private static final String DRAFT_DESCRIPTION =
            "QuantumForge RO-Crate metadata draft (Roadmap item 135). Entries carry "
                    + "byte sizes and SHA-256 checksums of artifacts that existed at "
                    + "scan time; payload packaging, licence metadata and author records "
                    + "are deliberately not automated by this draft.";

    /** Default-bound build entry point. */
    public static CrateDraft build(String projectName, Path projectDir, List<Path> files) {
        return build(projectName, projectDir, files, MAX_FILE_BYTES);
    }

    /**
     * Composes the draft. {@code projectName} falls back to the directory name.
     * The file list may contain paths that do not exist; they are reported in
     * the skipped list with a reason. Output ordering is sorted by relative
     * path, so repeated runs over unchanged files produce identical JSON.
     */
    public static CrateDraft build(String projectName, Path projectDir, List<Path> files,
                                   long maxBytes) {
        List<CrateEntry> entries = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        if (files != null && projectDir != null) {
            for (Path file : files) {
                if (file == null || !Files.isRegularFile(file)) {
                    skipped.add((file == null ? "(null)" : file.getFileName().toString())
                            + ": not a regular file");
                    continue;
                }
                long size;
                try {
                    size = Files.size(file);
                } catch (IOException ex) {
                    skipped.add(file.getFileName() + ": size unknown (" + ex.getMessage() + ")");
                    continue;
                }
                if (size > maxBytes) {
                    skipped.add(file.getFileName() + ": exceeds the " + maxBytes
                            + "-byte inclusion bound");
                    continue;
                }
                String digest;
                try {
                    digest = sha256Hex(file);
                } catch (IOException ex) {
                    skipped.add(file.getFileName() + ": could not be hashed (" + ex.getMessage()
                            + ")");
                    continue;
                }
                String relative = projectDir.relativize(file.toAbsolutePath().normalize())
                        .toString().replace('\\', '/');
                entries.add(new CrateEntry(relative, size, digest,
                        mediaType(file.getFileName().toString())));
            }
        }
        entries.sort(Comparator.comparing(CrateEntry::getRelativePath));

        String name = projectName == null || projectName.isBlank()
                ? (projectDir == null ? "quantumforge-project"
                        : projectDir.getFileName().toString())
                : projectName;
        return new CrateDraft(name, composeJson(name, DRAFT_DESCRIPTION, entries, null, List.of()),
                entries, skipped);
    }

    /**
     * The single owner of the {@code ro-crate-metadata.json} shape (Roadmap #135).
     * The metadata draft ({@link #build}) and the payload packer both compose
     * through here so a reviewed draft and a packed crate can never diverge in
     * structure. Optional {@code licence} / {@code authors} fields are EMITTED
     * ONLY when explicitly supplied - nothing is ever guessed or defaulted;
     * when absent the crate simply carries no such claim.
     *
     * <p>Output is deterministic: entries arrive pre-sorted by relative path,
     * authors are emitted in supplied order, and all strings are JSON-escaped.</p>
     */
    static String composeJson(String name, String description, List<CrateEntry> entries,
                              String licence, List<CrateAuthor> authors) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"@context\": \"https://w3id.org/ro/crate/1.1/context\",\n");
        json.append("  \"@graph\": [\n");
        json.append("    {\n");
        json.append("      \"@id\": \"ro-crate-metadata.json\",\n");
        json.append("      \"@type\": \"CreativeWork\",\n");
        json.append("      \"conformsTo\": [{\"@id\": \"https://w3id.org/ro/crate/1.1\"}],\n");
        json.append("      \"about\": {\"@id\": \"./\"}\n");
        json.append("    },\n");
        json.append("    {\n");
        json.append("      \"@id\": \"./\",\n");
        json.append("      \"@type\": \"Dataset\",\n");
        json.append("      \"name\": \"").append(escapeJson(name)).append("\",\n");
        json.append("      \"description\": \"").append(escapeJson(description)).append("\"");
        if (licence != null && !licence.isBlank()) {
            json.append(",\n      \"license\": \"").append(escapeJson(licence.trim())).append("\"");
        }
        if (authors != null && !authors.isEmpty()) {
            json.append(",\n      \"creator\": [");
            for (int i = 0; i < authors.size(); i++) {
                CrateAuthor author = authors.get(i);
                json.append(i == 0 ? "" : ", ");
                json.append("{\"@type\": \"Person\", \"name\": \"")
                        .append(escapeJson(author.getName())).append("\"");
                if (author.getIdentifier() != null) {
                    json.append(", \"identifier\": \"").append(escapeJson(author.getIdentifier()))
                            .append("\"");
                }
                json.append("}");
            }
            json.append("]");
        }
        json.append(",\n      \"hasPart\": [");
        for (int i = 0; i < entries.size(); i++) {
            json.append(i == 0 ? "" : ", ");
            json.append("{\"@id\": \"").append(escapeJson(entries.get(i).getRelativePath()))
                    .append("\"}");
        }
        json.append("]\n");
        json.append(entries.isEmpty() ? "    }\n" : "    },\n");
        for (int i = 0; i < entries.size(); i++) {
            CrateEntry entry = entries.get(i);
            json.append("    {\n");
            json.append("      \"@id\": \"").append(escapeJson(entry.getRelativePath()))
                    .append("\",\n");
            json.append("      \"@type\": \"File\",\n");
            json.append("      \"contentSize\": \"").append(entry.getBytes()).append("\",\n");
            json.append("      \"sha256\": \"").append(entry.getSha256()).append("\",\n");
            json.append("      \"encodingFormat\": \"").append(entry.getMediaType())
                    .append("\"\n");
            json.append(i + 1 < entries.size() ? "    },\n" : "    }\n");
        }
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    private static String sha256Hex(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("SHA-256 is unavailable", ex);
        }
        byte[] buffer = new byte[HASH_BUFFER];
        try (InputStream input = Files.newInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte b : digest.digest()) {
            hex.append(String.format(Locale.ROOT, "%02x", b));
        }
        return hex.toString();
    }

    /** Extension-only media hints (package-visible for tests); no content sniffing. */
    static String mediaType(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) {
            return "text/csv";
        }
        if (lower.endsWith(".xml")) {
            return "application/xml";
        }
        if (lower.endsWith(".json") || lower.endsWith(".jsonl")) {
            return "application/json";
        }
        if (lower.endsWith(".in") || lower.endsWith(".log") || lower.endsWith(".out")
                || lower.endsWith(".txt") || lower.endsWith(".md")) {
            return "text/plain";
        }
        return "application/octet-stream";
    }

    /** JSON string escaping for names/paths (quotes, backslashes, control chars). */
    static String escapeJson(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        return out.toString();
    }
}
