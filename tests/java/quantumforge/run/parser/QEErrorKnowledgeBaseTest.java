package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class QEErrorKnowledgeBaseTest {

    @Test
    void detectsMissingPseudopotential() throws Exception {
        String log = Files.readString(
                Path.of("tests/fixtures/qe/error_missing_pseudo.log"), StandardCharsets.UTF_8);
        List<QEErrorKnowledgeBase.Diagnosis> hits = QEErrorKnowledgeBase.diagnose(log);
        assertFalse(hits.isEmpty());
        assertTrue(hits.stream().anyMatch(d -> "MISSING_PSEUDO".equals(d.getSignature().getId())));
    }

    @Test
    void detectsScfNotConvergedAndMpiAbort() throws Exception {
        String log = Files.readString(
                Path.of("tests/fixtures/qe/scf_not_converged.log"), StandardCharsets.UTF_8);
        List<QEErrorKnowledgeBase.Diagnosis> hits = QEErrorKnowledgeBase.diagnose(log);
        assertTrue(hits.stream().anyMatch(d -> "SCF_NOT_CONVERGED".equals(d.getSignature().getId())));
        assertTrue(hits.stream().anyMatch(d -> "MPI_ABORT".equals(d.getSignature().getId())));
    }

    @Test
    void emptyLogYieldsNoHits() {
        assertTrue(QEErrorKnowledgeBase.diagnose("").isEmpty());
    }
}
