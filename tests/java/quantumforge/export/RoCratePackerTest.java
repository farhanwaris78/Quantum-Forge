/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.export.RoCrateExporter.CrateAuthor;
import quantumforge.export.RoCrateExporter.CrateDraft;
import quantumforge.export.RoCratePacker.PackSummary;
import quantumforge.operation.OperationResult;

/**
 * Roadmap #135 pack layer: the staged-copy + post-copy re-hash + atomic
 * activation contract, the drift/honesty refusals, and the explicit-only
 * licence/author metadata policy.
 */
class RoCratePackerTest {

    @TempDir
    Path tempDir;

    private Path write(String relative, String content) throws IOException {
        Path file = this.tempDir.resolve(relative);
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private CrateDraft draftOf(List<Path> files) {
        return RoCrateExporter.build("demo-project", this.tempDir, files);
    }

    @Test
    void testHappyPackCopiesVerifiesAndDeclaresMetadata() throws IOException {
        Path inp = write("espresso.in", "hello-in\n");
        Path log = write("outputs/run.log", "line1\nline2\n");
        CrateDraft draft = draftOf(List.of(inp, log));
        Path target = this.tempDir.resolve("crate-one");

        OperationResult<PackSummary> result = RoCratePacker.pack(draft, this.tempDir, target,
                "CC-BY-4.0", List.of(new CrateAuthor("Doe, Jane", "https://orcid.org/0000-0001"),
                        new CrateAuthor("Roe, John", null)));
        assertTrue(result.isSuccess(), () -> result.getMessage());
        assertEquals("ROCRATE_PACKED", result.getCode());
        PackSummary summary = result.getValue().orElseThrow();
        assertEquals(2, summary.getFileCount());
        assertEquals(21, summary.getTotalBytes());
        assertTrue(summary.isLicenceDeclared());
        assertEquals(2, summary.getAuthorCount());

        assertEquals("hello-in\n", Files.readString(target.resolve("espresso.in")));
        assertEquals("line1\nline2\n", Files.readString(target.resolve("outputs/run.log")));
        String metadata = Files.readString(target.resolve(RoCratePacker.METADATA_FILE));
        assertTrue(metadata.contains("\"license\": \"CC-BY-4.0\""), metadata);
        assertTrue(metadata.contains("{\"@type\": \"Person\", \"name\": \"Doe, Jane\", "
                + "\"identifier\": \"https://orcid.org/0000-0001\"}"), metadata);
        assertTrue(metadata.contains("{\"@type\": \"Person\", \"name\": \"Roe, John\"}"), metadata);
        assertTrue(metadata.contains("\"@id\": \"outputs/run.log\""), metadata);
        assertTrue(metadata.contains("re-hashed after copying"), metadata);
        assertTrue(metadata.contains("\"sha256\": \"" + draft.getEntries().get(0).getSha256()
                + "\""), metadata);
    }

    @Test
    void testPackedMetadataIsDeterministicAcrossRuns() throws IOException {
        Path a = write("espresso.in", "same bytes\n");
        CrateDraft draft = draftOf(List.of(a));
        Path first = this.tempDir.resolve("crate-a");
        Path second = this.tempDir.resolve("crate-b");
        assertTrue(RoCratePacker.pack(draft, this.tempDir, first, null, null).isSuccess());
        assertTrue(RoCratePacker.pack(draft, this.tempDir, second, null, null).isSuccess());
        assertEquals(Files.readString(first.resolve(RoCratePacker.METADATA_FILE)),
                Files.readString(second.resolve(RoCratePacker.METADATA_FILE)),
                "two packs over unchanged sources must produce byte-identical metadata");
    }

    @Test
    void testDraftJsonShapeIsUnchangedByTheSharedComposer() {
        // The metadata draft's own contract: composeJson without optional fields must
        // reproduce the historic draft byte-for-byte (delegation through one owner).
        CrateDraft draft = RoCrateExporter.build("demo", this.tempDir, List.of());
        String composed = RoCrateExporter.composeJson(draft.getProjectName(),
                "QuantumForge RO-Crate metadata draft (Roadmap item 135). Entries carry "
                        + "byte sizes and SHA-256 checksums of artifacts that existed at "
                        + "scan time; payload packaging, licence metadata and author records "
                        + "are deliberately not automated by this draft.",
                draft.getEntries(), null, List.of());
        assertEquals(draft.getJson(), composed);
        assertFalse(draft.getJson().contains("\"license\""), draft.getJson());
        assertFalse(draft.getJson().contains("\"creator\""), draft.getJson());
    }

    @Test
    void testExistingTargetIsRefusedAndUntouched() throws IOException {
        Path inp = write("espresso.in", "data\n");
        CrateDraft draft = draftOf(List.of(inp));
        Path target = this.tempDir.resolve("crate");
        Files.createDirectories(target);
        Files.writeString(target.resolve("keep.me"), "precious\n");
        OperationResult<PackSummary> result = RoCratePacker.pack(draft, this.tempDir, target,
                null, null);
        assertFalse(result.isSuccess());
        assertEquals("ROCRATE_TARGET", result.getCode());
        assertTrue(result.getMessage().contains("already exists"), result.getMessage());
        assertEquals("precious\n", Files.readString(target.resolve("keep.me")),
                "a refused pack never merges into or deletes existing content");
    }

    @Test
    void testEmptyDraftRefusesCeremonialCrate() {
        CrateDraft draft = RoCrateExporter.build("demo", this.tempDir, List.of());
        OperationResult<PackSummary> result = RoCratePacker.pack(draft, this.tempDir,
                this.tempDir.resolve("crate"), null, null);
        assertFalse(result.isSuccess());
        assertEquals("ROCRATE_EMPTY", result.getCode());
        assertTrue(result.getMessage().contains("no payload"), result.getMessage());
        assertFalse(Files.exists(this.tempDir.resolve("crate")),
                "an empty pack attempt must not create the folder");
    }

    @Test
    void testSourceDriftAbortsBeforeActivation() throws IOException {
        Path inp = write("espresso.in", "honest bytes\n");
        CrateDraft draft = draftOf(List.of(inp));
        Files.writeString(inp, "rerun produced different bytes!!\n"); // same length class, new hash
        Path target = this.tempDir.resolve("crate");
        OperationResult<PackSummary> result = RoCratePacker.pack(draft, this.tempDir, target,
                null, null);
        assertFalse(result.isSuccess());
        assertTrue(result.getCode().equals("ROCRATE_VERIFY") || result.getCode().equals("ROCRATE_SOURCE"),
                "size or hash drift must abort: " + result.getCode());
        assertTrue(result.getMessage().contains("invalidated")
                || result.getMessage().contains("changed size"), result.getMessage());
        assertFalse(Files.exists(target), "drift aborts before activation");
        assertNoStagingResidue();
    }

    @Test
    void testVanishedSourceFailsClosedWithNothingWritten() throws IOException {
        Path inp = write("espresso.in", "here today\n");
        CrateDraft draft = draftOf(List.of(inp));
        Files.delete(inp);
        OperationResult<PackSummary> result = RoCratePacker.pack(draft, this.tempDir,
                this.tempDir.resolve("crate"), null, null);
        assertFalse(result.isSuccess());
        assertEquals("ROCRATE_SOURCE", result.getCode());
        assertTrue(result.getMessage().contains("vanished"), result.getMessage());
        assertFalse(Files.exists(this.tempDir.resolve("crate")));
        assertNoStagingResidue();
    }

    @Test
    void testNoLicenceNoAuthorOmitsClaimsAndSaysSo() throws IOException {
        Path inp = write("espresso.in", "content\n");
        CrateDraft draft = draftOf(List.of(inp));
        Path target = this.tempDir.resolve("crate");
        OperationResult<PackSummary> result = RoCratePacker.pack(draft, this.tempDir, target,
                "   ", List.of()); // blank licence == not declared
        assertTrue(result.isSuccess(), () -> result.getMessage());
        String metadata = Files.readString(target.resolve(RoCratePacker.METADATA_FILE));
        assertFalse(metadata.contains("\"license\""), metadata);
        assertFalse(metadata.contains("\"creator\""), metadata);
        assertTrue(metadata.contains("No licence was declared"), metadata);
        assertTrue(metadata.contains("No author/creator was declared"), metadata);
        assertFalse(result.getValue().orElseThrow().isLicenceDeclared());
        assertEquals(0, result.getValue().orElseThrow().getAuthorCount());
    }

    @Test
    void testSkippedEntriesSurvivePackingVerbatim() throws IOException {
        Path inp = write("espresso.in", "real\n");
        Path ghost = this.tempDir.resolve("ghost.log"); // never created: draft skips it
        CrateDraft draft = draftOf(List.of(inp, ghost));
        assertEquals(1, draft.getEntries().size());
        assertEquals(1, draft.getSkipped().size());
        Path target = this.tempDir.resolve("crate");
        OperationResult<PackSummary> result = RoCratePacker.pack(draft, this.tempDir, target,
                null, null);
        assertTrue(result.isSuccess(), () -> result.getMessage());
        assertEquals(draft.getSkipped(), result.getValue().orElseThrow().getSkipped(),
                "draft skip reasons are preserved, never hidden");
        assertFalse(Files.exists(target.resolve("ghost.log")),
                "a file absent at draft time is not conjured at pack time");
    }

    @Test
    void testBinaryPayloadRoundTripsByteIdentical() throws IOException {
        byte[] original = new byte[4096];
        new Random(42L).nextBytes(original);
        Path bin = this.tempDir.resolve("blob.bin");
        Files.write(bin, original);
        CrateDraft draft = draftOf(List.of(bin));
        Path target = this.tempDir.resolve("crate");
        assertTrue(RoCratePacker.pack(draft, this.tempDir, target, null, null).isSuccess());
        byte[] copied = Files.readAllBytes(target.resolve("blob.bin"));
        assertTrue(java.util.Arrays.equals(original, copied),
                "copied payload bytes must be identical to the sources");
    }

    @Test
    void testAuthorNamesAreJsonEscaped() throws IOException {
        Path inp = write("espresso.in", "x\n");
        CrateDraft draft = draftOf(List.of(inp));
        Path target = this.tempDir.resolve("crate");
        OperationResult<PackSummary> result = RoCratePacker.pack(draft, this.tempDir, target,
                null, List.of(new CrateAuthor("Dr. \"Q\" uote\nLine2", null)));
        assertTrue(result.isSuccess(), () -> result.getMessage());
        String metadata = Files.readString(target.resolve(RoCratePacker.METADATA_FILE));
        assertTrue(metadata.contains("\"name\": \"Dr. \\\"Q\\\" uote\\nLine2\""), metadata);
        assertFalse(metadata.contains("uote\nLine2"), "raw control characters must be escaped");
    }

    private void assertNoStagingResidue() throws IOException {
        try (Stream<Path> listing = Files.list(this.tempDir)) {
            List<String> residue = listing
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.contains(".packing."))
                    .toList();
            assertTrue(residue.isEmpty(), "staging residue left behind: " + residue);
        }
    }
}
