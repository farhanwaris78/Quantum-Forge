package quantumforge.hpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.hpc.SiteProfileValidator.SiteProfileReport;
import quantumforge.input.validation.ValidationIssue;
import quantumforge.input.validation.ValidationSeverity;

/** Batch-42 coverage for the static site-profile validator (Roadmap #94/#103). */
class SiteProfileValidatorTest {

    @TempDir
    Path tempDir;

    private Path write(String name, String content) throws IOException {
        Path file = this.tempDir.resolve(name);
        Files.writeString(file, content, java.nio.charset.StandardCharsets.UTF_8);
        return file;
    }

    private static boolean hasCode(SiteProfileReport report, String code) {
        return report.getIssues().stream().anyMatch(issue -> issue.getCode().equals(code));
    }

    private static long count(SiteProfileReport report, ValidationSeverity severity) {
        return report.getIssues().stream()
                .filter(issue -> issue.getSeverity() == severity).count();
    }

    @Test
    void testExampleProfileHasNoErrors() throws IOException {
        Path example = Path.of("packaging/sites/example-slurm.yaml");
        SiteProfileReport report = SiteProfileValidator.validate(example);
        assertNotNull(report.getProfile(), "The shipped example must parse");
        assertEquals(0L, report.errorCount(),
                "The shipped example must not produce blocking errors: " + report.getIssues());
        assertEquals("example-cluster", report.getProfile().getId());
        assertEquals("slurm", report.getProfile().getScheduler());
        assertEquals(2, report.getProfile().getModules().size());
        // $USER expansion gets exactly one informational note.
        assertTrue(hasCode(report, "SITE_ENV_USER"), report.getIssues().toString());
    }

    @Test
    void testUnknownSchedulerIsBlocking() throws IOException {
        Path bad = write("bad.yaml", "id: x\nscheduler: lsf\nmpi_launcher: srun\n");
        SiteProfileReport report = SiteProfileValidator.validate(bad);
        assertTrue(hasCode(report, "SITE_SCHEDULER_UNKNOWN"), report.getIssues().toString());
        ValidationIssue issue = report.getIssues().stream()
                .filter(item -> item.getCode().equals("SITE_SCHEDULER_UNKNOWN")).findFirst()
                .orElseThrow();
        assertEquals(ValidationSeverity.ERROR, issue.getSeverity());
        assertEquals(1L, report.errorCount());
    }

    @Test
    void testTypoKeyIsReported() throws IOException {
        Path bad = write("typo.yaml", "id: x\nscheduler: slurm\nscheuler_root: /tmp\n");
        SiteProfileReport report = SiteProfileValidator.validate(bad);
        assertTrue(hasCode(report, "SITE_UNKNOWN_KEY"),
                "A misspelled key must not be silently ignored: " + report.getIssues());
    }

    @Test
    void testContainerDigestAndBindRules() throws IOException {
        Path tagOnly = write("container.yaml",
                "id: x\nscheduler: slurm\nstaging_root: /s\nscratch_root: /s\n"
                        + "container_image: qe.sif\ncontainer_bind: relative/path\n");
        SiteProfileReport report = SiteProfileValidator.validate(tagOnly);
        assertTrue(hasCode(report, "SITE_CONTAINER_DIGEST_MISSING"), report.getIssues().toString());
        assertTrue(hasCode(report, "SITE_CONTAINER_BIND_RELATIVE"), report.getIssues().toString());
        assertTrue(hasCode(report, "SITE_CONTAINER_MPI_ABI"),
                "The host-MPI ABI constraint must be stated: " + report.getIssues());
        assertTrue(count(report, ValidationSeverity.ERROR) >= 1L);

        String digest = "sha256:" + "ab".repeat(32);
        Path pinned = write("pinned.yaml",
                "id: x\nscheduler: slurm\nstaging_root: /s\nscratch_root: /s\n"
                        + "container_image: qe.sif\ncontainer_digest: " + digest + "\n"
                        + "container_bind: /scratch/project:/data\n");
        SiteProfileReport ok = SiteProfileValidator.validate(pinned);
        assertFalse(hasCode(ok, "SITE_CONTAINER_DIGEST_MISSING"), ok.getIssues().toString());
        assertFalse(hasCode(ok, "SITE_CONTAINER_BIND_RELATIVE"), ok.getIssues().toString());
        assertTrue(hasCode(ok, "SITE_CONTAINER_MPI_ABI"),
                "The ABI note stays even for a pinned image: " + ok.getIssues());
        assertEquals(List.of("qe.sif"), ok.containerValues("image"));
    }

    @Test
    void testIncompleteContainerBlock() throws IOException {
        Path file = write("half.yaml", "id: x\nscheduler: slurm\ncontainer_digest: sha256:"
                + "cd".repeat(32) + "\n");
        SiteProfileReport report = SiteProfileValidator.validate(file);
        assertTrue(hasCode(report, "SITE_CONTAINER_INCOMPLETE"), report.getIssues().toString());
    }

    @Test
    void testUnreadableAndMissingFilesFailClosed() throws IOException {
        SiteProfileReport missing = SiteProfileValidator.validate(
                this.tempDir.resolve("nope.yaml"));
        assertNull(missing.getProfile());
        assertTrue(hasCode(missing, "SITE_MISSING"));
        assertEquals(1L, missing.errorCount());

        Path garbage = write("broken.yaml", "id: x\nntasks: not-a-number\n");
        SiteProfileReport broken = SiteProfileValidator.validate(garbage);
        assertNull(broken.getProfile(), "A parse failure must not produce a half-loaded profile");
        assertTrue(hasCode(broken, "SITE_UNREADABLE"), broken.getIssues().toString());
    }

    @Test
    void testEmptyRootsAndModulesWarn() throws IOException {
        Path file = write("sparse.yaml", "id: x\nscheduler: sge\n");
        SiteProfileReport report = SiteProfileValidator.validate(file);
        assertTrue(hasCode(report, "SITE_STAGING_EMPTY"), report.getIssues().toString());
        assertTrue(hasCode(report, "SITE_SCRATCH_EMPTY"), report.getIssues().toString());
        assertTrue(hasCode(report, "SITE_MODULES_EMPTY"), report.getIssues().toString());
        assertEquals(0L, report.errorCount(), "Warnings are not blocking");
    }

    @Test
    void testPjmSchedulerIsAcceptedByTheRegistryProbe() throws IOException {
        Path file = write("pjm.yaml",
                "id: fugaku\nscheduler: pjm\nmpi_launcher: mpiexec\n");
        SiteProfileReport report = SiteProfileValidator.validate(file);
        assertFalse(hasCode(report, "SITE_SCHEDULER_UNKNOWN"),
                "pjm must resolve through the typed-adapter registry: "
                        + report.getIssues().toString());
        assertEquals("pjm", report.getProfile().getScheduler());
    }

    @Test
    void testVernacularAliasesAreCanonicalizedBeforeTheRegistryProbe() throws IOException {
        Path file = write("torque.yaml",
                "id: x\nscheduler: torque\nmpi_launcher: mpiexec\n");
        SiteProfileReport report = SiteProfileValidator.validate(file);
        assertFalse(hasCode(report, "SITE_SCHEDULER_UNKNOWN"),
                "torque must canonicalize to pbs before the probe: "
                        + report.getIssues().toString());
        assertEquals("pbs", report.getProfile().getScheduler(),
                "the parsed profile exposes the canonical name");
    }
}
