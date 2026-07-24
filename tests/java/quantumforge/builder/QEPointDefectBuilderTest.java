package quantumforge.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.com.math.Matrix3D;

class QEPointDefectBuilderTest {

    @Test
    void testDefectBuilderCreatesVacancyAndSubstitution() throws Exception {
        Cell cell = new Cell(Matrix3D.unit(5.0)); // 5x5x5 cell
        cell.addAtom("Si", 0.0, 0.0, 0.0);       // atom index 0
        cell.addAtom(new Atom("Si", 1.25, 1.25, 1.25));   // atom index 1

        QEPointDefectBuilder builder = new QEPointDefectBuilder(cell);

        // Turn atom 0 into a vacancy with -1 charge state
        builder.addVacancy(0, -1);
        // Turn atom 1 into a carbon substitution with +1 charge state
        builder.addSubstitution(1, "C", 1);

        Cell defectCell = builder.build();
        assertNotNull(defectCell);

        // Vacancy deleted atom 0, and substitution modified atom 1. So 1 atom left (Carbon).
        assertEquals(1, defectCell.listAtoms(true).length);
        assertEquals("C", defectCell.listAtoms(true)[0].getName());

        assertEquals(2, builder.getDefects().size());
        assertEquals(QEPointDefectBuilder.DefectType.VACANCY, builder.getDefects().get(0).getType());
        assertEquals(-1, builder.getDefects().get(0).getChargeState());
    }

    @Test
    void testImageSeparationDistanceCalculation() throws Exception {
        // Cubic unit cell size = 8.0 Angstroms
        Cell cell1 = new Cell(Matrix3D.unit(8.0));
        QEPointDefectBuilder builder1 = new QEPointDefectBuilder(cell1);

        // Shortest periodic vector translation is along the main axis: 8.0 Angstroms
        assertEquals(8.0, builder1.calculateImageSeparation(), 1e-6);
        assertFalse(builder1.isImageSeparationSafe(10.0), "8.0 A should be warned as too close to prevent image interactions");

        // Cubic unit cell size = 12.0 Angstroms
        Cell cell2 = new Cell(Matrix3D.unit(12.0));
        QEPointDefectBuilder builder2 = new QEPointDefectBuilder(cell2);

        assertEquals(12.0, builder2.calculateImageSeparation(), 1e-6);
        assertTrue(builder2.isImageSeparationSafe(10.0), "12.0 A is safe (> 10.0 A)");
    }
}
