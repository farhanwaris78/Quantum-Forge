/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import quantumforge.operation.OperationResult;
import quantumforge.run.parser.QEThermoPwSeriesParser.Series;
import quantumforge.run.parser.QEThermoPwSeriesParser.SeriesKind;

/**
 * Directory-level census of a thermo_pw run (Roadmap: thermo_pw doc
 * integration). The layout follows the project's own fetched user guide
 * (&sect;2.5: {@code thermo_control} is mandatory, {@code ph_control} when the
 * task needs it) and the upstream reference run trees of examples 04/05/09 at
 * commit b73edd6d75b92df80f3a322279c6b12b301b9947:
 *
 * <ul>
 *   <li>{@code thermo_control} - the &INPUT_THERMO control namelist
 *       (what= workflow selector, ngeo grid, lmurn, pressure, deltat);</li>
 *   <li>{@code energy_files/output_ev.dat[..._mur]} - mur_lc E(V) and EOS fit;</li>
 *   <li>{@code therm_files/output_therm.dat.gN[(.gN)_ph]} - per-geometry
 *       harmonic thermodynamics appearing as geometries complete;</li>
 *   <li>{@code anhar_files/output_anhar.dat[.therm|.bulk_mod|.heat|.gamma]} -
 *       the quasi-harmonic (mur_lc_t) result series, plus the ph-route
 *       {@code (_ph)} counterparts, {@code .dbulk_mod[(_ph)]},
 *       {@code .aux_grun}, {@code .gamma_grun}, and the
 *       {@code output_pgrun.dat[_freq].I[.J]} 2-column plot rows (their
 *       dotted index tail is preserved verbatim; its band-vs-segment
 *       semantics is deliberately not asserted);</li>
 *   <li>{@code restart/e_work_part.*} - one finished-task energy per file,
 *       the honest live task counter (never an ETA invented from nothing);</li>
 *   <li>top-level {@code *.out} stdout files - scanned for VERBATIM extracts
 *       only: the {@code unit-cell volume = ... (a.u.)^3} prints and the
 *       dashed EOS summary block ('The equilibrium lattice constant is ..
 *       a.u.', 'The bulk modulus is .. kbar', 'The pressure derivative of
 *       the bulk modulus is ..', 'The total energy at the minimum is: ..
 *       Ry'). Units come from the lines' own suffix text; a partially
 *       written block (live run) is reported as n-of-4 lines, never
 *       completed by guesswork. Oversized stdout is named, not parsed.</li>
 * </ul>
 *
 * <p>The scanner only ENUMERATES and never repairs: files outside the
 * supported set are counted as uninterpreted (their names are reported, not
 * parsed), a missing thermo_control flips an explicit flag, and the parsed
 * control values are verbatim extracts - ngeo totals are NOT fabricated when
 * the control omits them, the caller gets the raw extracts and states the
 * difference.</p>
 */
public final class QEThermoPwRunScanner {

    /** Upper bound for the control file read. */
    public static final long MAX_CONTROL_BYTES = 256L * 1024L;
    /** Upper bound when listing any artifact directory. */
    public static final int MAX_DIR_ENTRIES = 100_000;
    /** Upper bound per stdout file before it is named oversized and left unparsed. */
    public static final long MAX_STDOUT_BYTES = 32L * 1024L * 1024L;
    /** Upper bound on top-level *.out files scanned per run directory. */
    public static final int MAX_STDOUT_FILES = 32;
    /** Upper bound on unit-cell-volume values kept per stdout file. */
    public static final int MAX_VOLUME_PRINTS = 10_000;

    private static final Pattern WHAT = Pattern.compile(
            "(?i)\\bwhat\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern LMURN = Pattern.compile(
            "(?i)\\blmurn\\s*=\\s*\\.(true|false)\\.");
    private static final Pattern PRESSURE = Pattern.compile(
            "(?i)\\bpress\\w*\\s*=\\s*([+-]?[0-9.eEeDd+]+)");
    private static final Pattern DELTAT = Pattern.compile(
            "(?i)\\bdeltat\\s*=\\s*([+-]?[0-9.eEeDd+]+)");
    private static final Pattern NGEO = Pattern.compile(
            "(?i)\\bngeo\\((\\d)\\)\\s*=\\s*(\\d+)");
    private static final Pattern THERM_GEO = Pattern.compile(
            "output_therm\\.dat\\.g(\\d+)(_ph)?");
    private static final Pattern RESTART = Pattern.compile("e_work_part\\.(\\d+)\\.(\\d+)");
    private static final Pattern PGRUN = Pattern.compile(
            "output_pgrun\\.dat(_freq)?\\.(\\d+(?:\\.\\d+)?)");
    // stdout extracts: case-sensitive on purpose - these phrases are thermo_pw's
    // own verbatim prints (upstream example05 si.mur_lc.out, commit b73edd6d).
    private static final Pattern VOLUME = Pattern.compile(
            "unit-cell volume\\s*=\\s+([+-]?[0-9.EeDd+-]+)\\s+\\(a\\.u\\.\\)\\^3");
    private static final Pattern EOS_LATTICE = Pattern.compile(
            "The equilibrium lattice constant is\\s+([+-]?[0-9.EeDd+-]+)\\s+a\\.u\\.");
    private static final Pattern EOS_BULK = Pattern.compile(
            "The bulk modulus is\\s+([+-]?[0-9.EeDd+-]+)\\s+kbar");
    private static final Pattern EOS_DERIV = Pattern.compile(
            "The pressure derivative of the bulk modulus is\\s+([+-]?[0-9.EeDd+-]+)\\s*$");
    private static final Pattern EOS_EMIN = Pattern.compile(
            "The total energy at the minimum is:\\s+([+-]?[0-9.EeDd+-]+)\\s+Ry");

    private QEThermoPwRunScanner() {
        // Utility
    }

    /** Verbatim extracts from thermo_control (absent keywords stay absent). */
    public static final class ControlInfo {
        private final boolean present;
        private final String what;
        private final Boolean lmurn;
        private final Double pressureKbar;
        private final Double deltatK;
        private final int[] ngeo;
        private final String parseNote;

        private ControlInfo(boolean present, String what, Boolean lmurn, Double pressureKbar,
                            Double deltatK, int[] ngeo, String parseNote) {
            this.present = present;
            this.what = what;
            this.lmurn = lmurn;
            this.pressureKbar = pressureKbar;
            this.deltatK = deltatK;
            this.ngeo = ngeo;
            this.parseNote = parseNote;
        }

        public boolean isPresent() { return this.present; }
        /** what= verbatim (e.g. mur_lc_t), null when the control omits it. */
        public String getWhat() { return this.what; }
        /** lmurn verbatim when written, null otherwise (never defaulted here). */
        public Boolean getLmurn() { return this.lmurn; }
        /** pressure= verbatim in kbar when written (keyword unit), null otherwise. */
        public Double getPressureKbar() { return this.pressureKbar; }
        /** deltat= verbatim in K when written (keyword unit), null otherwise. */
        public Double getDeltatK() { return this.deltatK; }
        /** Explicit ngeo(i) values, zero where not written. */
        public int[] getNgeo() { return this.ngeo.clone(); }
        /** Any explicit-total product when every ngeo(i) slot was written; -1 otherwise. */
        public long getExplicitNgeoProduct() {
            long product = 1L;
            for (int value : this.ngeo) {
                if (value <= 0) {
                    return -1L;
                }
                product *= value;
            }
            return product;
        }
        public String getParseNote() { return this.parseNote; }
    }

    /** One enumerated artifact of the run directory. */
    public static final class Artifact {
        private final Path path;
        private final String role;
        private final SeriesKind kind;
        private final Integer geometryTag;
        private final boolean phVariant;
        private final String suffixTag;

        private Artifact(Path path, String role, SeriesKind kind, Integer geometryTag,
                         boolean phVariant) {
            this(path, role, kind, geometryTag, phVariant, null);
        }

        private Artifact(Path path, String role, SeriesKind kind, Integer geometryTag,
                         boolean phVariant, String suffixTag) {
            this.path = path;
            this.role = role;
            this.kind = kind;
            this.geometryTag = geometryTag;
            this.phVariant = phVariant;
            this.suffixTag = suffixTag;
        }

        public Path getPath() { return this.path; }
        /** Directory role: energy_files / therm_files / anhar_files / control. */
        public String getRole() { return this.role; }
        /** Parseable kind; the control artifact reports null. */
        public SeriesKind getKind() { return this.kind; }
        /** Geometry index parsed from .gN names; null otherwise. */
        public Integer getGeometryTag() { return this.geometryTag; }
        /** The (.gN)_ph variant flag. */
        public boolean isPhVariant() { return this.phVariant; }
        /**
         * The verbatim dotted index tail of an output_pgrun.dat[_freq].I[.J] name
         * (e.g. "1.1" or "5"); null for every other artifact. Whether the
         * numbers are bands or path segments is deliberately NOT asserted.
         */
        public String getSuffixTag() { return this.suffixTag; }
    }

    /** One restart token: which file, which task value (the task energy in Ry). */
    public static final class RestartToken {
        private final Path path;
        private final int first;
        private final int second;
        private final double valueRy;

        private RestartToken(Path path, int first, int second, double valueRy) {
            this.path = path;
            this.first = first;
            this.second = second;
            this.valueRy = valueRy;
        }

        public Path getPath() { return this.path; }
        public int getFirstIndex() { return this.first; }
        public int getSecondIndex() { return this.second; }
        /** The single leading number of the token file: the task energy in Ry. */
        public double getValueRy() { return this.valueRy; }
    }

    /**
     * Verbatim stdout extracts of one top-level {@code *.out} file. Every
     * captured value keeps BOTH the raw token (displayed verbatim) and the
     * parsed double; line numbers are 1-based absolute inside the file. A
     * live run appends the EOS block near the end, so a partial block is
     * reported as the count of lines found so far - it is never completed
     * by guesswork, and the LAST occurrence wins when a restarted run
     * prints a block more than once.
     */
    public static final class StdoutSummary {
        private final Path path;
        private final boolean oversized;
        private final int unitCellVolumeCount;
        private final List<Double> unitCellVolumesAU;
        private final String latticeConstantToken;
        private final String bulkModulusToken;
        private final String derivativeToken;
        private final String minEnergyToken;
        private final int latticeConstantLine;
        private final int bulkModulusLine;
        private final int derivativeLine;
        private final int minEnergyLine;

        private StdoutSummary(Path path, boolean oversized, int unitCellVolumeCount,
                              List<Double> unitCellVolumesAU,
                              String latticeConstantToken, String bulkModulusToken,
                              String derivativeToken, String minEnergyToken,
                              int latticeConstantLine, int bulkModulusLine,
                              int derivativeLine, int minEnergyLine) {
            this.path = path;
            this.oversized = oversized;
            this.unitCellVolumeCount = unitCellVolumeCount;
            this.unitCellVolumesAU = unitCellVolumesAU;
            this.latticeConstantToken = latticeConstantToken;
            this.bulkModulusToken = bulkModulusToken;
            this.derivativeToken = derivativeToken;
            this.minEnergyToken = minEnergyToken;
            this.latticeConstantLine = latticeConstantLine;
            this.bulkModulusLine = bulkModulusLine;
            this.derivativeLine = derivativeLine;
            this.minEnergyLine = minEnergyLine;
        }

        public Path getPath() { return this.path; }
        /** True when the file exceeded {@link #MAX_STDOUT_BYTES} and was left unparsed. */
        public boolean isOversized() { return this.oversized; }
        /** Count of the 'unit-cell volume = .. (a.u.)^3' prints (one per geometry). */
        public int getUnitCellVolumeCount() { return this.unitCellVolumeCount; }
        /** Captured unit-cell volumes in (a.u.)^3, in print order. */
        public List<Double> getUnitCellVolumesAU() { return this.unitCellVolumesAU; }
        /** Raw token of 'The equilibrium lattice constant is .. a.u.'; null when absent. */
        public String getLatticeConstantToken() { return this.latticeConstantToken; }
        /** Raw token of 'The bulk modulus is .. kbar'; null when absent. */
        public String getBulkModulusToken() { return this.bulkModulusToken; }
        /** Raw token of 'The pressure derivative of the bulk modulus is ..'; null when absent. */
        public String getDerivativeToken() { return this.derivativeToken; }
        /** Raw token of 'The total energy at the minimum is: .. Ry'; null when absent. */
        public String getMinEnergyToken() { return this.minEnergyToken; }
        /** 1-based line of the lattice-constant extract; -1 when absent. */
        public int getLatticeConstantLine() { return this.latticeConstantLine; }
        /** 1-based line of the bulk-modulus extract; -1 when absent. */
        public int getBulkModulusLine() { return this.bulkModulusLine; }
        /** 1-based line of the pressure-derivative extract; -1 when absent. */
        public int getDerivativeLine() { return this.derivativeLine; }
        /** 1-based line of the minimum-energy extract; -1 when absent. */
        public int getMinEnergyLine() { return this.minEnergyLine; }

        /** How many of the four EOS summary lines were found so far (0..4). */
        public int getEosLineCount() {
            int count = 0;
            if (this.latticeConstantToken != null) {
                count++;
            }
            if (this.bulkModulusToken != null) {
                count++;
            }
            if (this.derivativeToken != null) {
                count++;
            }
            if (this.minEnergyToken != null) {
                count++;
            }
            return count;
        }

        /** All four EOS summary lines are present. */
        public boolean isEosComplete() {
            return getEosLineCount() == 4;
        }

        /** Lowest 1-based EOS block line found; -1 when none. */
        public int getEosFirstLine() {
            int first = Integer.MAX_VALUE;
            for (int line : new int[] {this.latticeConstantLine, this.bulkModulusLine,
                    this.derivativeLine, this.minEnergyLine}) {
                if (line > 0) {
                    first = Math.min(first, line);
                }
            }
            return first == Integer.MAX_VALUE ? -1 : first;
        }
    }

    /** The whole census. */
    public static final class ThermoScan {
        private final Path runDir;
        private final ControlInfo control;
        private final List<Artifact> artifacts;
        private final List<RestartToken> restartTokens;
        private final List<String> uninterpreted;
        private final List<StdoutSummary> stdoutSummaries;

        private ThermoScan(Path runDir, ControlInfo control, List<Artifact> artifacts,
                           List<RestartToken> restartTokens, List<String> uninterpreted,
                           List<StdoutSummary> stdoutSummaries) {
            this.runDir = runDir;
            this.control = control;
            this.artifacts = artifacts;
            this.restartTokens = restartTokens;
            this.uninterpreted = uninterpreted;
            this.stdoutSummaries = stdoutSummaries;
        }

        public Path getRunDir() { return this.runDir; }
        public ControlInfo getControl() { return this.control; }
        public List<Artifact> getArtifacts() { return this.artifacts; }
        public List<RestartToken> getRestartTokens() { return this.restartTokens; }
        public int getRestartCount() { return this.restartTokens.size(); }
        /** Files present but outside the pinned set: named, never parsed. */
        public List<String> getUninterpreted() { return this.uninterpreted; }
        /** Verbatim extracts of the top-level *.out files (sorted by name). */
        public List<StdoutSummary> getStdoutSummaries() { return this.stdoutSummaries; }

        public List<Artifact> artifactsOfKind(SeriesKind kind) {
            List<Artifact> selected = new ArrayList<>();
            for (Artifact artifact : this.artifacts) {
                if (artifact.getKind() == kind) {
                    selected.add(artifact);
                }
            }
            return selected;
        }
    }

    /** Enumerates the run directory; null/absent pieces are explicit, never errors. */
    public static ThermoScan scan(Path runDir) {
        ControlInfo control = readControl(runDir == null ? null
                : runDir.resolve("thermo_control"));
        List<Artifact> artifacts = new ArrayList<>();
        List<RestartToken> restartTokens = new ArrayList<>();
        List<String> uninterpreted = new ArrayList<>();
        List<StdoutSummary> stdoutSummaries = new ArrayList<>();
        if (runDir == null || !Files.isDirectory(runDir)) {
            return new ThermoScan(runDir, control, artifacts, restartTokens, uninterpreted,
                    stdoutSummaries);
        }
        if (Files.isRegularFile(runDir.resolve("thermo_control"))) {
            artifacts.add(new Artifact(runDir.resolve("thermo_control"), "control", null,
                    null, false));
        }
        scanEnergyFiles(runDir.resolve("energy_files"), artifacts, uninterpreted);
        scanThermFiles(runDir.resolve("therm_files"), artifacts, uninterpreted);
        scanAnharFiles(runDir.resolve("anhar_files"), artifacts, uninterpreted);
        scanRestart(runDir.resolve("restart"), restartTokens, uninterpreted);
        scanStdoutFiles(runDir, stdoutSummaries);
        artifacts.sort(Comparator.comparing(a -> a.getPath().toString()));
        stdoutSummaries.sort(Comparator.comparing(s -> s.getPath().toString()));
        return new ThermoScan(runDir, control, artifacts, restartTokens, uninterpreted,
                stdoutSummaries);
    }

    /** Cheap change signature for live polling: size+mtime (-1/-1 when absent). */
    public static long[] signature(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return new long[] {-1L, -1L};
        }
        try {
            return new long[] {Files.size(file),
                    Files.getLastModifiedTime(file).toMillis()};
        } catch (IOException ex) {
            return new long[] {-2L, -2L};
        }
    }

    /** Load helper binding scanner and parser without duplicating either side. */
    public static OperationResult<Series> loadSeries(Artifact artifact) {
        if (artifact == null || artifact.getKind() == null) {
            return OperationResult.failed("THERMOPW_INPUT",
                    "That artifact is not a parseable series (control files and"
                            + " uninterpreted sidecars are excluded by design).", null);
        }
        return QEThermoPwSeriesParser.parse(artifact.getPath(), artifact.getKind());
    }

    private static void scanEnergyFiles(Path dir, List<Artifact> artifacts,
                                        List<String> uninterpreted) {
        for (Path file : listRegular(dir)) {
            String name = file.getFileName().toString();
            List<SeriesKind> kinds = QEThermoPwSeriesParser.candidateKinds(name);
            if (name.equals("output_ev.dat") || name.equals("output_ev.dat_mur")) {
                artifacts.add(new Artifact(file, "energy_files", kinds.get(0), null, false));
            } else {
                uninterpreted.add("energy_files/" + name);
            }
        }
    }

    private static void scanThermFiles(Path dir, List<Artifact> artifacts,
                                       List<String> uninterpreted) {
        for (Path file : listRegular(dir)) {
            String name = file.getFileName().toString();
            Matcher geo = THERM_GEO.matcher(name);
            if (geo.matches()) {
                artifacts.add(new Artifact(file, "therm_files", SeriesKind.THERMO_HARMONIC,
                        Integer.valueOf(geo.group(1)), geo.group(2) != null));
            } else {
                uninterpreted.add("therm_files/" + name);
            }
        }
    }

    private static void scanAnharFiles(Path dir, List<Artifact> artifacts,
                                       List<String> uninterpreted) {
        for (Path file : listRegular(dir)) {
            String name = file.getFileName().toString();
            List<SeriesKind> kinds = QEThermoPwSeriesParser.candidateKinds(name);
            if (!kinds.isEmpty() && (name.startsWith("output_anhar.dat")
                    || name.startsWith("output_pgrun.dat"))) {
                Matcher pgrun = PGRUN.matcher(name);
                String suffixTag = pgrun.matches() ? pgrun.group(2) : null;
                artifacts.add(new Artifact(file, "anhar_files", kinds.get(0), null,
                        name.endsWith("_ph"), suffixTag));
            } else {
                uninterpreted.add("anhar_files/" + name);
            }
        }
    }

    /**
     * Reads the top-level {@code *.out} files for the verbatim print
     * extracts. Bounded on both ends: at most {@link #MAX_STDOUT_FILES}
     * files (the rest are named in the summaries as unparsed would be a
     * lie - they are simply not reached, so the bound is stated in the
     * class docs and the census is name-sorted so the cut is stable), and
     * one oversized file is recorded as oversized instead of read.
     */
    private static void scanStdoutFiles(Path runDir, List<StdoutSummary> summaries) {
        List<Path> outs = new ArrayList<>();
        for (Path file : listRegular(runDir)) {
            if (file.getFileName().toString().endsWith(".out")) {
                outs.add(file);
            }
        }
        outs.sort(Comparator.comparing(p -> p.getFileName().toString()));
        for (int i = 0; i < outs.size() && i < MAX_STDOUT_FILES; i++) {
            summaries.add(readStdoutSummary(outs.get(i)));
        }
    }

    private static StdoutSummary readStdoutSummary(Path file) {
        long size;
        try {
            size = Files.size(file);
        } catch (IOException ex) {
            return new StdoutSummary(file, true, 0, List.of(), null, null, null, null,
                    -1, -1, -1, -1);
        }
        if (size > MAX_STDOUT_BYTES) {
            return new StdoutSummary(file, true, 0, List.of(), null, null, null, null,
                    -1, -1, -1, -1);
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file); // UTF-8; a binary stdout fails loudly to empty
        } catch (IOException | RuntimeException ex) {
            return new StdoutSummary(file, false, 0, List.of(), null, null, null, null,
                    -1, -1, -1, -1);
        }
        int volumeCount = 0;
        List<Double> volumes = new ArrayList<>();
        String lattice = null;
        String bulk = null;
        String deriv = null;
        String emin = null;
        int latticeLine = -1;
        int bulkLine = -1;
        int derivLine = -1;
        int eminLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher volume = VOLUME.matcher(line);
            if (volume.find()) {
                double value = QEThermoPwSeriesParser.parseFortranDouble(volume.group(1));
                volumeCount++;
                if (!Double.isNaN(value) && volumes.size() < MAX_VOLUME_PRINTS) {
                    volumes.add(Double.valueOf(value));
                }
            }
            Matcher lat = EOS_LATTICE.matcher(line);
            if (lat.find()) {
                lattice = lat.group(1);
                latticeLine = i + 1; // LAST occurrence wins (a restarted run reprints)
            }
            Matcher mod = EOS_BULK.matcher(line);
            if (mod.find()) {
                bulk = mod.group(1);
                bulkLine = i + 1;
            }
            Matcher der = EOS_DERIV.matcher(line);
            if (der.find()) {
                deriv = der.group(1);
                derivLine = i + 1;
            }
            Matcher em = EOS_EMIN.matcher(line);
            if (em.find()) {
                emin = em.group(1);
                eminLine = i + 1;
            }
        }
        return new StdoutSummary(file, false, volumeCount, List.copyOf(volumes),
                lattice, bulk, deriv, emin, latticeLine, bulkLine, derivLine, eminLine);
    }

    private static void scanRestart(Path dir, List<RestartToken> restartTokens,
                                    List<String> uninterpreted) {
        for (Path file : listRegular(dir)) {
            String name = file.getFileName().toString();
            Matcher matcher = RESTART.matcher(name);
            if (!matcher.matches()) {
                uninterpreted.add("restart/" + name);
                continue;
            }
            double value = Double.NaN;
            try {
                List<String> lines = Files.readAllLines(file);
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty()) {
                        value = QEThermoPwSeriesParser.parseFortranDouble(
                                trimmed.split("\\s+")[0]);
                        break;
                    }
                }
            } catch (IOException | RuntimeException ex) {
                value = Double.NaN;
            }
            restartTokens.add(new RestartToken(file, Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)), value));
        }
        restartTokens.sort(Comparator.comparingInt(RestartToken::getFirstIndex)
                .thenComparingInt(RestartToken::getSecondIndex));
    }

    private static List<Path> listRegular(Path dir) {
        List<Path> files = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) {
            return files;
        }
        try (Stream<Path> listing = Files.list(dir)) {
            listing.filter(Files::isRegularFile).limit(MAX_DIR_ENTRIES)
                    .forEach(files::add);
        } catch (IOException ex) {
            return files;
        }
        return files;
    }

    private static ControlInfo readControl(Path controlFile) {
        String[] absent = new String[1];
        if (controlFile == null || !Files.isRegularFile(controlFile)) {
            return new ControlInfo(false, null, null, null, null,
                    new int[] {0, 0, 0, 0, 0, 0}, "no thermo_control in the run directory");
        }
        try {
            if (Files.size(controlFile) > MAX_CONTROL_BYTES) {
                return new ControlInfo(true, null, null, null, null,
                        new int[] {0, 0, 0, 0, 0, 0},
                        "thermo_control exceeds the " + MAX_CONTROL_BYTES
                                + "-byte bound; left unparsed");
            }
            String text = Files.readString(controlFile); // UTF-8; a binary fails loudly
    
            String what = null;
            Matcher whatMatcher = WHAT.matcher(text);
            if (whatMatcher.find()) {
                what = whatMatcher.group(1).trim();
            }
            Boolean lmurn = null;
            Matcher lmurnMatcher = LMURN.matcher(text);
            if (lmurnMatcher.find()) {
                lmurn = Boolean.valueOf("true".equalsIgnoreCase(lmurnMatcher.group(1)));
            }
            Double pressure = firstDouble(PRESSURE.matcher(text));
            Double deltat = firstDouble(DELTAT.matcher(text));
            int[] ngeo = {0, 0, 0, 0, 0, 0};
            Matcher ngeoMatcher = NGEO.matcher(text);
            while (ngeoMatcher.find()) {
                int slot = Integer.parseInt(ngeoMatcher.group(1));
                if (slot >= 1 && slot <= 6) {
                    ngeo[slot - 1] = Integer.parseInt(ngeoMatcher.group(2));
                }
            }
            String note = (what == null && lmurn == null && pressure == null && deltat == null
                    && ngeo[0] == 0 && ngeo[1] == 0)
                    ? "thermo_control yielded no recognized extracts (what=/lmurn=/pressure=/"
                            + "deltat=/ngeo(i) all absent)" : null;
            return new ControlInfo(true, what, lmurn, pressure, deltat, ngeo, note);
        } catch (IOException | RuntimeException ex) {
            absent[0] = ex.getMessage();
            return new ControlInfo(true, null, null, null, null,
                    new int[] {0, 0, 0, 0, 0, 0},
                    "thermo_control could not be read as UTF-8 (" + absent[0] + ")");
        }
    }

    private static Double firstDouble(Matcher matcher) {
        if (!matcher.find()) {
            return null;
        }
        double value = QEThermoPwSeriesParser.parseFortranDouble(matcher.group(1));
        return Double.isNaN(value) ? null : Double.valueOf(value);
    }

    /** Renders one status line for dialogs (all values verbatim extracts). */
    public static String describe(ThermoScan scan) {
        StringBuilder text = new StringBuilder();
        ControlInfo control = scan.getControl();
        text.append("thermo_control: ")
                .append(control.isPresent() ? "present" : "ABSENT (stated, not assumed)")
                .append('\n');
        if (control.isPresent()) {
            text.append("  what=").append(control.getWhat() == null ? "(absent)"
                    : "'" + control.getWhat() + "'");
            if (control.getLmurn() != null) {
                text.append("  lmurn=").append(control.getLmurn() ? ".TRUE." : ".FALSE.");
            }
            if (control.getPressureKbar() != null) {
                text.append(String.format(Locale.ROOT, "  pressure=%.6g kbar",
                        control.getPressureKbar()));
            }
            if (control.getDeltatK() != null) {
                text.append(String.format(Locale.ROOT, "  deltat=%.6g K",
                        control.getDeltatK()));
            }
            text.append('\n');
            long product = control.getExplicitNgeoProduct();
            text.append("  ngeo: ").append(product > 0
                    ? "explicit product " + product
                    : "not fully explicit in thermo_control - no task total is fabricated");
            text.append('\n');
            if (control.getParseNote() != null) {
                text.append("  note: ").append(control.getParseNote()).append('\n');
            }
        }
        text.append("restart tasks completed: ").append(scan.getRestartCount()).append('\n');
        text.append("series artifacts: ").append(scan.getArtifacts().size())
                .append("; uninterpreted sidecars: ").append(scan.getUninterpreted().size())
                .append('\n');
        text.append("stdout extracts: ");
        if (scan.getStdoutSummaries().isEmpty()) {
            text.append("no *.out file at the run-directory top level");
        } else {
            for (int i = 0; i < scan.getStdoutSummaries().size(); i++) {
                StdoutSummary summary = scan.getStdoutSummaries().get(i);
                if (i > 0) {
                    text.append("; ");
                }
                text.append(summary.getPath().getFileName()).append(": ");
                if (summary.isOversized()) {
                    text.append("exceeds the ").append(MAX_STDOUT_BYTES)
                            .append("-byte stdout bound (named, not parsed)");
                } else {
                    text.append("EOS block ")
                            .append(summary.isEosComplete() ? "complete (lines "
                                    + summary.getEosFirstLine() + "..)"
                                    : summary.getEosLineCount() == 0
                                            ? "not written yet"
                                            : "partial, " + summary.getEosLineCount()
                                                    + "/4 lines so far")
                            .append(", ").append(summary.getUnitCellVolumeCount())
                            .append(" unit-cell-volume print(s)");
                }
            }
        }
        return text.toString();
    }
}
