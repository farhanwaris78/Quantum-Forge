package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Corpus-level checks that golden fixtures remain useful for analyzers.
 */
class GoldenFixtureCorpusTest {

    @Test
    void siliconAndIronScfFixturesConverge() throws Exception {
        for (String name : new String[] {
                "tests/fixtures/qe/scf_si_converged.log",
                "tests/fixtures/qe/scf_fe_spin.log"
        }) {
            String log = Files.readString(Path.of(name), StandardCharsets.UTF_8);
            ScfConvergenceAnalyzer.Report report = ScfConvergenceAnalyzer.analyze(log);
            assertTrue(report.isConverged(), name + " should converge");
            assertTrue(report.getIterations().size() >= 2, name);
            assertTrue(report.getFinalEnergyRy() < 0.0, name);
        }
    }

    @Test
    void bandPathFixtureHasHighSymmetryMarkers() throws Exception {
        String log = Files.readString(Path.of("tests/fixtures/qe/bands_path.log"),
                StandardCharsets.UTF_8);
        assertTrue(log.contains("high-symmetry point:"));
        long count = log.lines().filter(line -> line.contains("high-symmetry point:")).count();
        assertTrue(count >= 3);
    }

    @Test
    void dosFixtureHasEnergyGrid() throws Exception {
        String log = Files.readString(Path.of("tests/fixtures/qe/dos_header.log"),
                StandardCharsets.UTF_8);
        assertTrue(log.contains("E (eV)"));
        assertTrue(log.contains("EFermi"));
    }

    @Test
    void errorFixtureDiagnosesMissingPseudo() throws Exception {
        String log = Files.readString(Path.of("tests/fixtures/qe/error_missing_pseudo.log"),
                StandardCharsets.UTF_8);
        assertFalse(QEErrorKnowledgeBase.diagnose(log).isEmpty());
    }
}
