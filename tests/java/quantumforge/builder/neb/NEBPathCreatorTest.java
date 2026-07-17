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

    @Test
    void testDisplacementValidationWarnings() throws Exception {
        Cell a = new Cell(Matrix3D.unit(10.0));
        assertTrue(a.addAtom(new Atom("H", 0.0, 0.0, 0.0)));
        
        // Final position shifts by 2.0 Angstroms (exceeding 1.5 A threshold)
        Cell b = new Cell(Matrix3D.unit(10.0));
        assertTrue(b.addAtom(new Atom("H", 2.0, 0.0, 0.0)));

        OperationResult<NEBPathCreator.NebPath> result = NEBPathCreator.createPath(a, b, 3);
        assertTrue(result.isSuccess());
        NEBPathCreator.NebPath path = result.getValue().orElseThrow();

        // 3 images -> step displacement = 1.0 Angstrom (under 1.5 threshold, should be marked optimized)
        boolean hasOptimized = false;
        for (String msg : path.getDiagnostics()) {
            if (msg.contains("displacements optimized")) {
                hasOptimized = true;
            }
        }
        assertTrue(hasOptimized, "A step displacement of 1.0 A should be marked optimized");

        // Now shift by 4.0 Angstroms (step displacement = 2.0 A, exceeding 1.5 limit)
        Cell c = new Cell(Matrix3D.unit(10.0));
        assertTrue(c.addAtom(new Atom("H", 4.0, 0.0, 0.0)));

        OperationResult<NEBPathCreator.NebPath> result2 = NEBPathCreator.createPath(a, c, 3);
        assertTrue(result2.isSuccess());
        NEBPathCreator.NebPath path2 = result2.getValue().orElseThrow();

        boolean hasWarning = false;
        for (String msg : path2.getDiagnostics()) {
            if (msg.contains("Warning") && msg.contains("exceeding 1.5 A limit")) {
                hasWarning = true;
            }
        }
        assertTrue(hasWarning, "A step displacement of 2.0 A must trigger a coarse displacement warning");
    }
}
