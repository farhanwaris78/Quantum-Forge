/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.input.validation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import quantumforge.com.math.Matrix3D;
import quantumforge.input.QEInput;
import quantumforge.input.card.QEAtomicPositions;
import quantumforge.input.card.QEAtomicSpecies;
import quantumforge.input.card.QECellParameters;
import quantumforge.input.card.QEKPoints;
import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValue;

/**
 * Conservative deterministic preflight checks for pw.x-style inputs.
 * This supplements rather than replaces the version-specific QE parser.
 */
public final class QEInputValidator {
    private static final String PW_DOC = "https://www.quantum-espresso.org/Doc/INPUT_PW.html";

    public List<ValidationIssue> validate(QEInput input) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (input == null) {
            error(issues, "INPUT_NULL", "No Quantum ESPRESSO input was generated.");
            return issues;
        }

        QENamelist system = input.getNamelist(QEInput.NAMELIST_SYSTEM);
        if (system == null) {
            error(issues, "SYSTEM_MISSING", "The SYSTEM namelist is required for pw.x calculations.");
            return issues;
        }
        validateStructure(input, system, issues);
        validateCutoffs(system, issues);
        validateOccupations(system, issues);
        validateSpin(system, issues);
        validateKPoints(input.getCard(QEKPoints.class), issues);
        return issues;
    }

    public static boolean hasErrors(List<ValidationIssue> issues) {
        if (issues == null) return false;
        for (ValidationIssue issue : issues) {
            if (issue != null && issue.getSeverity() == ValidationSeverity.ERROR) return true;
        }
        return false;
    }

    private void validateStructure(QEInput input, QENamelist system, List<ValidationIssue> issues) {
        QEAtomicPositions positions = input.getCard(QEAtomicPositions.class);
        QEAtomicSpecies species = input.getCard(QEAtomicSpecies.class);
        int positionCount = positions == null ? 0 : positions.numPositions();
        int speciesCount = species == null ? 0 : species.numSpecies();

        if (positionCount == 0) error(issues, "ATOMS_EMPTY", "ATOMIC_POSITIONS contains no atoms.");
        if (speciesCount == 0) error(issues, "SPECIES_EMPTY", "ATOMIC_SPECIES contains no species.");

        Integer nat = integerValue(system, "nat");
        Integer ntyp = integerValue(system, "ntyp");
        if (nat == null) error(issues, "NAT_MISSING", "SYSTEM.nat is missing.");
        else if (nat != positionCount) error(issues, "NAT_MISMATCH",
                "SYSTEM.nat=" + nat + " but ATOMIC_POSITIONS has " + positionCount + " entries.");
        if (ntyp == null) error(issues, "NTYP_MISSING", "SYSTEM.ntyp is missing.");
        else if (ntyp != speciesCount) error(issues, "NTYP_MISMATCH",
                "SYSTEM.ntyp=" + ntyp + " but ATOMIC_SPECIES has " + speciesCount + " entries.");

        if (species != null) {
            Set<String> labels = new HashSet<>();
            for (int i = 0; i < species.numSpecies(); i++) {
                String label = species.getLabel(i);
                if (!labels.add(label)) error(issues, "SPECIES_DUPLICATE", "Duplicate species label: " + label);
                if (species.getMass(i) <= 0.0 || !Double.isFinite(species.getMass(i))) {
                    error(issues, "SPECIES_MASS", "Species " + label + " has an invalid mass.");
                }
                if (!species.hasPseudoPotential(i)) {
                    error(issues, "PSEUDO_MISSING", "Species " + label + " has no selected pseudopotential.");
                }
            }
            if (positions != null) {
                for (int i = 0; i < positions.numPositions(); i++) {
                    if (!labels.contains(positions.getLabel(i))) {
                        error(issues, "ATOM_SPECIES_UNKNOWN", "Atom label " + positions.getLabel(i)
                                + " is not declared in ATOMIC_SPECIES.");
                    }
                    double[] coordinate = positions.getPosition(i);
                    for (double value : coordinate) {
                        if (!Double.isFinite(value)) {
                            error(issues, "ATOM_NONFINITE", "Atom " + (i + 1) + " has a non-finite coordinate.");
                            break;
                        }
                    }
                }
            }
        }

        Integer ibrav = integerValue(system, "ibrav");
        if (ibrav == null) {
            error(issues, "IBRAV_MISSING", "SYSTEM.ibrav is missing.");
        } else if (ibrav == 0) {
            QECellParameters cell = input.getCard(QECellParameters.class);
            if (cell == null) {
                error(issues, "CELL_MISSING", "ibrav=0 requires CELL_PARAMETERS.");
            } else {
                double[][] matrix = {cell.getVector1(), cell.getVector2(), cell.getVector3()};
                double determinant = Matrix3D.determinant(matrix);
                if (!Double.isFinite(determinant) || Math.abs(determinant) < 1.0e-12) {
                    error(issues, "CELL_SINGULAR", "CELL_PARAMETERS is singular or has near-zero volume.");
                }
            }
        }
    }

    private void validateCutoffs(QENamelist system, List<ValidationIssue> issues) {
        Double wavefunction = realValue(system, "ecutwfc");
        Double density = realValue(system, "ecutrho");
        if (wavefunction == null || !Double.isFinite(wavefunction) || wavefunction <= 0.0) {
            error(issues, "ECUTWFC_MISSING", "A positive, convergence-tested SYSTEM.ecutwfc is required.");
        }
        if (density != null) {
            if (!Double.isFinite(density) || density <= 0.0) {
                error(issues, "ECUTRHO_INVALID", "SYSTEM.ecutrho must be positive when specified.");
            } else if (wavefunction != null && density < wavefunction) {
                error(issues, "ECUTRHO_LOW", "ecutrho cannot be lower than ecutwfc.");
            }
        } else {
            warning(issues, "ECUTRHO_DEFAULT", "ecutrho is omitted; verify QE's pseudo-dependent default is appropriate.");
        }
    }

    private void validateOccupations(QENamelist system, List<ValidationIssue> issues) {
        String occupations = characterValue(system, "occupations");
        if (occupations == null) return;
        occupations = occupations.toLowerCase(Locale.ROOT);
        if ("smearing".equals(occupations)) {
            String smearing = characterValue(system, "smearing");
            Double degauss = realValue(system, "degauss");
            if (smearing == null || smearing.trim().isEmpty()) {
                error(issues, "SMEARING_TYPE", "occupations='smearing' requires a smearing scheme.");
            }
            if (degauss == null || !Double.isFinite(degauss) || degauss <= 0.0) {
                error(issues, "DEGAUSS", "occupations='smearing' requires positive degauss in Ry.");
            }
        }
    }

    private void validateSpin(QENamelist system, List<ValidationIssue> issues) {
        boolean noncollinear = logicalValue(system, "noncolin", false);
        boolean spinOrbit = logicalValue(system, "lspinorb", false);
        Integer nspin = integerValue(system, "nspin");
        if (spinOrbit && !noncollinear) {
            error(issues, "SOC_NONCOLIN", "lspinorb=.true. requires noncolin=.true.");
        }
        if (noncollinear && nspin != null && nspin == 2) {
            error(issues, "SPIN_CONFLICT", "nspin=2 cannot be combined with noncolin=.true.");
        }
    }

    private void validateKPoints(QEKPoints points, List<ValidationIssue> issues) {
        if (points == null) {
            error(issues, "KPOINTS_MISSING", "K_POINTS is missing.");
            return;
        }
        if (points.isAutomatic()) {
            int[] grid = points.getKGrid();
            int[] offset = points.getKOffset();
            for (int i = 0; i < 3; i++) {
                if (grid[i] < 1) error(issues, "KGRID", "Automatic k-grid dimensions must be positive.");
                if (offset[i] != 0 && offset[i] != 1) error(issues, "KOFFSET", "K-point offsets must be 0 or 1.");
            }
        } else if (!points.isGamma() && points.numKPoints() == 0) {
            error(issues, "KPOINTS_EMPTY", "Explicit K_POINTS contains no points.");
        }
    }

    private static Integer integerValue(QENamelist list, String name) {
        QEValue value = list.getValue(name);
        return value == null ? null : value.getIntegerValue();
    }

    private static Double realValue(QENamelist list, String name) {
        QEValue value = list.getValue(name);
        return value == null ? null : value.getRealValue();
    }

    private static String characterValue(QENamelist list, String name) {
        QEValue value = list.getValue(name);
        return value == null ? null : value.getCharacterValue();
    }

    private static boolean logicalValue(QENamelist list, String name, boolean fallback) {
        QEValue value = list.getValue(name);
        return value == null ? fallback : value.getLogicalValue();
    }

    private static void error(List<ValidationIssue> issues, String code, String message) {
        issues.add(new ValidationIssue(ValidationSeverity.ERROR, code, message, PW_DOC));
    }

    private static void warning(List<ValidationIssue> issues, String code, String message) {
        issues.add(new ValidationIssue(ValidationSeverity.WARNING, code, message, PW_DOC));
    }
}
