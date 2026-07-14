/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package burai.export;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import burai.atoms.model.Atom;
import burai.atoms.model.Cell;
import burai.com.math.Lattice;
import burai.com.math.Matrix3D;

/**
 * Atomic configuration exporter.
 * 
 * Exports to formats:
 * - CIF (Crystallographic Information File)
 * - XYZ (Extended XYZ format)
 * - POSCAR (VASP input format)
 * - Quantum ESPRESSO input format
 * 
 * This provides interoperability with other software packages,
 * matching NanoLabo's export functionality.
 */
public class AtomicExporter {

    public static final int FORMAT_CIF = 0;
    public static final int FORMAT_XYZ = 1;
    public static final int FORMAT_POSCAR = 2;
    public static final int FORMAT_QE_INPUT = 3;

    /**
     * Export atomic structure to a file
     */
    public static boolean exportToFile(Cell cell, String filePath, int format) {
        if (cell == null || filePath == null) {
            return false;
        }

        String content;
        switch (format) {
            case FORMAT_CIF:
                content = toCIF(cell);
                break;
            case FORMAT_XYZ:
                content = toXYZ(cell);
                break;
            case FORMAT_POSCAR:
                content = toPOSCAR(cell);
                break;
            case FORMAT_QE_INPUT:
                content = toQEInput(cell);
                break;
            default:
                return false;
        }

        if (content == null || content.isEmpty()) {
            return false;
        }

        return writeFile(filePath, content);
    }

    /**
     * Export as CIF format
     */
    public static String toCIF(Cell cell) {
        if (cell == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("data_exported_from_QuantumForge\n");
        sb.append("_symmetry_space_group_name_H-M 'P1'\n");
        sb.append("_symmetry_int_tables_number 1\n\n");

        double a = Lattice.getA(cell.copyLattice());
        double b = Lattice.getB(cell.copyLattice());
        double c = Lattice.getC(cell.copyLattice());
        double alpha = Lattice.getAlpha(cell.copyLattice());
        double beta = Lattice.getBeta(cell.copyLattice());
        double gamma = Lattice.getGamma(cell.copyLattice());

        sb.append("_cell_length_a ").append(String.format("%.6f", a)).append("\n");
        sb.append("_cell_length_b ").append(String.format("%.6f", b)).append("\n");
        sb.append("_cell_length_c ").append(String.format("%.6f", c)).append("\n");
        sb.append("_cell_angle_alpha ").append(String.format("%.4f", alpha)).append("\n");
        sb.append("_cell_angle_beta ").append(String.format("%.4f", beta)).append("\n");
        sb.append("_cell_angle_gamma ").append(String.format("%.4f", gamma)).append("\n\n");

        int nAtoms = cell.numAtoms(true);
        sb.append("_atom_site_count ").append(nAtoms).append("\n");
        sb.append("_atom_site_type_symbol\n");
        sb.append("_atom_site_fract_x\n");
        sb.append("_atom_site_fract_y\n");
        sb.append("_atom_site_fract_z\n");

        Atom[] atoms = cell.listAtoms(true);
        double[][] recLattice = Matrix3D.inverse(cell.copyLattice());
        if (atoms != null) {
            for (Atom atom : atoms) {
                if (atom == null) continue;
                double[] frac = Matrix3D.mult(
                    new double[]{atom.getX(), atom.getY(), atom.getZ()}, recLattice);
                sb.append(" ").append(atom.getName())
                  .append(" ").append(String.format("%.6f", frac[0]))
                  .append(" ").append(String.format("%.6f", frac[1]))
                  .append(" ").append(String.format("%.6f", frac[2]))
                  .append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Export as XYZ format
     */
    public static String toXYZ(Cell cell) {
        if (cell == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        Atom[] atoms = cell.listAtoms(true);
        if (atoms == null) {
            return null;
        }

        sb.append(atoms.length).append("\n");
        sb.append("QuantumForge export: lattice=\"");
        double[][] lattice = cell.copyLattice();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                sb.append(String.format("%.6f", lattice[i][j]));
                if (i < 2 || j < 2) sb.append(" ");
            }
        }
        sb.append("\"\n");

        for (Atom atom : atoms) {
            if (atom == null) continue;
            sb.append(atom.getElementName())
              .append(" ").append(String.format("%.6f", atom.getX()))
              .append(" ").append(String.format("%.6f", atom.getY()))
              .append(" ").append(String.format("%.6f", atom.getZ()))
              .append("\n");
        }

        return sb.toString();
    }

    /**
     * Export as VASP POSCAR format
     */
    public static String toPOSCAR(Cell cell) {
        if (cell == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("QuantumForge export\n");
        sb.append("1.0\n");

        double[][] lattice = cell.copyLattice();
        for (int i = 0; i < 3; i++) {
            sb.append("  ").append(String.format("%.10f", lattice[i][0]))
              .append(" ").append(String.format("%.10f", lattice[i][1]))
              .append(" ").append(String.format("%.10f", lattice[i][2]))
              .append("\n");
        }

        Atom[] atoms = cell.listAtoms(true);
        if (atoms == null) {
            return null;
        }

        // Count elements
        java.util.Map<String, Integer> elementCount = new java.util.LinkedHashMap<>();
        for (Atom atom : atoms) {
            if (atom == null) continue;
            String elem = atom.getElementName();
            elementCount.put(elem, elementCount.getOrDefault(elem, 0) + 1);
        }

        for (String elem : elementCount.keySet()) {
            sb.append(" ").append(elem);
        }
        sb.append("\n");

        for (int count : elementCount.values()) {
            sb.append(" ").append(count);
        }
        sb.append("\n");

        sb.append("Direct\n");

        double[][] recLattice = Matrix3D.inverse(lattice);
        for (Atom atom : atoms) {
            if (atom == null) continue;
            double[] frac = Matrix3D.mult(
                new double[]{atom.getX(), atom.getY(), atom.getZ()}, recLattice);
            sb.append("  ").append(String.format("%.10f", frac[0]))
              .append(" ").append(String.format("%.10f", frac[1]))
              .append(" ").append(String.format("%.10f", frac[2]))
              .append("\n");
        }

        return sb.toString();
    }

    /**
     * Export as Quantum ESPRESSO input
     */
    public static String toQEInput(Cell cell) {
        if (cell == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("&CONTROL\n");
        sb.append("  calculation = 'scf'\n");
        sb.append("  pseudo_dir = './'\n");
        sb.append("/\n\n");

        sb.append("&SYSTEM\n");
        double[][] lattice = cell.copyLattice();
        double a = Lattice.getA(lattice);
        sb.append("  ibrav = 0\n");
        sb.append("  a = ").append(String.format("%.6f", a)).append("\n");
        sb.append("  nat = ").append(cell.numAtoms(true)).append("\n");
        sb.append("  ntyp = ").append(countSpecies(cell)).append("\n");
        sb.append("  ecutwfc = 50.0\n");
        sb.append("  ecutrho = 400.0\n");
        sb.append("/\n\n");

        sb.append("&ELECTRONS\n");
        sb.append("  conv_thr = 1.0e-6\n");
        sb.append("/\n\n");

        sb.append("CELL_PARAMETERS {angstrom}\n");
        for (int i = 0; i < 3; i++) {
            sb.append("  ").append(String.format("%.10f", lattice[i][0]))
              .append(" ").append(String.format("%.10f", lattice[i][1]))
              .append(" ").append(String.format("%.10f", lattice[i][2]))
              .append("\n");
        }
        sb.append("\n");

        sb.append("ATOMIC_SPECIES\n");
        Atom[] atoms = cell.listAtoms(true);
        java.util.Map<String, Integer> counted = new java.util.LinkedHashMap<>();
        for (Atom atom : atoms) {
            if (atom == null) continue;
            String elem = atom.getElementName();
            if (!counted.containsKey(elem)) {
                counted.put(elem, 1);
                sb.append("  ").append(elem).append("  1.0  ").append(elem).append(".upf\n");
            }
        }
        sb.append("\n");

        sb.append("ATOMIC_POSITIONS {angstrom}\n");
        for (Atom atom : atoms) {
            if (atom == null) continue;
            sb.append("  ").append(atom.getElementName())
              .append(" ").append(String.format("%.10f", atom.getX()))
              .append(" ").append(String.format("%.10f", atom.getY()))
              .append(" ").append(String.format("%.10f", atom.getZ()))
              .append("\n");
        }
        sb.append("\n");

        sb.append("K_POINTS {gamma}\n");

        return sb.toString();
    }

    private static int countSpecies(Cell cell) {
        Atom[] atoms = cell.listAtoms(true);
        java.util.Set<String> species = new java.util.HashSet<>();
        if (atoms != null) {
            for (Atom atom : atoms) {
                if (atom != null) species.add(atom.getElementName());
            }
        }
        return species.size();
    }

    private static boolean writeFile(String filePath, String content) {
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(new BufferedWriter(new FileWriter(filePath)));
            writer.print(content);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (writer != null) writer.close();
        }
    }
}
