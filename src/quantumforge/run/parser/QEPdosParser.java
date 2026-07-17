/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.run.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import quantumforge.project.property.ProjectProperty;

/**
 * Parses Quantum ESPRESSO projwfc.x projected DOS (.pdos) files, extracting
 * atom index, element, wavefunction index, orbital angular momentum (s, p, d, f),
 * and spin channels (Roadmap #48).
 */
public final class QEPdosParser extends LogParser {

    public static final class PdosComponent {
        private final File file;
        private final int atomIndex;
        private final String element;
        private final int wfcIndex;
        private final String orbitalL; // s, p, d, f
        private final double[] energies;
        private final double[] pdos;
        private final int spinChannel; // 1 for unpolarized/up, 2 for down

        public PdosComponent(File file, int atomIndex, String element, int wfcIndex, String orbitalL,
                             double[] energies, double[] pdos, int spinChannel) {
            this.file = file;
            this.atomIndex = atomIndex;
            this.element = element == null ? "" : element;
            this.wfcIndex = wfcIndex;
            this.orbitalL = orbitalL == null ? "" : orbitalL;
            this.energies = energies.clone();
            this.pdos = pdos.clone();
            this.spinChannel = spinChannel;
        }

        public File getFile() { return this.file; }
        public int getAtomIndex() { return this.atomIndex; }
        public String getElement() { return this.element; }
        public int getWfcIndex() { return this.wfcIndex; }
        public String getOrbitalL() { return this.orbitalL; }
        public double[] getEnergies() { return this.energies.clone(); }
        public double[] getPdos() { return this.pdos.clone(); }
        public int getSpinChannel() { return this.spinChannel; }
    }

    private final List<PdosComponent> components = new ArrayList<>();

    public QEPdosParser(ProjectProperty property) {
        super(property);
    }

    public List<PdosComponent> getComponents() { return List.copyOf(this.components); }

    @Override
    public void parse(File file) throws IOException {
        // Full directory scan is managed by parseDirectory
        if (file != null && file.isFile() && file.getName().contains(".pdos_atm#")) {
            PdosComponent comp = parseSingleFile(file);
            if (comp != null) {
                this.components.add(comp);
            }
        }
    }

    /**
     * Scans a calculation directory to automatically locate and parse all .pdos files.
     */
    public void parseDirectory(File dir, String prefix) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }

        this.components.clear();
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File f : files) {
            if (f.isFile() && f.getName().startsWith(prefix != null ? prefix : "")) {
                if (f.getName().contains(".pdos_atm#")) {
                    try {
                        PdosComponent comp = parseSingleFile(f);
                        if (comp != null) {
                            this.components.add(comp);
                        }
                    } catch (IOException e) {
                        // Log and skip malformed file
                    }
                }
            }
        }
    }

    /**
     * Parses a single PDOS file, extracting metadata from the filename:
     * e.g. <prefix>.pdos_atm#1(Si)_wfc#1(s)
     */
    public static PdosComponent parseSingleFile(File file) throws IOException {
        if (file == null || !file.exists()) {
            return null;
        }

        String name = file.getName();
        // Regex to parse atom index, element, wavefunction index, orbital L label, and optional spin channel
        Pattern pName = Pattern.compile("atm#(\\d+)\\((\\w+)\\)_wfc#(\\d+)\\((\\w+)\\)(?:_spin(\\d+))?", Pattern.CASE_INSENSITIVE);
        Matcher mName = pName.matcher(name);

        int atomIdx = 1;
        String element = "";
        int wfcIdx = 1;
        String orbitalL = "s";
        int spinChannel = 1;

        if (mName.find()) {
            atomIdx = Integer.parseInt(mName.group(1));
            element = mName.group(2);
            wfcIdx = Integer.parseInt(mName.group(3));
            orbitalL = mName.group(4);
            if (mName.group(5) != null) {
                spinChannel = Integer.parseInt(mName.group(5));
            }
        } else {
            // Unparseable filename format
            return null;
        }

        List<Double> energiesList = new ArrayList<>();
        List<Double> pdosList = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trim = line.trim();
                if (trim.isEmpty() || trim.startsWith("#")) {
                    continue; // Skip comments/headers
                }
                String[] tokens = trim.split("\\s+");
                if (tokens.length >= 2) {
                    try {
                        double energy = Double.parseDouble(tokens[0]);
                        double pdosVal = Double.parseDouble(tokens[1]); // ldos or pdos is in column 2
                        energiesList.add(energy);
                        pdosList.add(pdosVal);
                    } catch (NumberFormatException e) {
                        // Skip malformed data row
                    }
                }
            }
        }

        double[] energies = new double[energiesList.size()];
        double[] pdos = new double[pdosList.size()];
        for (int i = 0; i < energies.length; i++) {
            energies[i] = energiesList.get(i);
            pdos[i] = pdosList.get(i);
        }

        return new PdosComponent(file, atomIdx, element, wfcIdx, orbitalL, energies, pdos, spinChannel);
    }
}
