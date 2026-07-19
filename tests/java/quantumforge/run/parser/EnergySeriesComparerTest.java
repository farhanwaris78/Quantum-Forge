package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.operation.OperationResult;

/** Batch-58 coverage for the same-grid energy-series comparer (Roadmap #124). */
class EnergySeriesComparerTest {

    @TempDir
    private Path tempDir;

    private Path write(String name, String content) throws IOException {
        Path file = this.tempDir.resolve(name);
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    void exactMetricsOnKnownSeries() throws IOException {
        OperationResult<EnergySeriesComparer.SeriesComparison> result =
                EnergySeriesComparer.compare(write("series.csv",
                        "param,E_Ry_A,E_Ry_B\n"
                        + "30,-5.0,-4.5\n"
                        + "40,-6.0,-6.25\n"
                        + "50,-7.0,-7.75\n"));
        assertTrue(result.isSuccess(), result.getMessage());
        EnergySeriesComparer.SeriesComparison comparison = result.getValue().orElseThrow();
        assertEquals(3, comparison.getRowCount());
        assertEquals(0, comparison.getRejectedRows());
        assertEquals("param", comparison.getParameterLabel());
        assertEquals("E_Ry_A", comparison.getFirstSeriesLabel());
        assertEquals("E_Ry_B", comparison.getSecondSeriesLabel());
        assertEquals(0.5400617248673217, comparison.getRmsDeltaEv(), 1.0e-12);
        assertEquals(-0.16666666666666666, comparison.getMeanSignedDeltaEv(), 1.0e-12);
        assertEquals(0.75, comparison.getMaxAbsDeltaEv(), 1.0e-12);
        assertEquals(50.0, comparison.getMaxAbsDeltaAtParameter(), 1.0e-12);
        assertEquals(0.5, comparison.getFirstDeltaEv(), 1.0e-12);
        assertEquals(-0.75, comparison.getLastDeltaEv(), 1.0e-12);
        assertEquals(1, comparison.getSignCrossings());
    }

    @Test
    void whitespaceAndFortranDAndOrderNotes() throws IOException {
        OperationResult<EnergySeriesComparer.SeriesComparison> result =
                EnergySeriesComparer.compare(write("ws.dat",
                        "\n30 -5.0 -4.5\n50 -7.0 -7.75\n40 -6.0 -6.25\n"));
        assertTrue(result.isSuccess(), result.getMessage());
        EnergySeriesComparer.SeriesComparison comparison = result.getValue().orElseThrow();
        assertEquals(3, comparison.getRowCount());
        assertEquals(1, comparison.getOutOfOrderRows(),
                "The descending third row is counted, not re-sorted");
        assertEquals("(row order index)", comparison.getParameterLabel());
    }

    @Test
    void failClosedOnShortOrCorruptInput() throws IOException {
        assertEquals("SERIES_TOO_SHORT", EnergySeriesComparer.compare(
                write("one.csv", "a,b,c\n30,1,2\n")).getCode());
        assertEquals("SERIES_TOO_SHORT", EnergySeriesComparer.compare(
                write("ragged.csv", "30,1,2\njunk only\n40,3\n")).getCode(),
                "Ragged/non-numeric rows are rejected, never guessed");
        assertEquals("SERIES_IO", EnergySeriesComparer.compare(
                this.tempDir.resolve("absent.csv")).getCode());
        OperationResult<EnergySeriesComparer.SeriesComparison> partial =
                EnergySeriesComparer.compare(write("partial.csv",
                        "30,1,2\nNaN,2,3\n40,3,4\n"));
        assertTrue(partial.isSuccess(), partial.getMessage());
        assertEquals(2, partial.getValue().orElseThrow().getRowCount());
        assertEquals(1, partial.getValue().orElseThrow().getRejectedRows());
    }
}
