package quantumforge.input.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import quantumforge.input.QESCFInput;
import quantumforge.input.card.QEAtomicPositions;
import quantumforge.input.card.QEAtomicSpecies;
import quantumforge.input.card.QECellParameters;
import quantumforge.input.card.QEKPoints;
import quantumforge.input.namelist.QENamelist;

class QEInputValidatorTest {
    @Test
    void acceptsStructurallyCompleteMinimalScfAndRejectsSpinConflict() {
        QESCFInput input = createMinimalScf();
        QEInputValidator validator = new QEInputValidator();
        List<ValidationIssue> issues = validator.validate(input);
        assertFalse(QEInputValidator.hasErrors(issues), issues.toString());

        QENamelist system = input.getNamelist("SYSTEM");
        system.setValue("noncolin = .false.");
        system.setValue("lspinorb = .true.");
        issues = validator.validate(input);
        assertTrue(issues.stream().anyMatch(issue -> "SOC_NONCOLIN".equals(issue.getCode())));
        assertTrue(QEInputValidator.hasErrors(issues));
    }

    @Test
    void reportsUnverifiedPseudoMetadataInsteadOfTreatingAFileNameAsCompatibilityEvidence() {
        QESCFInput input = createMinimalScf();
        input.getCard(QEAtomicSpecies.class).setPseudoPotential(0, "not-a-verified-library-entry.UPF");
        List<ValidationIssue> issues = new QEInputValidator().validate(input);
        assertTrue(issues.stream().anyMatch(issue -> "PSEUDO_METADATA_UNAVAILABLE".equals(issue.getCode())));
    }

    @Test
    void catchesCountsMissingPseudopotentialAndSingularCell() {
        QESCFInput input = createMinimalScf();
        QENamelist system = input.getNamelist("SYSTEM");
        system.setValue("nat = 2");
        input.getCard(QEAtomicSpecies.class).setPseudoPotential(0, "unknown");
        QECellParameters cell = input.getCard(QECellParameters.class);
        cell.setVector(3, new double[] {0, 0, 0});

        List<ValidationIssue> issues = new QEInputValidator().validate(input);
        assertTrue(issues.stream().anyMatch(issue -> "NAT_MISMATCH".equals(issue.getCode())));
        assertTrue(issues.stream().anyMatch(issue -> "PSEUDO_MISSING".equals(issue.getCode())));
        assertTrue(issues.stream().anyMatch(issue -> "CELL_SINGULAR".equals(issue.getCode())));
    }

    private static QESCFInput createMinimalScf() {
        QESCFInput input = new QESCFInput();
        QENamelist system = input.getNamelist("SYSTEM");
        system.setValue("ibrav = 0");
        system.setValue("nat = 1");
        system.setValue("ntyp = 1");
        system.setValue("ecutwfc = 40.0");
        system.setValue("ecutrho = 320.0");

        QEAtomicSpecies species = input.getCard(QEAtomicSpecies.class);
        species.addSpecies("Si", 28.085, "Si.UPF");
        QEAtomicPositions positions = input.getCard(QEAtomicPositions.class);
        positions.setCrystal();
        positions.addPosition("Si", new double[] {0, 0, 0}, new boolean[] {true, true, true});
        QECellParameters cell = input.getCard(QECellParameters.class);
        cell.setAngstrom();
        cell.setVector(1, new double[] {5.43, 0, 0});
        cell.setVector(2, new double[] {0, 5.43, 0});
        cell.setVector(3, new double[] {0, 0, 5.43});
        QEKPoints points = input.getCard(QEKPoints.class);
        points.setGamma();
        return input;
    }
}
