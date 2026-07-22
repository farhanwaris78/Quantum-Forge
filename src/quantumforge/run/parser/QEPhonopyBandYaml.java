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
 * Reader for phonopy's {@code band.yaml} output - the pinned artifact of the
 * band-structure run mode. The grammar is pinned line-by-line against the
 * upstream writer {@code BandStructure._write_yaml /
 * _get_q_segment_yaml} (github.com/phonopy/phonopy commit
 * 3a3e0f099da5de2556e75d72ea89b3bb22c8e97e):
 *
 * <pre>
 * nqpoint: 101
 * npath:   3
 * segment_nqpoint:
 * - 41
 * - 21
 * - 39
 * labels:
 * - [ 'A', 'B' ]
 * - [ 'B', 'C' ]
 * - [ 'C', 'A' ]
 * reciprocal_lattice:
 * - [ ..., ..., ... ] # a*
 * ...
 * phonon:
 * - q-position: [ %12.7f, %12.7f, %12.7f ]
 *   distance: %12.7f
 *   band:
 *   - # 1
 *     frequency: %15.10f
 *     [group_velocity: [ ... ] - counted, not stored]
 *     [eigenvector: ... - counted, payload not stored]
 * </pre>
 *
 * <p>Facts kept verbatim: every q-position component, distance and frequency
 * (full {@code %.10f} text digits). Units: the yaml carries NO unit text;
 * frequencies are THz under phonopy's default unit setting - stated in
 * {@link BandYaml#getFrequencyUnitNote()}, never invented. Imaginary
 * frequencies are the negative values by phonopy's own convention (named in
 * the upstream band-structure docstring): counted and named, never hidden.</p>
 *
 * <p>Segment boundaries: {@code segment_nqpoint} is authoritative when
 * present and consistent (its parts cover the parsed rows); otherwise the
 * reader falls back to the distance-reset detection that phonopy-bandplot
 * itself relies on, and the chosen method is STATED in the report. A
 * truncated trailing band-entry list (a live write mid-row) is held back and
 * counted via {@link BandYaml#getPartialRowsHeld()}, while a grammar break
 * anywhere earlier is a refusal - measured rows are never silently
 * dropped.</p>
 *
 * <p>Verdicts: PHONOPY_BAND_INPUT (null / non-file / oversized),
 * PHONOPY_BAND_HEADER (no nqpoint key - wrong file selection),
 * PHONOPY_BAND_PARTIAL (header ok, rows still arriving),
 * PHONOPY_BAND_SHAPE (ragged grammar mid-file, non-number, non-uniform band
 * count), PHONOPY_BAND_OK. Bounded read: {@link #MAX_FILE_BYTES}.</p>
 */
public final class QEPhonopyBandYaml {

    /** Upper bound for one band.yaml read. */
    public static final long MAX_FILE_BYTES = 32L * 1024L * 1024L;

    private static final Pattern NQPOINT = Pattern.compile(
            "^nqpoint:\\s*(\\d+)\\s*$");
    private static final Pattern NPATH = Pattern.compile(
            "^npath:\\s*(\\d+)\\s*$");
    private static final Pattern SEG_NQ = Pattern.compile(
            "^segment_nqpoint:\\s*$");
    private static final Pattern SEG_NQ_ROW = Pattern.compile(
            "^-\\s*(\\d+)\\s*$");
    private static final Pattern LABELS = Pattern.compile(
            "^labels:\\s*$");
    private static final Pattern LABEL_PAIR = Pattern.compile(
            "^-\\s*\\[\\s*'([^']*)'\\s*,\\s*'([^']*)'\\s*\\]\\s*$");
    private static final Pattern PHONON_KEY = Pattern.compile(
            "^phonon:\\s*$");
    private static final Pattern Q_POSITION = Pattern.compile(
            "^- q-position:\\s*\\[\\s*([^\\]]+)\\]\\s*$");
    private static final Pattern DISTANCE = Pattern.compile(
            "^\\s+distance:\\s*(\\S+)\\s*$");
    private static final Pattern BAND_KEY = Pattern.compile(
            "^\\s+band:\\s*$");
    private static final Pattern BAND_INDEX = Pattern.compile(
            "^\\s+-\\s+#\\s*(\\d+)\\s*$");
    private static final Pattern FREQUENCY = Pattern.compile(
            "^\\s+frequency:\\s*(\\S+)\\s*$");
    private static final Pattern GROUP_VELOCITY = Pattern.compile(
            "^\\s+group_velocity:");
    private static final Pattern EIGENVECTOR = Pattern.compile(
            "^\\s+eigenvector:");
    private static final Pattern TOP_KEY = Pattern.compile(
            "^[A-Za-z_][A-Za-z0-9_]*:");

    private QEPhonopyBandYaml() {
        // Utility
    }

    /** One q-point row: position, path distance, band frequencies. */
    public static final class QRow {
        private final double[] qPosition;
        private final double distance;
        private final double[] frequencies;

        private QRow(double[] qPosition, double distance, double[] frequencies) {
            this.qPosition = qPosition;
            this.distance = distance;
            this.frequencies = frequencies;
        }

        /** Reciprocal reduced coordinates, verbatim decimals. */
        public double[] getQPosition() { return this.qPosition.clone(); }
        /** Cumulative path distance (phonopy's reciprocal-space measure). */
        public double getDistance() { return this.distance; }
        /** Band frequencies ascending (negative = imaginary, phonopy convention). */
        public double[] getFrequencies() { return this.frequencies.clone(); }
    }

    /** One contiguous path segment (one BAND-tag segment of the conf). */
    public static final class Segment {
        private final List<QRow> rows;
        private final String startLabel;
        private final String endLabel;

        private Segment(List<QRow> rows, String startLabel, String endLabel) {
            this.rows = rows;
            this.startLabel = startLabel;
            this.endLabel = endLabel;
        }

        public List<QRow> getRows() { return this.rows; }
        /** Label at the segment start (band.yaml labels; null when absent). */
        public String getStartLabel() { return this.startLabel; }
        /** Label at the segment end (band.yaml labels; null when absent). */
        public String getEndLabel() { return this.endLabel; }
        /** Path distance of the first row (x-origin of the segment plot). */
        public double getStartDistance() {
            return this.rows.isEmpty() ? 0.0 : this.rows.get(0).getDistance();
        }
        /** Path distance of the last row (segment right edge). */
        public double getEndDistance() {
            return this.rows.isEmpty() ? 0.0
                    : this.rows.get(this.rows.size() - 1).getDistance();
        }
    }

    /** A parsed band.yaml product. */
    public static final class BandYaml {
        private final String sourceName;
        private final int nqpoint;
        private final Integer npath;
        private final List<Segment> segments;
        private final int bandCount;
        private final double minFrequency;
        private final double maxFrequency;
        private final int negativeFrequencyCount;
        private final int groupVelocityEntries;
        private final int eigenvectorEntries;
        private final int partialRowsHeld;
        private final String segmentMethod;
        private final String frequencyUnitNote;

        private BandYaml(String sourceName, int nqpoint, Integer npath,
                         List<Segment> segments, int bandCount,
                         double minFrequency, double maxFrequency,
                         int negativeFrequencyCount, int groupVelocityEntries,
                         int eigenvectorEntries, int partialRowsHeld,
                         String segmentMethod) {
            this.sourceName = sourceName;
            this.nqpoint = nqpoint;
            this.npath = npath;
            this.segments = segments;
            this.bandCount = bandCount;
            this.minFrequency = minFrequency;
            this.maxFrequency = maxFrequency;
            this.negativeFrequencyCount = negativeFrequencyCount;
            this.groupVelocityEntries = groupVelocityEntries;
            this.eigenvectorEntries = eigenvectorEntries;
            this.partialRowsHeld = partialRowsHeld;
            this.segmentMethod = segmentMethod;
            this.frequencyUnitNote = "band.yaml carries no unit text: frequencies are"
                    + " THz under phonopy's default unit setting (phonopy doc,"
                    + " 'Phonon frequencies in THz, which is the default setting of"
                    + " phonopy') - stated; a non-default frequency conversion is NOT"
                    + " detectable from this file alone";
        }

        public String getSourceName() { return this.sourceName; }
        /** Total q-point rows the header promised. */
        public int getNqpoint() { return this.nqpoint; }
        public Integer getNpath() { return this.npath; }
        public List<Segment> getSegments() { return this.segments; }
        /** Bands per q row (3 * natom, uniform across rows). */
        public int getBandCount() { return this.bandCount; }
        public double getMinFrequency() { return this.minFrequency; }
        public double getMaxFrequency() { return this.maxFrequency; }
        /** Negative frequency values (= imaginary modes, phonopy's convention). */
        public int getNegativeFrequencyCount() { return this.negativeFrequencyCount; }
        public int getGroupVelocityEntries() { return this.groupVelocityEntries; }
        public int getEigenvectorEntries() { return this.eigenvectorEntries; }
        /** Trailing rows deliberately held back as a live partial append. */
        public int getPartialRowsHeld() { return this.partialRowsHeld; }
        /** How segment boundaries were derived (stated provenance). */
        public String getSegmentMethod() { return this.segmentMethod; }
        /** The stated, never-guessed frequency unit note. */
        public String getFrequencyUnitNote() { return this.frequencyUnitNote; }

        /** Total q rows actually parsed. */
        public int getParsedRowCount() {
            int sum = 0;
            for (Segment segment : this.segments) {
                sum += segment.getRows().size();
            }
            return sum;
        }

        /** End-to-end path distance (last row's distance). */
        public double getTotalDistance() {
            double total = 0.0;
            for (Segment segment : this.segments) {
                if (!segment.getRows().isEmpty()) {
                    total = segment.getEndDistance();
                }
            }
            return total;
        }
    }

    /** Bounded-file entry point. */
    public static OperationResult<BandYaml> parse(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return OperationResult.failed("PHONOPY_BAND_INPUT",
                    "Not a regular file: " + file, null);
        }
        long size;
        try {
            size = Files.size(file);
        } catch (IOException ex) {
            return OperationResult.failed("PHONOPY_BAND_INPUT",
                    "Size unreadable for " + file + ": " + ex.getMessage(), null);
        }
        if (size > MAX_FILE_BYTES) {
            return OperationResult.failed("PHONOPY_BAND_INPUT",
                    file.getFileName() + " exceeds the " + MAX_FILE_BYTES
                            + "-byte band.yaml bound; refusing an unbounded read.",
                    null);
        }
        String text;
        try {
            text = Files.readString(file);
        } catch (IOException | RuntimeException ex) {
            return OperationResult.failed("PHONOPY_BAND_INPUT",
                    "Could not read " + file + " as UTF-8 text: " + ex.getMessage(),
                    null);
        }
        return parseText(text, file.getFileName().toString());
    }

    /** Text entry point (tests, live-read content). */
    public static OperationResult<BandYaml> parseText(String text, String sourceName) {
        if (text == null) {
            return OperationResult.failed("PHONOPY_BAND_INPUT",
                    "No band.yaml content supplied.", null);
        }
        if (text.length() > MAX_FILE_BYTES) {
            return OperationResult.failed("PHONOPY_BAND_INPUT",
                    "band.yaml text exceeds the " + MAX_FILE_BYTES + "-char bound.",
                    null);
        }
        String[] lines = text.split("\n", -1);

        Integer nqpoint = null;
        Integer npath = null;
        List<Integer> segmentNq = new ArrayList<>();
        List<String[]> labels = new ArrayList<>();

        // ---- header scan (until 'phonon:') ----
        int cursor = 0;
        boolean phononSeen = false;
        while (cursor < lines.length) {
            String line = lines[cursor];
            if (PHONON_KEY.matcher(line).matches()) {
                phononSeen = true;
                cursor++;
                break;
            }
            Matcher nqMatcher = NQPOINT.matcher(line);
            if (nqMatcher.matches()) {
                nqpoint = Integer.valueOf(nqMatcher.group(1));
            } else {
                Matcher npMatcher = NPATH.matcher(line);
                if (npMatcher.matches()) {
                    npath = Integer.valueOf(npMatcher.group(1));
                } else if (SEG_NQ.matcher(line).matches()) {
                    int j = cursor + 1;
                    Matcher segRow = SEG_NQ_ROW.matcher("");
                    while (j < lines.length) {
                        segRow.reset(lines[j]);
                        if (!segRow.matches()) {
                            break;
                        }
                        segmentNq.add(Integer.valueOf(segRow.group(1)));
                        j++;
                    }
                    cursor = j;
                    continue;
                } else if (LABELS.matcher(line).matches()) {
                    int j = cursor + 1;
                    while (j < lines.length) {
                        Matcher pair = LABEL_PAIR.matcher(lines[j]);
                        if (!pair.matches()) {
                            break;
                        }
                        labels.add(new String[] {pair.group(1), pair.group(2)});
                        j++;
                    }
                    cursor = j;
                    continue;
                }
            }
            cursor++;
        }

        if (nqpoint == null) {
            return OperationResult.failed("PHONOPY_BAND_HEADER",
                    sourceName + " carries no 'nqpoint:' key before any 'phonon:' block"
                            + " - not a band.yaml (or a format this build has not"
                            + " pinned), refused rather than guessed.", null);
        }
        if (!phononSeen) {
            return OperationResult.failed("PHONOPY_BAND_PARTIAL",
                    sourceName + " has the band.yaml header (nqpoint " + nqpoint
                            + ") but no 'phonon:' block yet - a live run is still"
                            + " writing it; nothing is drawn half-way.", null);
        }

        // ---- phonon block scan: row builder ----
        List<QRow> rows = new ArrayList<>();
        int expectedBands = -1;
        int partialRowsHeld = 0;
        int groupVelocityEntries = 0;
        int eigenvectorEntries = 0;

        double[] rowQ = null;
        boolean rowDistanceSeen = false;
        double rowDistance = 0.0;
        List<Double> rowFreqs = new ArrayList<>();
        boolean inEigenvector = false;

        while (cursor < lines.length) {
            String line = lines[cursor];
            int currentLine = cursor + 1;
            cursor++;
            if (line.trim().isEmpty()) {
                continue;
            }
            Matcher qMatcher = Q_POSITION.matcher(line);
            if (qMatcher.matches()) {
                if (rowQ != null) {
                    OperationResult<BandYaml> failure = closeRow(rows, rowQ,
                            rowDistanceSeen, rowDistance, rowFreqs, expectedBands,
                            sourceName, currentLine);
                    if (failure != null) {
                        return failure;
                    }
                    if (expectedBands < 0) {
                        expectedBands = rowFreqs.size();
                        if (expectedBands == 0) {
                            return OperationResult.failed("PHONOPY_BAND_SHAPE",
                                    sourceName + ": the first q row's 'band:' list has"
                                            + " no frequency entries - grammar broken,"
                                            + " nothing inferred.", null);
                        }
                    }
                }
                double[] q = parseVector3(qMatcher.group(1));
                if (q == null) {
                    return OperationResult.failed("PHONOPY_BAND_SHAPE",
                            sourceName + " line " + currentLine + ": q-position is not"
                                    + " three finite numbers.", null);
                }
                rowQ = q;
                rowDistanceSeen = false;
                rowFreqs = new ArrayList<>();
                inEigenvector = false;
                continue;
            }
            if (rowQ == null) {
                if (TOP_KEY.matcher(line).find()) {
                    break; // a new top-level section after an empty phonon block
                }
                continue; // tolerated stray content before the first q row
            }
            Matcher dMatcher = DISTANCE.matcher(line);
            if (dMatcher.matches()) {
                double d = parseFinite(dMatcher.group(1));
                if (Double.isNaN(d)) {
                    return OperationResult.failed("PHONOPY_BAND_SHAPE",
                            sourceName + " line " + currentLine + ": distance is not a"
                                    + " finite number.", null);
                }
                rowDistance = d;
                rowDistanceSeen = true;
                inEigenvector = false;
                continue;
            }
            if (BAND_KEY.matcher(line).matches()) {
                inEigenvector = false;
                continue;
            }
            if (BAND_INDEX.matcher(line).matches()) {
                inEigenvector = false;
                continue;
            }
            Matcher fMatcher = FREQUENCY.matcher(line);
            if (fMatcher.matches()) {
                double f = parseFinite(fMatcher.group(1));
                if (Double.isNaN(f)) {
                    return OperationResult.failed("PHONOPY_BAND_SHAPE",
                            sourceName + " line " + currentLine + ": frequency is not a"
                                    + " finite number (upstream writes %15.10f decimals;"
                                    + " nan/inf values here mean a corrupt file, never"
                                    + " skipped).", null);
                }
                rowFreqs.add(Double.valueOf(f));
                continue;
            }
            if (GROUP_VELOCITY.matcher(line).find()) {
                groupVelocityEntries++;
                continue;
            }
            if (EIGENVECTOR.matcher(line).find()) {
                eigenvectorEntries++;
                inEigenvector = true;
                continue;
            }
            if (inEigenvector) {
                continue; // eigenvector payload: enumerated, never stored
            }
            if (TOP_KEY.matcher(line).find()) {
                cursor--; // hand the key back after closing the row
                OperationResult<BandYaml> failure = closeRow(rows, rowQ,
                        rowDistanceSeen, rowDistance, rowFreqs,
                        expectedBands, sourceName, currentLine);
                if (failure != null) {
                    return failure;
                }
                if (expectedBands < 0) {
                    expectedBands = rowFreqs.size();
                    if (expectedBands == 0) {
                        return OperationResult.failed("PHONOPY_BAND_SHAPE",
                                sourceName + ": the first q row's 'band:' list has no"
                                        + " frequency entries.", null);
                    }
                }
                rowQ = null;
                break;
            }
        }
        if (rowQ != null) {
            if (expectedBands >= 0 && rowFreqs.size() < expectedBands) {
                partialRowsHeld++; // a trailing short band list: live append, held back
            } else {
                OperationResult<BandYaml> failure = closeRow(rows, rowQ,
                        rowDistanceSeen, rowDistance, rowFreqs, expectedBands,
                        sourceName, cursor);
                if (failure != null) {
                    return failure;
                }
            }
        }

        if (rows.isEmpty()) {
            return OperationResult.failed("PHONOPY_BAND_PARTIAL",
                    sourceName + " has the header (nqpoint " + nqpoint + ") but no"
                            + " complete q-point rows yet - a live run is still"
                            + " writing.", null);
        }
        if (expectedBands < 0) {
            expectedBands = rows.get(0).getFrequencies().length;
        }
        int parsedRows = rows.size();
        if (parsedRows + partialRowsHeld < nqpoint.intValue()) {
            return OperationResult.failed("PHONOPY_BAND_PARTIAL",
                    sourceName + ": parsed " + parsedRows + " of " + nqpoint
                            + " promised q rows - the run is still writing later"
                            + " paths; earlier paths ARE chartable once the full row"
                            + " count lands (this reader stays fail-closed until"
                            + " then).", null);
        }

        // ---- segments ----
        int segNqSum = 0;
        for (int n : segmentNq) {
            segNqSum += n;
        }
        List<Segment> segments;
        String segmentMethod;
        if (!segmentNq.isEmpty() && segNqSum >= parsedRows) {
            segments = buildSegmentsByCounts(rows, segmentNq, labels);
            segmentMethod = "segment_nqpoint of the yaml header (authoritative; parts"
                    + " sum " + segNqSum + ")";
        } else {
            segments = buildSegmentsByDistanceReset(rows, labels);
            segmentMethod = segmentNq.isEmpty()
                    ? "distance-reset detection (phonopy-bandplot's own fallback;"
                            + " segment_nqpoint absent)"
                    : "distance-reset detection (segment_nqpoint parts sum " + segNqSum
                            + " but " + parsedRows + " rows parsed - the counts are"
                            + " NOT trusted where they disagree)";
        }

        double minF = Double.POSITIVE_INFINITY;
        double maxF = Double.NEGATIVE_INFINITY;
        int negative = 0;
        for (QRow row : rows) {
            for (double f : row.getFrequencies()) {
                minF = Math.min(minF, f);
                maxF = Math.max(maxF, f);
                if (f < 0.0) {
                    negative++;
                }
            }
        }
        BandYaml yaml = new BandYaml(sourceName, nqpoint.intValue(), npath,
                List.copyOf(segments), expectedBands, minF, maxF, negative,
                groupVelocityEntries, eigenvectorEntries, partialRowsHeld,
                segmentMethod);
        return OperationResult.success("PHONOPY_BAND_OK",
                parsedRows + " q rows, " + expectedBands + " bands, "
                        + segments.size() + " segment(s) (" + segmentMethod + ")"
                        + (partialRowsHeld > 0 ? ", " + partialRowsHeld
                                + " trailing partial row(s) held back" : "") + ".",
                yaml);
    }

    private static OperationResult<BandYaml> closeRow(List<QRow> rows, double[] q,
            boolean distanceSeen, double distance, List<Double> freqs,
            int expectedBands, String sourceName, int line) {
        if (!distanceSeen) {
            return OperationResult.failed("PHONOPY_BAND_SHAPE",
                    sourceName + " line " + line + ": a q-position row without a"
                            + " 'distance:' key - the writer grammar is broken,"
                            + " never repaired.", null);
        }
        if (expectedBands >= 0 && freqs.size() != expectedBands) {
            return OperationResult.failed("PHONOPY_BAND_SHAPE",
                    sourceName + " line " + line + ": row has " + freqs.size()
                            + " band frequencies, expected " + expectedBands
                            + " like the first row - non-uniform band counts are a"
                            + " corrupt file, not a partial one.", null);
        }
        double[] f = new double[freqs.size()];
        for (int i = 0; i < f.length; i++) {
            f[i] = freqs.get(i).doubleValue();
        }
        rows.add(new QRow(q, distance, f));
        return null;
    }

    private static List<Segment> buildSegmentsByCounts(List<QRow> rows,
            List<Integer> counts, List<String[]> labels) {
        List<Segment> segments = new ArrayList<>();
        int cursor = 0;
        for (int s = 0; s < counts.size(); s++) {
            List<QRow> segRows = new ArrayList<>();
            for (int i = 0; i < counts.get(s).intValue() && cursor < rows.size();
                    i++, cursor++) {
                segRows.add(rows.get(cursor));
            }
            if (segRows.isEmpty()) {
                break;
            }
            segments.add(new Segment(List.copyOf(segRows),
                    s < labels.size() ? labels.get(s)[0] : null,
                    s < labels.size() ? labels.get(s)[1] : null));
        }
        return segments;
    }

    private static List<Segment> buildSegmentsByDistanceReset(List<QRow> rows,
            List<String[]> labels) {
        List<Segment> segments = new ArrayList<>();
        List<QRow> current = new ArrayList<>();
        double previous = Double.NEGATIVE_INFINITY;
        for (QRow row : rows) {
            if (!current.isEmpty() && row.getDistance() < previous - 1.0e-9) {
                int index = segments.size();
                segments.add(new Segment(List.copyOf(current),
                        index < labels.size() ? labels.get(index)[0] : null,
                        index < labels.size() ? labels.get(index)[1] : null));
                current = new ArrayList<>();
            }
            current.add(row);
            previous = row.getDistance();
        }
        if (!current.isEmpty()) {
            int index = segments.size();
            segments.add(new Segment(List.copyOf(current),
                    index < labels.size() ? labels.get(index)[0] : null,
                    index < labels.size() ? labels.get(index)[1] : null));
        }
        return segments;
    }

    private static double[] parseVector3(String content) {
        String[] parts = content.split(",");
        if (parts.length != 3) {
            return null;
        }
        double[] out = new double[3];
        for (int i = 0; i < 3; i++) {
            out[i] = parseFinite(parts[i].trim());
            if (Double.isNaN(out[i])) {
                return null;
            }
        }
        return out;
    }

    private static double parseFinite(String token) {
        try {
            double value = QEThermoPwSeriesParser.parseFortranDouble(token);
            return Double.isFinite(value) ? value : Double.NaN;
        } catch (RuntimeException ex) {
            return Double.NaN;
        }
    }

    /** Header/census renderer whose every value comes from the parsed product. */
    public static String describe(BandYaml yaml) {
        StringBuilder text = new StringBuilder();
        text.append(String.format(Locale.ROOT,
                "%s: nqpoint %d (parsed %d, %d held back as live partial), bands %d,"
                        + " segments %d%n",
                yaml.getSourceName(), yaml.getNqpoint(), yaml.getParsedRowCount(),
                yaml.getPartialRowsHeld(), yaml.getBandCount(),
                yaml.getSegments().size()));
        for (int i = 0; i < yaml.getSegments().size(); i++) {
            Segment segment = yaml.getSegments().get(i);
            text.append(String.format(Locale.ROOT,
                    "  segment %d: %d rows, distance %.6f -> %.6f%s%s%n",
                    i + 1, segment.getRows().size(), segment.getStartDistance(),
                    segment.getEndDistance(),
                    segment.getStartLabel() != null ? "  " + segment.getStartLabel() : "",
                    segment.getEndLabel() != null ? " -> " + segment.getEndLabel() : ""));
        }
        text.append(String.format(Locale.ROOT,
                "frequency range [%.6f, %.6f] - no unit text in file, THz under"
                        + " phonopy's default setting (stated)%n",
                yaml.getMinFrequency(), yaml.getMaxFrequency()));
        text.append("segment boundaries: ").append(yaml.getSegmentMethod())
                .append('\n');
        if (yaml.getNegativeFrequencyCount() > 0) {
            text.append(String.format(Locale.ROOT,
                    "%d negative frequency value(s) = imaginary modes by phonopy's own"
                            + " convention - NAMED, never hidden.%n",
                    yaml.getNegativeFrequencyCount()));
        }
        return text.toString();
    }
}
