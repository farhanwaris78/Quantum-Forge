/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import quantumforge.operation.OperationResult;

/**
 * Grammar of the thermo_pw elastic-constants outputs (Roadmap: ELATE
 * integration - the thermo_pw input channel). Pinned read-only against the
 * upstream example13 reference tree (scf_elastic_constants on silicon) at
 * commit b73edd6d75b92df80f3a322279c6b12b301b9947:
 *
 * <ul>
 *   <li>{@code elastic_constants/output_el_cons.dat[.gN]} - the per-geometry
 *       constants file. Twelve data lines of alternating 4+2 floats hold the
 *       symmetric 6x6 stiffness in KBAR; after a blank separator, another 12
 *       lines OPTIONALLY hold the 6x6 compliance in 1/Mbar. The file itself
 *       carries no unit text - the units are CROSS-PINNED from the sibling
 *       run stdout of the same upstream example, whose printed blocks read
 *       "Elastic constants C_ij (kbar)" and "Elastic compliances  S_ij
 *       (1/Mbar)" with the same digits; provenance is stated, never guessed;</li>
 *   <li>the run stdout elastic section - the 'i j=' headered
 *       "Elastic constants C_ij (kbar)" block (6 numbered rows of 6 floats),
 *       the compliance block, and the verbatim Voigt / Reuss /
 *       Voigt-Reuss-Hill property prints (Bulk/Young/Shear moduli in kbar,
 *       Poisson ratio), the Hill sound velocities (m/s) and the two Debye
 *       temperature prints. Case-sensitive phrases on purpose: they are
 *       thermo_pw's own verbatim print strings.</li>
 * </ul>
 *
 * <p>Live doctrine matches the series parser: thermo_pw writes one geometry's
 * file when that geometry completes, so ONLY the final line may be a partial
 * append (dropped and counted); a mid-file grammar break is
 * THERMOPW_ELASTIC_CORRUPT with the line number. The bridge to the ELATE
 * engine is a verbatim 6-row text of the stiffness in kbar - declare
 * {@code ElateUnit.KBAR} there (the '10 kbar = 1 GPa' factor is thermo_pw's
 * own printed aid, stated in both classes).</p>
 */
public final class QEThermoPwElasticParser {

    /** Upper bound for one constants file (reference files are bytes). */
    public static final long MAX_FILE_BYTES = 16L * 1024L * 1024L;
    /** Upper bound for a stdout scan. */
    public static final long MAX_STDOUT_BYTES = 32L * 1024L * 1024L;

    private static final Pattern GEOMETRY_TAG = Pattern.compile(
            "output_el_cons\\.dat(?:\\.g(\\d+))?");
    private static final Pattern C_BLOCK_HEADER = Pattern.compile(
            "Elastic constants C_ij \\(kbar\\)");
    private static final Pattern S_BLOCK_HEADER = Pattern.compile(
            "Elastic compliances .*S_ij \\(1/Mbar\\)");
    private static final Pattern VRH_SCHEME = Pattern.compile(
            "^\\s*(Voigt approximation:|Reuss approximation:"
                    + "|Voigt-Reuss-Hill average of the two approximations:)\\s*$");
    private static final Pattern VRH_VALUE = Pattern.compile(
            "^\\s*(Bulk modulus  B|Young modulus E|Shear modulus G|Poisson Ratio n)"
                    + " =\\s*([+-]?[0-9.EeDd+-]+)\\s*(kbar)?\\s*$");
    private static final Pattern SOUND = Pattern.compile(
            "^\\s*(Compressional V_P|Bulk          V_B|Shear         V_G)"
                    + " =\\s*([+-]?[0-9.EeDd+-]+)\\s*m/s\\s*$");
    private static final Pattern DEBYE = Pattern.compile(
            "^\\s*(The approximate Debye temperature is|Debye temperature"
                    + "|  Debye temperature=|Average Debye sound velocity =)"
                    + "\\s*=*\\s*([+-]?[0-9.EeDd+-]+)\\s*(K|m/s)?\\s*$");

    private QEThermoPwElasticParser() {
        // Utility
    }

    /** The parsed constants file (stiffness in kbar, compliance in 1/Mbar). */
    public static final class ElasticConstantsFile {
        private final String sourceName;
        private final Integer geometryTag;
        private final double[][] stiffnessKbar;
        private final double[][] compliancePerMbar;
        private final boolean compliancePresent;
        private final String unitProvenance;

        private ElasticConstantsFile(String sourceName, Integer geometryTag,
                                     double[][] stiffnessKbar,
                                     double[][] compliancePerMbar,
                                     String unitProvenance) {
            this.sourceName = sourceName;
            this.geometryTag = geometryTag;
            this.stiffnessKbar = stiffnessKbar;
            this.compliancePerMbar = compliancePerMbar;
            this.compliancePresent = compliancePerMbar != null;
            this.unitProvenance = unitProvenance;
        }

        public String getSourceName() { return this.sourceName; }
        /** Geometry index from the .gN suffix; null for the unsuffixed name. */
        public Integer getGeometryTag() { return this.geometryTag; }
        /** Symmetric 6x6 stiffness in kbar (cross-pinned unit). */
        public double[][] getStiffnessKbar() { return copy6(this.stiffnessKbar); }
        /** 6x6 compliance in 1/Mbar; null when the file wrote none. */
        public double[][] getCompliancePerMbar() {
            return this.compliancePerMbar == null ? null : copy6(this.compliancePerMbar);
        }
        public boolean hasCompliance() { return this.compliancePresent; }
        /** Where the unit labels came from (stdout cross-pin, stated). */
        public String getUnitProvenance() { return this.unitProvenance; }

        /** Verbatim 6-row text of the stiffness for {@link QEElateAnalyzer} (kbar). */
        public String toElateMatrixText() {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                for (int j = 0; j < 6; j++) {
                    if (j > 0) {
                        text.append(' ');
                    }
                    text.append(String.format(Locale.ROOT, "%.10g",
                            this.stiffnessKbar[i][j]));
                }
                text.append('\n');
            }
            return text.toString();
        }
    }

    /** One Voigt / Reuss / Hill printed property set, tokens verbatim. */
    public static final class StdoutScheme {
        private final String scheme;
        private final String bulkKbar;
        private final String youngKbar;
        private final String shearKbar;
        private final String poisson;
        private final int firstLine;

        private StdoutScheme(String scheme, String bulkKbar, String youngKbar,
                             String shearKbar, String poisson, int firstLine) {
            this.scheme = scheme;
            this.bulkKbar = bulkKbar;
            this.youngKbar = youngKbar;
            this.shearKbar = shearKbar;
            this.poisson = poisson;
            this.firstLine = firstLine;
        }

        public String getScheme() { return this.scheme; }
        /** 'Bulk modulus  B = .. kbar' token, verbatim. */
        public String getBulkKbar() { return this.bulkKbar; }
        public String getYoungKbar() { return this.youngKbar; }
        public String getShearKbar() { return this.shearKbar; }
        public String getPoisson() { return this.poisson; }
        /** 1-based line of the scheme header. */
        public int getFirstLine() { return this.firstLine; }
    }

    /** The parsed stdout elastic section. */
    public static final class ElasticStdout {
        private final String sourceName;
        private final double[][] stiffnessKbar;
        private final int stiffnessFirstLine;
        private final double[][] compliancePerMbar;
        private final List<StdoutScheme> schemes;
        private final List<String> soundTokens;
        private final List<String> debyeTokens;

        private ElasticStdout(String sourceName, double[][] stiffnessKbar,
                              int stiffnessFirstLine, double[][] compliancePerMbar,
                              List<StdoutScheme> schemes, List<String> soundTokens,
                              List<String> debyeTokens) {
            this.sourceName = sourceName;
            this.stiffnessKbar = stiffnessKbar;
            this.stiffnessFirstLine = stiffnessFirstLine;
            this.compliancePerMbar = compliancePerMbar;
            this.schemes = schemes;
            this.soundTokens = soundTokens;
            this.debyeTokens = debyeTokens;
        }

        public String getSourceName() { return this.sourceName; }
        /** 6x6 stiffness in kbar from the LAST 'Elastic constants C_ij (kbar)' block. */
        public double[][] getStiffnessKbar() { return copy6(this.stiffnessKbar); }
        /** 1-based line of the block header that the matrix was taken from. */
        public int getStiffnessFirstLine() { return this.stiffnessFirstLine; }
        /** 6x6 compliance in 1/Mbar; null when no compliance block followed. */
        public double[][] getCompliancePerMbar() {
            return this.compliancePerMbar == null ? null : copy6(this.compliancePerMbar);
        }
        /** Voigt / Reuss / Hill printed sets (tokens verbatim; may be empty live). */
        public List<StdoutScheme> getSchemes() { return this.schemes; }
        /** Sound-velocity tokens in print order ('8751.406 m/s' style, no unit text). */
        public List<String> getSoundTokens() { return this.soundTokens; }
        /** Debye temperature tokens in print order (K). */
        public List<String> getDebyeTokens() { return this.debyeTokens; }

        /** Verbatim 6-row text of the stiffness for {@link QEElateAnalyzer} (kbar). */
        public String toElateMatrixText() {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                for (int j = 0; j < 6; j++) {
                    if (j > 0) {
                        text.append(' ');
                    }
                    text.append(String.format(Locale.ROOT, "%.10g",
                            this.stiffnessKbar[i][j]));
                }
                text.append('\n');
            }
            return text.toString();
        }
    }

    /**
     * Parses one {@code output_el_cons.dat[.gN]} file. Verdicts:
     * THERMOPW_ELASTIC_INPUT (null/missing/oversized/not the pinned name),
     * THERMOPW_ELASTIC_SHAPE (a grammar break before the final line),
     * THERMOPW_ELASTIC_PARTIAL (only a partial trailing block - the run is
     * still writing), THERMOPW_ELASTIC_EMPTY, THERMOPW_ELASTIC_OK.
     */
    public static OperationResult<ElasticConstantsFile> parseElasticConstantsFile(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return OperationResult.failed("THERMOPW_ELASTIC_INPUT",
                    "Not a regular file: " + file, null);
        }
        String name = file.getFileName().toString();
        Matcher tag = GEOMETRY_TAG.matcher(name);
        if (!tag.matches()) {
            return OperationResult.failed("THERMOPW_ELASTIC_INPUT",
                    "Not the pinned elastic-constants name (output_el_cons.dat[.gN]): "
                            + name, null);
        }
        long size;
        try {
            size = Files.size(file);
        } catch (IOException ex) {
            return OperationResult.failed("THERMOPW_ELASTIC_INPUT",
                    "Size unreadable for " + file + ": " + ex.getMessage(), null);
        }
        if (size > MAX_FILE_BYTES) {
            return OperationResult.failed("THERMOPW_ELASTIC_INPUT",
                    name + " exceeds the " + MAX_FILE_BYTES
                            + "-byte bound; refusing an unbounded read.", null);
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException | RuntimeException ex) {
            return OperationResult.failed("THERMOPW_ELASTIC_INPUT",
                    "Could not read " + file + " as UTF-8 text: " + ex.getMessage(), null);
        }
        List<double[]> numericRows = new ArrayList<>();
        List<Integer> numericLineNumbers = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] tokens = trimmed.split("\\s+");
            double[] row = new double[tokens.length];
            boolean ok = true;
            for (int t = 0; t < tokens.length; t++) {
                row[t] = QEThermoPwSeriesParser.parseFortranDouble(tokens[t]);
                if (Double.isNaN(row[t]) || !Double.isFinite(row[t])) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                return OperationResult.failed("THERMOPW_ELASTIC_SHAPE",
                        name + " line " + (i + 1) + " is not a row of finite numbers"
                                + " ('" + (trimmed.length() <= 40 ? trimmed
                                        : trimmed.substring(0, 37) + "...")
                                + "') - this file has no header or comment grammar, so a"
                                + " non-number is a corrupt constants file, never skipped.",
                        null);
            }
            numericRows.add(row);
            numericLineNumbers.add(Integer.valueOf(i + 1));
        }
        if (numericRows.isEmpty()) {
            return OperationResult.failed("THERMOPW_ELASTIC_EMPTY",
                    name + " contains no numeric rows yet (the geometry has not started"
                            + " writing constants).", null);
        }
        // Live rule: blocks complete 6 rows; a trailing partial BLOCK (mid-run)
        // is dropped honestly, a mid-file ragged row is a shape error.
        int[] widths = new int[numericRows.size()];
        for (int i = 0; i < numericRows.size(); i++) {
            widths[i] = numericRows.get(i).length;
        }
        List<double[][]> blocks = new ArrayList<>();
        int cursor = 0;
        while (cursor < widths.length && blocks.size() < 2) {
            int[] rowShape = new int[12];
            boolean blockOk = true;
            int blockEnd = Math.min(cursor + 12, widths.length);
            for (int r = cursor; r < blockEnd; r++) {
                int expectedWidth = ((r - cursor) % 2 == 0) ? 4 : 2;
                if (widths[r] != expectedWidth) {
                    boolean lastLineIsPartial = numericLineNumbers
                            .get(r).intValue() == lines.size();
                    if (lastLineIsPartial) {
                        blockOk = false;
                        break; // running write: block completes later
                    }
                    return OperationResult.failed("THERMOPW_ELASTIC_SHAPE",
                            name + " line " + numericLineNumbers.get(r)
                                    + ": expected " + expectedWidth + " numbers in this"
                                    + " alternating 4+2 grammar, found " + widths[r]
                                    + " (mid-file rows are never silently dropped).", null);
                }
                rowShape[r - cursor] = 1;
            }
            int shapeCount = 0;
            for (int s : rowShape) {
                shapeCount += s;
            }
            if (shapeCount < 12) {
                if (blockOk) {
                    if (blocks.isEmpty()) {
                        return OperationResult.failed("THERMOPW_ELASTIC_PARTIAL",
                                name + " holds " + shapeCount
                                        + "/12 lines of one 6x6 block so far - the run is"
                                        + " still writing it; nothing is drawn half-way.",
                                null);
                    }
                    break; // trailing partial second block: keep the full first one
                }
                break;
            }
            blocks.add(assembleBlock(numericRows, cursor));
            cursor += 12;
        }
        if (blocks.isEmpty()) {
            return OperationResult.failed("THERMOPW_ELASTIC_EMPTY",
                    name + " yielded no complete 6x6 block.", null);
        }
        double[][] stiffness = blocks.get(0);
        double[][] compliance = blocks.size() > 1 ? blocks.get(1) : null;
        Integer geometryTag = tag.group(1) == null ? null : Integer.valueOf(tag.group(1));
        return OperationResult.success("THERMOPW_ELASTIC_OK",
                "6x6 stiffness (kbar) parsed" + (compliance != null
                        ? " + 6x6 compliance (1/Mbar)" : "") + ".",
                new ElasticConstantsFile(name, geometryTag, stiffness, compliance,
                        "the file itself carries no unit text: kbar (stiffness) and 1/Mbar"
                                + " (compliance) are cross-pinned from the sibling run stdout's"
                                + " printed 'Elastic constants C_ij (kbar)' / 'Elastic"
                                + " compliances  S_ij (1/Mbar)' blocks of the same upstream"
                                + " example13 (si.elastic.out, commit b73edd6d) - stated,"
                                + " never guessed"));
    }

    private static double[][] assembleBlock(List<double[]> rows, int cursor) {
        double[][] block = new double[6][6];
        for (int r = 0; r < 12; r += 2) {
            double[] first = rows.get(cursor + r);
            double[] second = rows.get(cursor + r + 1);
            int matrixRow = r / 2;
            System.arraycopy(first, 0, block[matrixRow], 0, 4);
            System.arraycopy(second, 0, block[matrixRow], 4, 2);
        }
        return block;
    }

    /**
     * Extracts the LAST 'Elastic constants C_ij (kbar)' block from a run
     * stdout, plus the printed Voigt/Reuss/Hill property sets and the sound
     * velocity / Debye prints (tokens verbatim with line numbers). Verdicts:
     * THERMOPW_ELASTIC_INPUT (null/missing/oversized), THERMOPW_ELASTIC_HEADER
     * (no constants block - a wrong file selection, never guessed),
     * THERMOPW_ELASTIC_SHAPE (a ragged row inside the block),
     * THERMOPW_ELASTIC_OK.
     */
    public static OperationResult<ElasticStdout> parseElasticStdout(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return OperationResult.failed("THERMOPW_ELASTIC_INPUT",
                    "Not a regular file: " + file, null);
        }
        long size;
        try {
            size = Files.size(file);
        } catch (IOException ex) {
            return OperationResult.failed("THERMOPW_ELASTIC_INPUT",
                    "Size unreadable for " + file + ": " + ex.getMessage(), null);
        }
        if (size > MAX_STDOUT_BYTES) {
            return OperationResult.failed("THERMOPW_ELASTIC_INPUT",
                    file.getFileName() + " exceeds the " + MAX_STDOUT_BYTES
                            + "-byte stdout bound; refusing an unbounded read.", null);
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException | RuntimeException ex) {
            return OperationResult.failed("THERMOPW_ELASTIC_INPUT",
                    "Could not read " + file + " as UTF-8 text: " + ex.getMessage(),
                    null);
        }
        String name = file.getFileName().toString();
        int lastHeader = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (C_BLOCK_HEADER.matcher(lines.get(i)).find()) {
                lastHeader = i;
            }
        }
        if (lastHeader < 0) {
            return OperationResult.failed("THERMOPW_ELASTIC_HEADER",
                    name + " carries no 'Elastic constants C_ij (kbar)' block - wrong"
                            + " file selection or a format this build has not pinned;",
                    null);
        }
        double[][] stiffness = new double[6][6];
        int rowsFound = 0;
        for (int r = 0; r < 6; r++) {
            int i = lastHeader + 2 + r; // header, then the 'i j=' line, then 6 rows
            if (i >= lines.size()) {
                break;
            }
            String[] tokens = lines.get(i).trim().split("\\s+");
            if (tokens.length == 6 && r > 0) {
                // tolerate an absent row-index column on continuation (grammar guard)
                return OperationResult.failed("THERMOPW_ELASTIC_SHAPE",
                        name + " line " + (i + 1) + " lost the row-index column of the"
                                + " 7-token 'i j=' row grammar - not silently tolerated.",
                        null);
            }
            if (tokens.length != 7) {
                if (rowsFound == 0) {
                    break; // live run: block header just printed, rows not yet written
                }
                return OperationResult.failed("THERMOPW_ELASTIC_SHAPE",
                        name + " line " + (i + 1) + ": expected the 7-token 'index + 6"
                                + " values' row grammar, found " + tokens.length + ".",
                        null);
            }
            double[] values = new double[6];
            boolean ok = true;
            for (int t = 1; t < 7; t++) {
                values[t - 1] = QEThermoPwSeriesParser.parseFortranDouble(tokens[t]);
                if (Double.isNaN(values[t - 1]) || !Double.isFinite(values[t - 1])) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                return OperationResult.failed("THERMOPW_ELASTIC_SHAPE",
                        name + " line " + (i + 1) + " holds a non-number inside the"
                                + " constants block - refused, never skipped.", null);
            }
            System.arraycopy(values, 0, stiffness[rowsFound], 0, 6);
            rowsFound++;
        }
        if (rowsFound < 6) {
            return OperationResult.failed("THERMOPW_ELASTIC_PARTIAL",
                    name + " has the block header at line " + (lastHeader + 1)
                            + " but only " + rowsFound + "/6 rows so far - the run is"
                            + " still writing; nothing is completed by guesswork.", null);
        }
        double[][] compliance = null;
        for (int i = lastHeader + 8; i < Math.min(lines.size(), lastHeader + 60); i++) {
            if (S_BLOCK_HEADER.matcher(lines.get(i)).find()) {
                double[][] block = new double[6][6];
                int rows = 0;
                for (int r = 0; r < 6; r++) {
                    int j = i + 2 + r;
                    if (j >= lines.size()) {
                        break;
                    }
                    String[] tokens = lines.get(j).trim().split("\\s+");
                    if (tokens.length != 7) {
                        break;
                    }
                    boolean ok = true;
                    for (int t = 1; t < 7; t++) {
                        double value = QEThermoPwSeriesParser.parseFortranDouble(tokens[t]);
                        if (Double.isNaN(value) || !Double.isFinite(value)) {
                            ok = false;
                            break;
                        }
                        block[rows][t - 1] = value;
                    }
                    if (!ok) {
                        break;
                    }
                    rows++;
                }
                if (rows == 6) {
                    compliance = block;
                }
                break;
            }
        }
        List<StdoutScheme> schemes = new ArrayList<>();
        List<String> soundTokens = new ArrayList<>();
        List<String> debyeTokens = new ArrayList<>();
        for (int i = lastHeader; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher schemeMatcher = VRH_SCHEME.matcher(line);
            if (schemeMatcher.find()) {
                String scheme = schemeMatcher.group(1).contains("Voigt-Reuss-Hill")
                        ? "Voigt-Reuss-Hill" : (schemeMatcher.group(1).contains("Reuss")
                                ? "Reuss" : "Voigt");
                String bulk = null;
                String young = null;
                String shear = null;
                String poisson = null;
                for (int j = i + 1; j < Math.min(lines.size(), i + 8); j++) {
                    Matcher value = VRH_VALUE.matcher(lines.get(j));
                    if (value.find()) {
                        String field = value.group(1);
                        if (field.startsWith("Bulk")) {
                            bulk = value.group(2);
                        } else if (field.startsWith("Young")) {
                            young = value.group(2);
                        } else if (field.startsWith("Shear")) {
                            shear = value.group(2);
                        } else if (field.startsWith("Poisson")) {
                            poisson = value.group(2);
                        }
                    }
                }
                if (bulk != null && young != null && shear != null && poisson != null) {
                    schemes.add(new StdoutScheme(scheme, bulk, young, shear, poisson,
                            i + 1));
                }
                continue;
            }
            Matcher sound = SOUND.matcher(line);
            if (sound.find()) {
                soundTokens.add(sound.group(2));
                continue;
            }
            Matcher debye = DEBYE.matcher(line);
            if (debye.find()) {
                debyeTokens.add(debye.group(2));
            }
        }
        return OperationResult.success("THERMOPW_ELASTIC_OK",
                "C_ij block (lines " + (lastHeader + 1) + "..)" + (compliance != null
                        ? " + S_ij block" : "") + "; " + schemes.size()
                        + " printed scheme set(s), " + soundTokens.size()
                        + " sound-velocity token(s), " + debyeTokens.size()
                        + " Debye token(s).",
                new ElasticStdout(name, stiffness, lastHeader + 1, compliance,
                        List.copyOf(schemes), List.copyOf(soundTokens),
                        List.copyOf(debyeTokens)));
    }

    private static double[][] copy6(double[][] matrix) {
        if (matrix == null) {
            return null;
        }
        double[][] out = new double[6][6];
        for (int i = 0; i < 6; i++) {
            System.arraycopy(matrix[i], 0, out[i], 0, 6);
        }
        return out;
    }
}
