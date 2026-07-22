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
 * Reader for phonopy's {@code thermal_properties.yaml} (the TPROP mode
 * output). The grammar is pinned against the upstream writer
 * {@code ThermalProperties._get_tp_yaml_lines} (github.com/phonopy/phonopy
 * commit 3a3e0f099da5de2556e75d72ea89b3bb22c8e97e):
 *
 * <pre>
 * # Thermal properties / unit cell (natom)
 *
 * unit:
 *   temperature:   K
 *   free_energy:   kJ/mol
 *   entropy:       J/K/mol
 *   heat_capacity: J/K/mol
 *
 * natom: 2
 * cutoff_frequency: 0.00000
 * num_modes: 48002
 * num_integrated_modes: 48002
 *
 * zero_point_energy:      6.3456789
 *
 * thermal_properties:
 * - temperature:         0.0000000
 *   free_energy:         6.3456789
 *   entropy:             0.0000000
 *   heat_capacity:       0.0000000
 *   energy:              6.3456789
 * </pre>
 *
 * <p>Units are NOT hardcoded here: the file carries its OWN {@code unit:}
 * block and the labels are kept verbatim ({@link ThermalYaml#getUnitLabels()}).
 * The upstream doc adds one caveat that this class carries as a stated note:
 * 'mol' means N_A times the PRIMITIVE CELL (not the formula unit), so a
 * per-formula-unit comparison is the user's arithmetic, never silently done
 * here.</p>
 *
 * <p>Live doctrine: ONLY the last property entry may be a partial append
 * (held back, counted); anything else off-grammar is PHONOPY_TPROP_SHAPE.
 * Verdicts: PHONOPY_TPROP_INPUT, PHONOPY_TPROP_HEADER (no unit: block / no
 * thermal_properties: key), PHONOPY_TPROP_PARTIAL, PHONOPY_TPROP_SHAPE,
 * PHONOPY_TPROP_OK. Bounded read: {@link #MAX_FILE_BYTES}.</p>
 */
public final class QEPhonopyThermalYaml {

    /** Upper bound for one thermal_properties.yaml read. */
    public static final long MAX_FILE_BYTES = 32L * 1024L * 1024L;

    private static final Pattern TOP_KEY = Pattern.compile(
            "^([a-z_]+):\\s*(.*)$");
    private static final Pattern UNIT_ROW = Pattern.compile(
            "^\\s+([a-z_]+):\\s*(\\S.*)$");
    private static final Pattern ENTRY_START = Pattern.compile(
            "^-\\s+temperature:\\s*(\\S+)\\s*$");
    private static final Pattern ENTRY_FIELD = Pattern.compile(
            "^\\s+(free_energy|entropy|heat_capacity|energy):\\s*(\\S+)\\s*$");

    private QEPhonopyThermalYaml() {
        // Utility
    }

    /** One temperature row (energy = free_energy + entropy*T/1000, upstream formula). */
    public static final class ThermalRow {
        private final double temperature;
        private final double freeEnergy;
        private final double entropy;
        private final double heatCapacity;
        private final double energy;

        private ThermalRow(double temperature, double freeEnergy, double entropy,
                           double heatCapacity, double energy) {
            this.temperature = temperature;
            this.freeEnergy = freeEnergy;
            this.entropy = entropy;
            this.heatCapacity = heatCapacity;
            this.energy = energy;
        }

        public double getTemperature() { return this.temperature; }
        public double getFreeEnergy() { return this.freeEnergy; }
        public double getEntropy() { return this.entropy; }
        public double getHeatCapacity() { return this.heatCapacity; }
        public double getEnergy() { return this.energy; }
    }

    /** A parsed thermal_properties.yaml product. */
    public static final class ThermalYaml {
        private final String sourceName;
        private final List<String[]> unitLabels;
        private final Integer natom;
        private final Double volume;
        private final Double cutoffFrequency;
        private final Integer numModes;
        private final Integer numIntegratedModes;
        private final Double zeroPointEnergy;
        private final List<ThermalRow> rows;
        private final int partialRowsHeld;
        private final String molNote;

        private ThermalYaml(String sourceName, List<String[]> unitLabels,
                            Integer natom, Double volume, Double cutoffFrequency,
                            Integer numModes, Integer numIntegratedModes,
                            Double zeroPointEnergy, List<ThermalRow> rows,
                            int partialRowsHeld) {
            this.sourceName = sourceName;
            this.unitLabels = unitLabels;
            this.natom = natom;
            this.volume = volume;
            this.cutoffFrequency = cutoffFrequency;
            this.numModes = numModes;
            this.numIntegratedModes = numIntegratedModes;
            this.zeroPointEnergy = zeroPointEnergy;
            this.rows = rows;
            this.partialRowsHeld = partialRowsHeld;
            this.molNote = "unit block read VERBATIM from the file; the phonopy doc adds:"
                    + " 'mol' is N_A x the primitive cell, NOT the formula unit - a"
                    + " per-formula-unit comparison is the user's own arithmetic, never"
                    + " silently applied here";
        }

        public String getSourceName() { return this.sourceName; }
        /** (quantity, unit-label) rows read verbatim from the file's unit: block. */
        public List<String[]> getUnitLabels() { return this.unitLabels; }
        /** Unit label for one quantity ('free_energy' -> 'kJ/mol'); null if absent. */
        public String unitOf(String quantity) {
            for (String[] pair : this.unitLabels) {
                if (pair[0].equals(quantity)) {
                    return pair[1];
                }
            }
            return null;
        }
        public Integer getNatom() { return this.natom; }
        public Double getVolume() { return this.volume; }
        public Double getCutoffFrequency() { return this.cutoffFrequency; }
        public Integer getNumModes() { return this.numModes; }
        public Integer getNumIntegratedModes() { return this.numIntegratedModes; }
        /** Zero-point energy (same unit as free_energy per the writer). */
        public Double getZeroPointEnergy() { return this.zeroPointEnergy; }
        public List<ThermalRow> getRows() { return this.rows; }
        /** Trailing entries held back as a live partial append. */
        public int getPartialRowsHeld() { return this.partialRowsHeld; }
        /** The mol-convention caveat, stated verbatim from the phonopy doc. */
        public String getMolNote() { return this.molNote; }
    }

    /** Bounded-file entry point. */
    public static OperationResult<ThermalYaml> parse(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return OperationResult.failed("PHONOPY_TPROP_INPUT",
                    "Not a regular file: " + file, null);
        }
        long size;
        try {
            size = Files.size(file);
        } catch (IOException ex) {
            return OperationResult.failed("PHONOPY_TPROP_INPUT",
                    "Size unreadable for " + file + ": " + ex.getMessage(), null);
        }
        if (size > MAX_FILE_BYTES) {
            return OperationResult.failed("PHONOPY_TPROP_INPUT",
                    file.getFileName() + " exceeds the " + MAX_FILE_BYTES
                            + "-byte thermal_properties.yaml bound; refusing an"
                            + " unbounded read.", null);
        }
        String text;
        try {
            text = Files.readString(file);
        } catch (IOException | RuntimeException ex) {
            return OperationResult.failed("PHONOPY_TPROP_INPUT",
                    "Could not read " + file + " as UTF-8 text: " + ex.getMessage(),
                    null);
        }
        return parseText(text, file.getFileName().toString());
    }

    /** Text entry point (tests, live-read content). */
    public static OperationResult<ThermalYaml> parseText(String text,
            String sourceName) {
        if (text == null) {
            return OperationResult.failed("PHONOPY_TPROP_INPUT",
                    "No thermal_properties.yaml content supplied.", null);
        }
        if (text.length() > MAX_FILE_BYTES) {
            return OperationResult.failed("PHONOPY_TPROP_INPUT",
                    "thermal_properties.yaml text exceeds the " + MAX_FILE_BYTES
                            + "-char bound.", null);
        }
        String[] lines = text.split("\n", -1);

        List<String[]> unitLabels = new ArrayList<>();
        Integer natom = null;
        Double volume = null;
        Double cutoff = null;
        Integer numModes = null;
        Integer numIntegrated = null;
        Double zeroPoint = null;
        List<ThermalRow> rows = new ArrayList<>();
        int partialHeld = 0;

        int cursor = 0;
        boolean unitSeen = false;
        boolean propertiesSeen = false;
        while (cursor < lines.length) {
            String line = lines[cursor];
            cursor++;
            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                continue;
            }
            Matcher entry = ENTRY_START.matcher(line);
            if (entry.matches()) {
                if (!propertiesSeen) {
                    return OperationResult.failed("PHONOPY_TPROP_SHAPE",
                            sourceName + ": a thermal_properties entry appeared without"
                                    + " the 'thermal_properties:' header key - grammar"
                                    + " broken, nothing guessed.", null);
                }
                double t = parseFinite(entry.group(1));
                if (Double.isNaN(t)) {
                    return OperationResult.failed("PHONOPY_TPROP_SHAPE",
                            sourceName + " line " + cursor + ": temperature is not a"
                                    + " finite number.", null);
                }
                Double fe = null;
                Double s = null;
                Double cv = null;
                Double e = null;
                while (cursor < lines.length) {
                    String fieldLine = lines[cursor];
                    Matcher field = ENTRY_FIELD.matcher(fieldLine);
                    int fieldLineNo = cursor + 1;
                    if (field.matches()) {
                        cursor++;
                        double value = parseFinite(field.group(2));
                        if (Double.isNaN(value)) {
                            return OperationResult.failed("PHONOPY_TPROP_SHAPE",
                                    sourceName + " line " + fieldLineNo + ": "
                                            + field.group(1) + " is not a finite"
                                            + " number.", null);
                        }
                        switch (field.group(1)) {
                            case "free_energy": fe = value; break;
                            case "entropy": s = value; break;
                            case "heat_capacity": cv = value; break;
                            default: e = value; break;
                        }
                        continue;
                    }
                    break;
                }
                if (fe == null || s == null || cv == null || e == null) {
                    boolean atEnd = true;
                    for (int i = cursor; i < lines.length; i++) {
                        if (!lines[i].trim().isEmpty()) {
                            atEnd = false;
                            break;
                        }
                    }
                    if (atEnd) {
                        partialHeld++; // a live append mid-entry: held back
                        continue;
                    }
                    return OperationResult.failed("PHONOPY_TPROP_SHAPE",
                            sourceName + " line " + cursor + ": a thermal entry is"
                                    + " expected to carry temperature + free_energy +"
                                    + " entropy + heat_capacity + energy; a mid-file"
                                    + " short entry is a grammar break, never"
                                    + " completed by a lookup.", null);
                }
                rows.add(new ThermalRow(t, fe.doubleValue(), s.doubleValue(),
                        cv.doubleValue(), e.doubleValue()));
                continue;
            }
            Matcher top = TOP_KEY.matcher(line);
            if (top.matches() && !line.startsWith(" ") && !line.startsWith("\t")) {
                String key = top.group(1);
                String value = top.group(2).trim();
                switch (key) {
                    case "unit":
                        unitSeen = true;
                        while (cursor < lines.length) {
                            Matcher unitRow = UNIT_ROW.matcher(lines[cursor]);
                            if (!unitRow.matches()) {
                                break;
                            }
                            unitLabels.add(new String[] {unitRow.group(1),
                                    unitRow.group(2).trim()});
                            cursor++;
                        }
                        continue;
                    case "natom":
                        natom = tryInt(value);
                        continue;
                    case "volume":
                        volume = tryDouble(value);
                        continue;
                    case "cutoff_frequency":
                        cutoff = tryDouble(value);
                        continue;
                    case "num_modes":
                        numModes = tryInt(value);
                        continue;
                    case "num_integrated_modes":
                        numIntegrated = tryInt(value);
                        continue;
                    case "zero_point_energy":
                        zeroPoint = tryDouble(value);
                        continue;
                    case "thermal_properties":
                        propertiesSeen = true;
                        continue;
                    default:
                        continue; // band_index / foreign keys: enumerated, not stored
                }
            }
        }
        if (!propertiesSeen) {
            return OperationResult.failed("PHONOPY_TPROP_HEADER",
                    sourceName + " carries no 'thermal_properties:' key - not a"
                            + " thermal_properties.yaml (or a format this build has"
                            + " not pinned), refused rather than guessed.", null);
        }
        if (rows.isEmpty()) {
            return OperationResult.failed("PHONOPY_TPROP_PARTIAL",
                    sourceName + " has the header keys but no complete property rows"
                            + (partialHeld > 0 ? " (" + partialHeld
                                    + " partial entry held back)" : "")
                            + " - a live TPROP run is still writing.", null);
        }
        if (!unitSeen || unitLabels.isEmpty()) {
            return OperationResult.failed("PHONOPY_TPROP_HEADER",
                    sourceName + ": the writer ALWAYS emits a 'unit:' block; its"
                            + " absence means a hand-made or foreign file - unit"
                            + " labels are never invented here.", null);
        }
        ThermalYaml yaml = new ThermalYaml(sourceName, List.copyOf(unitLabels), natom,
                volume, cutoff, numModes, numIntegrated, zeroPoint,
                List.copyOf(rows), partialHeld);
        return OperationResult.success("PHONOPY_TPROP_OK",
                rows.size() + " temperature rows (units verbatim from the file's own"
                        + " unit: block)" + (partialHeld > 0 ? ", " + partialHeld
                                + " trailing partial entr(ies) held back" : "") + ".",
                yaml);
    }

    private static Integer tryInt(String value) {
        try {
            return Integer.valueOf(value.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static Double tryDouble(String value) {
        double parsed = parseFinite(value.trim());
        return Double.isNaN(parsed) ? null : Double.valueOf(parsed);
    }

    private static double parseFinite(String token) {
        try {
            double value = QEThermoPwSeriesParser.parseFortranDouble(token);
            return Double.isFinite(value) ? value : Double.NaN;
        } catch (RuntimeException ex) {
            return Double.NaN;
        }
    }

    /** Unit-aware series for the four chartable quantities. */
    public static String[][] chartSeries(ThermalYaml yaml) {
        List<ThermalRow> rows = yaml.getRows();
        String[][] out = new String[4][rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            ThermalRow row = rows.get(i);
            out[0][i] = String.format(Locale.ROOT, "%.7f", row.getFreeEnergy());
            out[1][i] = String.format(Locale.ROOT, "%.7f", row.getEntropy());
            out[2][i] = String.format(Locale.ROOT, "%.7f", row.getHeatCapacity());
            out[3][i] = String.format(Locale.ROOT, "%.7f", row.getEnergy());
        }
        return out;
    }
}
