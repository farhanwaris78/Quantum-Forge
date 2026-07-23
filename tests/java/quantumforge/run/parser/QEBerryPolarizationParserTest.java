package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import quantumforge.atoms.model.Cell;
import quantumforge.com.math.Matrix3D;
import quantumforge.project.property.ProjectProperty;

class QEBerryPolarizationParserTest {

    @Test
    void testParserProcessesIonicAndElectronicPolarization() throws IOException {
        File tempFile = File.createTempFile("pw-berry-polarization", ".log");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("     Ionic Polarization    =    1.54212 electrons * bohr\n");
            writer.write("     Electronic Polarization =   -0.51234 electrons * bohr\n");
        }

        ProjectProperty property = new ProjectProperty();
        QEBerryPolarizationParser parser = new QEBerryPolarizationParser(property);
        parser.parse(tempFile);

        assertEquals(1.54212, parser.getIonicPolarizationBohr(), 1e-6);
        assertEquals(-0.51234, parser.getElectronicPolarizationBohr(), 1e-6);
        assertEquals(1.02978, parser.getTotalPolarizationBohr(), 1e-6);
    }

    @Test
    void testCalculatePolarizationQuantumSIForCubicCell() throws Exception {
        QEBerryPolarizationParser parser = new QEBerryPolarizationParser(new ProjectProperty());
        Cell cell = new Cell(Matrix3D.unit(4.0)); // 4x4x4 Angstrom cubic cell (volume = 64)

        // P0 along x direction:
        // P0 = (e * 4.0 / 64) * 1e20 = e * 0.0625 * 1e20 = 1.602176634e-19 * 0.0625 * 1e20 = 1.00136039625 C/m^2
        double pQuantum = parser.calculatePolarizationQuantumSI(cell, 0);
        assertEquals(1.00136, pQuantum, 1e-4);
    }

    @Test
    void testUnwrapPolarizationResolvesModuloAmbiguity() {
        double pOld = 0.25;
        // The raw calculation output pNew is at -0.70, but adding +1.0 polarization quantum
        // places it at +0.30, which is much closer to the old value (representing a small physical change of +0.05).
        double pNew = -0.70;
        double pQuantum = 1.0;

        double unwrapped = QEBerryPolarizationParser.unwrapPolarization(pOld, pNew, pQuantum);
        assertEquals(0.30, unwrapped, 1e-6, "Branch unwrapping must minimize the physical polarization step");
    }
}
