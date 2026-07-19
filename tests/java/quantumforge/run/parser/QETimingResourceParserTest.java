package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import quantumforge.project.property.ProjectProperty;

class QETimingResourceParserTest {

    @Test
    void testTimingResourceParserHandlesMpiAndFftAndMemory() throws IOException {
        File tempFile = File.createTempFile("qe-output-timing", ".log");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("Parallel version (MPI), running on     8 processors\n");
            writer.write("Estimated max_memory     =   120.50 MB\n");
            writer.write("FFT dimensions:  (  64,  64,  64)\n");
            writer.write("PWSCF        :      5m 12.34s CPU      5m 14.56s WALL\n");
        }

        ProjectProperty mockProperty = new ProjectProperty();
        QETimingResourceParser parser = new QETimingResourceParser(mockProperty);
        parser.parse(tempFile);

        assertEquals(8, parser.getNumProcessors());
        assertEquals(120.50, parser.getEstimatedMaxMemoryMb(), 0.01);
        assertEquals("64 x 64 x 64", parser.getFftGrid());

        // 5m 12.34s CPU = 5 * 60 + 12.34 = 312.34s
        assertEquals(312.34, parser.getCpuTimeSeconds(), 0.01);

        // 5m 14.56s WALL = 5 * 60 + 14.56 = 314.56s
        assertEquals(314.56, parser.getWallTimeSeconds(), 0.01);

        // A later missing/rotated output must not retain an older run's timing.
        parser.parse(new File(tempFile.getParentFile(), "does-not-exist.out"));
        assertTrue(Double.isNaN(parser.getCpuTimeSeconds()));
        assertEquals(0, parser.getNumProcessors());
    }
}
