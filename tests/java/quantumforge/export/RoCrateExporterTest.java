package quantumforge.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Batch-50 coverage for the RO-Crate metadata draft (Roadmap #135). */
class RoCrateExporterTest {

    @TempDir
    Path tempDir;

    @Test
    void testDeterministicJsonWithExactHashes() throws IOException {
        Path a = this.tempDir.resolve("espresso.in");
        Files.writeString(a, "hello", StandardCharsets.UTF_8);
        Path b = this.tempDir.resolve(".quantumforge.run-manifest.jsonl");
        Files.writeString(b, "{}\n", StandardCharsets.UTF_8);

        RoCrateExporter.CrateDraft crate = RoCrateExporter.build(
                "espresso", this.tempDir, List.of(b, a) // deliberately unsorted input
        );
        assertEquals(2, crate.getEntries().size());
        assertTrue(crate.getSkipped().isEmpty());
        // Sorted by relative path regardless of input order:
        assertEquals(".quantumforge.run-manifest.jsonl",
                crate.getEntries().get(0).getRelativePath());
        assertEquals("espresso.in", crate.getEntries().get(1).getRelativePath());
        // sha256("hello") is a well-known constant:
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                crate.getEntries().get(1).getSha256());

        String json = crate.getJson();
        assertTrue(json.contains("https://w3id.org/ro/crate/1.1/context"), json);
        assertTrue(json.contains("\"@id\": \"./\""), json);
        assertTrue(json.contains("\"@id\": \"espresso.in\""), json);
        assertTrue(json.contains("\"contentSize\": \"5\""), json);
        // Determinism: build again, compare byte content.
        RoCrateExporter.CrateDraft again = RoCrateExporter.build(
                "espresso", this.tempDir, List.of(a, b));
        assertEquals(json, again.getJson(), "Byte-identical JSON for unchanged inputs");
    }

    @Test
    void testSkippedEntriesAreExplicit() throws IOException {
        Path ok = this.tempDir.resolve("ok.txt");
        Files.writeString(ok, "1234", StandardCharsets.UTF_8);
        Path big = this.tempDir.resolve("big.csv");
        Files.writeString(big, "12345", StandardCharsets.UTF_8);
        Path missing = this.tempDir.resolve("gone.log");

        RoCrateExporter.CrateDraft crate = RoCrateExporter.build(
                "p", this.tempDir, List.of(ok, big, missing), 4L);
        assertEquals(1, crate.getEntries().size(), "Only the within-bound file is included");
        assertEquals(2, crate.getSkipped().size());
        assertTrue(crate.getSkipped().toString().contains("big.csv"), crate.getSkipped().toString());
        assertTrue(crate.getSkipped().toString().contains("exceeds the 4-byte inclusion bound"),
                crate.getSkipped().toString());
        assertTrue(crate.getSkipped().toString().contains("gone.log"), crate.getSkipped().toString());
        assertTrue(crate.getJson().contains("\"hasPart\": [{\"@id\": \"ok.txt\"}]"),
                crate.getJson());
    }

    @Test
    void testJsonEscapingOfNames() throws IOException {
        Path quote = this.tempDir.resolve("we\"ird.in");
        Files.writeString(quote, "x", StandardCharsets.UTF_8);
        RoCrateExporter.CrateDraft crate = RoCrateExporter.build(
                "proj\"ect", this.tempDir, List.of(quote));
        String json = crate.getJson();
        assertTrue(json.contains("we\\\"ird.in"), json);
        assertTrue(json.contains("proj\\\"ect"), json);
        assertTrue(!json.contains("proj\"ect\""), "Raw quotes must be escaped");
    }

    @Test
    void testEmptyInputYieldsEmptyGraphPart() {
        RoCrateExporter.CrateDraft crate = RoCrateExporter.build("", this.tempDir, List.of());
        assertTrue(crate.getEntries().isEmpty());
        assertTrue(crate.getJson().contains("\"hasPart\": []"), crate.getJson());
        // Fallback name from the directory itself.
        String name = this.tempDir.getFileName().toString();
        assertTrue(crate.getJson().contains(name), crate.getJson());
    }

    @Test
    void testMediaHintsAreExtensionOnly() {
        assertEquals("text/plain", RoCrateExporter.mediaType("a.in"));
        assertEquals("text/csv", RoCrateExporter.mediaType("a.csv"));
        assertEquals("application/octet-stream", RoCrateExporter.mediaType("a.wfc1"));
    }
}
