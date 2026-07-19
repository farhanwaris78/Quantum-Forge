package quantumforge.neural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.neural.MlModelManifest.ManifestReport;
import quantumforge.input.validation.ValidationSeverity;

/** Batch-43 coverage for model-manifest validation (Roadmap #139/#140). */
class MlModelManifestTest {

    @TempDir
    Path tempDir;

    private static final String VALID =
            "# MLP manifest\n"
            + "name: MACE-MP-0b\n"
            + "version: 2024-01\n"
            + "license: MIT\n"
            + "citation: Batatia et al., arXiv:2206.07697\n"
            + "cutoff_angstrom: 6.0\n"
            + "species: Si, O, Al\n"
            + "sha256: " + "ab".repeat(32) + "\n";

    private static boolean hasCode(ManifestReport report, String code) {
        return report.getIssues().stream().anyMatch(issue -> issue.getCode().equals(code));
    }

    @Test
    void testValidManifestParses() {
        ManifestReport report = MlModelManifest.parseLines(List.of(VALID.split("\n")));
        assertTrue(report.isUsable(), report.getIssues().toString());
        assertEquals(0L, report.errorCount());
        assertEquals("MACE-MP-0b", report.getManifest().getName());
        assertEquals("2024-01", report.getManifest().getVersion());
        assertEquals("MIT", report.getManifest().getLicense());
        assertEquals(6.0, report.getManifest().getCutoffAngstrom(), 1.0e-12);
        assertEquals(List.of("Si", "O", "Al"), report.getManifest().getSpecies());
        assertTrue(report.getUnparsedLines().isEmpty(), report.getUnparsedLines().toString());
    }

    @Test
    void testMissingProvenanceFieldsAreBlocking() {
        ManifestReport report = MlModelManifest.parseLines(List.of(
                "name: x", "version: 1", "cutoff_angstrom: 5.0", "species: Si"));
        assertFalse(report.isUsable());
        assertTrue(hasCode(report, "ML_LICENSE_MISSING"), report.getIssues().toString());
        assertTrue(hasCode(report, "ML_HASH_MISSING"), report.getIssues().toString());
        assertTrue(hasCode(report, "ML_CITATION_MISSING"), report.getIssues().toString());
        assertEquals(2L, report.errorCount(), "license and hash are blocking; citation warns");
    }

    @Test
    void testAmbiguousLicenseAndBadHash() {
        ManifestReport report = MlModelManifest.parseLines(List.of(
                "name: x", "version: 1", "license: unknown", "citation: c",
                "cutoff_angstrom: 5.0", "species: Si", "sha256: 1234"));
        assertTrue(hasCode(report, "ML_LICENSE_AMBIGUOUS"), report.getIssues().toString());
        assertTrue(hasCode(report, "ML_HASH_MISSING"), report.getIssues().toString());
    }

    @Test
    void testCutoffRules() {
        ManifestReport big = MlModelManifest.parseLines(List.of(
                "name: x", "version: 1", "license: MIT", "citation: c",
                "cutoff_angstrom: 12.5", "species: Si", "sha256: " + "cd".repeat(32)));
        assertTrue(big.isUsable(), report(big));
        assertTrue(hasCode(big, "ML_CUTOFF_LARGE"),
                "An oversized cutoff warns but is not blocking: " + report(big));

        ManifestReport zero = MlModelManifest.parseLines(List.of(
                "name: x", "version: 1", "license: MIT", "citation: c",
                "cutoff_angstrom: 0.0", "species: Si", "sha256: " + "cd".repeat(32)));
        assertTrue(hasCode(zero, "ML_CUTOFF_NONPOSITIVE"), report(zero));

        ManifestReport text = MlModelManifest.parseLines(List.of(
                "name: x", "version: 1", "license: MIT", "citation: c",
                "cutoff_angstrom: wide", "species: Si", "sha256: " + "cd".repeat(32)));
        assertTrue(hasCode(text, "ML_CUTOFF_NONNUMERIC"), report(text));
    }

    private static String report(ManifestReport r) {
        return r.getIssues().toString();
    }

    @Test
    void testUnknownSpeciesIsBlocking() {
        ManifestReport report = MlModelManifest.parseLines(List.of(
                "name: x", "version: 1", "license: MIT", "citation: c",
                "cutoff_angstrom: 5.0", "species: Si, Xx1", "sha256: " + "ef".repeat(32)));
        assertTrue(hasCode(report, "ML_SPECIES_UNKNOWN"), report(report));
        assertEquals(List.of("Si"), report.getManifest().getSpecies(),
                "Valid species are kept even when one token is rejected");
    }

    @Test
    void testDomainGate() {
        ManifestReport report = MlModelManifest.parseLines(List.of(VALID.split("\n")));
        assertTrue(report.isUsable());
        assertTrue(report.getManifest().elementsOutsideDomain(List.of("Si", "O")).isEmpty());
        assertEquals(List.of("H"),
                report.getManifest().elementsOutsideDomain(List.of("Si", "H")),
                "Unlisted elements must be reported by name");
        assertTrue(report.getManifest().elementsOutsideDomain(null).isEmpty());
    }

    @Test
    void testFileParsingFailureModes() throws IOException {
        ManifestReport missing = MlModelManifest.parse(this.tempDir.resolve("none.mlmf"));
        assertTrue(hasCode(missing, "ML_MANIFEST_MISSING"));

        Path file = this.tempDir.resolve("model.mlmf");
        Files.writeString(file, VALID, StandardCharsets.UTF_8);
        ManifestReport parsed = MlModelManifest.parse(file);
        assertTrue(parsed.isUsable(), report(parsed));
        assertNotNull(parsed.getManifest());
    }

    @Test
    void testUnparsedLinesAreCountedNotGuessed() {
        ManifestReport report = MlModelManifest.parseLines(List.of(
                "name: x", "version: 1", "license: MIT", "citation: c",
                "cutoff_angstrom: 5.0", "species: Si", "sha256: " + "ab".repeat(32),
                "this is not a key value line", "unknown_key: value"));
        assertTrue(report.isUsable(), report(report));
        assertEquals(2, report.getUnparsedLines().size(),
                "Foreign lines are reported, never interpreted");
    }

    @Test
    void testSeverityLevelsAreStable() {
        ManifestReport report = MlModelManifest.parseLines(List.of(
                "name: x", "version: 1", "license: MIT", "citation: c",
                "cutoff_angstrom: 15.0", "species: Si", "sha256: " + "ab".repeat(32)));
        assertEquals(0L, report.getIssues().stream()
                .filter(i -> i.getSeverity() == ValidationSeverity.ERROR).count());
        assertEquals(1L, report.getIssues().stream()
                .filter(i -> i.getSeverity() == ValidationSeverity.WARNING).count());
    }
}
