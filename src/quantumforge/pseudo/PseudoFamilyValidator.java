/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.pseudo;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import quantumforge.input.QEInput;
import quantumforge.input.card.QEAtomicSpecies;
import quantumforge.input.validation.ValidationIssue;
import quantumforge.input.validation.ValidationSeverity;

/**
 * Validates consistency across selected pseudopotentials in a calculation.
 * Prevents mixed functionals (e.g. mixing LDA and PBE) and mixed relativity,
 * fulfilling SSSP / PSLibrary family validation constraints (Roadmap #35).
 */
public final class PseudoFamilyValidator {

    private static final String PW_DOC = "https://www.quantum-espresso.org/Doc/INPUT_PW.html";

    public static List<ValidationIssue> validateFamilies(QEInput input) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (input == null) {
            return issues;
        }

        QEAtomicSpecies speciesCard = input.getCard(QEAtomicSpecies.class);
        if (speciesCard == null || speciesCard.numSpecies() == 0) {
            return issues;
        }

        Set<Integer> functionals = new HashSet<>();
        Set<Integer> relativityTypes = new HashSet<>();
        Set<String> metadataUnavailable = new LinkedHashSet<>();

        PseudoLibrary library = PseudoLibrary.getInstance();
        library.waitToLoad();

        for (int i = 0; i < speciesCard.numSpecies(); i++) {
            String label = speciesCard.getLabel(i);
            String pseudoName = speciesCard.getPseudoName(i);
            if (pseudoName == null || pseudoName.trim().isEmpty()) {
                continue;
            }

            PseudoPotential pseudoPot = library.peekPseudoPotential(pseudoName);
            if (pseudoPot == null || pseudoPot.getData() == null) {
                // A filename alone does not establish XC, valence, or
                // relativity compatibility. Do not silently pass it.
                metadataUnavailable.add(label + "=" + pseudoName);
                continue;
            }

            PseudoData data = pseudoPot.getData();

            if (data.hasFunctional()) {
                functionals.add(data.getFunctional());
            }
            if (data.hasRelativistic()) {
                relativityTypes.add(data.getRelativistic());
            }

        }

        if (!metadataUnavailable.isEmpty()) {
            issues.add(new ValidationIssue(ValidationSeverity.WARNING, "PSEUDO_METADATA_UNAVAILABLE",
                    "Cannot verify pseudopotential family/XC/relativity metadata for "
                            + String.join(", ", metadataUnavailable)
                            + ". Import a verified pseudo manifest or inspect each UPF before running.",
                    PW_DOC));
        }

        // Rule 1: Functional mixing is a severe physical inconsistency (e.g., mixing PBE and LDA)
        if (functionals.size() > 1) {
            StringBuilder msg = new StringBuilder("Incompatible mixed functionals detected in pseudopotentials: ");
            int idx = 0;
            for (int f : functionals) {
                if (idx > 0) msg.append(", ");
                msg.append(getFunctionalLabel(f));
                idx++;
            }
            msg.append(". All species must use the same exchange-correlation functional.");
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, "PSEUDO_MIXED_FUNCTIONALS", msg.toString(), PW_DOC));
        }

        // Rule 2: Mixing scalar and fully relativistic potentials can break SOC / noncollinear runs
        if (relativityTypes.contains(PseudoData.RELATIVISTIC_FULL) && relativityTypes.contains(PseudoData.RELATIVISTIC_SCALAR)) {
            issues.add(new ValidationIssue(ValidationSeverity.WARNING, "PSEUDO_MIXED_RELATIVITY",
                "Mixed relativistic pseudopotentials detected (Scalar and Full). Ensure this is intentional for Spin-Orbit Coupling.",
                PW_DOC));
        }

        return issues;
    }

    private static String getFunctionalLabel(int functional) {
        switch (functional) {
            case PseudoData.FUNCTIONAL_PZ: return "LDA (PZ)";
            case PseudoData.FUNCTIONAL_PW91: return "GGA (PW91)";
            case PseudoData.FUNCTIONAL_PBE: return "GGA (PBE)";
            case PseudoData.FUNCTIONAL_REVPBE: return "GGA (revPBE)";
            case PseudoData.FUNCTIONAL_PBESOL: return "GGA (PBEsol)";
            case PseudoData.FUNCTIONAL_BLYP: return "GGA (BLYP)";
            default: return "Unknown";
        }
    }
}
