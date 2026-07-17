package quantumforge.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import quantumforge.atoms.model.Cell;
import quantumforge.com.math.Matrix3D;
import quantumforge.input.QEInput;
import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValueBase;

class QEScratchStoragePolicyTest {

    private static class MockQEInput extends QEInput {
        @Override
        protected void setupNamelists(QEInputReader reader) throws IOException {
            this.setupNamelist(NAMELIST_CONTROL, reader);
            this.setupNamelist(NAMELIST_SYSTEM, reader);
        }

        @Override
        protected void setupCards(QEInputReader reader) throws IOException {
            // No-op
        }

        @Override
        protected quantumforge.input.correcter.QEInputCorrecter createInputCorrector() {
            return null;
        }

        @Override
        public void reload() {}

        @Override
        public QEInput copy() {
            return null;
        }

        @Override
        public boolean isValid() {
            return true;
        }
    }

    @Test
    void testEstimateScratchSizeGeneratesReasonableValues() {
        QEScratchStoragePolicy policy = new QEScratchStoragePolicy();
        Cell cell = new Cell(Matrix3D.unit(10.0)); // 1000 Bohr^3 volume
        MockQEInput input = new MockQEInput();

        QENamelist system = input.getNamelist(QEInput.NAMELIST_SYSTEM);
        system.setValue(QEValueBase.getInstance("ecutwfc", "40.0"));
        system.setValue(QEValueBase.getInstance("nbnd", "50"));

        long sizeBytes = policy.estimateScratchSize(cell, input);
        assertTrue(sizeBytes > 1024 * 1024, "Estimated scratch size must be physically positive and reasonable");
    }

    @Test
    void testPerformCleanupSafelyDeletesWfcAndIgkFiles() throws IOException {
        Path tempDir = Files.createTempDirectory("qe-scratch-test");
        tempDir.toFile().deleteOnExit();

        Path wfc = tempDir.resolve("silicon.wfc");
        Path igk = tempDir.resolve("silicon.igk");
        Path log = tempDir.resolve("silicon.out");

        Files.createFile(wfc);
        Files.createFile(igk);
        Files.createFile(log);

        assertTrue(Files.exists(wfc));
        assertTrue(Files.exists(igk));
        assertTrue(Files.exists(log));

        QEScratchStoragePolicy policy = new QEScratchStoragePolicy(
            tempDir, QEScratchStoragePolicy.RetentionPolicy.CLEAN_WFC_ONLY, 1024L
        );

        int deleted = policy.performCleanup(tempDir);
        assertEquals(2, deleted, "Should clean up exactly the .wfc and .igk files");

        assertTrue(Files.exists(log), "Clean-up must preserve calculation output logs");
        assertTrue(!Files.exists(wfc), "Clean-up must delete transient wavefunctions");
        assertTrue(!Files.exists(igk), "Clean-up must delete transient G-vector lists");
    }
}
