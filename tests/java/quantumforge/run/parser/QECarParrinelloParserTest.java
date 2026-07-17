package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import quantumforge.project.property.ProjectProperty;

class QECarParrinelloParserTest {

    @Test
    void testParserProcessesConservedCpMdTrajectory() throws IOException {
        File tempFile = File.createTempFile("cp-md-traj", ".log");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("     nfi=     10, ekinc=   0.00012, ekinh=   0.01245, etot=  -12.78452\n");
            writer.write("     nfi=     20, ekinc=   0.00015, ekinh=   0.01423, etot=  -12.78455\n");
            writer.write("     nfi=     30, ekinc=   0.00013, ekinh=   0.01524, etot=  -12.78454\n");
        }

        ProjectProperty property = new ProjectProperty();
        QECarParrinelloParser parser = new QECarParrinelloParser(property);
        parser.parse(tempFile);

        List<QECarParrinelloParser.CpMdFrame> trajectory = parser.getTrajectory();
        assertNotNull(trajectory);
        assertEquals(3, trajectory.size(), "Should parse exactly 3 CP frames");

        assertEquals(10, trajectory.get(0).getStep());
        assertEquals(0.00012, trajectory.get(0).getEkincAu(), 1e-6);
        assertEquals(0.01245, trajectory.get(0).getEkinhAu(), 1e-6);
        assertEquals(-12.78452, trajectory.get(0).getEtotAu(), 1e-6);

        assertTrue(parser.isAdiabatic(), "Conserved run should satisfy adiabaticity limits (max ekinc 0.00015 < 0.005)");
        assertTrue(parser.getDiagnostics().get(0).contains("satisfied"));
    }

    @Test
    void testParserDetectsAdiabaticityViolationForHeatedElectrons() throws IOException {
        File tempFile = File.createTempFile("cp-md-traj-heated", ".log");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("     nfi=     10, ekinc=   0.00020, ekinh=   0.01245, etot=  -12.78452\n");
            // Fictitious electronic energy grows to 0.00650 a.u. (exceeding 0.005)
            writer.write("     nfi=     20, ekinc=   0.00650, ekinh=   0.01423, etot=  -12.78455\n");
        }

        ProjectProperty property = new ProjectProperty();
        QECarParrinelloParser parser = new QECarParrinelloParser(property);
        parser.parse(tempFile);

        assertFalse(parser.isAdiabatic(), "Heated run should fail adiabaticity constraint checks");
        assertTrue(parser.getDiagnostics().get(0).contains("exceeding"));
    }
}
