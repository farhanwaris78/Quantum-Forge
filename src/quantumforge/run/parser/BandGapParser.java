/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses explicit gap summaries and QE occupied/unoccupied level summaries.
 * It does not infer directness unless the input explicitly states it; rigorous
 * direct/indirect analysis belongs in the k-resolved BandGapDetector.
 */
public final class BandGapParser {
    private static final String NUMBER = "([-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[EeDd][-+]?\\d+)?)";
    private static final Pattern FERMI = Pattern.compile(
            "(?i)the\\s+Fermi\\s+energy\\s+is\\s+" + NUMBER);
    private static final Pattern OCCUPIED_UNOCCUPIED = Pattern.compile(
            "(?i)highest\\s+occupied\\s*,\\s*lowest\\s+unoccupied\\s+level\\s*\\(ev\\)\\s*:\\s*"
                    + NUMBER + "\\s+" + NUMBER);
    private static final Pattern EXPLICIT_GAP = Pattern.compile(
            "(?i)\\b(?:(direct|indirect)\\s+)?band\\s+gap\\s*(?:=|:|is)\\s*" + NUMBER);

    private final Path file;
    private final List<String> diagnostics = new ArrayList<>();
    private double bandGap;
    private double fermiEnergy;
    private boolean insulator;
    private boolean direct;
    private boolean directKnown;

    public BandGapParser(String filePath) {
        this.file = filePath == null ? null : Paths.get(filePath);
        this.reset();
    }

    private void reset() {
        this.bandGap = Double.NaN;
        this.fermiEnergy = Double.NaN;
        this.insulator = false;
        this.direct = false;
        this.directKnown = false;
        this.diagnostics.clear();
    }

    public boolean parse() {
        this.reset();
        if (this.file == null || !Files.isRegularFile(this.file)) {
            this.diagnostics.add("Output file does not exist or is not a regular file.");
            return false;
        }

        try (BufferedReader reader = Files.newBufferedReader(this.file, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                parseLine(line, lineNumber);
            }
        } catch (IOException e) {
            this.diagnostics.add("I/O error: " + e.getMessage());
            return false;
        }

        if (!Double.isFinite(this.bandGap) || this.bandGap < 0.0) {
            this.diagnostics.add("No explicit or occupied/unoccupied band-gap summary was found.");
            return false;
        }
        this.insulator = this.bandGap > 0.01;
        return true;
    }

    private void parseLine(String line, int lineNumber) {
        Matcher fermiMatcher = FERMI.matcher(line);
        if (fermiMatcher.find()) {
            this.fermiEnergy = parseNumber(fermiMatcher.group(1), "Fermi energy", lineNumber);
        }

        Matcher levelsMatcher = OCCUPIED_UNOCCUPIED.matcher(line);
        if (levelsMatcher.find()) {
            double occupied = parseNumber(levelsMatcher.group(1), "highest occupied level", lineNumber);
            double unoccupied = parseNumber(levelsMatcher.group(2), "lowest unoccupied level", lineNumber);
            if (Double.isFinite(occupied) && Double.isFinite(unoccupied)) {
                double gap = unoccupied - occupied;
                if (gap >= 0.0) {
                    this.bandGap = gap;
                    this.directKnown = false;
                } else {
                    this.diagnostics.add("Line " + lineNumber + " has unoccupied energy below occupied energy.");
                }
            }
        }

        Matcher gapMatcher = EXPLICIT_GAP.matcher(line);
        if (gapMatcher.find()) {
            double gap = parseNumber(gapMatcher.group(2), "band gap", lineNumber);
            if (Double.isFinite(gap) && gap >= 0.0) {
                this.bandGap = gap;
                String type = gapMatcher.group(1);
                this.directKnown = type != null;
                this.direct = type != null && "direct".equals(type.toLowerCase(Locale.ROOT));
            }
        }
    }

    private double parseNumber(String value, String field, int lineNumber) {
        try {
            return Double.parseDouble(value.replace('d', 'e').replace('D', 'E'));
        } catch (NumberFormatException e) {
            this.diagnostics.add("Line " + lineNumber + " has invalid " + field + ": " + value);
            return Double.NaN;
        }
    }

    public double getBandGap() { return this.bandGap; }
    public double getFermiEnergy() { return this.fermiEnergy; }
    public boolean isInsulator() { return this.insulator; }
    public boolean isDirect() { return this.directKnown && this.direct; }
    public boolean isDirectKnown() { return this.directKnown; }
    public List<String> getDiagnostics() { return Collections.unmodifiableList(this.diagnostics); }
}
