/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.operation.OperationResult;

class NebPathAuditTest {

    @TempDir
    Path tempDir;

    private static String frame(String... rows) {
        StringBuilder frame = new StringBuilder();
        frame.append(rows.length).append('\n');
        frame.append("comment line - author owned\n");
        for (String row : rows) {
            frame.append(row).append('\n');
        }
        return frame.toString();
    }

    @Test
    void evenLadderMeasuresExactly() throws IOException {
        Path file = this.tempDir.resolve("neb_even.xyz");
        Files.writeString(file,
                frame("H 0.0 0.0 0.0", "O 1.0 0.0 0.0")
                        + frame("H 0.25 0.25 0.25", "O 1.25 0.25 0.25")
                        + frame("H 0.5 0.5 0.5", "O 1.5 0.5 0.5"));
        OperationResult<NebPathAudit.Audit> result = NebPathAudit.audit(file);
        assertTrue(result.isSuccess(), result.toString());
        NebPathAudit.Audit audit = result.getValue().orElseThrow();
        assertEquals(3, audit.getFrames());
        assertEquals(2, audit.getAtomsPerFrame());
        // per-atom displacement vector magnitude = sqrt(3 * 0.0625) = 0.4330127018922193 (H/O equal),
        // RMSD = sqrt(sum(|d_i|^2)/N) = 0.4330127018922193 (verified in python)
        for (NebPathAudit.PairMetrics pair : audit.getPairs()) {
            assertEquals(0.4330127018922193, pair.getRmsd(), 1e-12);
            assertEquals(0.4330127018922193, pair.getMaxDisp(), 1e-12);
        }
        assertEquals(1.0, audit.spacingRatio(), 1e-12,
                "an even ladder has ratio exactly 1 - the 1.5 rule is an owned BOUND");
        assertEquals(0.8660254037844386, audit.getTotalLength(), 1e-12);
        assertTrue(audit.getDuplicatePairs().isEmpty());
        assertFalse(audit.hasCoincidentEndpoints());
    }

    @Test
    void duplicatesAndUnevenSpacingAreNamedNotHidden() throws IOException {
        Path file = this.tempDir.resolve("neb_path.xyz");
        // Frame 2 equals frame 1 (duplicated image); frame 3 jumps far.
        Files.writeString(file,
                frame("Cu 0.0 0.0 0.0")
                        + frame("Cu 0.0 0.0 0.0")
                        + frame("Cu 0.0 0.0 1.0"));
        NebPathAudit.Audit audit = NebPathAudit.audit(file).getValue().orElseThrow();
        assertEquals(List.of(0), audit.getDuplicatePairs(),
                "the duplicated image pair (1->2) is NAMED by index, never averaged away");
        assertEquals(0.0, audit.getMinRmsd(), 1e-15);
        assertEquals(1.0, audit.getMaxRmsd(), 1e-15);
        // best = smallest-RMSD pair (the duplicate 0->1), worst = largest (1->2)
        assertEquals(0, audit.getBestPairFrom());
        assertEquals(1, audit.getWorstPairFrom());
        assertTrue(Double.isInfinite(audit.spacingRatio()),
                "a zero-RMSD pair makes the ratio honestly infinite");
        assertFalse(audit.hasCoincidentEndpoints());
        assertEquals(1.0, audit.getEndpointMaxDisp(), 1e-15);
    }

    @Test
    void coincidentEndpointsReportedButLegal() throws IOException {
        Path file = this.tempDir.resolve("neb_ring.path");
        Files.writeString(file,
                frame("Li 0.0 0.0 0.0")
                        + frame("Li 0.5 0.0 0.0")
                        + frame("Li 1.0e-9 0.0 0.0"));
        NebPathAudit.Audit audit = NebPathAudit.audit(file).getValue().orElseThrow();
        assertTrue(audit.hasCoincidentEndpoints(),
                "ring/closed paths are legitimate - REPORTED, never refused");
        assertEquals(2, audit.getPairs().size());
        assertEquals(0.5, audit.getPairs().get(0).getRmsd(), 1e-9);
        assertEquals(0.5, audit.getPairs().get(1).getRmsd(), 1e-6);
        assertEquals(1.0, audit.spacingRatio(), 1e-3);
    }

    @Test
    void grammarAndConsistencyRefuseClosed() throws IOException {
        Path file = this.tempDir.resolve("bad.xyz");
        Files.writeString(file, "abc\ncomment\nH 0 0 0\n");
        assertEquals("NEB_SHAPE", NebPathAudit.audit(file).getCode(),
                "non-numeric atom count refused with line context");

        Files.writeString(file, "2\ncomment\nH 0 0 0\n");
        assertEquals("NEB_SHAPE", NebPathAudit.audit(file).getCode(),
                "truncated frame refused, never padded");

        Files.writeString(file, frame("H 0 0 nan"));
        assertEquals("NEB_SHAPE", NebPathAudit.audit(file).getCode(),
                "non-finite coordinates refuse - and one frame is not a ladder anyway");

        Files.writeString(file, frame("H 0 0 0"));
        assertEquals("NEB_SHAPE", NebPathAudit.audit(file).getCode(),
                "a single frame is a structure, not a ladder");

        Files.writeString(file, frame("H 0 0 0", "O 1 0 0") + frame("H 0 0 0.5"));
        assertEquals("NEB_SHAPE", NebPathAudit.audit(file).getCode(),
                "atom-count mismatch across frames refused");

        Files.writeString(file, frame("H 0 0 0", "O 1 0 0")
                + frame("O 1 0 0.5", "H 0 0 0.5"));
        assertEquals("NEB_SHAPE", NebPathAudit.audit(file).getCode(),
                "reordered atoms between images refused - displacement would lie");

        Files.writeString(file, frame("H 0 0 1e999") + frame("H 0 0 0"));
        assertEquals("NEB_SHAPE", NebPathAudit.audit(file).getCode(),
                "overflow-to-infinity coordinates are non-finite and refuse");

        assertEquals("NEB_FILE", NebPathAudit.audit(
                this.tempDir.resolve("nope.xyz")).getCode());
        assertEquals("NEB_FILE", NebPathAudit.audit(null).getCode());
    }
}
