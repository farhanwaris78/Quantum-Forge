/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.export.ELateTensorDraft.TensorBlock;
import quantumforge.operation.OperationResult;

class ELateTensorDraftTest {

    private static final String CUBIC_KBAR = "  Elastic Constant Matrix (kbar)\n"
            + "  5000 1000 1000    0    0    0\n"
            + "  1000 5000 1000    0    0    0\n"
            + "  1000 1000 5000    0    0    0\n"
            + "     0    0    0 2000    0    0\n"
            + "     0    0    0    0 2000    0\n"
            + "     0    0    0    0    0 2000\n";

    @TempDir
    Path tempDir;

    private Path write(String name, String content) throws IOException {
        Path path = this.tempDir.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    @Test
    void readsSymmetricBlockWithAuditValues() throws IOException {
        OperationResult<TensorBlock> result =
                ELateTensorDraft.readTensor(write("elastic.out", CUBIC_KBAR));
        assertTrue(result.isSuccess(), result.toString());
        TensorBlock block = result.getValue().orElseThrow();
        assertEquals(0.0, block.getMaxAsymmetry(), 0.0);
        assertEquals(5000.0, block.getMaxAbs(), 0.0);
        assertEquals(1000.0, block.getCij()[0][1], 0.0);
    }

    @Test
    void printLevelAsymmetryIsSymmetrizedAndAudited() throws IOException {
        String slight = CUBIC_KBAR.replace("  1000 5000 1000", "  1000.01 5000 1000");
        OperationResult<TensorBlock> result =
                ELateTensorDraft.readTensor(write("elastic2.out", slight));
        assertTrue(result.isSuccess(), result.toString());
        TensorBlock block = result.getValue().orElseThrow();
        assertEquals(0.01, block.getMaxAsymmetry(), 1e-12,
                "the audit reports the print-level asymmetry");
        assertEquals(1000.005, block.getCij()[0][1], 1e-12, "(C+C^T)/2 symmetrization");
        assertEquals(1000.005, block.getCij()[1][0], 1e-12);
    }

    @Test
    void draftCarriesConventionProvenanceAndRequiredEditGuard() throws IOException {
        TensorBlock block =
                ELateTensorDraft.readTensor(write("elastic.out", CUBIC_KBAR))
                        .getValue().orElseThrow();
        String stableDraft = ELateTensorDraft.draft(block, "elastic.out", true,
                "all leading minors positive");
        assertTrue(stableDraft.contains("Voigt stiffness order 1=xx 2=yy 3=zz"),
                stableDraft);
        assertTrue(stableDraft.contains("CONVERT_TO_GPA = False"), stableDraft);
        assertTrue(stableDraft.contains("Source: elastic.out"), stableDraft);
        assertTrue(stableDraft.contains("STABLE"), stableDraft);
        assertTrue(stableDraft.contains("[5000.0, 1000.0, 1000.0"), stableDraft);
        assertTrue(stableDraft.contains("progs.coudert.name/elate"), stableDraft);
        assertTrue(!stableDraft.contains("REVIEW-REQUIRED"), stableDraft);

        String unstableDraft = ELateTensorDraft.draft(block, "elastic.out", false,
                "first leading minor negative");
        assertTrue(unstableDraft.contains("UNSTABLE"), unstableDraft);
        assertTrue(unstableDraft.contains("REVIEW-REQUIRED"), unstableDraft);
    }

    @Test
    void malformedBlocksFailClosedWithCodes() throws IOException {
        assertEquals("ELATE_BLOCK", ELateTensorDraft.readTensor(
                write("none.out", "scf output only\n")).getCode());
        assertEquals("ELATE_SYNTAX", ELateTensorDraft.readTensor(
                write("short.out", "Elastic Constant Matrix\n1 2 3\n")).getCode());
        assertEquals("ELATE_VALUE", ELateTensorDraft.readTensor(
                write("bad.out", "Elastic Constant Matrix (kbar)\n1 x 1 0 0 0\n"
                        + "1 1 1 0 0 0\n1 1 1 0 0 0\n0 0 0 1 0 0\n0 0 0 0 1 0\n"
                        + "0 0 0 0 0 1\n")).getCode());
        assertEquals("ELATE_BLOCK", ELateTensorDraft.readTensor(
                write("five.out", "Elastic Constant Matrix (kbar)\n"
                        + "1 1 1 0 0 0\n1 1 1 0 0 0\n1 1 1 0 0 0\n0 0 0 1 0 0\n"
                        + "0 0 0 0 1 0\n")).getCode(), "5 rows cannot become 6");
        assertEquals("ELATE_VALUE", ELateTensorDraft.readTensor(
                write("zero.out", "Elastic Constant Matrix (kbar)\n"
                        + "0 0 0 0 0 0\n0 0 0 0 0 0\n0 0 0 0 0 0\n0 0 0 0 0 0\n"
                        + "0 0 0 0 0 0\n0 0 0 0 0 0\n")).getCode(),
                "an all-zero tensor is a placeholder, not data");
        String asymmetric = "Elastic Constant Matrix (kbar)\n"
                + "5000 1000 1000 0 0 0\n"
                + "1200 5000 1000 0 0 0\n"
                + "1000 1000 5000 0 0 0\n"
                + "0 0 0 2000 0 0\n0 0 0 0 2000 0\n0 0 0 0 0 2000\n";
        assertEquals("ELATE_ASYMMETRY", ELateTensorDraft.readTensor(
                write("asym.out", asymmetric)).getCode(),
                "200 > 1e-4 * 5000 must refuse the non-Voigt file");
        assertEquals("ELATE_IO", ELateTensorDraft.readTensor(
                this.tempDir.resolve("missing.out")).getCode());
    }
}
