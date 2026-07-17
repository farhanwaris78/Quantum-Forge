/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.atoms.reader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.atoms.model.exception.ZeroVolumCellException;
import quantumforge.com.math.Matrix3D;

/**
 * Parses standard Protein Data Bank (PDB) structural logs, extracting unit cell dimensions 
 * from CRYST1 entries and 3D site positions from ATOM/HETATM blocks (Roadmap #78).
 */
public final class QEPdbReader extends AtomsReader {

    public QEPdbReader(String filePath) throws FileNotFoundException {
        super(filePath);
    }

    public QEPdbReader(File file) throws FileNotFoundException {
        super(file);
    }

    @Override
    public Cell readCell() throws IOException {
        if (this.reader == null) {
            return null;
        }

        double[][] lattice = Matrix3D.unit(15.0); // Default 15A cubic vacuum box for molecules
        List<Atom> parsedAtoms = new ArrayList<>();

        try {
            String line;
            while ((line = this.reader.readLine()) != null) {
                String trim = line.trim();

                // 1. Parse CRYST1 unit cell if present:
                // CRYST1   10.000   10.000   10.000  90.00  90.00  90.00 P 1           1
                if (trim.startsWith("CRYST1")) {
                    double a = Double.parseDouble(line.substring(6, 15).trim());
                    double b = Double.parseDouble(line.substring(15, 24).trim());
                    double c = Double.parseDouble(line.substring(24, 33).trim());
                    // Create an orthogonal cubic/orthorhombic cell based on standard lattice constraints
                    lattice = Matrix3D.zero();
                    lattice[0][0] = a;
                    lattice[1][1] = b;
                    lattice[2][2] = c;
                }

                // 2. Parse coordinates from ATOM/HETATM lines:
                // Columns: atom name (12-16), x (30-38), y (38-46), z (46-54), element symbol (76-78)
                if (trim.startsWith("ATOM") || trim.startsWith("HETATM")) {
                    if (line.length() < 54) {
                        continue; // skip truncated rows
                    }

                    double x = Double.parseDouble(line.substring(30, 38).trim());
                    double y = Double.parseDouble(line.substring(38, 46).trim());
                    double z = Double.parseDouble(line.substring(46, 54).trim());

                    String element = "";
                    if (line.length() >= 78) {
                        element = line.substring(76, 78).trim();
                    }
                    if (element.isEmpty()) {
                        // Fallback: extract from atom name column (12-14)
                        element = line.substring(12, 14).trim();
                        // Strip numbers and formatting
                        element = element.replaceAll("[\\d\\s]", "");
                    }

                    // Format element symbol nicely (capital head, lowercase tail)
                    if (!element.isEmpty()) {
                        String clean = element.substring(0, 1).toUpperCase(Locale.ROOT);
                        if (element.length() > 1) {
                            clean += element.substring(1).toLowerCase(Locale.ROOT);
                        }
                        parsedAtoms.add(new Atom(clean, x, y, z));
                    }
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse standard PDB structure: " + e.getMessage(), e);
        }

        Cell cell = null;
        try {
            cell = new Cell(lattice);
            for (Atom atom : parsedAtoms) {
                cell.addAtom(atom);
            }
        } catch (ZeroVolumCellException e) {
            throw new IOException(e);
        }

        return cell;
    }
}
