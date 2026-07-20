/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import quantumforge.run.QEErrorSignatureCatalog.Hit;
import quantumforge.run.QEErrorSignatureCatalog.ScanResult;
import quantumforge.run.QEErrorSignatureCatalog.Severity;
import quantumforge.run.QEErrorSignatureCatalog.Signature;

class QEErrorSignatureCatalogTest {

    @Test
    void curatedSignaturesAreWellFormed() {
        List<Signature> signatures = QEErrorSignatureCatalog.listSignatures();
        assertEquals(8, signatures.size(), "7 specific + 1 generic fallback");
        Set<String> ids = new HashSet<>();
        for (Signature signature : signatures) {
            assertTrue(signature.getNeedle() != null && !signature.getNeedle().isBlank(),
                    signature.getId() + " needs a verbatim needle");
            assertTrue(signature.getChecks().contains(";")
                    || signature.getChecks().contains(": "), signature.getId());
            assertTrue(signature.getDocsUrl().contains("user_guide"), signature.getId());
            assertTrue(ids.add(signature.getId()), "duplicate id " + signature.getId());
        }
        assertEquals("generic-error-routine",
                signatures.get(signatures.size() - 1).getId(),
                "the generic fallback must evaluate LAST so specific attribution wins");
    }

    @Test
    void hitsCarryVerbatimQuotesAndLineNumbers() {
        String log = "# Copyright (C) pw.x\n"
                + "     io routine, version 7.2\n"
                + "convergence NOT achieved after 100 iterations: stopping\n"
                + "final energy line\n"
                + "     Error in routine electrons (2):\n"
                + "     problems computing cholesky\n"
                + "     Error in routine charge is wrong (1):\n";
        ScanResult result = QEErrorSignatureCatalog.scanText(log);
        assertEquals(7, result.getLineCount());
        assertEquals(3, result.distinctSignatures());
        assertEquals(4, result.getHits().size());

        Hit scf = result.getHits().get(0);
        assertEquals("scf-not-converged", scf.getSignature().getId());
        assertEquals(3, scf.getLineNumber(), "1-based line numbering");
        assertEquals("convergence NOT achieved after 100 iterations: stopping",
                scf.getQuotedLine(), "the quote is the verbatim line, trimmed only");

        Hit generic = result.getHits().get(1);
        assertEquals("generic-error-routine", generic.getSignature().getId());
        assertEquals(5, generic.getLineNumber());

        Hit cholesky = result.getHits().get(2);
        assertEquals("cholesky", cholesky.getSignature().getId());
        assertEquals(6, cholesky.getLineNumber());
        assertTrue(cholesky.getSignature().getSeverity() == Severity.ERROR);

        Hit precedence = result.getHits().get(3);
        assertEquals("charge-wrong", precedence.getSignature().getId(),
                "specific needles beat the generic fallback on the same line");
    }

    @Test
    void repeatSpamIsCountedButQuoteCapped() {
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            log.append("convergence NOT achieved after 100 iterations: stopping\n");
        }
        ScanResult result = QEErrorSignatureCatalog.scanText(log.toString());
        assertEquals(6L, result.totalMatches("scf-not-converged"),
                "all repeats counted");
        assertEquals(QEErrorSignatureCatalog.MAX_QUOTES_PER_SIGNATURE,
                result.getHits().size(), "quotes capped; nothing hidden (count kept)");
        assertEquals(1, result.distinctSignatures());
    }

    @Test
    void longQuotesAreTruncatedDeterministically() {
        String longLine = "Error in routine very_long_routine_name (1): "
                + "x".repeat(300);
        ScanResult result = QEErrorSignatureCatalog.scanText(longLine);
        Hit hit = result.getHits().get(0);
        assertEquals(QEErrorSignatureCatalog.QUOTE_CAP,
                hit.getQuotedLine().trim().length(),
                "quoted lines are display-bounded");
        assertTrue(hit.getQuotedLine().startsWith("Error in routine"),
                "the truncation keeps the informative head");
    }

    @Test
    void noMatchIsAnHonestEmptyResult() {
        ScanResult result = QEErrorSignatureCatalog.scanText(
                "program ran fine\nno curated needle here\n");
        assertTrue(result.isEmpty(), "empty hit list is honest, not 'healthy'");
        assertEquals(2, result.getLineCount());
        assertEquals(0L, result.totalMatches("scf-not-converged"));
        assertEquals(0, QEErrorSignatureCatalog.scanText(null).getLineCount());
    }
}
