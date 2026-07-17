package quantumforge.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QEPpWavefunctionBuilderTest {

    @Test
    void testWavefunctionBuilderGeneratesValidInputStructure() {
        // prefix="silicon", outdir="./scratch", kpoint=2, band=4, spin=1 (spin-up), lsign=true (retains phase sign)
        QEPpWavefunctionBuilder builder = new QEPpWavefunctionBuilder(
            "silicon", "./scratch", 2, 4, 1, true, "psi_bonding.cube"
        );

        assertNotNull(builder);
        assertEquals("silicon", builder.getPrefix());
        assertEquals("./scratch", builder.getOutdir());
        assertEquals(2, builder.getKpointIndex());
        assertEquals(4, builder.getBandIndex());
        assertEquals(1, builder.getSpinComponent());
        assertTrue(builder.isLsign());
        assertEquals("psi_bonding.cube", builder.getOutputFilename());

        String input = builder.generateInput();
        assertNotNull(input);

        // Verify key pp.x parameters
        assertTrue(input.contains("&inputpp"));
        assertTrue(input.contains("prefix = 'silicon'"));
        assertTrue(input.contains("outdir = './scratch'"));
        assertTrue(input.contains("plot_num = 7"));
        assertTrue(input.contains("kpoint = 2"));
        assertTrue(input.contains("kband = 4"));
        assertTrue(input.contains("spin_component = 1"));
        assertTrue(input.contains("lsign = .true."));

        // Verify plot settings
        assertTrue(input.contains("&plot"));
        assertTrue(input.contains("output_format = 6")); // Gaussian Cube
        assertTrue(input.contains("fileout = 'psi_bonding.cube'"));
    }

    @Test
    void testInvalidParametersThrowsException() {
        // Mismatched / negative k-point index must fail
        assertThrows(IllegalArgumentException.class, () -> {
            new QEPpWavefunctionBuilder("si", "./tmp", -1, 4, 0, true, "out.cube");
        });

        // Mismatched / negative band index must fail
        assertThrows(IllegalArgumentException.class, () -> {
            new QEPpWavefunctionBuilder("si", "./tmp", 1, 0, 0, true, "out.cube");
        });
    }
}
