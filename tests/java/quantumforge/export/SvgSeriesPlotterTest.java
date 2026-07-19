package quantumforge.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;

/** Batch-48 coverage for the deterministic SVG series plotter (Roadmap #122). */
class SvgSeriesPlotterTest {

    @Test
    void testExactPixelMappingOnKnownSeries() {
        List<String> csv = List.of("energy,value", "0.0,0.0", "1.0,1.0", "2.0,0.0");
        OperationResult<String> result = SvgSeriesPlotter.plot(csv, "Test series");
        assertTrue(result.isSuccess(), result.getMessage());
        String svg = result.getValue().orElseThrow();
        // Mapping: xPix = 90 + fx*(810-90), yPix = 490 - fy*(490-50).
        assertTrue(svg.contains("points=\"90.00,490.00 450.00,50.00 810.00,490.00\""), svg);
        assertTrue(svg.contains(">energy</text>"), svg);
        assertTrue(svg.contains(">value</text>"), svg);
        assertTrue(svg.contains("points=3 rejected=0"), svg);
        assertTrue(Pattern.compile("sha256=[0-9a-f]{64}").matcher(svg).find(),
                "A 64-hex provenance hash of the source rows must be embedded");
    }

    @Test
    void testHeaderlessSeriesGetsDefaultLabels() {
        OperationResult<String> result = SvgSeriesPlotter.plot(
                List.of("0.0,1.0", "1.0,2.0", "2.0,1.0"), "t");
        assertTrue(result.isSuccess(), result.getMessage());
        String svg = result.getValue().orElseThrow();
        assertTrue(svg.contains(">x</text>"), svg);
        assertTrue(svg.contains(">y</text>"), svg);
        assertTrue(svg.contains("points=3"), svg);
    }

    @Test
    void testRejectedRowsAreCounted() {
        OperationResult<String> result = SvgSeriesPlotter.plot(
                List.of("a,b", "0,0", "junk", "1,1", "c,d", "2,0"), "t");
        assertTrue(result.isSuccess(), result.getMessage());
        assertTrue(result.getValue().orElseThrow().contains("rejected=3"),
                "The one-cell row and the two non-numeric data rows are counted: "
                        + result.getValue().orElseThrow().substring(0, 200));
    }

    @Test
    void testXmlEscapingOfTitle() {
        OperationResult<String> result = SvgSeriesPlotter.plot(
                List.of("0,0", "1,1", "2,0"), "a<b>&\"q\"");
        assertTrue(result.isSuccess());
        String svg = result.getValue().orElseThrow();
        assertTrue(svg.contains("a&lt;b&gt;&amp;&quot;q&quot;"), svg);
        assertFalse(svg.contains(">a<b>"), "Raw angle brackets must never reach the document");
    }

    @Test
    void testTooFewPointsFailClosed() {
        assertFalse(SvgSeriesPlotter.plot(List.of("0,0", "1,1"), "t").isSuccess(),
                "Two points do not make a series");
        assertFalse(SvgSeriesPlotter.plot(List.of("only,a,header"), "t").isSuccess());
        assertFalse(SvgSeriesPlotter.plot(List.of(), "t").isSuccess());
        OperationResult<String> nullResult = SvgSeriesPlotter.plot(null, "t");
        assertFalse(nullResult.isSuccess());
        assertEquals("PLOT_EMPTY", nullResult.getCode());
    }

    @Test
    void testFlatSeriesDoesNotDivideByZero() {
        OperationResult<String> result = SvgSeriesPlotter.plot(
                List.of("0,5.0", "1,5.0", "2,5.0"), "flat");
        assertTrue(result.isSuccess(), result.getMessage());
        String svg = result.getValue().orElseThrow();
        assertTrue(svg.contains("<polyline"), svg);
        assertFalse(svg.contains("NaN"), svg);
    }
}
