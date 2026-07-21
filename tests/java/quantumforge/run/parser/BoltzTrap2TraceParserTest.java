/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.project.property.ProjectProperty;
import quantumforge.run.parser.BoltzTrap2TraceParser.FileKind;
import quantumforge.run.parser.BoltzTrap2TraceParser.TransportRow;

/**
 * Batch 152 pins of the BoltzTraP2 transport-table reader (Roadmap #109).
 * Every fixture is synthesized in the exact grammar BoltzTraP2 26.3.1's own
 * writers emit ({@code BoltzTraP2/io.py save_trace / save_condtens /
 * save_halltens}): 10-number .trace rows, 30-number Fortran-ordered tensor
 * rows, six-name condtens headers, and the scattering-model header variants.
 */
class BoltzTrap2TraceParserTest {

    @TempDir
    private Path tempDir;

    private BoltzTrap2TraceParser parse(String name, String content) throws IOException {
        Path file = this.tempDir.resolve(name);
        Files.writeString(file, content);
        BoltzTrap2TraceParser parser = new BoltzTrap2TraceParser(new ProjectProperty());
        parser.parse(file.toFile());
        return parser;
    }

    private static String traceLine(double mu, double t, double n, double dos,
            double s, double sigma, double rh, double kappa, double cv, double chi) {
        return String.format(java.util.Locale.ROOT,
                "%10g %9g %25g %25g %25g %25g %25g %25g %25g %25g%n",
                mu, t, n, dos, s, sigma, rh, kappa, cv, chi);
    }

    private String traceFixture() {
        StringBuilder sb = new StringBuilder();
        sb.append("#    Ef[Ry]      T[K]                    N[e/uc]"
                + "            DOS(ef)[1/(Ha*uc)]                     S[V/K]"
                + "     sigma/tau0[1/(ohm*m*s)]                   RH[m**3/C]"
                + "            kappae/tau0[W/(m*K*s)]               cv[J/(mol*K)]"
                + "               chi[m**3/mol]\n");
        sb.append(traceLine(0.10, 300, 10.005, 0.123, -2.0e-4, 1.5e19, 1.0e-7, 3.0e4, 24.9, 1.1e-9));
        sb.append(traceLine(0.10, 600, 10.005, 0.130, -2.5e-4, 1.8e19, 0.9e-7, 3.4e4, 25.1, 1.2e-9));
        sb.append("this line is garbage and must be skipped\n");
        sb.append(traceLine(0.20, 300, 10.010, 0.115, -2.5e-4, 1.2e19, 1.2e-7, 2.8e4, 24.8, 1.0e-9));
        sb.append("0.30 300 10.02\n"); // ragged row: skipped and counted
        return sb.toString();
    }

    @Test
    void traceGrammarIsParsedVerbatim() throws IOException {
        BoltzTrap2TraceParser parser = parse("si.trace", traceFixture());
        assertEquals(FileKind.TRACE, parser.getFileKind());
        assertEquals(3, parser.getRows().size(), "two garbage rows are excluded");
        assertEquals(2, parser.getSkippedRowCount(), "skipped rows are counted, not healed");
        assertEquals("uniform_tau", parser.getScatteringModel());
        assertEquals("sigma/tau0[1/(ohm*m*s)]", parser.getSigmaUnits(),
                "unit token surfaces verbatim from the header");
        assertEquals("kappae/tau0[W/(m*K*s)]", parser.getKappaUnits());
        assertTrue(parser.getFamilyNote().contains("10-column"), parser.getFamilyNote());
    }

    @Test
    void traceRowFieldsLandInWriterColumnOrder() throws IOException {
        BoltzTrap2TraceParser parser = parse("si.trace", traceFixture());
        TransportRow row = parser.getRows().get(0);
        assertEquals(0.10, row.getMuRy(), 1.0e-12);
        assertEquals(0.10 * BoltzTrap2TraceParser.RY_TO_EV, row.getMuEv(), 1.0e-9,
                "Ry->eV uses the one named pinned constant");
        assertEquals(300.0, row.getTemperatureK(), 1.0e-12);
        assertEquals(10.005, row.getCarriersPerCell(), 1.0e-12);
        assertEquals(-2.0e-4, row.getSeebeckVK(), 1.0e-12);
        assertEquals(1.5e19, row.getSigmaOverTau(), 1.0e14);
        assertEquals(3.0e4, row.getKappaOverTau(), 1.0e-9);
        assertNull(row.getSeebeckDiag(), "a .trace row carries no tensor diagonal");
        assertEquals(List.of(300.0, 600.0), List.copyOf(parser.getTemperatures()));
    }

    @Test
    void powerFactorIsTheDocumentedIsotropicApproximation() throws IOException {
        BoltzTrap2TraceParser parser = parse("si.trace", traceFixture());
        TransportRow row = parser.getRows().get(0);
        assertEquals((-2.0e-4) * (-2.0e-4) * 1.5e19,
                BoltzTrap2TraceParser.powerFactor(row), 1.0e5,
                "PF = S^2 * sigma verbatim from the recorded columns");
    }

    @Test
    void maxAbsSeebeckTieBreaksByTemperature() throws IOException {
        BoltzTrap2TraceParser parser = parse("si.trace", traceFixture());
        TransportRow best = parser.maxAbsSeebeck();
        assertNotNull(best);
        assertEquals(-2.5e-4, best.getSeebeckVK(), 1.0e-12);
        assertEquals(300.0, best.getTemperatureK(), 1.0e-12,
                "equal |S| magnitudes resolve to the cooler row");
        assertEquals(0.20, best.getMuRy(), 1.0e-12);
    }

    // ---------------------------------------------------------------- condtens

    private static StringBuilder appendTensorRow(StringBuilder sb, double mu, double t,
            double n) {
        // tensors distinct enough to catch any layout slip
        double[] sigmaF = {3.0e19, 0.01e19, 0.02e19, 0.03e19, 6.0e19, 0.04e19,
                0.05e19, 0.06e19, 9.0e19};               // xx,yx,zx,xy,yy,zy,xz,yz,zz
        double[] sF = {-1.0e-4, 0.01e-4, 0.02e-4, 0.03e-4, -2.0e-4, 0.04e-4,
                0.05e-4, 0.06e-4, -3.6e-4};
        double[] kF = {3.0e4, 1.0, 2.0, 3.0, 3.3e4, 4.0, 5.0, 6.0, 3.9e4};
        StringBuilder line = new StringBuilder();
        line.append(String.format(java.util.Locale.ROOT, "%10g %9g %25g", mu, t, n));
        for (double v : sigmaF) {
            line.append(String.format(java.util.Locale.ROOT, " %25g", v));
        }
        for (double v : sF) {
            line.append(String.format(java.util.Locale.ROOT, " %25g", v));
        }
        for (double v : kF) {
            line.append(String.format(java.util.Locale.ROOT, " %25g", v));
        }
        sb.append(line).append('\n');
        return sb;
    }

    private String condtensFixture(String sigmaHeader, String kappaHeader) {
        // six names over thirty print slots - exactly what save_condtens emits
        StringBuilder sb = new StringBuilder();
        sb.append("#    Ef[Ry]      T[K]                    N[e/uc]         ")
                .append(sigmaHeader)
                .append("                                                     ")
                .append("                                    S[V/K]             ")
                .append("                                        ")
                .append("                 ").append(kappaHeader)
                .append("\n");
        appendTensorRow(sb, 0.05, 300, 10.001);
        appendTensorRow(sb, 0.05, 600, 10.001);
        return sb.toString();
    }

    @Test
    void condtensFortranBlocksDecodeToIsotropicAverages() throws IOException {
        BoltzTrap2TraceParser parser = parse("si.condtens",
                condtensFixture("sigma/tau0[1/(ohm*m*s)]", "kappae/tau0[W/(m*K*s)]"));
        assertEquals(FileKind.CONDTENS, parser.getFileKind(),
                "30-column rows + sigma-named header lock the tensor family");
        assertEquals(2, parser.getRows().size());
        assertEquals(0, parser.getSkippedRowCount());
        TransportRow row = parser.getRows().get(0);
        assertEquals((3.0e19 + 6.0e19 + 9.0e19) / 3.0, row.getSigmaOverTau(), 1.0e14,
                "isotropic sigma is the diagonal (xx+yy+zz)/3, offsets 0/4/8 of the F-block");
        assertEquals((-1.0e-4 - 2.0e-4 - 3.6e-4) / 3.0, row.getSeebeckVK(), 1.0e-15);
        assertEquals((3.0e4 + 3.3e4 + 3.9e4) / 3.0, row.getKappaOverTau(), 1.0e-9);
        assertArrayEquals(new double[] {-1.0e-4, -2.0e-4, -3.6e-4},
                row.getSeebeckDiag(), 1.0e-15, "Fortran offsets 0/4/8 hold xx, yy, zz");
    }

    @Test
    void condtensAnisotropySpreadUsesTheRealDiagonal() throws IOException {
        BoltzTrap2TraceParser parser = parse("si.condtens",
                condtensFixture("sigma/tau0[1/(ohm*m*s)]", "kappae/tau0[W/(m*K*s)]"));
        double[] spread = parser.seebeckDiagonalSpread(parser.getRows().get(0));
        assertNotNull(spread);
        assertArrayEquals(new double[] {-3.6e-4, -1.0e-4, 2.6e-4}, spread, 1.0e-15);
        assertNull(parser.seebeckDiagonalSpread(null));
    }

    @Test
    void uniformLambdaAndCustomTauHeadersAreNamedNotConverted() throws IOException {
        BoltzTrap2TraceParser lambda = parse("l.condtens",
                condtensFixture("sigma/lambda0[1/(ohm*m**2)]", "kappae/lambda0[W/(m**2*K)]"));
        assertEquals("uniform_lambda", lambda.getScatteringModel());
        assertEquals("sigma/lambda0[1/(ohm*m**2)]", lambda.getSigmaUnits());

        BoltzTrap2TraceParser custom = parse("c.condtens",
                condtensFixture("sigma[1/(ohm*m)]", "kappae[W/(m*K)]"));
        assertEquals("custom_tau", custom.getScatteringModel(),
                "the 1e-9 factor stays with the writer; we just name the family");
        assertEquals("sigma[1/(ohm*m)]", custom.getSigmaUnits());
        assertEquals((3.0e19 + 6.0e19 + 9.0e19) / 3.0,
                custom.getRows().get(0).getSigmaOverTau(), 1.0e14,
                "numbers parse exactly as written - no re-scaling");
    }

    // ------------------------------------------------------------- refusal

    @Test
    void hallTensorFilesRefuseLoudlyNotReinterpreted() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("#    Ef[Ry]      T[K]                    N[e/uc]")
                .append("                    RH[m**3/C]\n");
        appendTensorRow(sb, 0.05, 300, 10.001);
        appendTensorRow(sb, 0.05, 600, 10.001);
        appendTensorRow(sb, 0.10, 300, 10.001);
        BoltzTrap2TraceParser parser = parse("si.halltens", sb.toString());
        assertEquals(FileKind.TENSOR_OTHER, parser.getFileKind());
        assertEquals(0, parser.getRows().size(),
                "a .halltens file is NOT transport data - no row may surface");
        assertEquals(3, parser.getSkippedRowCount());
        assertTrue(parser.getFamilyNote().contains("Hall"), parser.getFamilyNote());
        assertTrue(parser.getScatteringModel().startsWith("n/a"), parser.getScatteringModel());
    }

    @Test
    void headerlessTensorRowsParseWithExplicitProvenance() throws IOException {
        StringBuilder sb = new StringBuilder();
        appendTensorRow(sb, 0.05, 300, 10.001);
        appendTensorRow(sb, 0.05, 600, 10.001);
        BoltzTrap2TraceParser parser = parse("bt1.condtens", sb.toString());
        assertEquals(FileKind.CONDTENS, parser.getFileKind());
        assertEquals("no header", parser.getScatteringModel());
        assertTrue(parser.getFamilyNote().contains("no header"), parser.getFamilyNote());
        assertEquals("", parser.getSigmaUnits(), "units are never invented on a headerless file");
    }

    @Test
    void fallbackFailureModesStayHonest() throws IOException {
        BoltzTrap2TraceParser empty = parse("empty.trace", "");
        assertNull(empty.getFileKind());
        assertEquals(0, empty.getRows().size());
        assertNull(empty.maxAbsSeebeck());

        BoltzTrap2TraceParser alien = parse("alien.dat", "1 2 3 4 5\n6 7 8 9 0\n");
        assertNull(alien.getFileKind(), "5-column rows match neither grammar");
        assertEquals(2, alien.getSkippedRowCount());
        assertTrue(alien.getFamilyNote().contains("no clean data row"),
                alien.getFamilyNote());

        BoltzTrap2TraceParser singleton = parse("one.trace",
                traceLine(0.1, 300, 10, 0.1, -1.0e-4, 1.0e19, 1.0, 1.0e4, 1.0, 1.0));
        assertEquals(1, singleton.getRows().size(),
                "a single clean point parses but is not a curve (analysis refuses it)");
        assertEquals(FileKind.TRACE, singleton.getFileKind());
        BoltzTrap2TraceParser nan = parse("nan.trace",
                traceLine(0.1, 300, 10, 0.1, Double.NaN, 1.0, 1.0, 1.0, 1.0, 1.0)
                        + traceLine(0.1, 300, 10, 0.1, Double.POSITIVE_INFINITY, 1.0, 1.0,
                                1.0, 1.0, 1.0)
                        + traceLine(0.1, 300, 10, 0.1, -1.0e-4, 1.0e19, 1.0, 1.0e4, 1.0, 1.0));
        assertEquals(1, nan.getRows().size());
        assertEquals(2, nan.getSkippedRowCount(),
                "NaN/Infinity rows never reach the row list");
    }

    @Test
    void repeatedParseResetsState() throws IOException {
        BoltzTrap2TraceParser parser = new BoltzTrap2TraceParser(new ProjectProperty());
        Path file = this.tempDir.resolve("si.trace");
        Files.writeString(file, traceFixture());
        parser.parse(file.toFile());
        assertEquals(3, parser.getRows().size());
        Path file2 = this.tempDir.resolve("si.condtens");
        Files.writeString(file2,
                condtensFixture("sigma/tau0[1/(ohm*m*s)]", "kappae/tau0[W/(m*K*s)]"));
        parser.parse(file2.toFile());
        assertEquals(FileKind.CONDTENS, parser.getFileKind());
        assertEquals(2, parser.getRows().size(), "state was fully reset between parses");
        assertEquals(0, parser.getSkippedRowCount());
    }
}
