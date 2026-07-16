package quantumforge.builder.neb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.com.math.Matrix3D;
import quantumforge.operation.OperationResult;

class NEBPathCreatorTest {

    @Test
    void createsOrderedPathWithCorrectImageCount() throws Exception {
        Cell a = new Cell(Matrix3D.unit(5.0));
        assertTrue(a.addAtom(new Atom("H", 0.0, 0.0, 0.0)));
        assertTrue(a.addAtom(new Atom("H", 1.0, 0.0, 0.0)));
        Cell b = new Cell(Matrix3D.unit(5.0));
        assertTrue(b.addAtom(new Atom("H", 0.0, 0.0, 0.0)));
        assertTrue(b.addAtom(new Atom("H", 2.0, 0.0, 0.0)));

        OperationResult<NEBPathCreator.NebPath> result = NEBPathCreator.createPath(a, b, 5);
        assertTrue(result.isSuccess(), result.toString());
        NEBPathCreator.NebPath path = result.getValue().orElseThrow();
        assertEquals(5, path.size());
        assertEquals(0.0, path.getFractions().get(0), 1e-12);
        assertEquals(1.0, path.getFractions().get(4), 1e-12);
        // midpoint second H should be near x=1.5
        assertEquals(1.5, path.getImages().get(2).listAtoms(true)[1].getX(), 1e-9);
    }

    @Test
    void rejectsAtomCountMismatchAndTooFewImages() throws Exception {
        Cell a = new Cell(Matrix3D.unit());
        assertTrue(a.addAtom(new Atom("H", 0, 0, 0)));
        Cell b = new Cell(Matrix3D.unit());
        assertTrue(b.addAtom(new Atom("H", 0, 0, 0)));
        assertTrue(b.addAtom(new Atom("H", 1, 0, 0)));
        assertTrue(NEBPathCreator.createPath(a, b, 5).getCode().contains("ATOM_COUNT")
                || !NEBPathCreator.createPath(a, b, 5).isSuccess());
        Cell c = new Cell(Matrix3D.unit());
        assertTrue(c.addAtom(new Atom("H", 0, 0, 0)));
        assertTrue(!NEBPathCreator.createPath(a, c, 2).isSuccess());
    }

    @Test
    void legacyWrapperStillWorks() throws Exception {
        Cell a = new Cell(Matrix3D.unit(4.0));
        assertTrue(a.addAtom(new Atom("C", 0, 0, 0)));
        Cell b = new Cell(Matrix3D.unit(4.0));
        assertTrue(b.addAtom(new Atom("C", 1, 0, 0)));
        assertEquals(4, NEBPathCreator.createInterpolatedPath(a, b, 4).size());
    }
}
