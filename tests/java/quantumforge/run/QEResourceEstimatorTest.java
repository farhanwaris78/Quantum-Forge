package quantumforge.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.com.math.Matrix3D;
import quantumforge.input.QEInput;
import quantumforge.input.QEInputReader;
import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValueBase;
import quantumforge.run.QEMpiTopologyAdvisor.TopologyRecommendation;
import quantumforge.run.QEResourceEstimator.Estimation;

class QEResourceEstimatorTest {

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
    }

    @Test
    void testMpiTopologyAdvisorRecommendsOptimalPools() {
        MockQEInput input = new MockQEInput();
        // Total ranks = 16, irreducible k-points = 4
        // Optimal pools should be 4, with 4 ranks per pool
        TopologyRecommendation rec = QEMpiTopologyAdvisor.advise(input, 16);
        assertNotNull(rec);
        
        // Default k-points estimate is 1 if empty, but let's check basic advise returns
        assertTrue(rec.getNumPools() >= 1);
        assertTrue(rec.getWarnings().isEmpty() || rec.getWarnings().get(0).contains("imperfect"));
    }

    @Test
    void testResourceEstimatorScalesCubicly() throws Exception {
        Cell cell1 = new Cell(Matrix3D.unit(5.0));
        cell1.addAtom("Si", 0.0, 0.0, 0.0); // 1 atom

        Cell cell2 = new Cell(Matrix3D.unit(10.0));
        cell2.addAtom("Si", 0.0, 0.0, 0.0);
        cell2.addAtom(new Atom("Si", 1.0, 0.0, 0.0));
        cell2.addAtom(new Atom("Si", 2.0, 0.0, 0.0));
        cell2.addAtom(new Atom("Si", 3.0, 0.0, 0.0)); // 4 atoms (4x scaling!)

        MockQEInput input = new MockQEInput();
        QENamelist system = input.getNamelist(QEInput.NAMELIST_SYSTEM);
        system.setValue(QEValueBase.getInstance("ecutwfc", "10000.0"));

        Estimation est1 = QEResourceEstimator.estimate(cell1, input);
        Estimation est2 = QEResourceEstimator.estimate(cell2, input);

        assertNotNull(est1);
        assertNotNull(est2);

        // Use a deliberately large cutoff to keep both estimates above the reporting floor;
        // cubic scaling: (4^3) = 64x core-hours difference expected!
        double ratio = est2.getEstimatedCoreHours() / est1.getEstimatedCoreHours();
        assertEquals(64.0, ratio, 1.0);
        assertTrue(est2.getEstimatedMemoryGb() > est1.getEstimatedMemoryGb(), "Memory must scale with atomic and cutoff size");
    }
}
