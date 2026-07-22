/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.com.math;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ChartGeometryTest {

    @Test
    void linearMappingHitsEndpointsAndMidpoint() {
        double sx0 = ChartGeometry.mapLinear(0.0, 0.0, 100.0, 40.0, 540.0);
        double sx1 = ChartGeometry.mapLinear(100.0, 0.0, 100.0, 40.0, 540.0);
        double sxm = ChartGeometry.mapLinear(50.0, 0.0, 100.0, 40.0, 540.0);
        assertEquals(40.0, sx0, 1e-12);
        assertEquals(540.0, sx1, 1e-12);
        assertEquals(290.0, sxm, 1e-12);
        // flipped screen axis (canvas y grows downward) is supported:
        double sy0 = ChartGeometry.mapLinear(0.0, 0.0, 10.0, 420.0, 40.0);
        assertEquals(420.0, sy0, 1e-12);
        assertEquals(40.0, ChartGeometry.mapLinear(10.0, 0.0, 10.0, 420.0, 40.0), 1e-12);
        assertThrows(IllegalArgumentException.class,
                () -> ChartGeometry.mapLinear(1.0, 5.0, 5.0, 0.0, 100.0),
                "degenerate extent refuses instead of dividing through zero");
        assertThrows(IllegalArgumentException.class,
                () -> ChartGeometry.mapLinear(1.0, 6.0, 5.0, 0.0, 100.0),
                "inverted extent refuses");
        assertThrows(IllegalArgumentException.class,
                () -> ChartGeometry.mapLinear(1.0, Double.NaN, 5.0, 0.0, 100.0));
    }

    @Test
    void paddingGrowsRangesAndRescuesConstants() {
        double[] normal = ChartGeometry.padded(0.0, 10.0);
        assertArrayEquals(new double[] {-0.5, 10.5}, normal, 1e-12);
        double[] constant = ChartGeometry.padded(4.0, 4.0);
        assertArrayEquals(new double[] {3.8, 4.2}, constant, 1e-12,
                "constant series: 5% of the level on each side");
        double[] zero = ChartGeometry.padded(0.0, 0.0);
        assertArrayEquals(new double[] {-0.5, 0.5}, zero, 0.0);
        assertThrows(IllegalArgumentException.class, () -> ChartGeometry.padded(2, 1));
        assertThrows(IllegalArgumentException.class,
                () -> ChartGeometry.padded(Double.POSITIVE_INFINITY, 2));
    }

    @Test
    void niceTicksLandOnStepMultiplesInsideTheExtent() {
        double[] century = ChartGeometry.niceTicks(0.0, 100.0, 5);
        assertArrayEquals(new double[] {0, 20, 40, 60, 80, 100}, century, 1e-12,
                "raw step 20 years -> 20");
        double[] unit = ChartGeometry.niceTicks(0.0, 1.0, 5);
        assertArrayEquals(new double[] {0, 0.2, 0.4, 0.6, 0.8, 1.0}, unit, 1e-12);
        double[] negative = ChartGeometry.niceTicks(-3.0, 7.0, 5);
        assertArrayEquals(new double[] {-2, 0, 2, 4, 6}, negative, 1e-12,
                "step 2 above the raw 2, no ticks outside the extent");
        double[] constant = ChartGeometry.niceTicks(5.0, 5.0, 4);
        assertTrue(constant[0] < 5.0 && constant[constant.length - 1] > 5.0,
                "constant data still brackets its level after padding");
        assertArrayEquals(new double[] {100, 120, 140, 160, 180, 200},
                ChartGeometry.niceTicks(100.0, 200.0, 5), 1e-12,
                "offset extents tick on multiples of the step, not of the min");
        assertThrows(IllegalArgumentException.class,
                () -> ChartGeometry.niceTicks(0, 1, 1));
    }

    @Test
    void extentRefusesEmptyAndNonFiniteSeries() {
        assertArrayEquals(new double[] {1.0, 9.0},
                ChartGeometry.extent(List.of(3.0, 1.0, 9.0)), 0.0);
        assertThrows(IllegalArgumentException.class,
                () -> ChartGeometry.extent(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> ChartGeometry.extent(List.of(1.0, Double.NaN)),
                "a NaN never slips into a drawn curve");
    }
}
