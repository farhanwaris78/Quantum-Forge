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
 * Reader for phonopy-gruneisen's yaml outputs - the mode Grüneisen parameter
 * channel ({@code gruneisen.yaml} band mode and {@code gruneisen_mesh.yaml}
 * mesh mode). The grammar is pinned line-by-line against the upstream
 * writers {@code phonopy/gruneisen/band_structure.py _write_yaml} and
 * {@code phonopy/gruneisen/mesh.py _write_yaml}
 * (github.com/phonopy/phonopy commit
 * 3a3e0f099da5de2556e75d72ea89b3bb22c8e97e):
 *
 * <pre>
 * BAND mode (script writes gruneisen.yaml):
 *   nqpoint: %-7d / npath: %-7d
 *   segment_nqpoint: + [ '- N' ]
 *   labels: + [ \"- [ 'A', 'B' ]\" ]  (optional; connection-aware consumption
 *                                    identical to band.yaml's writer)
 *   reciprocal_lattice: + [ \"- [ %12.8f, %12.8f, %12.8f ] # a*\" ]
 *   natom: %-7d + cell text
 *   path:
 *   - nqpoint: N
 *     phonon:
 *     - q-position: [ %10.7f, %10.7f, %10.7f ]
 *       distance: %10.7f
 *       band:
 *       - # k
 *         gruneisen: %15.10f
 *         frequency: %15.10f
 *
 * MESH mode (script writes gruneisen_mesh.yaml):
 *   mesh: [ %5d, %5d, %5d ]
 *   nqpoint: %d          (IRREDUCIBLE q count)
 *   reciprocal_lattice: / natom:   %-7d + cell text
 *   phonon:
 *   - q-position: [ %10.7f, %10.7f, %10.7f ]
 *     multiplicity: %d
 *     band: (same two-line entries as band mode)
 * </pre>
 *
 * <p>Doc truth stated, never re-derived here (doc/gruneisen.md): the mode
 * Grüneisen parameter is {@code gamma(q,nu) = -V/(2*omega^2) * &lt;e|dD/dV|e&gt;},
 * approximated by finite difference {@code dD/dV ~= Delta D / Delta V} from
 * THREE phonon calculations (equilibrium + slightly larger + slightly
 * smaller volume, each fully relaxed under its volume constraint). Band-mode
 * frequencies may NOT be ordered inside a segment (neighboring q-points are
 * connected considering phonon symmetry to treat band crossing - verbatim
 * doc statement). Gamma may DIVERGE around Gamma; upstream's own plot avoids
 * the Gamma value and shows the neighboring q-points instead. These are the
 * file's facts; this reader reports them and never recomputes gamma.</p>
 *
 * <p>Content authority: the MODE is decided by the file's own grammar
 * ({@code path:}+segments vs {@code mesh: [...]}+multiplicity), NOT by the
 * file name - a renamed file still classifies honestly. A trailing partial
 * q-block (live write) is held back and counted; a malformed MID-file row
 * is {@code PHONOPY_GRUNEISEN_SHAPE}, never skipped.</p>
 */
public final class QEPhonopyGruneisenYaml {

    private QEPhonopyGruneisenYaml() {
        // Utility
    }

    /** Fail-closed size bound. */
    public static final long MAX_FILE_BYTES = 32L * 1024L * 1024L;

    private static final Pattern KV = Pattern.compile("^(nqpoint|npath|natom):\\s*(\\d+)\\s*$");
    private static final Pattern MESH_HEAD = Pattern.compile(
            "^mesh:\\s*\\[\\s*(\\d+),\\s*(\\d+),\\s*(\\d+)\\s*\\]\\s*$");
    private static final Pattern SEGMENT_NQ = Pattern.compile("^- nqpoint:\\s*(\\d+)\\s*$");
    private static final Pattern SEGMENT_ITEM = Pattern.compile("^-\\s*(\\d+)\\s*$");
    private static final Pattern QROW = Pattern.compile(
            "^\\s*- q-position:\\s*\\[\\s*(\\S+),\\s*(\\S+),\\s*(\\S+)\\s*\\]\\s*$");
    private static final Pattern DIST = Pattern.compile("^\\s*distance:\\s*(\\S+)\\s*$");
    private static final Pattern MULT = Pattern.compile("^\\s*multiplicity:\\s*(\\d+)\\s*$");
    private static final Pattern GAMMA = Pattern.compile("^\\s*gruneisen:\\s*(\\S+)\\s*$");
    private static final Pattern FREQ = Pattern.compile("^\\s*frequency:\\s*(\\S+)\\s*$");
    private static final Pattern BAND_ENTRY = Pattern.compile("^\\s*- # (\\d+)\\s*$");
    private static final Pattern LABEL = Pattern.compile("^- \\[ '([^']*)', '([^']*)' \\]\\s*$");

    /** The yaml flavor, decided by content not name. */
    public enum Mode {
        BAND, MESH
    }

    /** One band entry: gamma + frequency verbatim text digits. */
    public static final class GammaBand {
        private final int index;         // 1-based '- # k'
        private final String gruneisenText;
        private final String frequencyText;
        private final double gruneisen;
        private final double frequency;

        GammaBand(int index, String gruneisenText, String frequencyText,
                double gruneisen, double frequency) {
            this.index = index;
            this.gruneisenText = gruneisenText;
            this.frequencyText = frequencyText;
            this.gruneisen = gruneisen;
            this.frequency = frequency;
        }

        public int getIndex() { return this.index; }

        /** gamma as printed by the upstream '%15.10f' writer (verbatim). */
        public String getGruneisenText() { return this.gruneisenText; }

        public double getGruneisen() { return this.gruneisen; }

        public double getFrequency() { return this.frequency; }

        /** Negative frequency = imaginary mode, named never hidden. */
        public boolean isImaginaryFrequency() { return this.frequency < 0.0; }
    }

    /** One q row (band mode has distance; mesh mode has multiplicity). */
    public static final class GammaQ {
        private final double[] q;
        private final Double distance;      // band mode
        private final Integer multiplicity; // mesh mode
        private final List<GammaBand> bands;

        GammaQ(double[] q, Double distance, Integer multiplicity,
                List<GammaBand> bands) {
            this.q = q;
            this.distance = distance;
            this.multiplicity = multiplicity;
            this.bands = bands;
        }

        public double[] getQ() { return this.q.clone(); }

        /** Band-mode distance along the path (null in mesh mode). */
        public Double getDistance() { return this.distance; }

        /** Mesh-mode multiplicity (null in band mode). */
        public Integer getMultiplicity() { return this.multiplicity; }

        public List<GammaBand> getBands() { return this.bands; }

        /** True for q == (0,0,0): gamma may diverge there (doc-stated). */
        public boolean isGammaPoint() {
            return Math.abs(this.q[0]) < 1e-12 && Math.abs(this.q[1]) < 1e-12
                    && Math.abs(this.q[2]) < 1e-12;
        }
    }

    /** One band-mode segment (path: '- nqpoint: N' + phonon rows). */
    public static final class GammaSegment {
        private final int declaredNqpoint;
        private final String startLabel;
        private final String endLabel;
        private final List<GammaQ> rows;

        GammaSegment(int declaredNqpoint, String startLabel, String endLabel,
                List<GammaQ> rows) {
            this.declaredNqpoint = declaredNqpoint;
            this.startLabel = startLabel;
            this.endLabel = endLabel;
            this.rows = rows;
        }

        /** The segment's own '- nqpoint: N' declaration. */
        public int getDeclaredNqpoint() { return this.declaredNqpoint; }

        public String getStartLabel() { return this.startLabel; }

        public String getEndLabel() { return this.endLabel; }

        public List<GammaQ> getRows() { return this.rows; }
    }

    /** The parsed document. */
    public static final class GruneisenYaml {
        private final String sourceName;
        private final Mode mode;
        private final int[] mesh;                    // mesh mode header, else null
        private final Integer headerNqpoint;         // 'nqpoint:' header count
        private final Integer headerNpath;           // band mode
        private final int[] headerSegmentNqpoint;    // band mode header, may be empty
        private final List<String[]> labels;         // [start,end] pairs as printed
        private final List<GammaSegment> segments;   // band mode
        private final List<GammaQ> meshRows;         // mesh mode
        private final int bandCount;
        private final int partialRowsHeld;
        private final List<String> notes;

        GruneisenYaml(String sourceName, Mode mode, int[] mesh, Integer headerNqpoint,
                Integer headerNpath, int[] headerSegmentNqpoint, List<String[]> labels,
                List<GammaSegment> segments, List<GammaQ> meshRows, int bandCount,
                int partialRowsHeld, List<String> notes) {
            this.sourceName = sourceName;
            this.mode = mode;
            this.mesh = mesh;
            this.headerNqpoint = headerNqpoint;
            this.headerNpath = headerNpath;
            this.headerSegmentNqpoint = headerSegmentNqpoint;
            this.labels = labels;
            this.segments = segments;
            this.meshRows = meshRows;
            this.bandCount = bandCount;
            this.partialRowsHeld = partialRowsHeld;
            this.notes = notes;
        }

        public String getSourceName() { return this.sourceName; }

        /** Content-decided flavor (BAND = 'path:'/segments, MESH = multiplicity rows). */
        public Mode getMode() { return this.mode; }

        /** MESH mode header triple, null in band mode. */
        public int[] getMesh() { return this.mesh == null ? null : this.mesh.clone(); }

        public Integer getHeaderNqpoint() { return this.headerNqpoint; }

        public Integer getHeaderNpath() { return this.headerNpath; }

        public int[] getHeaderSegmentNqpoint() { return this.headerSegmentNqpoint.clone(); }

        /** Label pairs as printed (connection-aware consumption, band-mode only). */
        public List<String[]> getLabels() { return this.labels; }

        /** Band mode: the path segments. Mesh mode: empty. */
        public List<GammaSegment> getSegments() { return this.segments; }

        /** Mesh mode: the irreducible q rows. Band mode: empty. */
        public List<GammaQ> getMeshRows() { return this.meshRows; }

        /** Band entries per q row (uniform across the file, enforced). */
        public int getBandCount() { return this.bandCount; }

        public int getPartialRowsHeld() { return this.partialRowsHeld; }

        public List<String> getNotes() { return this.notes; }

        /** All q rows flattened from segments (band mode) or mesh rows. */
        public List<GammaQ> flatRows() {
            if (this.mode == Mode.MESH) {
                return this.meshRows;
            }
            List<GammaQ> rows = new ArrayList<>();
            for (GammaSegment segment : this.segments) {
                rows.addAll(segment.getRows());
            }
            return rows;
        }

        /** gamma extent over every band entry (computed fact). */
        public double[] gammaExtent() {
            double lo = Double.POSITIVE_INFINITY;
            double hi = Double.NEGATIVE_INFINITY;
            for (GammaQ row : flatRows()) {
                for (GammaBand band : row.getBands()) {
                    lo = Math.min(lo, band.getGruneisen());
                    hi = Math.max(hi, band.getGruneisen());
                }
            }
            return lo <= hi ? new double[] {lo, hi} : null;
        }

        /** Count of band entries with negative gamma. */
        public int getNegativeGammaCount() {
            int count = 0;
            for (GammaQ row : flatRows()) {
                for (GammaBand band : row.getBands()) {
                    if (band.getGruneisen() < 0.0) {
                        count++;
                    }
                }
            }
            return count;
        }

        /** Count of q rows exactly at Gamma (doc: gamma may diverge there). */
        public int getGammaPointRowCount() {
            int count = 0;
            for (GammaQ row : flatRows()) {
                if (row.isGammaPoint()) {
                    count++;
                }
            }
            return count;
        }
    }

    /** Reads a gruneisen yaml from disk (bounded). */
    public static OperationResult<GruneisenYaml> parse(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return OperationResult.failed("PHONOPY_GRUNEISEN_INPUT",
                    "No such gruneisen yaml: " + file, null);
        }
        try {
            long size = Files.size(file);
            if (size > MAX_FILE_BYTES) {
                return OperationResult.failed("PHONOPY_GRUNEISEN_INPUT",
                        "gruneisen yaml exceeds the " + MAX_FILE_BYTES
                                + "-byte safety bound (" + size + " bytes): refusing"
                                + " rather than reading an unbounded artifact.", null);
            }
            return parseText(Files.readString(file), file.getFileName().toString());
        } catch (IOException e) {
            return OperationResult.failed("PHONOPY_GRUNEISEN_INPUT",
                    "Could not read gruneisen yaml " + file + ": " + e.getMessage(),
                    null);
        }
    }

    /** Parses gruneisen yaml text; mode decided by content, not name. */
    public static OperationResult<GruneisenYaml> parseText(String text,
            String sourceName) {
        if (text == null) {
            return OperationResult.failed("PHONOPY_GRUNEISEN_INPUT", "null text", null);
        }
        String[] raw = text.split("\n", -1);
        List<String> lines = new ArrayList<>(raw.length);
        for (String s : raw) {
            String rightTrimmed = s.stripTrailing();
            if (!rightTrimmed.strip().isEmpty()) {
                lines.add(rightTrimmed);
            }
        }
        if (lines.isEmpty()) {
            return OperationResult.failed("PHONOPY_GRUNEISEN_EMPTY",
                    sourceName + ": no content - expected the 'path:'/segments"
                            + " layout (band mode) or the 'mesh: [...]' layout"
                            + " (mesh mode) of the pinned upstream writers.", null);
        }

        // ---- mode by CONTENT ----
        boolean meshContent = false;
        boolean bandContent = false;
        for (String line : lines) {
            if (MESH_HEAD.matcher(line).matches()) {
                meshContent = true;
            }
            if (line.equals("path:")) {
                bandContent = true;
            }
        }
        if (meshContent && bandContent) {
            return OperationResult.failed("PHONOPY_GRUNEISEN_SHAPE",
                    sourceName + ": carries BOTH a 'mesh: [...]' header and a 'path:'"
                            + " section - no upstream writer emits that combination;"
                            + " refusing rather than guessing the mode.", null);
        }
        if (!meshContent && !bandContent) {
            return OperationResult.failed("PHONOPY_GRUNEISEN_HEADER",
                    sourceName + ": neither a 'path:' section (band mode,"
                            + " gruneisen.yaml) nor a 'mesh: [ m1, m2, m3 ]' header"
                            + " (mesh mode, gruneisen_mesh.yaml) found - the two"
                            + " pinned layouts of the upstream gruneisen writers.",
                    null);
        }
        return meshContent ? parseMesh(lines, sourceName) : parseBand(lines, sourceName);
    }

    // ------------------------- BAND mode -------------------------

    private static OperationResult<GruneisenYaml> parseBand(List<String> lines,
            String sourceName) {
        Integer headerNqpoint = null;
        Integer headerNpath = null;
        List<Integer> headerSegments = new ArrayList<>();
        List<String[]> labels = new ArrayList<>();
        int headerEnd = -1; // index of the 'path:' line
        boolean inSegmentNq = false;
        boolean inLabels = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.equals("path:")) {
                headerEnd = i;
                break;
            }
            Matcher kv = KV.matcher(line);
            if (kv.matches()) {
                inSegmentNq = false;
                inLabels = false;
                int value = Integer.parseInt(kv.group(2));
                if (kv.group(1).equals("nqpoint")) {
                    headerNqpoint = value;
                } else if (kv.group(1).equals("npath")) {
                    headerNpath = value;
                }
                // natom is accepted but not tracked
                continue;
            }
            if (line.equals("segment_nqpoint:")) {
                inSegmentNq = true;
                inLabels = false;
                continue;
            }
            if (line.equals("labels:")) {
                inLabels = true;
                inSegmentNq = false;
                continue;
            }
            if (line.equals("reciprocal_lattice:")) {
                inSegmentNq = false;
                inLabels = false;
                continue;
            }
            if (inSegmentNq) {
                Matcher item = SEGMENT_ITEM.matcher(line);
                if (item.matches()) {
                    headerSegments.add(Integer.valueOf(item.group(1)));
                    continue;
                }
                inSegmentNq = false;
            }
            if (inLabels) {
                Matcher label = LABEL.matcher(line);
                if (label.matches()) {
                    labels.add(new String[] {label.group(1), label.group(2)});
                    continue;
                }
                inLabels = false;
            }
            // reciprocal lattice rows + cell text + comment block: tolerated verbatim
        }
        if (headerEnd < 0) {
            return OperationResult.failed("PHONOPY_GRUNEISEN_HEADER",
                    sourceName + ": no 'path:' section marker - the band-mode"
                            + " writer (gruneisen/band_structure.py _write_yaml)"
                            + " always emits it after the header block.", null);
        }

        List<GammaSegment> segments = new ArrayList<>();
        int held = 0;
        int bandCount = -1;
        int cursor = headerEnd + 1;
        String shapeIssue = null;
        List<GammaQ> currentRows = null;
        int currentDeclared = -1;

        int i = cursor;
        while (i < lines.size()) {
            String line = lines.get(i);
            Matcher segHead = SEGMENT_NQ.matcher(line);
            if (segHead.matches()) {
                if (currentRows != null) {
                    segments.add(buildSegment(currentDeclared, currentRows,
                            labels, segments.size()));
                }
                currentDeclared = Integer.parseInt(segHead.group(1));
                currentRows = new ArrayList<>();
                i++;
                if (i >= lines.size() || !lines.get(i).equals("  phonon:")) {
                    if (i >= lines.size()) {
                        held++; // segment cut at its very start (live)
                        currentRows = null;
                        break;
                    }
                    shapeIssue = "segment '- nqpoint: " + currentDeclared
                            + "' is not followed by '  phonon:' (found '"
                            + lines.get(i) + "')";
                    break;
                }
                i++;
                continue;
            }
            if (currentRows == null) {
                shapeIssue = "content inside 'path:' before the first '- nqpoint:'"
                        + " segment header: '" + line + "'";
                break;
            }
            if (QROW.matcher(line).matches()) {
                BlockProbe probe = readQBlock(lines, i, false);
                if (probe.row != null) {
                    if (bandCount < 0) {
                        bandCount = probe.row.getBands().size();
                    } else if (probe.row.getBands().size() != bandCount) {
                        shapeIssue = "q row has " + probe.row.getBands().size()
                                + " band entries where earlier rows have " + bandCount
                                + " (the upstream writer zips a uniform band count"
                                + " per segment; refusing rather than picking one)";
                        break;
                    }
                    currentRows.add(probe.row);
                    i = probe.nextIndex;
                    continue;
                }
                if (probe.truncatedAtEof) {
                    held++;
                    break;
                }
                shapeIssue = probe.issue;
                break;
            }
            shapeIssue = "unexpected line inside a path segment: '" + line + "'";
            break;
        }
        if (shapeIssue != null) {
            return OperationResult.failed("PHONOPY_GRUNEISEN_SHAPE",
                    sourceName + ": " + shapeIssue
                            + " (grammar pinned to gruneisen/band_structure.py"
                            + " _write_yaml @ 3a3e0f09)", null);
        }
        if (currentRows != null) {
            segments.add(buildSegment(currentDeclared, currentRows, labels,
                    segments.size()));
        }
        if (segments.isEmpty()) {
            return OperationResult.failed("PHONOPY_GRUNEISEN_PARTIAL",
                    sourceName + ": 'path:' present but not even ONE complete"
                            + " segment parsed (file just opened or mid-write?) -"
                            + " refusing rather than reading an empty path as real.",
                    null);
        }
        return finishBand(sourceName, headerNqpoint, headerNpath, headerSegments,
                labels, segments, bandCount, held);
    }

    private static GammaSegment buildSegment(int declared, List<GammaQ> rows,
            List<String[]> labels, int segmentIndex) {
        String start = null;
        String end = null;
        // labels are consumed connection-aware by the writer; without path
        // connection info the SAFEST report is the pair list verbatim - assign
        // [i] when the count allows, else leave null (stated in the note)
        if (segmentIndex < labels.size()) {
            start = labels.get(segmentIndex)[0];
            end = labels.get(segmentIndex)[1];
        }
        return new GammaSegment(declared, start, end, rows);
    }

    private static OperationResult<GruneisenYaml> finishBand(String sourceName,
            Integer headerNqpoint, Integer headerNpath, List<Integer> headerSegments,
            List<String[]> labels, List<GammaSegment> segments, int bandCount,
            int held) {
        List<String> notes = new ArrayList<>();
        notes.add("mode decided by CONTENT: band layout ('path:' + '- nqpoint:'"
                + " segments) - the file name plays no part");
        notes.add("gamma(q,nu) = -V/(2*omega^2) * <e|dD/dV|e> by finite difference"
                + " over THREE volumes (equilibrium + larger + smaller, each fully"
                + " relaxed under its volume constraint) - doc/gruneisen.md verbatim"
                + " definition; QuantumForge reports gamma, it does not recompute it");
        int parsedQ = 0;
        boolean mismatch = false;
        for (int s = 0; s < segments.size(); s++) {
            GammaSegment segment = segments.get(s);
            parsedQ += segment.getRows().size();
            if (segment.getDeclaredNqpoint() != segment.getRows().size()) {
                mismatch = true;
                notes.add("segment " + (s + 1) + ": declared '- nqpoint: "
                        + segment.getDeclaredNqpoint() + "' but "
                        + segment.getRows().size() + " q rows parsed"
                        + (held > 0 ? " (trailing partial held - live write)"
                                : " - REPORTED mismatch, never guessed"));
            }
        }
        if (headerNqpoint != null && headerNqpoint != parsedQ) {
            notes.add("header nqpoint: " + headerNqpoint + " vs " + parsedQ
                    + " rows parsed" + (held > 0 ? " (trailing partial held" : " (REPORTED")
                    + (held > 0 ? ")" : ")"));
        }
        if (!headerSegments.isEmpty()) {
            notes.add("header segment_nqpoint: " + headerSegments + " - read against"
                    + " the per-segment '- nqpoint:' declarations verbatim");
        }
        if (!labels.isEmpty()) {
            notes.add(labels.size() + " label pair(s) printed verbatim; their"
                    + " connection-aware consumption (shared vertex advances 1) mirrors"
                    + " the writer, identical to band.yaml's rule");
        }
        notes.add("band-crossing truth from the doc: neighboring q-points in each"
                + " segment are connected considering phonon symmetry, so frequencies"
                + " inside a segment may NOT be ordered - verbatim statement, and the"
                + " reason this reader never re-sorts anything");
        if (held > 0) {
            notes.add(held + " trailing partial q block held back (live write in"
                    + " progress)");
        }
        GruneisenYaml yaml = new GruneisenYaml(sourceName, Mode.BAND, null,
                headerNqpoint, headerNpath,
                headerSegments.stream().mapToInt(Integer::intValue).toArray(), labels,
                segments, List.of(), bandCount < 0 ? 0 : bandCount, held,
                List.copyOf(notes));
        int gammaPoints = yaml.getGammaPointRowCount();
        StringBuilder message = new StringBuilder(sourceName)
                .append(": BAND mode, ").append(segments.size()).append(" segment(s), ")
                .append(parsedQ).append(" q rows, band entries per q: ")
                .append(bandCount);
        double[] extent = yaml.gammaExtent();
        if (extent != null) {
            message.append(String.format(Locale.ROOT, ", gamma in [%.6f, %.6f]",
                    extent[0], extent[1]));
        }
        if (gammaPoints > 0) {
            message.append("; ").append(gammaPoints)
                    .append(" Gamma-point row(s): gamma may diverge there (doc: the"
                            + " upstream plot itself skips the exact Gamma value)");
        }
        if (held > 0) {
            message.append("; ").append(held).append(" trailing partial held");
        }
        return OperationResult.success("PHONOPY_GRUNEISEN_OK", message.toString(), yaml);
    }

    // ------------------------- MESH mode -------------------------

    private static OperationResult<GruneisenYaml> parseMesh(List<String> lines,
            String sourceName) {
        int[] mesh = null;
        Integer headerNqpoint = null;
        int phononIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher meshHead = MESH_HEAD.matcher(line);
            if (meshHead.matches()) {
                mesh = new int[] {Integer.parseInt(meshHead.group(1)),
                        Integer.parseInt(meshHead.group(2)),
                        Integer.parseInt(meshHead.group(3))};
                continue;
            }
            Matcher kv = KV.matcher(line);
            if (kv.matches() && kv.group(1).equals("nqpoint")) {
                headerNqpoint = Integer.valueOf(kv.group(2));
                continue;
            }
            if (line.equals("phonon:")) {
                phononIndex = i;
                break;
            }
        }
        if (mesh == null) {
            return OperationResult.failed("PHONOPY_GRUNEISEN_HEADER",
                    sourceName + ": no 'mesh: [ m1, m2, m3 ]' header - the mesh-mode"
                            + " writer (gruneisen/mesh.py _write_yaml) always opens"
                            + " with it.", null);
        }
        if (phononIndex < 0) {
            return OperationResult.failed("PHONOPY_GRUNEISEN_HEADER",
                    sourceName + ": no 'phonon:' section - the mesh-mode writer"
                            + " emits irreducible q rows under it.", null);
        }
        List<GammaQ> rows = new ArrayList<>();
        int held = 0;
        int bandCount = -1;
        int i = phononIndex + 1;
        String shapeIssue = null;
        while (i < lines.size()) {
            String line = lines.get(i);
            if (QROW.matcher(line).matches()) {
                BlockProbe probe = readQBlock(lines, i, true);
                if (probe.row != null) {
                    if (bandCount < 0) {
                        bandCount = probe.row.getBands().size();
                    } else if (probe.row.getBands().size() != bandCount) {
                        shapeIssue = "irreducible q row has " + probe.row.getBands().size()
                                + " band entries where earlier rows have " + bandCount
                                + " (uniform per mesh per the upstream writer zip;"
                                + " refusing rather than picking one)";
                        break;
                    }
                    rows.add(probe.row);
                    i = probe.nextIndex;
                    continue;
                }
                if (probe.truncatedAtEof) {
                    held++;
                    break;
                }
                shapeIssue = probe.issue;
                break;
            }
            shapeIssue = "unexpected line inside the mesh 'phonon:' section: '" + line
                    + "'";
            break;
        }
        if (shapeIssue != null) {
            return OperationResult.failed("PHONOPY_GRUNEISEN_SHAPE",
                    sourceName + ": " + shapeIssue
                            + " (grammar pinned to gruneisen/mesh.py _write_yaml"
                            + " @ 3a3e0f09)", null);
        }
        if (rows.isEmpty()) {
            return OperationResult.failed("PHONOPY_GRUNEISEN_PARTIAL",
                    sourceName + ": 'phonon:' present but not even ONE complete"
                            + " irreducible q row parsed - refusing rather than"
                            + " reading an empty mesh as real.", null);
        }
        List<String> notes = new ArrayList<>();
        notes.add("mode decided by CONTENT: mesh layout ('mesh: [...]' +"
                + " multiplicity rows) - the file name plays no part");
        notes.add("gamma(q,nu) = -V/(2*omega^2) * <e|dD/dV|e> by finite difference"
                + " over THREE volumes (equilibrium + larger + smaller, each fully"
                + " relaxed under its volume constraint) - doc/gruneisen.md verbatim"
                + " definition; QuantumForge reports gamma, it does not recompute it");
        long multSum = 0;
        for (GammaQ row : rows) {
            multSum += row.getMultiplicity() == null ? 0 : row.getMultiplicity();
        }
        long meshProduct = (long) mesh[0] * mesh[1] * mesh[2];
        notes.add("multiplicities sum " + multSum + " over " + rows.size()
                + " irreducible q rows, against mesh product " + meshProduct
                + " (" + mesh[0] + "x" + mesh[1] + "x" + mesh[2] + ")"
                + " - phonopy's own weight-partition expectation, REPORTED verbatim"
                + (multSum == meshProduct ? " (agrees)" : " (DIFFERS - reported,"
                        + " never corrected)"));
        if (headerNqpoint != null && headerNqpoint != rows.size()) {
            notes.add("header nqpoint: " + headerNqpoint + " vs " + rows.size()
                    + " irreducible rows parsed" + (held > 0 ? " (trailing partial"
                            + " held)" : " - REPORTED mismatch"));
        }
        if (held > 0) {
            notes.add(held + " trailing partial q block held back (live write in"
                    + " progress)");
        }
        GruneisenYaml yaml = new GruneisenYaml(sourceName, Mode.MESH, mesh,
                headerNqpoint, null, new int[0], List.of(), List.of(), rows,
                bandCount < 0 ? 0 : bandCount, held, List.copyOf(notes));
        int gammaPoints = yaml.getGammaPointRowCount();
        StringBuilder message = new StringBuilder(sourceName)
                .append(": MESH mode, ").append(rows.size())
                .append(" irreducible q rows, band entries per q: ").append(bandCount);
        double[] extent = yaml.gammaExtent();
        if (extent != null) {
            message.append(String.format(Locale.ROOT, ", gamma in [%.6f, %.6f]",
                    extent[0], extent[1]));
        }
        if (gammaPoints > 0) {
            message.append("; ").append(gammaPoints)
                    .append(" Gamma-point row(s): gamma may diverge there (doc: the"
                            + " upstream plot itself skips the exact Gamma value)");
        }
        if (held > 0) {
            message.append("; ").append(held).append(" trailing partial held");
        }
        return OperationResult.success("PHONOPY_GRUNEISEN_OK", message.toString(), yaml);
    }

    // ------------------------- shared q-block probe -------------------------

    private static final class BlockProbe {
        GammaQ row;
        int nextIndex;
        boolean truncatedAtEof;
        String issue;
    }

    /**
     * Reads one q block at {@code start} (the q-position line). Band mode:
     * q + distance + band entries. Mesh mode: q + multiplicity + band entries.
     */
    private static BlockProbe readQBlock(List<String> lines, int start,
            boolean meshMode) {
        BlockProbe probe = new BlockProbe();
        String[] qTokens = new String[3];
        Matcher q = QROW.matcher(lines.get(start));
        if (!q.matches()) {
            probe.issue = "q-position line is malformed: '" + lines.get(start) + "'";
            return probe;
        }
        qTokens[0] = q.group(1);
        qTokens[1] = q.group(2);
        qTokens[2] = q.group(3);
        int cursor = start + 1;
        Double distance = null;
        Integer multiplicity = null;
        if (meshMode) {
            Matcher multM = cursor >= lines.size() ? null
                    : MULT.matcher(lines.get(cursor));
            if (multM == null) {
                probe.truncatedAtEof = true;
                return probe;
            }
            if (!multM.matches()) {
                probe.issue = "mesh-mode q row without its 'multiplicity:' line:"
                        + " '" + lines.get(cursor) + "'";
                return probe;
            }
            multiplicity = Integer.valueOf(multM.group(1));
            cursor++;
        } else {
            Matcher distM = cursor >= lines.size() ? null
                    : DIST.matcher(lines.get(cursor));
            if (distM == null) {
                probe.truncatedAtEof = true;
                return probe;
            }
            if (!distM.matches()) {
                probe.issue = "band-mode q row without its 'distance:' line: '"
                        + lines.get(cursor) + "'";
                return probe;
            }
            distance = parseDoubleSafe(distM.group(1));
            if (distance == null) {
                probe.issue = "band-mode distance is not plain double text: '"
                        + lines.get(cursor).strip() + "'";
                return probe;
            }
            cursor++;
        }
        if (cursor >= lines.size()) {
            probe.truncatedAtEof = true;
            return probe;
        }
        if (!lines.get(cursor).strip().equals("band:")) {
            probe.issue = "q row without its 'band:' block: '" + lines.get(cursor) + "'";
            return probe;
        }
        cursor++;
        List<GammaBand> bands = new ArrayList<>();
        while (cursor < lines.size()) {
            Matcher entry = BAND_ENTRY.matcher(lines.get(cursor));
            if (!entry.matches()) {
                break; // next q row / segment header / EOF: block done
            }
            int index = Integer.parseInt(entry.group(1));
            if (cursor + 2 > lines.size() - 1) {
                probe.truncatedAtEof = true; // entry cut after its '- # k' line (live)
                return probe;
            }
            Matcher gamma = GAMMA.matcher(lines.get(cursor + 1));
            Matcher freq = FREQ.matcher(lines.get(cursor + 2));
            if (!gamma.matches() || !freq.matches()) {
                probe.issue = "band entry '- # " + index + "' is not followed by"
                        + " 'gruneisen:' + 'frequency:' lines: '"
                        + lines.get(cursor + 1).strip() + "' / '"
                        + lines.get(cursor + 2).strip() + "'";
                return probe;
            }
            Double g = parseDoubleSafe(gamma.group(1));
            Double f = parseDoubleSafe(freq.group(1));
            if (g == null || f == null) {
                probe.issue = "band entry '- # " + index + "' carries non-double"
                        + " text: '" + gamma.group(1) + "' / '" + freq.group(1) + "'";
                return probe;
            }
            bands.add(new GammaBand(index, gamma.group(1), freq.group(1), g, f));
            cursor += 3;
        }
        if (bands.isEmpty()) {
            // q row with an EMPTY band block: only legal as a trailing live write
            if (cursor >= lines.size()) {
                probe.truncatedAtEof = true;
            } else {
                probe.issue = "q row's 'band:' block holds no complete entries";
            }
            return probe;
        }
        double[] qValues = new double[3];
        for (int c = 0; c < 3; c++) {
            Double v = parseDoubleSafe(qTokens[c]);
            if (v == null) {
                probe.issue = "q-position token is not plain double text: '"
                        + qTokens[c] + "'";
                return probe;
            }
            qValues[c] = v;
        }
        probe.row = new GammaQ(qValues, distance, multiplicity, bands);
        probe.nextIndex = cursor;
        return probe;
    }

    private static Double parseDoubleSafe(String token) {
        try {
            return Double.valueOf(token);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** One-line report helper for the service + viewer. */
    public static String describe(GruneisenYaml yaml) {
        StringBuilder sb = new StringBuilder();
        sb.append("mode: ").append(yaml.getMode())
                .append(yaml.getMode() == Mode.MESH
                        ? " (mesh " + joinMesh(yaml.getMesh()) + ", "
                                + yaml.getMeshRows().size() + " irreducible rows)"
                        : " (" + yaml.getSegments().size() + " segments, "
                                + yaml.flatRows().size() + " q rows)")
                .append('\n');
        sb.append("bands per q: ").append(yaml.getBandCount()).append('\n');
        double[] extent = yaml.gammaExtent();
        if (extent != null) {
            sb.append(String.format(Locale.ROOT, "gamma extent: [%.6f, %.6f]%n",
                    extent[0], extent[1]));
        }
        sb.append("negative gamma entries: ").append(yaml.getNegativeGammaCount())
                .append('\n');
        if (yaml.getGammaPointRowCount() > 0) {
            sb.append("Gamma-point rows present: ").append(yaml.getGammaPointRowCount())
                    .append(" (gamma may diverge - doc-stated)\n");
        }
        for (String note : yaml.getNotes()) {
            sb.append("- ").append(note).append('\n');
        }
        return sb.toString().trim();
    }

    private static String joinMesh(int[] mesh) {
        return mesh == null ? "?" : mesh[0] + "x" + mesh[1] + "x" + mesh[2];
    }
}
