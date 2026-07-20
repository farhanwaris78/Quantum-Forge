/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.operation.OperationResult;

class QETimingParserTest {

    @TempDir
    Path tempDir;

    private Path write(String name, String content) throws IOException {
        Path path = this.tempDir.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    @Test
    void parsesTotalAndRoutineRows() throws IOException {
        Path log = write("si.log",
                "some noisy output\n"
                + "     init_run     :      1.24s CPU      1.30s WALL (        1 calls)\n"
                + "     electrons    :     10.50s CPU     11.00s WALL (        9 calls)\n"
                + "     c_bands      :      8.25s CPU      8.40s WALL (       90 calls)\n"
                + "     wfcinit      :      0.01s CPU      0.02s WALL\n"
                + "     PWSCF        :     26.10s CPU     27.05s WALL\n");
        OperationResult<QETimingParser.TimingSummary> result =
                QETimingParser.parse(log);
        assertTrue(result.isSuccess(), result.getMessage());
        QETimingParser.TimingSummary summary = result.getValue().orElseThrow();
        assertEquals(26.10, summary.getTotalCpuSeconds(), 1e-9);
        assertEquals(27.05, summary.getTotalWallSeconds(), 1e-9);
        assertEquals(4, summary.getRoutines().size());
        assertEquals("electrons", summary.getRoutines().get(1).getName());
        assertEquals(11.00, summary.getRoutines().get(1).getWallSeconds(), 1e-9);
        assertEquals(90L, summary.getRoutines().get(2).getCalls());
        assertEquals(1L, summary.getRoutines().get(0).getCalls());
        assertEquals(-1L, summary.getRoutines().get(3).getCalls(),
                "A routine without a call count reports -1, not 0");
    }

    @Test
    void parsesCompactDurations() {
        assertEquals(98460.0, QETimingParser.parseDuration("27h21m"), 1e-9);
        assertEquals(5023.6, QETimingParser.parseDuration("1h23m43.6s"), 1e-9);
        assertEquals(0.22, QETimingParser.parseDuration("0.22s"), 1e-12);
        assertEquals(176460.0, QETimingParser.parseDuration("2d1h1m"), 1e-9);
        assertTrue(Double.isNaN(QETimingParser.parseDuration("n/a")));
    }

    @Test
    void failsClosedWithoutPwsfTotal() throws IOException {
        Path partial = write("partial.log",
                "     init_run     :      1.24s CPU      1.30s WALL (        1 calls)\n");
        assertEquals("TIMING_EMPTY", QETimingParser.parse(partial).getCode(),
                "An unfinished run must not report routine sums as totals");
        Path missing = this.tempDir.resolve("none.log");
        assertEquals("TIMING_IO", QETimingParser.parse(missing).getCode());
        assertFalse(QETimingParser.parse(write("other.log", "nothing here\n")).isSuccess());
    }
}
