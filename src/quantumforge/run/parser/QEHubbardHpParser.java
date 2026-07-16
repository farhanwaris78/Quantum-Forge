/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.run.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import quantumforge.project.property.ProjectProperty;

/**
 * Parses calculated first-principles Hubbard U parameters from hp.x (linear-response)
 * output files and formats the corresponding pw.x correlated HUBBARD cards (Roadmap #63).
 */
public final class QEHubbardHpParser extends LogParser {

    public static final class ParsedHubbardU {
        private final int atomIndex;
        private final String element;
        private final String shell; // d or f
        private final double uValueEv;

        public ParsedHubbardU(int atomIndex, String element, String shell, double uValueEv) {
            this.atomIndex = atomIndex;
            this.element = element == null ? "" : element;
            this.shell = shell == null ? "" : shell;
            this.uValueEv = uValueEv;
        }

        public int getAtomIndex() { return this.atomIndex; }
        public String getElement() { return this.element; }
        public String getShell() { return this.shell; }
        public double getUValueEv() { return this.uValueEv; }
    }

    private final List<ParsedHubbardU> parsedParameters = new ArrayList<>();

    public QEHubbardHpParser(ProjectProperty property) {
        super(property);
    }

    public List<ParsedHubbardU> getParsedParameters() { return List.copyOf(this.parsedParameters); }

    @Override
    public void parse(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        this.parsedParameters.clear();

        // Regex to parse computed Hubbard U:
        // e.g. "Hubbard U for atom    1 (Fe, d) =    4.3212 eV"
        Pattern pU = Pattern.compile("Hubbard\\s+U\\s+for\\s+atom\\s+(\\d+)\\s*\\((\\w+)\\s*,\\s*(\\w+)\\)\\s*=\\s*([-\\d.]+)\\s*eV", Pattern.CASE_INSENSITIVE);

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trim = line.trim();

                Matcher mU = pU.matcher(trim);
                if (mU.find()) {
                    try {
                        int idx = Integer.parseInt(mU.group(1));
                        String element = mName(mU.group(2));
                        String shell = mName(mU.group(3));
                        double val = Double.parseDouble(mU.group(4));
                        this.parsedParameters.add(new ParsedHubbardU(idx, element, shell, val));
                    } catch (NumberFormatException e) {
                        // Skip malformed lines
                    }
                }
            }
        }
    }

    private static String mName(String str) {
        return str == null ? "" : str.trim();
    }

    /**
     * Synthesizes the exact, mathematically correct HUBBARD card block for subsequent
     * pw.x correlated runs (QE 7.x format).
     * 
     * Format:
     * HUBBARD {ortho-atomic}
     *   U Fe-3d 4.3212
     */
    public String generateHubbardCard() {
        if (this.parsedParameters.isEmpty()) {
            return "";
        }

        // Group by element/shell to avoid duplicating cards per individual site
        // unless site-dependent U is preferred. We average U per species shell.
        Map<String, List<Double>> grouped = new LinkedHashMap<>();
        for (ParsedHubbardU p : this.parsedParameters) {
            String key = p.element + "-" + getFullShellLabel(p.shell);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(p.uValueEv);
        }

        StringBuilder sb = new StringBuilder("HUBBARD {ortho-atomic}\n");
        for (Map.Entry<String, List<Double>> entry : grouped.entrySet()) {
            double sum = 0.0;
            for (double val : entry.getValue()) {
                sum += val;
            }
            double average = sum / entry.getValue().size();
            sb.append(String.format(java.util.Locale.ROOT, "  U %s %.4f\n", entry.getKey(), average));
        }

        return sb.toString();
    }

    private static String getFullShellLabel(String shell) {
        // Map d -> 3d, f -> 4f for standard correlated shells
        String sh = shell.toLowerCase();
        if ("d".equals(sh)) {
            return "3d";
        } else if ("f".equals(sh)) {
            return "4f";
        }
        return sh;
    }
}
