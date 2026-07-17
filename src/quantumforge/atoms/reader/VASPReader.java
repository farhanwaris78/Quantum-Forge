/*
 * Copyright (C) 2025-2026 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.atoms.reader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.atoms.model.exception.ZeroVolumCellException;
import quantumforge.atoms.model.property.AtomProperty;
import quantumforge.com.math.Matrix3D;

/**
 * Robust VASP POSCAR and CONTCAR reader supporting both VASP 4 (number-only line 6)
 * and VASP 5 (element-labeled line 6) formats, selective dynamics, negative scaling factors,
 * and Cartesian/direct coordinates (Roadmap #76).
 */
public class VASPReader extends AtomsReader {

    public VASPReader(String filePath) throws FileNotFoundException {
        super(filePath);
    }

    public VASPReader(File file) throws FileNotFoundException {
        super(file);
    }

    @Override
    public Cell readCell() throws IOException {
        if (this.reader == null) {
            return null;
        }

        /*
         * read comment
         */
        this.readNetLine();

        /*
         * read scale
         */
        double scale = this.readDouble();

        /*
         * read lattice
         */
        double[][] lattice = new double[3][];
        lattice[0] = this.readDoubles(3);
        lattice[1] = this.readDoubles(3);
        lattice[2] = this.readDoubles(3);

        if (scale < 0.0) {
            double volume = Math.abs(Matrix3D.determinant(lattice));
            scale = Math.pow(Math.abs(scale) / volume, 1.0 / 3.0);
        }

        lattice = Matrix3D.mult(scale, lattice);

        /*
         * read elements or counts (VASP 4 vs VASP 5 check)
         */
        String[] elements = this.readSubLines(-1);

        int numElem = elements.length;
        if (numElem < 1) {
            throw new IOException("elements are not defined in a VASP file.");
        }

        // Check if elements are actually integers (VASP 4 format)
        boolean isVasp4 = true;
        int[] numAtom = new int[numElem];
        for (int i = 0; i < numElem; i++) {
            try {
                numAtom[i] = Integer.parseInt(elements[i]);
            } catch (NumberFormatException e) {
                isVasp4 = false;
                break;
            }
        }

        if (isVasp4) {
            // VASP 4 format: auto-generate placeholder element symbols (e.g. X1, X2)
            elements = new String[numElem];
            for (int i = 0; i < numElem; i++) {
                elements[i] = "X" + (i + 1);
            }
        } else {
            // VASP 5 format: read counts from the next line
            numAtom = this.readIntegers(numElem);
        }

        int totAtom = 0;
        for (int i = 0; i < numElem; i++) {
            int natom = numAtom[i];
            if (natom < 0) {
                throw new IOException("a number of atoms is negative in a VASP file.");
            }
            totAtom += natom;
        }

        if (totAtom < 1) {
            throw new IOException("there are no atoms in a VASP file.");
        }

        /*
         * read "Selective dynamics"
         */
        String tmpLine = this.readNetLine();
        if (tmpLine.isEmpty()) {
            throw new IOException("cannot read 'Selective dynamics' in a VASP file.");
        }

        boolean withMobile = false;
        char selecDyn = tmpLine.charAt(0);
        if (selecDyn == 'S' || selecDyn == 's') {
            withMobile = true;
            tmpLine = this.readNetLine();
        }

        /*
         * read atomic coordinates
         */
        boolean withCartesian = false;
        char cart = tmpLine.charAt(0);
        if (cart == 'C' || cart == 'c' || cart == 'K' || cart == 'k') {
            withCartesian = true;
        }

        Cell cell = null;
        try {
            cell = new Cell(lattice);
        } catch (ZeroVolumCellException e) {
            throw new IOException(e);
        }

        cell.stopResolving();

        for (int i = 0; i < numElem; i++) {
            String elem = elements[i];

            if (withMobile) {
                double[] coord = new double[3];
                boolean[] mobile = new boolean[3];

                if (withCartesian) {
                    for (int j = 0; j < numAtom[i]; j++) {
                        this.readCoordinate(coord, mobile);
                        double x = scale * coord[0];
                        double y = scale * coord[1];
                        double z = scale * coord[2];
                        Atom atom = new Atom(elem, x, y, z);
                        atom.setProperty(AtomProperty.FIXED_X, !mobile[0]);
                        atom.setProperty(AtomProperty.FIXED_Y, !mobile[1]);
                        atom.setProperty(AtomProperty.FIXED_Z, !mobile[2]);
                        cell.addAtom(atom);
                    }

                } else {
                    for (int j = 0; j < numAtom[i]; j++) {
                        this.readCoordinate(coord, mobile);
                        coord = cell.convertToCartesianPosition(coord[0], coord[1], coord[2]);
                        Atom atom = new Atom(elem, coord[0], coord[1], coord[2]);
                        atom.setProperty(AtomProperty.FIXED_X, !mobile[0]);
                        atom.setProperty(AtomProperty.FIXED_Y, !mobile[1]);
                        atom.setProperty(AtomProperty.FIXED_Z, !mobile[2]);
                        cell.addAtom(atom);
                    }
                }

            } else {
                if (withCartesian) {
                    for (int j = 0; j < numAtom[i]; j++) {
                        double[] coord = this.readDoubles(3);
                        double x = scale * coord[0];
                        double y = scale * coord[1];
                        double z = scale * coord[2];
                        cell.addAtom(new Atom(elem, x, y, z));
                    }

                } else {
                    for (int j = 0; j < numAtom[i]; j++) {
                        double[] coord = this.readDoubles(3);
                        cell.addAtom(elem, coord[0], coord[1], coord[2]);
                    }
                }
            }
        }

        cell.restartResolving();

        return cell;
    }

    private String readNetLine() throws IOException {
        String line = this.reader.readLine();
        if (line == null) {
            throw new IOException("not enough lines in reading a VASP file.");
        }

        return line.trim();
    }

    private String[] readSubLines(int size) throws IOException {
        String line = this.readNetLine();
        String[] subLines = line.split("[\\s,]+");
        if (subLines == null || subLines.length < size) {
            throw new IOException("not enough tokens in reading a VASP file.");
        }

        return subLines;
    }

    private double readDouble() throws IOException {
        String line = this.readNetLine();

        double value = 0.0;

        try {
            value = Double.parseDouble(line);
        } catch (NumberFormatException e) {
            throw new IOException(e);
        }

        return value;
    }

    private double[] readDoubles(int size) throws IOException {
        String[] subLines = this.readSubLines(size);

        int size_ = size > 0 ? size : subLines.length;
        double[] values = new double[size_];

        try {
            for (int i = 0; i < values.length; i++) {
                values[i] = Double.parseDouble(subLines[i]);
            }
        } catch (NumberFormatException e) {
            throw new IOException(e);
        }

        return values;
    }

    private int[] readIntegers(int size) throws IOException {
        String[] subLines = this.readSubLines(size);

        int size_ = size > 0 ? size : subLines.length;
        int[] values = new int[size_];

        try {
            for (int i = 0; i < values.length; i++) {
                values[i] = Integer.parseInt(subLines[i]);
            }
        } catch (NumberFormatException e) {
            throw new IOException(e);
        }

        return values;
    }

    private void readCoordinate(double[] coord, boolean[] mobile) throws IOException {
        String[] subLines = this.readSubLines(3);

        try {
            for (int i = 0; i < 3; i++) {
                coord[i] = Double.parseDouble(subLines[i]);
                mobile[i] = true;
            }

            for (int i = 3; i < subLines.length; i++) {
                if (this.isFalse(subLines[i])) {
                    mobile[i - 3] = false;
                }
            }

        } catch (NumberFormatException e) {
            throw new IOException(e);
        }
    }

    private boolean isFalse(String str) {
        return "F".equalsIgnoreCase(str) || "FALSE".equalsIgnoreCase(str) ||
                ".F.".equalsIgnoreCase(str) || ".FALSE.".equalsIgnoreCase(str);
    }
}
