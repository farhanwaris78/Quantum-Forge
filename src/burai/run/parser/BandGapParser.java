/*
 * Copyright (C) 2025 QuantumForge Team
 */
package burai.run.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Parser for band gap from QE output files.
 * 
 * Extracts the band gap value from SCF/NSCF output,
 * Fermi energy, and band structure information.
 */
public class BandGapParser {

    private String filePath;
    private double bandGap;
    private double fermiEnergy;
    private boolean isInsulator;
    private boolean isDirect;

    public BandGapParser(String filePath) {
        this.filePath = filePath;
        this.bandGap = -1.0;
        this.fermiEnergy = 0.0;
        this.isInsulator = false;
        this.isDirect = false;
    }

    /**
     * Parse QE output file for band gap information
     */
    public boolean parse() {
        if (this.filePath == null) return false;

        File file = new File(this.filePath);
        if (!file.exists()) return false;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Fermi energy
                if (line.contains("the Fermi energy is")) {
                    String[] parts = line.split("\\s+");
                    for (int i = 0; i < parts.length; i++) {
                        if (parts[i].equals("is") && i + 1 < parts.length) {
                            try { this.fermiEnergy = Double.parseDouble(parts[i + 1]); } catch (Exception e) {}
                        }
                    }
                }

                // Band gap from scf output
                if (line.contains("highest occupied")) {
                    this.isInsulator = true;
                }

                // Band gap from bands output
                if (line.contains("band gap") || line.contains("Band gap")) {
                    String[] parts = line.split("\\s+");
                    for (int i = 0; i < parts.length; i++) {
                        if (parts[i].equals("gap") && i + 2 < parts.length) {
                            try { this.bandGap = Double.parseDouble(parts[i + 2]); } catch (Exception e) {}
                        }
                        if (parts[i].equals("Direct") || parts[i].equals("Indirect")) {
                            this.isDirect = parts[i].equals("Direct");
                        }
                    }
                }
            }
        } catch (IOException e) {
            return false;
        }

        return this.bandGap >= 0;
    }

    public double getBandGap() { return this.bandGap; }
    public double getFermiEnergy() { return this.fermiEnergy; }
    public boolean isInsulator() { return this.isInsulator; }
    public boolean isDirect() { return this.isDirect; }
}
