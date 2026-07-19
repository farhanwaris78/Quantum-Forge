package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import quantumforge.project.property.ProjectProperty;

class QEThermoPwEosParserTest {

    @Test
    void testParserProcessesBirchMurnaghanEquationOfState() throws IOException {
        File tempFile = File.createTempFile("thermopw-eos", ".log");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("     Equation of state: Birch-Murnaghan\n");
            writer.write("     V0 =    140.0000 A^3,  B0 =    130.0000 GPa,  B'0 =      4.0000,  E0 =  -120.0000 Ry\n");
        }

        ProjectProperty property = new ProjectProperty();
        QEThermoPwEosParser parser = new QEThermoPwEosParser(property);
        parser.parse(tempFile);

        assertTrue(parser.isEosParsed());
        assertEquals(140.0, parser.getEquilibriumVolume(), 1e-6);
        assertEquals(130.0, parser.getBulkModulus(), 1e-6);
        assertEquals(4.0, parser.getBulkModulusDerivative(), 1e-6);
        assertEquals(-120.0, parser.getMinimumEnergy(), 1e-6);

        QEThermoPwEosParser.EosResult result = parser.getResult();
        assertNotNull(result);
        assertTrue(result.isSuccess());

        // At V = V0 (equilibrium volume), energy should equal E0 (-120.0 Ry) exactly
        assertEquals(-120.0, result.evaluateEnergyRy(140.0), 1e-6);

        // At slightly larger volume V = 145.0, energy must be strictly greater than E0
        // because E0 is the absolute minimum of the Birch-Murnaghan potential!
        double energyLarger = result.evaluateEnergyRy(145.0);
        assertTrue(energyLarger > -120.0, "Energy at non-equilibrium volume must exceed minimum energy E0");
    }

    @Test
    void convertsBohrCubedAndFortranExponentBeforeBuildingCurve() throws IOException {
        File tempFile = File.createTempFile("thermopw-bohr", ".log");
        tempFile.deleteOnExit();
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("V0 = 1000.0 bohr^3, B0 = 1.300D+02 GPa, B'0 = 4.0, E0 = -1.200D+02 Ry\\n");
        }
        QEThermoPwEosParser parser = new QEThermoPwEosParser(new ProjectProperty());
        parser.parse(tempFile);
        assertTrue(parser.isEosParsed());
        double expectedAng3 = 1000.0 * Math.pow(0.52917720859, 3);
        assertEquals(expectedAng3, parser.getEquilibriumVolume(), 1e-8);
        assertEquals(130.0, parser.getBulkModulus(), 1e-8);
        assertEquals(-120.0, parser.getResult().evaluateEnergyRy(expectedAng3), 1e-8);
    }
}
