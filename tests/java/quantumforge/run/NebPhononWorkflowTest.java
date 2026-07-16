package quantumforge.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;


class NebPhononWorkflowTest {

    @Test
    void nebCommandListAndDag() {
        List<String[]> commands = RunningType.NEB.getCommandList("espresso.in", 2);
        assertEquals(2, commands.size());
        assertTrue(String.join(" ", commands.get(1)).contains("neb.x")
                || commands.get(1)[0].contains("neb"));

        QECommandDag dag = RunningType.NEB.getCommandDag("espresso.in", 2);
        assertEquals(2, dag.size());
        assertEquals(QECommandStage.Kind.NEB_X, dag.getStages().get(1).getKind());
        assertEquals("neb", dag.getStages().get(1).getId());
    }

    @Test
    void phononCommandListAndDagOrder() {
        List<String[]> commands = RunningType.PHONON.getCommandList("espresso.in", 1);
        assertEquals(4, commands.size());
        QECommandDag dag = RunningType.PHONON.getCommandDag("espresso.in", 1);
        assertEquals(4, dag.size());
        assertEquals("scf", dag.getStages().get(0).getId());
        assertEquals("ph", dag.getStages().get(1).getId());
        assertEquals("q2r", dag.getStages().get(2).getId());
        assertEquals("matdyn", dag.getStages().get(3).getId());
        assertTrue(dag.getStages().get(1).getRequires().contains("charge-density"));
        assertTrue(dag.getStages().get(3).getProduces().contains("phonon-dos")
                || dag.getStages().get(3).getProduces().contains("phonon-bands"));
    }

    @Test
    void runningTypeNamesAndStageListsForNullProjectAreSafe() {
        assertEquals("NEB", RunningType.NEB.name());
        assertEquals("PHONON", RunningType.PHONON.name());
        // null project => null log list (existing API contract)
        assertTrue(RunningType.NEB.getLogNameList(null) == null);
        assertTrue(RunningType.PHONON.getParserList(null) == null);
    }
}
