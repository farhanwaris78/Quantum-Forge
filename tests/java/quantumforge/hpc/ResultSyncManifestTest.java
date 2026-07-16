package quantumforge.hpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.run.RunningType;

class ResultSyncManifestTest {

    @Test
    void scfManifestHasRequiredLog() {
        ResultSyncManifest m = ResultSyncManifest.forWorkflow(RunningType.SCF, "espresso");
        assertTrue(m.requiredPaths().stream().anyMatch(p -> p.contains("log")));
        assertFalse(m.allPaths(false).contains("espresso.save/charge-density.dat"));
        assertTrue(m.allPaths(true).stream().anyMatch(p -> p.contains("charge-density")));
    }

    @Test
    void rejectsTraversalPaths() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResultSyncManifest.Entry("../etc/passwd", ResultSyncManifest.Priority.REQUIRED));
    }
}
