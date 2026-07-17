/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.matapi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import quantumforge.atoms.model.Cell;
import quantumforge.atoms.model.exception.ZeroVolumCellException;
import quantumforge.com.math.Matrix3D;

/**
 * Standard MDL SDF (Structure Data File) parser that extracts 3D coordinates,
 * atom symbols, centers molecules in vacuum boxes, and builds atomic Cells (Roadmap #118).
 */
public final class QESdfParser {

    public static final class SdfAtom {
        private final String element;
        private final double x, y, z;

        public SdfAtom(String element, double x, double y, double z) {
            this.element = element == null ? "" : element.trim();
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public String getElement() { return this.element; }
        public double getX() { return this.x; }
        public double getY() { return this.y; }
        public double getZ() { return this.z; }
    }

    private QESdfParser() {
        // Utility
    }

    /**
     * Parses standard V2000 MDL SDF strings, centering coordinates inside a 
     * 15x15x15 Angstrom cubic vacuum box to isolate the molecule periodic images.
     */
    public static Cell parseSDF(String sdfContent) throws IOException, ZeroVolumCellException {
        if (sdfContent == null || sdfContent.isBlank()) {
            throw new IllegalArgumentException("SDF content is empty");
        }

        List<SdfAtom> parsedAtoms = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new StringReader(sdfContent))) {
            // Line 1-3: Headers
            reader.readLine();
            reader.readLine();
            reader.readLine();

            // Line 4: Counts line (V2000 format, e.g., "  3  2  0  0  0  0  0  0  0  0999 V2000")
            String countLine = reader.readLine();
            if (countLine == null || countLine.length() < 3) {
                throw new IOException("Malformed SDF header: count line is missing.");
            }

            int numAtoms = Integer.parseInt(countLine.substring(0, 3).trim());

            // Next N lines: Atomic coordinates blocks
            for (int i = 0; i < numAtoms; i++) {
                String line = reader.readLine();
                if (line == null) {
                    throw new IOException("Truncated coordinates block in SDF.");
                }

                // V2000 format has fixed width columns:
                // x (10 chars), y (10 chars), z (10 chars), space (1 char), element (3 chars)
                double x = Double.parseDouble(line.substring(0, 10).trim());
                double y = Double.parseDouble(line.substring(10, 20).trim());
                double z = Double.parseDouble(line.substring(20, 30).trim());
                String element = line.substring(31, 34).trim();

                parsedAtoms.add(new SdfAtom(element, x, y, z));
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse standard V2000 SDF string: " + e.getMessage(), e);
        }

        if (parsedAtoms.isEmpty()) {
            throw new IOException("No atoms parsed from SDF.");
        }

        // Build a 15x15x15 Angstrom cubic cell to provide a safe vacuum buffer for DFT
        double boxSize = 15.0;
        double[][] lattice = Matrix3D.unit(boxSize);
        Cell cell = new Cell(lattice);

        // Calculate molecule center of mass
        double cx = 0.0, cy = 0.0, cz = 0.0;
        for (SdfAtom a : parsedAtoms) {
            cx += a.getX();
            cy += a.getY();
            cz += a.getZ();
        }
        cx /= parsedAtoms.size();
        cy /= parsedAtoms.size();
        cz /= parsedAtoms.size();

        // Translate and place atoms centered in the box (x=7.5, y=7.5, z=7.5)
        double centerOffset = boxSize / 2.0;
        for (SdfAtom a : parsedAtoms) {
            double tx = (a.getX() - cx) + centerOffset;
            double ty = (a.getY() - cy) + centerOffset;
            double tz = (a.getZ() - cz) + centerOffset;
            cell.addAtom(a.getElement(), tx, ty, tz);
        }

        return cell;
    }
}
