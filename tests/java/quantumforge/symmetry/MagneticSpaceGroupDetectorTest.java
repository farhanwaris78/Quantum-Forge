package quantumforge.symmetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.com.math.Matrix3D;
import quantumforge.symmetry.MagneticSpaceGroupDetector.MagneticOrder;
import quantumforge.symmetry.MagneticSpaceGroupDetector.MsgReport;

class MagneticSpaceGroupDetectorTest {

    @Test
    void testMagneticSymmetryDetectorIdentifiesFerromagneticOrder() throws Exception {
        Cell cell = new Cell(Matrix3D.unit(5.0));
        Atom a1 = new Atom("Fe", 0.0, 0.0, 0.0);
        a1.setProperty("magnetic_moment_z", 2.2); // spin up
        Atom a2 = new Atom("Fe", 2.5, 2.5, 2.5);
        a2.setProperty("magnetic_moment_z", 2.2); // spin up

        cell.addAtom(a1);
        cell.addAtom(a2);

        MagneticSpaceGroupDetector detector = new MagneticSpaceGroupDetector(cell);
        MsgReport report = detector.analyzeMagneticSymmetry();

        assertNotNull(report);
        assertEquals(MagneticOrder.FERROMAGNETIC, report.getOrder(), "Symmetric spin-up must be Ferromagnetic");
        assertEquals(4.4, report.getNetMoment(), 1e-6);
        assertEquals(4.4, report.getAbsoluteMomentSum(), 1e-6);
        assertEquals("Type I", report.getShubnikovTypeGuess());
        assertTrue(detector.detectMSG().contains("Ferromagnetic"));
    }

    @Test
    void testMagneticSymmetryDetectorIdentifiesAntiferromagneticOrder() throws Exception {
        Cell cell = new Cell(Matrix3D.unit(5.0));
        Atom a1 = new Atom("Fe", 0.0, 0.0, 0.0);
        a1.setProperty("magnetic_moment_z", 2.2); // spin up
        Atom a2 = new Atom("Fe", 2.5, 2.5, 2.5);
        a2.setProperty("magnetic_moment_z", -2.2); // spin down (antiferromagnetic!)

        cell.addAtom(a1);
        cell.addAtom(a2);

        MagneticSpaceGroupDetector detector = new MagneticSpaceGroupDetector(cell);
        MsgReport report = detector.analyzeMagneticSymmetry();

        assertNotNull(report);
        assertEquals(MagneticOrder.ANTIFERROMAGNETIC, report.getOrder(), "Cancelling spins must be Antiferromagnetic");
        assertEquals(0.0, report.getNetMoment(), 1e-6, "Net moment should sum to 0.0 for AFM");
        assertEquals(4.4, report.getAbsoluteMomentSum(), 1e-6);
        assertEquals("Type IV", report.getShubnikovTypeGuess(), "AFM should guess Type IV black-and-white translation group");
    }
}
