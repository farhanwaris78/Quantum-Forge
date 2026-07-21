/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import quantumforge.run.parser.BoltzTrap2SeriesSlicer.SeriesKind;
import quantumforge.run.parser.BoltzTrap2SeriesSlicer.TemperatureSeries;
import quantumforge.run.parser.BoltzTrap2TraceParser.TransportRow;

class BoltzTrap2SeriesSlicerTest {

    private static TransportRow row(double mu, double temperature, double seebeck,
            double sigma, double kappa) {
        return new TransportRow(mu, temperature, 0.0, seebeck, sigma, kappa, null);
    }

    private static TransportRow condRow(double mu, double temperature, double seebeck,
            double sx, double sy, double sz) {
        return new TransportRow(mu, temperature, 0.0, seebeck, 1.0, 1.0,
                new double[] {sx, sy, sz});
    }

    @Test
    void distinctMuValuesAreExactAndAscending() {
        List<TransportRow> rows = List.of(
                row(0.25, 300, 1, 1, 1), row(-0.10, 500, 1, 1, 1),
                row(0.25, 700, 1, 1, 1), row(-0.10, 300, 1, 1, 1),
                row(0.0, 300, 1, 1, 1));
        assertEquals(List.of(-0.10, 0.0, 0.25),
                BoltzTrap2SeriesSlicer.distinctMuRy(rows),
                "file order never decides the mu picker order");
        assertTrue(BoltzTrap2SeriesSlicer.distinctMuRy(List.of()).isEmpty());
    }

    @Test
    void sliceSelectsOneMuSortsByTemperatureAndKeepsValues() {
        List<TransportRow> rows = List.of(
                row(0.0, 700, 3.0, 1, 1),
                row(0.0, 300, 1.0, 1, 1),
                row(0.5, 300, 99.0, 1, 1),
                row(0.0, 500, 2.0, 1, 1));
        TemperatureSeries series =
                BoltzTrap2SeriesSlicer.slice(rows, 0.0, SeriesKind.SEEBECK);
        assertEquals(List.of(300.0, 500.0, 700.0), series.getTemperatures(),
                "ascending even though the file grid was unsorted");
        assertEquals(List.of(1.0, 2.0, 3.0), series.getValues());
        assertEquals(3, series.size());
        // an absent mu is an honest empty series - never a nearest-mu fake:
        TemperatureSeries empty =
                BoltzTrap2SeriesSlicer.slice(rows, 0.123, SeriesKind.SEEBECK);
        assertEquals(0, empty.size());
        assertEquals(0.123, empty.getMuRy(), 0.0);
    }

    @Test
    void sigmaKappaAndPowerFactorComeFromTheParserVerbatim() {
        List<TransportRow> rows = List.of(row(0.0, 300, 2.0, 5.0, 7.0));
        assertEquals(5.0, BoltzTrap2SeriesSlicer
                .slice(rows, 0.0, SeriesKind.SIGMA_OVER_TAU).getValues().get(0), 0.0);
        assertEquals(7.0, BoltzTrap2SeriesSlicer
                .slice(rows, 0.0, SeriesKind.KAPPA_OVER_TAU).getValues().get(0), 0.0);
        TemperatureSeries pf =
                BoltzTrap2SeriesSlicer.slice(rows, 0.0, SeriesKind.POWER_FACTOR);
        assertEquals(BoltzTrap2TraceParser.powerFactor(rows.get(0)),
                pf.getValues().get(0), 0.0,
                "the power-factor series is the parser's own S^2.sigma, no re-derivation");
    }

    @Test
    void diagonalKindsRideTheCondtensGrammarOrRefuse() {
        List<TransportRow> tensorRows = List.of(
                condRow(0.0, 300, 2.0, 3.0, 4.0, 5.0));
        assertEquals(3.0, BoltzTrap2SeriesSlicer
                .slice(tensorRows, 0.0, SeriesKind.SEEBECK_XX).getValues().get(0), 0.0);
        assertEquals(4.0, BoltzTrap2SeriesSlicer
                .slice(tensorRows, 0.0, SeriesKind.SEEBECK_YY).getValues().get(0), 0.0);
        assertEquals(5.0, BoltzTrap2SeriesSlicer
                .slice(tensorRows, 0.0, SeriesKind.SEEBECK_ZZ).getValues().get(0), 0.0);
        List<TransportRow> traceRows = List.of(row(0.0, 300, 1, 1, 1));
        IllegalArgumentException problem = assertThrows(IllegalArgumentException.class,
                () -> BoltzTrap2SeriesSlicer.slice(traceRows, 0.0, SeriesKind.SEEBECK_XX));
        assertTrue(problem.getMessage().contains("condtens"), problem.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> BoltzTrap2SeriesSlicer.slice(traceRows, 0.0, null),
                "no kind selected is a refusal, not a guess");
    }

    @Test
    void duplicateTemperaturesStayVisibleWithFileOrderTieBreak() {
        List<TransportRow> rows = List.of(
                row(0.0, 300, 1.0, 1, 1), row(0.0, 300, 9.0, 1, 1));
        TemperatureSeries series =
                BoltzTrap2SeriesSlicer.slice(rows, 0.0, SeriesKind.SEEBECK);
        assertEquals(List.of(300.0, 300.0), series.getTemperatures(),
                "duplicate temperatures are displayed, never silently deduplicated");
        assertEquals(List.of(1.0, 9.0), series.getValues(),
                "stable sort keeps the written order at equal temperatures");
    }

    @Test
    void axisLabelsCarryVerbatimUnitsOrHonestFallbacks() {
        assertEquals("Seebeck S [V/K]",
                BoltzTrap2SeriesSlicer.axisLabel(SeriesKind.SEEBECK, "", ""));
        assertEquals("sigma/tau [1/(ohm m s)]",
                BoltzTrap2SeriesSlicer.axisLabel(SeriesKind.SIGMA_OVER_TAU,
                        "1/(ohm m s)", ""));
        assertEquals("kappa/tau [header units]",
                BoltzTrap2SeriesSlicer.axisLabel(SeriesKind.KAPPA_OVER_TAU, null, null));
        assertTrue(BoltzTrap2SeriesSlicer
                .axisLabel(SeriesKind.POWER_FACTOR, "1/(ohm m s)", "")
                .contains("isotropic-average approximation"),
                "the power factor axis never pretends to be the tensor trace form");
    }
}
