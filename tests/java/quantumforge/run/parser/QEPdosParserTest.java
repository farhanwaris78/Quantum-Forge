package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import quantumforge.project.property.ProjectProperty;

class QEPdosParserTest {

    @Test
    void testParserProcessesSinglePdosFile() throws IOException {
        Path tempDir = Files.createTempDirectory("qe-pdos-test");
        tempDir.toFile().deleteOnExit();

        // Mock projected DOS file: silicon.pdos_atm#2(Si)_wfc#1(s)_spin2
        File pdosFile = tempDir.resolve("silicon.pdos_atm#2(Si)_wfc#1(s)_spin2").toFile();
        pdosFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(pdosFile)) {
            writer.write("#  E (eV)   ldos(E)   pdos(E)\n");
            writer.write("   -5.000    0.1500    0.1200\n");
            writer.write("   -4.500    0.3500    0.2800\n");
            writer.write("   -4.000    0.8500    0.6400\n");
        }

        QEPdosParser.PdosComponent comp = QEPdosParser.parseSingleFile(pdosFile);
        assertNotNull(comp);

        assertEquals(2, comp.getAtomIndex());
        assertEquals("Si", comp.getElement());
        assertEquals(1, comp.getWfcIndex());
        assertEquals("s", comp.getOrbitalL());
        assertEquals(2, comp.getSpinChannel());

        assertEquals(3, comp.getEnergies().length);
        assertEquals(-5.000, comp.getEnergies()[0], 1e-6);
        assertEquals(0.1200, comp.getPdos()[0], 1e-6); // projected DOS, not LDOS
    }

    @Test
    void testParserScansDirectorySuccessfully() throws IOException {
        Path tempDir = Files.createTempDirectory("qe-pdos-dir-test");
        tempDir.toFile().deleteOnExit();

        File f1 = tempDir.resolve("silicon.pdos_atm#1(Si)_wfc#1(s)").toFile();
        File f2 = tempDir.resolve("silicon.pdos_atm#1(Si)_wfc#2(p)").toFile();
        f1.deleteOnExit();
        f2.deleteOnExit();

        try (FileWriter writer = new FileWriter(f1)) {
            writer.write("# E ldos pdos\n -1.0 0.5 0.4\n");
        }
        try (FileWriter writer = new FileWriter(f2)) {
            writer.write("# E ldos pdos(px) pdos(py) pdos(pz)\n -1.0 1.2 0.3 0.4 0.5\n");
        }

        ProjectProperty property = new ProjectProperty();
        QEPdosParser parser = new QEPdosParser(property);
        parser.parseDirectory(tempDir.toFile(), "silicon");

        assertEquals(2, parser.getComponents().size());
        
        boolean hasS = false;
        boolean hasP = false;
        for (QEPdosParser.PdosComponent comp : parser.getComponents()) {
            if ("s".equals(comp.getOrbitalL())) hasS = true;
            if ("p".equals(comp.getOrbitalL())) {
                hasP = true;
                assertEquals(1.2, comp.getPdos()[0], 1e-12);
            }
        }
        assertTrue(hasS);
        assertTrue(hasP);
    }

    @Test
    void integratesNonuniformProjectedDosWithoutCallingItAnElectronCount() {
        assertEquals(3.0, QEPdosParser.integratePdos(
                new double[] {-1.0, 0.0, 2.0}, new double[] {1.0, 1.0, 1.0}), 1e-12);
    }

    @Test
    void refusesColumnAmbiguousPdosWithoutHeader() throws IOException {
        Path tempDir = Files.createTempDirectory("qe-pdos-ambiguous");
        File file = tempDir.resolve("x.pdos_atm#1(Si)_wfc#1(s)").toFile();
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("-1.0 999.0\n");
        }
        assertTrue(QEPdosParser.parseSingleFile(file) == null);
    }
}
