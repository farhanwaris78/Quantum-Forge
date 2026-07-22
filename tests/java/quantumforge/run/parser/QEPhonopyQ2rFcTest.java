/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.run.parser.QEPhonopyQ2rFc.Q2rFc;

/**
 * Pins {@link QEPhonopyQ2rFc} against the upstream experimental parser
 * (phonopy/interface/qe.py PH_Q2R @ 3a3e0f09) using exact-grammar synthetic
 * fixtures shaped like the REAL example/NaCl-QE-q2r/NaCl.fc (line-1 token
 * shape, ' T' NAC flag, epsilon/born rows, dims line, 'k ll i j' block
 * headers, 'i1 i2 i3 value' data lines with Fortran E exponents, i1 fastest).
 * The REAL 18,489-line file was additionally parsed in a probe:
 * 36/36 blocks, order census 18432/18432, eps=2.474413280838,
 * borns=+-1.1007123, block(3,3,2,2) last value=7.18412023826E-06 - matching
 * this parser's output exactly.
 */
class QEPhonopyQ2rFcTest {

    /** natom=1 / dim 1x1x1 with NAC: 9 blocks x (header+1 data line). */
    private static String miniNac() {
        StringBuilder sb = new StringBuilder();
        sb.append("   1    1   0  5.4300000  0.0000000\n");
        sb.append("      5.430000000    0.000000000    0.000000000\n");
        sb.append("      0.000000000    5.430000000    0.000000000\n");
        sb.append("      0.000000000    0.000000000    5.430000000\n");
        sb.append("           1  'Si '    51128.0000000000     \n");
        sb.append("    1    1      0.0000000000      0.0000000000      0.0000000000\n");
        sb.append(" T\n");
        sb.append("         12.000000000000          0.000000000000          0.000000000000\n");
        sb.append("          0.000000000000         12.000000000000          0.000000000000\n");
        sb.append("          0.000000000000          0.000000000000         12.000000000000\n");
        sb.append("    1\n");
        sb.append("      0.1000000      0.0000000      0.0000000\n");
        sb.append("      0.0000000      0.1000000      0.0000000\n");
        sb.append("      0.0000000      0.0000000      0.1000000\n");
        sb.append("   1   1   1\n");
        int blockNo = 0;
        for (int k = 1; k <= 3; k++) {
            for (int ll = 1; ll <= 3; ll++) {
                blockNo++;
                sb.append("   ").append(k).append("   ").append(ll)
                        .append("   1   1\n");
                sb.append(String.format("   1   1   1  % .6E%n",
                        0.01 * blockNo * (k == 2 ? -1 : 1)));
            }
        }
        return sb.toString();
    }

    @Test
    void miniWithNacParsesExactly() {
        OperationResult<Q2rFc> result =
                QEPhonopyQ2rFc.parseText(miniNac(), "mini.fc");
        assertTrue(result.isSuccess(), () -> result.getMessage());
        assertEquals("PHONOPY_Q2R_OK", result.getCode());
        Q2rFc fc = result.getValue().orElseThrow();
        assertEquals(1, fc.getNtype());
        assertEquals(1, fc.getNatom());
        assertEquals(0, fc.getIbrav());
        assertEquals(3, fc.getCellLines().size()); // ibrav=0
        assertEquals(1, fc.getSpeciesLines().size());
        // QE writes the label padded inside the quotes ('Na ' in NaCl.fc)
        assertTrue(fc.getSpeciesLines().get(0).contains("'Si '"));
        assertEquals(1, fc.getAtomLines().size());
        assertTrue(fc.hasNac());
        assertEquals(12.0, fc.getEpsilon().orElseThrow()[0], 1e-12);
        assertEquals(0.1, fc.getBorns().get(0)[0], 1e-12);
        assertEquals("1", fc.getBornHeaders().get(0));
        assertEquals(1, fc.getDim()[0]);
        assertEquals(9, fc.getExpectedBlockCount());
        assertEquals(9, fc.getBlocks().size());
        assertEquals(0, fc.getPartialBlocksHeld());
        // blocks in (k,ll,i,j) order, value negated for k==2 as constructed
        assertEquals("1   1   1   1", fc.getBlocks().get(0).getHeaderLine());
        assertEquals(0.01, fc.getBlocks().get(0).getValues()[0], 1e-12);
        assertEquals(-0.06, fc.getBlocks().get(5).getValues()[0], 1e-12); // k=2,ll=3
        assertEquals(1, fc.getBlocks().get(0).getOrderChecked());
        assertEquals(0.09, fc.getMaxAbsElement(), 1e-12);
        // verdict message census
        assertTrue(result.getMessage().contains("9/9 fc blocks"));
        assertTrue(result.getMessage().contains("NAC block present"));
        // verbatim doctrine notes
        assertTrue(fc.getNotes().stream().anyMatch(n -> n.contains("Ry/au^2.")));
        assertTrue(fc.getNotes().stream().anyMatch(n
                -> n.contains("partially corrected by QE's implemented NAC method")));
        assertTrue(fc.getNotes().stream().anyMatch(n -> n.contains("pw_forum")));
    }

    @Test
    void bornTextMirrorsTheReadmePrintShape() {
        Q2rFc fc = QEPhonopyQ2rFc.parseText(miniNac(), "m").getValue().orElseThrow();
        String born = fc.toBornText();
        String[] rows = born.split("\n");
        assertEquals("default", rows[0]); // make_born_q2r.py's first line
        assertEquals(3, rows.length);     // epsilon + 1 born tensor
        // '%13.8f' x9 joined by single spaces and NO trailing space
        // (the q2r script's own shape, unlike phonopy-qe-born's trailing-space)
        assertFalse(rows[1].endsWith(" "));
        assertEquals(9, rows[1].trim().split("\\s+").length);
        assertTrue(rows[1].contains("  12.00000000 "));
        assertTrue(rows[2].contains("0.10000000"));
        // the BORN parser reads it back (default -> default factor)
        var reread = QEPhonopyBorn.parseText(born, "BORN-from-q2r");
        assertTrue(reread.isSuccess());
        assertEquals(1, reread.getValue().orElseThrow().getChargeCount());
    }

    @Test
    void fcWithoutNacHasNoBornAndSaysSo() {
        // remove the whole NAC block: T line + 3 eps rows + 1 header + 3 born rows
        int tIdx = miniNac().indexOf(" T\n");
        int dIdx = miniNac().indexOf("   1   1   1\n");
        String stripped = miniNac().substring(0, tIdx) + miniNac().substring(dIdx);
        OperationResult<Q2rFc> result = QEPhonopyQ2rFc.parseText(stripped, "n.fc");
        assertTrue(result.isSuccess(), () -> result.getMessage());
        Q2rFc fc = result.getValue().orElseThrow();
        assertFalse(fc.hasNac());
        assertTrue(fc.getEpsilon().isEmpty());
        assertNull(fc.toBornText());
        assertTrue(result.getMessage().contains("no NAC block"));
        assertTrue(fc.getNotes().stream().anyMatch(n -> n.contains("UNcorrected")));
        assertEquals(9, fc.getBlocks().size());
    }

    @Test
    void gridOrderCensusAndI1Fastest() {
        // natom=2, dim 2x1x1 (ndim=2): 36 blocks x (1 header + 2 data)
        StringBuilder sb = new StringBuilder();
        sb.append("   1    2   0\n");
        sb.append("      5.430000000    0.000000000    0.000000000\n");
        sb.append("      0.000000000    5.430000000    0.000000000\n");
        sb.append("      0.000000000    0.000000000    5.430000000\n");
        sb.append("           1  'Xx '    1000.0\n");
        sb.append("    1    1      0.0000000000      0.0000000000      0.0000000000\n");
        sb.append("    2    1      0.5000000000      0.5000000000      0.5000000000\n");
        sb.append("   2   1   1\n");
        int blockNo = 0;
        for (int k = 1; k <= 3; k++) {
            for (int ll = 1; ll <= 3; ll++) {
                for (int i = 1; i <= 2; i++) {
                    for (int j = 1; j <= 2; j++) {
                        blockNo++;
                        sb.append("   ").append(k).append("   ").append(ll)
                                .append("   ").append(i).append("   ").append(j)
                                .append('\n');
                        // i1 fastest: t=0 -> (1,1,1), t=1 -> (2,1,1)
                        sb.append(String.format("   1   1   1  % .6E%n",
                                0.001 * blockNo));
                        sb.append(String.format("   2   1   1  % .6E%n",
                                0.001 * blockNo + 1.0));
                    }
                }
            }
        }
        OperationResult<Q2rFc> result = QEPhonopyQ2rFc.parseText(sb.toString(), "g.fc");
        assertTrue(result.isSuccess(), () -> result.getMessage());
        Q2rFc fc = result.getValue().orElseThrow();
        assertEquals(2, fc.getNatom());
        assertFalse(fc.hasNac());
        assertEquals(2, fc.getDim()[0]);
        assertEquals(36, fc.getBlocks().size());
        // (k,ll,i,j) ndindex iterates j fastest: index 4 = (1, 2, 1, 1)
        assertEquals("1   2   1   1", fc.getBlocks().get(4).getHeaderLine());
        assertEquals(0.005, fc.getBlocks().get(4).getValues()[0], 1e-12);
        assertEquals(1.005, fc.getBlocks().get(4).getValues()[1], 1e-12);
        // census: every data line in the i1-fastest order
        assertTrue(fc.getNotes().stream().anyMatch(n -> n.contains("72/72")));
        assertEquals(1.036, fc.getMaxAbsElement(), 1e-12);
    }

    @Test
    void shapeRefusalsNeverSkipped() {
        // fewer than 3 tokens on line 1
        assertEquals("PHONOPY_Q2R_HEADER",
                QEPhonopyQ2rFc.parseText("2 2\n", "x").getCode());
        // non-int line 1
        assertEquals("PHONOPY_Q2R_HEADER",
                QEPhonopyQ2rFc.parseText("a b c\n", "x").getCode());
        assertEquals("PHONOPY_Q2R_EMPTY",
                QEPhonopyQ2rFc.parseText("", "x").getCode());
        // corrupted epsilon row
        String badEps = miniNac().replace(
                "         12.000000000000          0.000000000000          0.000000000000",
                "         12.000000000000          nan nan");
        assertEquals("PHONOPY_Q2R_SHAPE",
                QEPhonopyQ2rFc.parseText(badEps, "x").getCode());
        // corrupted data token[3] (the block-1 data row as constructed)
        String badVal = miniNac().replace(" 1.000000E-02", " X");
        assertEquals("PHONOPY_Q2R_SHAPE",
                QEPhonopyQ2rFc.parseText(badVal, "x").getCode());
        // 2-token data line
        String shortRow = miniNac().replace("   1   1   1   1.000000E-02", "   1   1");
        assertEquals("PHONOPY_Q2R_SHAPE",
                QEPhonopyQ2rFc.parseText(shortRow, "x").getCode());
        // wrong dims token count
        String badDims = miniNac().replace("   1   1   1\n   1   1   1   1\n",
                "   1   1\n   1   1   1   1\n");
        assertEquals("PHONOPY_Q2R_SHAPE",
                QEPhonopyQ2rFc.parseText(badDims, "x").getCode());
    }

    @Test
    void livePartialBlocksHeld() {
        // cut the file inside the 5th block's data line
        String full = miniNac();
        int cutHere = full.indexOf("   2   2   1   1\n");
        String live = full.substring(0, cutHere + "   2   2   1   1\n".length());
        OperationResult<Q2rFc> result = QEPhonopyQ2rFc.parseText(live, "live.fc");
        assertTrue(result.isSuccess(), () -> result.getMessage());
        Q2rFc fc = result.getValue().orElseThrow();
        assertEquals(4, fc.getBlocks().size());
        assertEquals(1, fc.getPartialBlocksHeld());
        assertTrue(result.getMessage().contains("4/9"));
        assertTrue(fc.getNotes().stream().anyMatch(n -> n.contains("held back")));
    }
}
