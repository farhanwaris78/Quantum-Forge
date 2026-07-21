/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.operation.OperationResult;
import quantumforge.run.parser.QEElateAnalyzer.AverageRow;
import quantumforge.run.parser.QEElateAnalyzer.ElateReport;
import quantumforge.run.parser.QEElateAnalyzer.ElateUnit;
import quantumforge.run.parser.QEThermoPwElasticParser.ElasticConstantsFile;
import quantumforge.run.parser.QEThermoPwElasticParser.ElasticStdout;

/**
 * Pins for the thermo_pw elastic-channel parsers. Every numeric row below is
 * verbatim from the upstream example13 reference of thermo_pw (commit
 * b73edd6d75b92df80f3a322279c6b12b301b9947): elastic_constants/output_el_cons.dat.g1
 * (Si scf_elastic_constants run) and the matching si.elastic.out blocks. The
 * in-file grammar (12 alternating 4+2 numeric lines per 6x6 block, no unit
 * text) and the stdout grammar ('Elastic constants C_ij (kbar)' + the 'i j='
 * header + index-prefixed rows) are upstream facts; the ROUNDED stdout digits
 * (1588.86049 vs the file's 1588.860492) are pinned as-is - never blended.
 */
class QEThermoPwElasticParserTest {

    @TempDir
    Path tempDir;

    private Path write(String relative, String content) throws IOException {
        Path file = this.tempDir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    /** Verbatim 24 data lines of upstream output_el_cons.dat.g1 (Si). */
    private static final String STIFFNESS_BLOCK =
            "    0.1588860492E+04    0.6030012079E+03    0.6030012079E+03    0.0000000000E+00\n"
            + "    0.0000000000E+00    0.0000000000E+00\n"
            + "    0.6030012079E+03    0.1588860492E+04    0.6030012079E+03    0.0000000000E+00\n"
            + "    0.0000000000E+00    0.0000000000E+00\n"
            + "    0.6030012079E+03    0.6030012079E+03    0.1588860492E+04    0.0000000000E+00\n"
            + "    0.0000000000E+00    0.0000000000E+00\n"
            + "    0.0000000000E+00    0.0000000000E+00    0.0000000000E+00    0.8004009608E+03\n"
            + "    0.0000000000E+00    0.0000000000E+00\n"
            + "    0.0000000000E+00    0.0000000000E+00    0.0000000000E+00    0.0000000000E+00\n"
            + "    0.8004009608E+03    0.0000000000E+00\n"
            + "    0.0000000000E+00    0.0000000000E+00    0.0000000000E+00    0.0000000000E+00\n"
            + "    0.0000000000E+00    0.8004009608E+03\n";

    private static final String COMPLIANCE_BLOCK =
            "    0.7954954633E-03   -0.2188480802E-03   -0.2188480802E-03   -0.0000000000E+00\n"
            + "   -0.0000000000E+00   -0.0000000000E+00\n"
            + "   -0.2188480802E-03    0.7954954633E-03   -0.2188480802E-03   -0.0000000000E+00\n"
            + "   -0.0000000000E+00   -0.0000000000E+00\n"
            + "   -0.2188480802E-03   -0.2188480802E-03    0.7954954633E-03   -0.0000000000E+00\n"
            + "   -0.0000000000E+00   -0.0000000000E+00\n"
            + "    0.0000000000E+00    0.0000000000E+00    0.0000000000E+00    0.1249373813E-02\n"
            + "   -0.0000000000E+00   -0.0000000000E+00\n"
            + "    0.0000000000E+00    0.0000000000E+00    0.0000000000E+00    0.0000000000E+00\n"
            + "    0.1249373813E-02   -0.0000000000E+00\n"
            + "    0.0000000000E+00    0.0000000000E+00    0.0000000000E+00    0.0000000000E+00\n"
            + "    0.0000000000E+00    0.1249373813E-02\n";

    /** Verbatim si.elastic.out elastic section (lines 6495..6559 of the reference). */
    private static final String STDOUT_BLOCK =
            "     Elastic constants C_ij (kbar) \n"
            + "    i j=        1           2           3           4           5           6\n"
            + "    1  1588.86049   603.00121   603.00121     0.00000     0.00000     0.00000\n"
            + "    2   603.00121  1588.86049   603.00121     0.00000     0.00000     0.00000\n"
            + "    3   603.00121   603.00121  1588.86049     0.00000     0.00000     0.00000\n"
            + "    4     0.00000     0.00000     0.00000   800.40096     0.00000     0.00000\n"
            + "    5     0.00000     0.00000     0.00000     0.00000   800.40096     0.00000\n"
            + "    6     0.00000     0.00000     0.00000     0.00000     0.00000   800.40096\n"
            + "\n"
            + "     1 bar = 10^5 Pa; 10 kbar = 1 GPa; 1 atm = 1.01325 bar; 1 Pa = 1 N/m^2\n"
            + "\n"
            + "     Elastic compliances  S_ij (1/Mbar) \n"
            + "    i j=        1           2           3           4           5           6\n"
            + "    1     0.79550    -0.21885    -0.21885    -0.00000    -0.00000    -0.00000\n"
            + "    2    -0.21885     0.79550    -0.21885    -0.00000    -0.00000    -0.00000\n"
            + "    3    -0.21885    -0.21885     0.79550    -0.00000    -0.00000    -0.00000\n"
            + "    4     0.00000     0.00000     0.00000     1.24937    -0.00000    -0.00000\n"
            + "    5     0.00000     0.00000     1.24937     0.00000     0.00000    -0.00000\n"
            + "    6     0.00000     0.00000     0.00000     0.00000     0.00000     1.24937\n"
            + "\n"
            + "     1/Mbar = 1/10^{11} Pa; 1 Pa = 1 N/m^2\n"
            + "\n"
            + "     Voigt approximation:\n"
            + "     Bulk modulus  B =    931.62097 kbar\n"
            + "     Young modulus E =   1635.76447 kbar\n"
            + "     Shear modulus G =    677.41243 kbar\n"
            + "     Poisson Ratio n =      0.20736\n"
            + "\n"
            + "     Reuss approximation:\n"
            + "     Bulk modulus  B =    931.62097 kbar\n"
            + "     Young modulus E =   1563.39698 kbar\n"
            + "     Shear modulus G =    640.57431 kbar\n"
            + "     Poisson Ratio n =      0.22031\n"
            + "\n"
            + "     Voigt-Reuss-Hill average of the two approximations:\n"
            + "     Bulk modulus  B =    931.62097 kbar\n"
            + "     Young modulus E =   1599.58073 kbar\n"
            + "     Shear modulus G =    658.99337 kbar\n"
            + "     Poisson Ratio n =      0.21365\n"
            + "\n"
            + "     Voigt-Reuss-Hill average; sound velocities:\n"
            + "\n"
            + "     Compressional V_P =     8751.406 m/s\n"
            + "     Bulk          V_B =     6278.049 m/s\n"
            + "     Shear         V_G =     5280.142 m/s\n"
            + "\n"
            + "     The approximate Debye temperature is      643.200 K\n"
            + "\n"
            + "     Average Debye sound velocity =     5808.483 m/s\n"
            + "\n"
            + "     Debye temperature =      640.001 K\n";

    @Test
    void testConstantsFileParsesBothBlocksWithGeometryTag() throws IOException {
        Path file = write("elastic_constants/output_el_cons.dat.g1",
                STIFFNESS_BLOCK + "\n" + COMPLIANCE_BLOCK);
        OperationResult<ElasticConstantsFile> result =
                QEThermoPwElasticParser.parseElasticConstantsFile(file);
        assertTrue(result.isSuccess(), () -> result.getMessage());
        assertEquals("THERMOPW_ELASTIC_OK", result.getCode());
        ElasticConstantsFile parsed = result.getValue().orElseThrow();
        assertEquals(Integer.valueOf(1), parsed.getGeometryTag());
        double[][] stiffness = parsed.getStiffnessKbar();
        assertEquals(1588.860492, stiffness[0][0], 1e-9);
        assertEquals(603.0012079, stiffness[0][1], 1e-9);
        assertEquals(800.4009608, stiffness[3][3], 1e-9);
        assertEquals(0.0, stiffness[0][3], 0.0, "exact zero couplings stay zero");
        assertTrue(parsed.hasCompliance());
        double[][] compliance = parsed.getCompliancePerMbar();
        assertEquals(0.7954954633e-3, compliance[0][0], 1e-13);
        assertEquals(-0.2188480802e-3, compliance[0][1], 1e-13);
        assertEquals(0.1249373813e-2, compliance[3][3], 1e-13);
        assertTrue(parsed.getUnitProvenance().contains("kbar"),
                "the never-in-file unit must be stated as a stdout cross-pin");
        // The stiffness block feeds the ELATE channel as-is (declared kbar).
        OperationResult<ElateReport> elate = QEElateAnalyzer.analyze(
                parsed.toElateMatrixText(), ElateUnit.KBAR);
        assertTrue(elate.isSuccess(), () -> elate.getMessage());
        AverageRow voigt = elate.getValue().orElseThrow().getAverages().get(0);
        assertEquals(93.162097, voigt.getBulkGpa(), 1e-4,
                "thermo_pw's printed 931.62097 kbar / 10, the full-precision file"
                        + " tensor reaches it through the ELATE channel");
    }

    @Test
    void testConstantsFileUnsuffixedNameHasNoGeometryTag() throws IOException {
        Path file = write("elastic_constants/output_el_cons.dat", STIFFNESS_BLOCK);
        OperationResult<ElasticConstantsFile> result =
                QEThermoPwElasticParser.parseElasticConstantsFile(file);
        assertTrue(result.isSuccess(), () -> result.getMessage());
        ElasticConstantsFile parsed = result.getValue().orElseThrow();
        assertNull(parsed.getGeometryTag());
        assertFalse(parsed.hasCompliance(), "one complete block: stiffness only");
        assertNull(parsed.getCompliancePerMbar());
    }

    @Test
    void testConstantsFileRefusals() throws IOException {
        assertFalse(QEThermoPwElasticParser.parseElasticConstantsFile(null).isSuccess());
        Path wrong = write("elastic_constants/output_therm.dat.g1", STIFFNESS_BLOCK);
        OperationResult<ElasticConstantsFile> wrongName =
                QEThermoPwElasticParser.parseElasticConstantsFile(wrong);
        assertFalse(wrongName.isSuccess());
        assertEquals("THERMOPW_ELASTIC_INPUT", wrongName.getCode(),
                "not the pinned name -> fail-closed, never a guessed grammar");

        Path empty = write("output_el_cons.dat.g2", "   \n\n");
        OperationResult<ElasticConstantsFile> emptyResult =
                QEThermoPwElasticParser.parseElasticConstantsFile(empty);
        assertFalse(emptyResult.isSuccess());
        assertEquals("THERMOPW_ELASTIC_EMPTY", emptyResult.getCode());

        // Ten proper-width lines of the 12-line block: mid-run, still writing.
        String[] blockLines = STIFFNESS_BLOCK.split("\n");
        StringBuilder partialText = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            partialText.append(blockLines[i]).append('\n');
        }
        Path partial = write("output_el_cons.dat.g1", partialText.toString());
        OperationResult<ElasticConstantsFile> partialResult =
                QEThermoPwElasticParser.parseElasticConstantsFile(partial);
        assertFalse(partialResult.isSuccess());
        assertEquals("THERMOPW_ELASTIC_PARTIAL", partialResult.getCode(),
                "2/12 lines missing -> the live run keeps writing, nothing drawn");

        Path garbage = write("output_el_cons.dat.g3",
                "    0.1588860492E+04    HOLD_ON    0.6030012079E+03    0.0\n");
        OperationResult<ElasticConstantsFile> garbageResult =
                QEThermoPwElasticParser.parseElasticConstantsFile(garbage);
        assertFalse(garbageResult.isSuccess());
        assertEquals("THERMOPW_ELASTIC_SHAPE", garbageResult.getCode(),
                "a non-number is a corrupt constants file - header-less grammar");

        Path ragged = write("output_el_cons.dat.g4",
                STIFFNESS_BLOCK.replaceFirst(
                        "0\\.6030012079E\\+03    0\\.6030012079E\\+03",
                        "0.6030012079E+03"));
        OperationResult<ElasticConstantsFile> raggedResult =
                QEThermoPwElasticParser.parseElasticConstantsFile(ragged);
        assertFalse(raggedResult.isSuccess());
        assertEquals("THERMOPW_ELASTIC_SHAPE", raggedResult.getCode(),
                "a ragged width mid-file is a grammar break, never skipped");
    }

    @Test
    void testConstantsFileTrailingPartialSecondBlockKeepsFirst() throws IOException {
        // Live geometry write: stiffness block complete, compliance block has
        // written 5 proper-width lines plus a truncated last (partial append).
        String liveTail = STIFFNESS_BLOCK + "\n"
                + "    0.7954954633E-03   -0.2188480802E-03   -0.2188480802E-03   -0.0000000000E+00\n"
                + "   -0.0000000000E+00   -0.0000000000E+00\n"
                + "   -0.2188480802E-03    0.7954954633E-03   -0.2188480802E-03   -0.0000000000E+00\n"
                + "   -0.0000000000E+00   -0.0000000000E+00\n"
                + "   -0.2188480802E-03   -0.2188480802E-03    0.7954954633E-03   -0.0000";
        Path file = write("output_el_cons.dat.g1", liveTail);
        OperationResult<ElasticConstantsFile> result =
                QEThermoPwElasticParser.parseElasticConstantsFile(file);
        assertTrue(result.isSuccess(), () -> result.getMessage());
        ElasticConstantsFile parsed = result.getValue().orElseThrow();
        assertEquals(1588.860492, parsed.getStiffnessKbar()[0][0], 1e-9);
        assertFalse(parsed.hasCompliance(),
                "the half-written second block is held back, the first one kept");
    }

    @Test
    void testStdoutParsesBlocksSchemesAndTokens() throws IOException {
        Path file = write("si.elastic.out", "preamble line one\npreamble two\n"
                + "preamble three\n" + STDOUT_BLOCK);
        OperationResult<ElasticStdout> result =
                QEThermoPwElasticParser.parseElasticStdout(file);
        assertTrue(result.isSuccess(), () -> result.getMessage());
        ElasticStdout parsed = result.getValue().orElseThrow();
        assertEquals(4, parsed.getStiffnessFirstLine(),
                "the LAST C_ij block header, 1-based");
        double[][] stiffness = parsed.getStiffnessKbar();
        assertEquals(1588.86049, stiffness[0][0], 1e-12,
                "stdout digits are ROUNDER than the file's and pinned as-is");
        assertEquals(603.00121, stiffness[2][0], 1e-12);
        assertEquals(800.40096, stiffness[5][5], 1e-12);
        assertEquals(0.79550, parsed.getCompliancePerMbar()[0][0], 1e-12);
        assertEquals(-0.21885, parsed.getCompliancePerMbar()[1][2], 1e-12);

        assertEquals(3, parsed.getSchemes().size());
        assertEquals("Voigt", parsed.getSchemes().get(0).getScheme());
        assertEquals("931.62097", parsed.getSchemes().get(0).getBulkKbar());
        assertEquals("1635.76447", parsed.getSchemes().get(0).getYoungKbar());
        assertEquals("677.41243", parsed.getSchemes().get(0).getShearKbar());
        assertEquals("0.20736", parsed.getSchemes().get(0).getPoisson());
        assertEquals("Reuss", parsed.getSchemes().get(1).getScheme());
        assertEquals("1563.39698", parsed.getSchemes().get(1).getYoungKbar());
        assertEquals("Voigt-Reuss-Hill", parsed.getSchemes().get(2).getScheme());
        assertEquals("1599.58073", parsed.getSchemes().get(2).getYoungKbar());
        assertEquals("0.21365", parsed.getSchemes().get(2).getPoisson());

        assertEquals(java.util.List.of("8751.406", "6278.049", "5280.142"),
                parsed.getSoundTokens());
        assertEquals(java.util.List.of("643.200", "5808.483", "640.001"),
                parsed.getDebyeTokens());

        // The rounded stdout tensor still lands on the same physics, honestly.
        OperationResult<ElateReport> elate = QEElateAnalyzer.analyze(
                parsed.toElateMatrixText(), ElateUnit.KBAR);
        assertTrue(elate.isSuccess(), () -> elate.getMessage());
        assertEquals(93.16210, elate.getValue().orElseThrow().getAverages().get(0)
                .getBulkGpa(), 1e-4);
    }

    @Test
    void testStdoutLivePartialAndRefusals() throws IOException {
        // Header printed, only 3 of 6 rows so far: the ELATE channel must wait.
        Path partial = write("run.elastic.out",
                "     Elastic constants C_ij (kbar) \n"
                + "    i j=        1           2           3           4           5           6\n"
                + "    1  1588.86049   603.00121   603.00121     0.00000     0.00000     0.00000\n"
                + "    2   603.00121  1588.86049   603.00121     0.00000     0.00000     0.00000\n"
                + "    3   603.00121   603.00121  1588.86049     0.00000     0.00000     0.00000\n");
        OperationResult<ElasticStdout> partialResult =
                QEThermoPwElasticParser.parseElasticStdout(partial);
        assertFalse(partialResult.isSuccess());
        assertEquals("THERMOPW_ELASTIC_PARTIAL", partialResult.getCode());

        Path noHeader = write("scf.out", "     Program PWSCF v.7.2 starts ...\n"
                + "     total energy = -31.68 Ry\n");
        OperationResult<ElasticStdout> headerResult =
                QEThermoPwElasticParser.parseElasticStdout(noHeader);
        assertFalse(headerResult.isSuccess());
        assertEquals("THERMOPW_ELASTIC_HEADER", headerResult.getCode());

        Path badRow = write("run.elastic.out2",
                "     Elastic constants C_ij (kbar) \n"
                + "    i j=        1           2           3           4           5           6\n"
                + "    1  1588.86049   603.00121   603.00121     0.00000     0.00000     0.00000\n"
                + "    2   603.00121  1588.86049   603.00121     0.00000     0.00000     0.00000\n"
                + "    3   603.00121   603.00121  1588.86049     0.00000     0.00000     0.00000\n"
                + "    4     0.00000     0.00000     NaNROW0   800.40096     0.00000     0.00000\n"
                + "    5     0.00000     0.00000     0.00000     0.00000   800.40096     0.00000\n"
                + "    6     0.00000     0.00000     0.00000     0.00000     0.00000   800.40096\n");
        OperationResult<ElasticStdout> badRowResult =
                QEThermoPwElasticParser.parseElasticStdout(badRow);
        assertFalse(badRowResult.isSuccess());
        assertEquals("THERMOPW_ELASTIC_SHAPE", badRowResult.getCode());
        assertFalse(QEThermoPwElasticParser.parseElasticStdout(null).isSuccess());
    }

    @Test
    void testStdoutKeepsLastBlockOfTwo() throws IOException {
        // Geometry 1 ended, geometry 2 is writing: only the LAST block feeds ELATE.
        String first = STDOUT_BLOCK.replace("1588.86049", "1000.00000");
        Path file = write("two.out", first + "\nsecond geometry starts\n"
                + STDOUT_BLOCK);
        OperationResult<ElasticStdout> result =
                QEThermoPwElasticParser.parseElasticStdout(file);
        assertTrue(result.isSuccess(), () -> result.getMessage());
        ElasticStdout parsed = result.getValue().orElseThrow();
        assertEquals(1588.86049, parsed.getStiffnessKbar()[0][0], 1e-12,
                "the running geometry's (last) block is the one that feeds ELATE");
    }
}
