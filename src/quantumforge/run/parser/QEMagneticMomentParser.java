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
 * Parses collinear and noncollinear magnetic moments, total/absolute magnetization
 * vectors, and atomic 3D local magnetic moments from pw.x logs, enabling 
 * vector-arrow visualization of magnetic configurations (Roadmap #59).
 */
public final class QEMagneticMomentParser extends LogParser {

    public static final class AtomicMoment {
        private final int atomIndex;
        private final double mx;
        private final double my;
        private final double mz;

        public AtomicMoment(int atomIndex, double mx, double my, double mz) {
            this.atomIndex = atomIndex;
            this.mx = mx;
            this.my = my;
            this.mz = mz;
        }

        public int getAtomIndex() { return this.atomIndex; }
        public double getMx() { return this.mx; }
        public double getMy() { return this.my; }
        public double getMz() { return this.mz; }

        public double getMagnitude() {
            return Math.sqrt(mx * mx + my * my + mz * mz);
        }
    }

    private double totalMagnetizationBohr = 0.0;
    private double absoluteMagnetizationBohr = 0.0;
    private final double[] totalMagnetizationVector = new double[3];
    private final List<AtomicMoment> atomicMoments = new ArrayList<>();
    private boolean noncollinear = false;

    public QEMagneticMomentParser(ProjectProperty property) {
        super(property);
    }

    public double getTotalMagnetizationBohr() { return this.totalMagnetizationBohr; }
    public double getAbsoluteMagnetizationBohr() { return this.absoluteMagnetizationBohr; }
    public double[] getTotalMagnetizationVector() { return this.totalMagnetizationVector.clone(); }
    public List<AtomicMoment> getAtomicMoments() { return List.copyOf(this.atomicMoments); }
    public boolean isNoncollinear() { return this.noncollinear; }

    @Override
    public void parse(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        this.totalMagnetizationBohr = 0.0;
        this.absoluteMagnetizationBohr = 0.0;
        this.totalMagnetizationVector[0] = 0.0;
        this.totalMagnetizationVector[1] = 0.0;
        this.totalMagnetizationVector[2] = 0.0;
        this.atomicMoments.clear();
        this.noncollinear = false;

        Pattern pTotCol = Pattern.compile("total\\s+magnetization\\s*=\\s*([-\\d.]+)\\s*Bohr\\s+mag/cell", Pattern.CASE_INSENSITIVE);
        Pattern pAbsCol = Pattern.compile("absolute\\s+magnetization\\s*=\\s*([-\\d.]+)\\s*Bohr\\s+mag/cell", Pattern.CASE_INSENSITIVE);
        
        // Noncollinear vector parser: "total magnetization          = (   0.0000,   0.0000,   1.2412) Bohr mag/cell"
        Pattern pTotNonCol = Pattern.compile("total\\s+magnetization\\s*=\\s*\\(\\s*([-\\d.]+)\\s*,\\s*([-\\d.]+)\\s*,\\s*([-\\d.]+)\\s*\\)\\s*Bohr\\s+mag/cell", Pattern.CASE_INSENSITIVE);
        
        // Atomic moment parser: "atom    1 local magnetic moment:     0.0500    0.0000    1.2400"
        Pattern pAtomMom = Pattern.compile("atom\\s+(\\d+)\\s+local\\s+magnetic\\s+moment:\\s+([-\\d.]+)\\s+([-\\d.]+)\\s+([-\\d.]+)", Pattern.CASE_INSENSITIVE);
        // Collinear atomic moment: "atom    1 local magnetic moment:     1.2400"
        Pattern pAtomCol = Pattern.compile("atom\\s+(\\d+)\\s+local\\s+magnetic\\s+moment:\\s+([-\\d.]+)$", Pattern.CASE_INSENSITIVE);

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trim = line.trim();

                Matcher mTotCol = pTotCol.matcher(trim);
                if (mTotCol.find()) {
                    this.totalMagnetizationBohr = Double.parseDouble(mTotCol.group(1));
                    this.totalMagnetizationVector[2] = this.totalMagnetizationBohr; // aligned to z by default
                }

                Matcher mAbsCol = pAbsCol.matcher(trim);
                if (mAbsCol.find()) {
                    this.absoluteMagnetizationBohr = Double.parseDouble(mAbsCol.group(1));
                }

                Matcher mTotNonCol = pTotNonCol.matcher(trim);
                if (mTotNonCol.find()) {
                    this.totalMagnetizationVector[0] = Double.parseDouble(mTotNonCol.group(1));
                    this.totalMagnetizationVector[1] = Double.parseDouble(mTotNonCol.group(2));
                    this.totalMagnetizationVector[2] = Double.parseDouble(mTotNonCol.group(3));
                    this.totalMagnetizationBohr = Math.sqrt(
                        totalMagnetizationVector[0]*totalMagnetizationVector[0] + 
                        totalMagnetizationVector[1]*totalMagnetizationVector[1] + 
                        totalMagnetizationVector[2]*totalMagnetizationVector[2]
                    );
                    this.noncollinear = true;
                }

                Matcher mAtomMom = pAtomMom.matcher(trim);
                if (mAtomMom.find()) {
                    int idx = Integer.parseInt(mAtomMom.group(1));
                    double mx = Double.parseDouble(mAtomMom.group(2));
                    double my = Double.parseDouble(mAtomMom.group(3));
                    double mz = Double.parseDouble(mAtomMom.group(4));
                    this.atomicMoments.add(new AtomicMoment(idx, mx, my, mz));
                    this.noncollinear = true;
                }

                Matcher mAtomCol = pAtomCol.matcher(trim);
                if (mAtomCol.find()) {
                    int idx = Integer.parseInt(mAtomCol.group(1));
                    double mz = Double.parseDouble(mAtomCol.group(2));
                    this.atomicMoments.add(new AtomicMoment(idx, 0.0, 0.0, mz));
                }
            }
        }
    }
}
