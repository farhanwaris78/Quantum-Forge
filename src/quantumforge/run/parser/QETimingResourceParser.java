/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.run.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import quantumforge.project.property.ProjectProperty;

/**
 * Parses stage timers, CPU/Wall time, FFT grid parameters, memory estimates,
 * and MPI rank layout from standard Quantum ESPRESSO output files (Roadmap #43).
 */
public final class QETimingResourceParser extends LogParser {

    private double cpuTimeSeconds = 0.0;
    private double wallTimeSeconds = 0.0;
    private double estimatedMaxMemoryMb = 0.0;
    private int numProcessors = 1;
    private int fftX = 0, fftY = 0, fftZ = 0;

    public QETimingResourceParser(ProjectProperty property) {
        super(property);
    }

    public double getCpuTimeSeconds() { return this.cpuTimeSeconds; }
    public double getWallTimeSeconds() { return this.wallTimeSeconds; }
    public double getEstimatedMaxMemoryMb() { return this.estimatedMaxMemoryMb; }
    public int getNumProcessors() { return this.numProcessors; }
    public String getFftGrid() { return fftX + " x " + fftY + " x " + fftZ; }

    @Override
    public void parse(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                parseLine(line);
            }
        }
    }

    private void parseLine(String line) {
        if (line == null || line.isBlank()) {
            return;
        }

        String trim = line.trim();

        // 1. Parse MPI processors: "Parallel version (MPI), running on     4 processors"
        if (trim.contains("running on") && trim.contains("processors")) {
            Matcher m = Pattern.compile("running\\s+on\\s+(\\d+)\\s+processors", Pattern.CASE_INSENSITIVE).matcher(trim);
            if (m.find()) {
                this.numProcessors = Integer.parseInt(m.group(1));
            }
        }

        // 2. Parse estimated memory: "Estimated max_memory     =   15.42 MB"
        if (trim.contains("Estimated max_memory") || trim.contains("estimated max_memory")) {
            Matcher m = Pattern.compile("Estimated\\s+max_memory\\s*=\\s*([-\\d.eE+]+)\\s*([kKmMgG]?[bB])", Pattern.CASE_INSENSITIVE).matcher(trim);
            if (m.find()) {
                double val = Double.parseDouble(m.group(1));
                String unit = m.group(2).toUpperCase(Locale.ROOT);
                if (unit.startsWith("K")) {
                    this.estimatedMaxMemoryMb = val / 1024.0;
                } else if (unit.startsWith("G")) {
                    this.estimatedMaxMemoryMb = val * 1024.0;
                } else {
                    this.estimatedMaxMemoryMb = val;
                }
            }
        }

        // 3. Parse FFT Grid dimensions: "FFT dimensions:  (  32,  32,  32)"
        if (trim.contains("FFT dimensions:") || trim.contains("fft dimensions:")) {
            Matcher m = Pattern.compile("FFT\\s+dimensions:\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)", Pattern.CASE_INSENSITIVE).matcher(trim);
            if (m.find()) {
                this.fftX = Integer.parseInt(m.group(1));
                this.fftY = Integer.parseInt(m.group(2));
                this.fftZ = Integer.parseInt(m.group(3));
            }
        }

        // 4. Parse PWSCF timers: "PWSCF        :      1m 4.31s CPU      1m 5.21s WALL"
        // or "init_run     :      0.15s CPU      0.16s WALL"
        if (trim.contains("CPU") && trim.contains("WALL")) {
            Matcher m = Pattern.compile("(\\w+)?\\s*:\\s*(?:(\\d+)m\\s*)?([\\d.]+)s\\s+CPU\\s*(?:(\\d+)m\\s*)?([\\d.]+)s\\s+WALL", Pattern.CASE_INSENSITIVE).matcher(trim);
            if (m.find()) {
                String stage = m.group(1);
                // We focus on the total run summary name like "PWSCF", "BANDS", "PHONON", "NEB"
                if (stage == null || stage.equalsIgnoreCase("PWSCF") || stage.equalsIgnoreCase("BANDS") || stage.equalsIgnoreCase("PHONON") || stage.equalsIgnoreCase("NEB")) {
                    double cpuMin = m.group(2) != null ? Double.parseDouble(m.group(2)) : 0.0;
                    double cpuSec = Double.parseDouble(m.group(3));
                    this.cpuTimeSeconds = cpuMin * 60.0 + cpuSec;

                    double wallMin = m.group(4) != null ? Double.parseDouble(m.group(4)) : 0.0;
                    double wallSec = Double.parseDouble(m.group(5));
                    this.wallTimeSeconds = wallMin * 60.0 + wallSec;
                }
            }
        }
    }
}
