package quantumforge.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QEPpChargePotentialBuilderTest {

    @Test
    void testPotentialBuilderGeneratesValidChargeDensityStructure() {
        // prefix="silicon", outdir="./scratch", isPotential=false (charge density), outputFilename="density.cube"
        QEPpChargePotentialBuilder builder = new QEPpChargePotentialBuilder(
            "silicon", "./scratch", false, "density.cube"
        );

        assertNotNull(builder);
        assertEquals("silicon", builder.getPrefix());
        assertEquals("./scratch", builder.getOutdir());
        assertTrue(!builder.isPotential());
        assertEquals("density.cube", builder.getOutputFilename());

        String input = builder.generateInput();
        assertNotNull(input);

        // Verify key pp.x parameters
        assertTrue(input.contains("&inputpp"));
        assertTrue(input.contains("prefix = 'silicon'"));
        assertTrue(input.contains("outdir = './scratch'"));
        assertTrue(input.contains("plot_num = 0")); // 0 = charge density

        // Verify plot settings
        assertTrue(input.contains("&plot"));
        assertTrue(input.contains("output_format = 6")); // Gaussian Cube
        assertTrue(input.contains("fileout = 'density.cube'"));
    }

    @Test
    void testPotentialBuilderGeneratesValidElectrostaticPotentialStructure() {
        // prefix="silicon", outdir="./scratch", isPotential=true (electrostatic potential), outputFilename="potential.cube"
        QEPpChargePotentialBuilder builder = new QEPpChargePotentialBuilder(
            "silicon", "./scratch", true, "potential.cube"
        );

        assertNotNull(builder);
        assertTrue(builder.isPotential());
        assertEquals("potential.cube", builder.getOutputFilename());

        String input = builder.generateInput();
        assertNotNull(input);

        // Verify plot_num = 11 for electrostatic potentials
        assertTrue(input.contains("plot_num = 11"));
        assertTrue(input.contains("fileout = 'potential.cube'"));
    }
}
