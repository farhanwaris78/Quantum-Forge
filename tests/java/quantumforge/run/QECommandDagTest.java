package quantumforge.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class QECommandDagTest {

    @Test
    void scfHasSingleStage() {
        QECommandDag dag = QECommandDag.build(RunningType.SCF, "espresso.in", 1);
        assertEquals(1, dag.size());
        assertEquals(QECommandStage.Kind.PW_SCF, dag.getStages().get(0).getKind());
        assertTrue(dag.getStages().get(0).getProduces().contains("charge-density"));
    }

    @Test
    void dosPipelineOrderAndDependencies() {
        QECommandDag dag = QECommandDag.build(RunningType.DOS, "espresso.in", 2);
        assertEquals(4, dag.size());
        assertEquals("scf", dag.getStages().get(0).getId());
        assertEquals("nscf", dag.getStages().get(1).getId());
        assertEquals("dos", dag.getStages().get(2).getId());
        assertEquals("projwfc", dag.getStages().get(3).getId());
        assertTrue(dag.getStages().get(1).getRequires().contains("charge-density"));
        assertTrue(dag.getStages().get(3).isOptional());
    }

    @Test
    void bandPipelineHasOptionalSpinDown() {
        QECommandDag dag = QECommandDag.build(RunningType.BAND, "espresso.in", 1);
        assertEquals(4, dag.size());
        assertEquals(QECommandStage.Kind.BANDS_X, dag.getStages().get(3).getKind());
        assertTrue(dag.getStages().get(3).isOptional());
    }

    @Test
    void remainingSkipsCompletedArtifacts() {
        QECommandDag dag = QECommandDag.build(RunningType.DOS, "espresso.in", 1);
        List<QECommandStage> remaining = dag.remaining(Set.of(
                "charge-density", "wavefunctions", "nscf-wavefunctions"));
        assertFalse(remaining.isEmpty());
        assertEquals("dos", remaining.get(0).getId());
    }

    @Test
    void convergeThrowsUntilSweepExists() {
        assertThrows(UnsupportedOperationException.class,
                () -> QECommandDag.build(RunningType.CONVERGE, "espresso.in", 1));
    }

    @Test
    void runningTypeExposesDagHelper() {
        QECommandDag dag = RunningType.SCF.getCommandDag("espresso.in", 4);
        assertEquals(RunningType.SCF, dag.getRunningType());
        assertTrue(dag.describe().contains("PW_SCF"));
    }
}
