package quantumforge.app.project.editor.result.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.com.math.Matrix3D;

class GeometryMeasurerTest {

    @Test
    void testStandardDistanceWithoutCellIsCorrect() {
        GeometryMeasurer measurer = new GeometryMeasurer();
        Atom a = new Atom("Si", 0.0, 0.0, 0.0);
        Atom b = new Atom("Si", 3.0, 4.0, 0.0);

        measurer.setAtomA(a);
        measurer.setAtomB(b);
        assertTrue(measurer.calculate());

        // Euclidean distance = sqrt(3^2 + 4^2) = 5.0
        assertEquals(5.0, measurer.getBondLengthAB(), 1e-6);
    }

    @Test
    void testMinimumImageDistanceWithCubicCellIsCorrect() throws Exception {
        GeometryMeasurer measurer = new GeometryMeasurer();
        Cell cell = new Cell(Matrix3D.unit(10.0)); // 10x10x10 cubic cell
        
        // Atom A is at (0.5, 0, 0)
        Atom a = new Atom("Si", 0.5, 0.0, 0.0);
        // Atom B is at (9.5, 0, 0)
        Atom b = new Atom("Si", 9.5, 0.0, 0.0);

        measurer.setAtomA(a);
        measurer.setAtomB(b);
        measurer.setCell(cell);
        assertTrue(measurer.calculate());

        // Bare Cartesian distance would be 9.0 Angstroms.
        // Under minimum image convention, they are separated by the boundary,
        // so distance is exactly 1.0 Angstrom!
        assertEquals(1.0, measurer.getBondLengthAB(), 1e-6);
    }

    @Test
    void testMinimumImageAngleWithCubicCellIsCorrect() throws Exception {
        GeometryMeasurer measurer = new GeometryMeasurer();
        Cell cell = new Cell(Matrix3D.unit(10.0)); // 10x10x10 cubic cell

        // Atom A is at (0.5, 0, 0)
        Atom a = new Atom("Si", 0.5, 0.0, 0.0);
        // Atom B (vertex) is at (0.0, 0.0, 0.0)
        Atom b = new Atom("Si", 0.0, 0.0, 0.0);
        // Atom C is at (9.0, 0.0, 0.0) -> across boundary minimum image vector is (-1.0, 0.0, 0.0)
        Atom c = new Atom("Si", 9.0, 0.0, 0.0);

        measurer.setAtomA(a);
        measurer.setAtomB(b);
        measurer.setAtomC(c);
        measurer.setCell(cell);
        assertTrue(measurer.calculate());

        // Vector BA = a - b = (0.5, 0.0, 0.0)
        // Vector BC = c - b = (-1.0, 0.0, 0.0) after minimum image!
        // So the angle must be exactly 180 degrees!
        assertEquals(180.0, measurer.getBondAngleABC(), 1e-6);
    }
}
