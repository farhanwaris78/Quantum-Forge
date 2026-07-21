/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run.parser;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import quantumforge.operation.OperationResult;

/**
 * Bounded reader for Gaussian CUBE volumetric grids.
 *
 * <p>It accepts standard orthogonal or skew voxel vectors and converts the
 * resulting cell from bohr to Angstrom unless all voxel counts are negative,
 * the convention used by many writers to denote Angstrom axes. It refuses
 * oversized/malformed grids instead of exhausting the GUI heap.</p>
 */
public final class CubeGridReader {
    public static final long DEFAULT_MAX_VOXELS = 16L * 1024L * 1024L;
    private static final double BOHR_TO_ANGSTROM = 0.529177210903;

    private CubeGridReader() { }

    public static OperationResult<QEGridDensityDifference.Grid3D> read(Path file) {
        return read(file, DEFAULT_MAX_VOXELS);
    }

    public static OperationResult<QEGridDensityDifference.Grid3D> read(Path file, long maxVoxels) {
        if (file == null || !Files.isRegularFile(file)) {
            return OperationResult.failed("CUBE_MISSING", "CUBE file is missing: " + file, null);
        }
        if (maxVoxels <= 0L) {
            return OperationResult.failed("CUBE_LIMIT", "Maximum voxel count must be positive.", null);
        }
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            reader.readLine();
            reader.readLine();
            String originLine = requireLine(reader, "atom/origin");
            String[] origin = tokens(originLine, 4, "atom/origin");
            int atoms = Integer.parseInt(origin[0]);
            int nat = Math.abs(atoms);
            int[] n = new int[3];
            double[][] voxel = new double[3][3];
            boolean angstromAxes = true;
            for (int axis = 0; axis < 3; axis++) {
                String[] values = tokens(requireLine(reader, "grid axis"), 4, "grid axis");
                int rawCount = Integer.parseInt(values[0]);
                if (rawCount == 0) throw new IOException("CUBE grid axis has zero points");
                n[axis] = Math.abs(rawCount);
                angstromAxes &= rawCount < 0;
                for (int component = 0; component < 3; component++) {
                    voxel[axis][component] = Double.parseDouble(values[component + 1]);
                }
            }
            long total = (long) n[0] * (long) n[1] * (long) n[2];
            if (total <= 0L || total > maxVoxels || total > Integer.MAX_VALUE) {
                return OperationResult.failed("CUBE_TOO_LARGE", "CUBE grid has " + total
                        + " voxels; limit is " + maxVoxels + ".", null);
            }
            for (int atom = 0; atom < nat; atom++) requireLine(reader, "atom record");
            List<Double> values = new ArrayList<>((int) total);
            String line;
            while ((line = reader.readLine()) != null && values.size() < total) {
                for (String token : line.trim().split("\\s+")) {
                    if (!token.isEmpty()) values.add(Double.parseDouble(token.replace('D', 'E').replace('d', 'e')));
                    if (values.size() == total) break;
                }
            }
            if (values.size() != total) {
                return OperationResult.failed("CUBE_TRUNCATED", "CUBE grid ended after " + values.size()
                        + " values; expected " + total + ".", null);
            }
            double scale = angstromAxes ? 1.0 : BOHR_TO_ANGSTROM;
            double[][] lattice = new double[3][3];
            for (int axis = 0; axis < 3; axis++) {
                for (int component = 0; component < 3; component++) {
                    lattice[axis][component] = voxel[axis][component] * n[axis] * scale;
                }
            }
            double[][][] grid = new double[n[0]][n[1]][n[2]];
            int index = 0;
            for (int i = 0; i < n[0]; i++) for (int j = 0; j < n[1]; j++) for (int k = 0; k < n[2]; k++)
                grid[i][j][k] = values.get(index++);
            return OperationResult.success("CUBE_OK", "Read " + total + " CUBE voxels ("
                    + (angstromAxes ? "Angstrom" : "bohr converted to Angstrom") + ").",
                    new QEGridDensityDifference.Grid3D(lattice, n[0], n[1], n[2], grid));
        } catch (IOException | IllegalArgumentException ex) {
            return OperationResult.failed("CUBE_PARSE", "Could not parse CUBE file: " + ex.getMessage(), ex);
        }
    }

    private static String requireLine(BufferedReader reader, String section) throws IOException {
        String line = reader.readLine();
        if (line == null) throw new IOException("Unexpected end of CUBE file in " + section);
        return line;
    }

    private static String[] tokens(String line, int count, String section) throws IOException {
        String[] tokens = line.trim().split("\\s+");
        if (tokens.length < count) throw new IOException("Malformed CUBE " + section + " line");
        return tokens;
    }
}
