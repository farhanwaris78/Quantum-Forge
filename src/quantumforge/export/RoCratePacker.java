/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.export;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import quantumforge.operation.OperationResult;

/**
 * RO-Crate payload packer (Roadmap #135, completion arc).
 *
 * <p>Materializes a metadata {@link RoCrateExporter.CrateDraft} into a real crate
 * folder: every pinned file is copied into the folder under its relative path,
 * {@code ro-crate-metadata.json} is composed through the SAME
 * {@link RoCrateExporter#composeJson} owner the draft used, and the folder is
 * activated with a single rename so a crate never appears half-written.</p>
 *
 * <p>Integrity doctrine (mirrors the verified-transfer layer): the draft's
 * SHA-256 pins are the CONTRACT. Each staged copy is re-hashed after the copy;
 * a mismatch means the source drifted after the draft was built (or the copy
 * corrupted bytes) - the pack aborts, the staging directory is removed, and the
 * target never exists. The packer never re-hashes a drifted source and silently
 * "updates" the metadata: drift invalidates the draft, and the failure says so.</p>
 *
 * <p>Metadata doctrine: licence and author/creator entries are emitted only
 * when explicitly supplied by the caller. When omitted, the crate carries no
 * such claim and the pack description states the omission in prose.</p>
 *
 * <p>Consent doctrine: this class writes anywhere only when invoked; the GUI
 * calls it strictly after an explicit pack action. The target directory must
 * not exist beforehand (an existing path is refused, never merged into).</p>
 */
public final class RoCratePacker {

    /** The metadata file placed at the crate root (RO-Crate 1.1). */
    public static final String METADATA_FILE = "ro-crate-metadata.json";

    private static final int HASH_BUFFER = 1 << 16;

    private RoCratePacker() {
        // Utility
    }

    /** Bookkeeping of one successful pack. */
    public static final class PackSummary {
        private final Path targetDir;
        private final int fileCount;
        private final long totalBytes;
        private final long metadataBytes;
        private final boolean licenceDeclared;
        private final int authorCount;
        private final List<String> skipped;

        private PackSummary(Path targetDir, int fileCount, long totalBytes, long metadataBytes,
                            boolean licenceDeclared, int authorCount, List<String> skipped) {
            this.targetDir = targetDir;
            this.fileCount = fileCount;
            this.totalBytes = totalBytes;
            this.metadataBytes = metadataBytes;
            this.licenceDeclared = licenceDeclared;
            this.authorCount = authorCount;
            this.skipped = List.copyOf(skipped);
        }

        public Path getTargetDir() { return this.targetDir; }
        public int getFileCount() { return this.fileCount; }
        public long getTotalBytes() { return this.totalBytes; }
        public long getMetadataBytes() { return this.metadataBytes; }
        public boolean isLicenceDeclared() { return this.licenceDeclared; }
        public int getAuthorCount() { return this.authorCount; }
        /** Skip reasons inherited from the draft (never hidden by packing). */
        public List<String> getSkipped() { return this.skipped; }
    }

    /**
     * Packs {@code draft} (built against {@code projectDir}) into the new
     * directory {@code targetDir}.
     *
     * <p>Fail-closed verdicts: {@code ROCRATE_INPUT} (null arguments),
     * {@code ROCRATE_EMPTY} (zero artifacts - a publication claim with no
     * payload is never written), {@code ROCRATE_TARGET} (target already exists
     * or parent missing), {@code ROCRATE_SOURCE} (a pinned file vanished,
     * changed size, or resolves outside the project), {@code ROCRATE_STAGE} /
     * {@code ROCRATE_COPY} / {@code ROCRATE_METADATA} (I/O failures, staging
     * removed), {@code ROCRATE_VERIFY} (post-copy hash mismatch = drift, draft
     * invalidated, nothing activated), {@code ROCRATE_ACTIVATE} (final rename
     * failed; the verified staging copy was removed).</p>
     */
    public static OperationResult<PackSummary> pack(RoCrateExporter.CrateDraft draft,
                                                    Path projectDir, Path targetDir,
                                                    String licenceText,
                                                    List<RoCrateExporter.CrateAuthor> authors) {
        if (draft == null || projectDir == null || targetDir == null) {
            return OperationResult.failed("ROCRATE_INPUT",
                    "Packing needs the reviewable draft, its project directory, and an explicit"
                            + " target directory; one of them was null.", null);
        }
        List<RoCrateExporter.CrateEntry> entries = draft.getEntries();
        if (entries.isEmpty()) {
            return OperationResult.failed("ROCRATE_EMPTY",
                    "Zero artifacts are includable, so no crate folder is written: a publication"
                            + " claim with no payload would be ceremonial. The draft's skipped"
                            + " list explains why (" + draft.getSkipped().size() + " reason(s)).", null);
        }
        Path normalizedTarget = targetDir.toAbsolutePath().normalize();
        Path parent = normalizedTarget.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return OperationResult.failed("ROCRATE_TARGET",
                    "The parent folder does not exist: " + parent
                            + ". Choose an existing parent folder; the packer creates only the"
                            + " crate directory itself.", null);
        }
        if (Files.exists(normalizedTarget)) {
            return OperationResult.failed("ROCRATE_TARGET",
                    normalizedTarget + " already exists. The packer creates its directory in one"
                            + " rename so a crate never appears half-written; merging into"
                            + " existing content is refused.", null);
        }

        Path normalizedProject = projectDir.toAbsolutePath().normalize();
        // Preflight: every pinned file must still exist with its drafted size before
        // anything is staged. The post-copy hash remains the authoritative guard.
        for (RoCrateExporter.CrateEntry entry : entries) {
            OperationResult<PackSummary> refusal = preflightEntry(normalizedProject, entry);
            if (refusal != null) {
                return refusal;
            }
        }

        Path stage = parent.resolve("." + normalizedTarget.getFileName()
                + ".packing." + System.nanoTime());
        try {
            Files.createDirectories(stage);
        } catch (IOException ex) {
            return OperationResult.failed("ROCRATE_STAGE",
                    "Could not create the staging folder " + stage + ": " + ex.getMessage(), null);
        }

        long totalBytes = 0L;
        for (RoCrateExporter.CrateEntry entry : entries) {
            Path source = normalizedProject.resolve(entry.getRelativePath()).normalize();
            Path staged = stage.resolve(entry.getRelativePath()).normalize();
            if (!staged.startsWith(stage)) {
                deleteTreeQuietly(stage);
                return OperationResult.failed("ROCRATE_COPY",
                        "Refusing a staged path escaping the staging folder: "
                                + entry.getRelativePath(), null);
            }
            try {
                if (staged.getParent() != null) {
                    Files.createDirectories(staged.getParent());
                }
                Files.copy(source, staged);
            } catch (IOException ex) {
                deleteTreeQuietly(stage);
                return OperationResult.failed("ROCRATE_COPY",
                        "Copy failed for '" + entry.getRelativePath() + "': " + ex.getMessage()
                                + ". Nothing was activated.", null);
            }
            String copiedHash;
            try {
                copiedHash = sha256Hex(staged);
            } catch (IOException ex) {
                deleteTreeQuietly(stage);
                return OperationResult.failed("ROCRATE_VERIFY",
                        "The staged copy of '" + entry.getRelativePath()
                                + "' could not be re-hashed: " + ex.getMessage()
                                + ". Nothing was activated.", null);
            }
            if (!copiedHash.equals(entry.getSha256())) {
                deleteTreeQuietly(stage);
                return OperationResult.failed("ROCRATE_VERIFY",
                        "Source drift detected for '" + entry.getRelativePath()
                                + "': the draft pins sha256=" + entry.getSha256()
                                + " but the bytes on disk now hash " + copiedHash
                                + ". The draft is invalidated; rebuild the metadata draft"
                                + " (re-running a calculation changes artifacts) and pack"
                                + " again. Nothing was activated.", null);
            }
            totalBytes += entry.getBytes();
        }

        boolean licenceDeclared = licenceText != null && !licenceText.isBlank();
        int authorCount = authors == null ? 0 : authors.size();
        String description = "QuantumForge RO-Crate (packed from the Roadmap item 135"
                + " metadata draft). Every payload file was re-hashed after copying and matches"
                + " the SHA-256 pinned by the draft. "
                + (licenceDeclared
                        ? "The licence was declared explicitly by the operator at pack time."
                        : "No licence was declared at pack time - the crate deliberately carries"
                                + " no licence claim.")
                + " "
                + (authorCount > 0
                        ? authorCount + " author/creator record(s) were declared explicitly by"
                                + " the operator at pack time."
                        : "No author/creator was declared at pack time - the crate deliberately"
                                + " carries no authorship claim.");
        String metadataJson = RoCrateExporter.composeJson(
                draft.getProjectName(), description, entries, licenceText, authors);
        byte[] metadataBytes = metadataJson.getBytes(StandardCharsets.UTF_8);
        try {
            Files.write(stage.resolve(METADATA_FILE), metadataBytes,
                    StandardOpenOption.CREATE_NEW);
        } catch (IOException ex) {
            deleteTreeQuietly(stage);
            return OperationResult.failed("ROCRATE_METADATA",
                    "Writing " + METADATA_FILE + " failed: " + ex.getMessage()
                            + ". Nothing was activated.", null);
        }

        try {
            Files.move(stage, normalizedTarget, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException notAtomic) {
            try {
                Files.move(stage, normalizedTarget);
            } catch (IOException ex) {
                deleteTreeQuietly(stage);
                return OperationResult.failed("ROCRATE_ACTIVATE",
                        "Activating the crate failed: " + ex.getMessage()
                                + ". The verified staging copy was removed; retry with a new"
                                + " target name if the problem persists.", null);
            }
        } catch (IOException ex) {
            deleteTreeQuietly(stage);
            return OperationResult.failed("ROCRATE_ACTIVATE",
                    "Activating the crate failed: " + ex.getMessage()
                            + ". The verified staging copy was removed.", null);
        }

        String message = "Packed " + entries.size() + " file(s), " + totalBytes
                + " byte(s), every hash re-verified after copy; licence "
                + (licenceDeclared ? "declared explicitly" : "not declared (stated in prose)")
                + "; " + authorCount + " author record(s)"
                + (draft.getSkipped().isEmpty() ? ""
                        : "; " + draft.getSkipped().size() + " draft skip(s) preserved verbatim");
        return OperationResult.success("ROCRATE_PACKED", message,
                new PackSummary(normalizedTarget, entries.size(), totalBytes, metadataBytes.length,
                        licenceDeclared, authorCount, draft.getSkipped()));
    }

    /** Cheap early verdicts (hash after copy stays the authoritative guard). */
    private static OperationResult<PackSummary> preflightEntry(Path projectDir,
                                                               RoCrateExporter.CrateEntry entry) {
        Path source = projectDir.resolve(entry.getRelativePath()).normalize();
        if (!source.startsWith(projectDir)) {
            return OperationResult.failed("ROCRATE_SOURCE",
                    "Refusing to read outside the project directory: " + entry.getRelativePath(),
                    null);
        }
        if (!Files.isRegularFile(source)) {
            return OperationResult.failed("ROCRATE_SOURCE",
                    "'" + entry.getRelativePath() + "' vanished since the draft was built;"
                            + " nothing was written. Rebuild the metadata draft.", null);
        }
        try {
            long size = Files.size(source);
            if (size != entry.getBytes()) {
                return OperationResult.failed("ROCRATE_SOURCE",
                        "'" + entry.getRelativePath() + "' changed size since the draft ("
                                + entry.getBytes() + " -> " + size
                                + " bytes); the draft is invalidated. Nothing was written -"
                                + " rebuild the metadata draft.", null);
            }
        } catch (IOException ex) {
            return OperationResult.failed("ROCRATE_SOURCE",
                    "Size of '" + entry.getRelativePath() + "' is unreadable: " + ex.getMessage(),
                    null);
        }
        return null;
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

    /** Best-effort staging cleanup; never fails the primary error path. */
    private static void deleteTreeQuietly(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Staging residue is reported by the primary error path already.
                }
            });
        } catch (IOException ignored) {
            // Best effort only.
        }
    }
}
