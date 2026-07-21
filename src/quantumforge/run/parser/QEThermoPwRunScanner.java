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
 *       the quasi-harmonic (mur_lc_t) result series;</li>
 *   <li>{@code restart/e_work_part.*} - one finished-task energy per file,
 *       the honest live task counter (never an ETA invented from nothing).</li>
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

        private Artifact(Path path, String role, SeriesKind kind, Integer geometryTag,
                         boolean phVariant) {
            this.path = path;
            this.role = role;
            this.kind = kind;
            this.geometryTag = geometryTag;
            this.phVariant = phVariant;
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

    /** The whole census. */
    public static final class ThermoScan {
        private final Path runDir;
        private final ControlInfo control;
        private final List<Artifact> artifacts;
        private final List<RestartToken> restartTokens;
        private final List<String> uninterpreted;

        private ThermoScan(Path runDir, ControlInfo control, List<Artifact> artifacts,
                           List<RestartToken> restartTokens, List<String> uninterpreted) {
            this.runDir = runDir;
            this.control = control;
            this.artifacts = artifacts;
            this.restartTokens = restartTokens;
            this.uninterpreted = uninterpreted;
        }

        public Path getRunDir() { return this.runDir; }
        public ControlInfo getControl() { return this.control; }
        public List<Artifact> getArtifacts() { return this.artifacts; }
        public List<RestartToken> getRestartTokens() { return this.restartTokens; }
        public int getRestartCount() { return this.restartTokens.size(); }
        /** Files present but outside the pinned set: named, never parsed. */
        public List<String> getUninterpreted() { return this.uninterpreted; }

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
        if (runDir == null || !Files.isDirectory(runDir)) {
            return new ThermoScan(runDir, control, artifacts, restartTokens, uninterpreted);
        }
        if (Files.isRegularFile(runDir.resolve("thermo_control"))) {
            artifacts.add(new Artifact(runDir.resolve("thermo_control"), "control", null,
                    null, false));
        }
        scanEnergyFiles(runDir.resolve("energy_files"), artifacts, uninterpreted);
        scanThermFiles(runDir.resolve("therm_files"), artifacts, uninterpreted);
        scanAnharFiles(runDir.resolve("anhar_files"), artifacts, uninterpreted);
        scanRestart(runDir.resolve("restart"), restartTokens, uninterpreted);
        artifacts.sort(Comparator.comparing(a -> a.getPath().toString()));
        return new ThermoScan(runDir, control, artifacts, restartTokens, uninterpreted);
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
            if (!kinds.isEmpty() && name.startsWith("output_anhar.dat")) {
                artifacts.add(new Artifact(file, "anhar_files", kinds.get(0), null,
                        name.endsWith("_ph")));
            } else {
                uninterpreted.add("anhar_files/" + name);
            }
        }
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
                .append("; uninterpreted sidecars: ").append(scan.getUninterpreted().size());
        return text.toString();
    }
}
