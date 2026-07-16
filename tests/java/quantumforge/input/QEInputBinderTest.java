package quantumforge.input;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.event.AtomEvent;

class QEInputBinderTest {
    @Test
    void ignoresAtomEventsWhoseInputIndexIsOutsideThePositionsCard() {
        QEGeometryInput input = new QEGeometryInput();
        QEInputBinder binder = new QEInputBinder(input);
        Atom atom = new Atom("Si", 0, 0, 0); // default INPUT_INDEX=0, but card is empty
        AtomEvent rename = new AtomEvent(atom);
        rename.setOldName("Si");
        rename.setName("Ge");

        assertDoesNotThrow(() -> binder.onAtomRenamed(rename));
    }
}
