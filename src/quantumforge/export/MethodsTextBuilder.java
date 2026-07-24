/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.export;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.input.QEInput;
import quantumforge.input.card.QEAtomicSpecies;
import quantumforge.input.card.QEKPoints;
import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValue;
import quantumforge.input.namelist.QEValueBuffer;

/**
 * Draft methods-section generator (Roadmap #123, partial).
 *
 * <p>Every sentence in the draft is emitted only when its value was actually
 * parsed from the open input/cell, and every physics item that was NOT parsed
 * is listed in an explicit "Not recorded" section to be filled in manually -
 * the generator never fabricates a functional name from a pseudopotential file
 * name, never invents convergence thresholds, and never cites engines the
 * project has not run. The draft is Markdown text for review; it is not a
 * publication artifact by itself.</p>
 */
public final class MethodsTextBuilder {

    private MethodsTextBuilder() {
        // Utility
    }

    /** The generated draft plus the list of items the user must fill in. */
    public static final class MethodsDraft {
        private final String text;
        private final List<String> missing;

        private MethodsDraft(String text, List<String> missing) {
            this.text = text;
            this.missing = missing;
        }

        public String getText() { return this.text; }
        public List<String> getMissing() { return this.missing; }
    }

    /**
     * Composes the draft. {@code input} may be null (all input-dependent items
     * then appear in the missing list); {@code cell} may be null (composition
     * then appears in the missing list). {@code bibtex} is appended verbatim in
     * a references section when non-blank.
     */
    public static MethodsDraft build(QEInput input, Cell cell, List<String> citationKeys,
                                     String bibtex) {
        List<String> missing = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        text.append("## Computational methods (draft - verify every value against your raw "
                + "outputs)\n\n");
        text.append("Density-functional theory calculations were performed with "
                + "QUANTUM ESPRESSO, driven by QuantumForge. Values below are transcribed "
                + "from the currently open input; anything absent is listed under \"Not "
                + "recorded\" and must be filled in by hand.\n\n");

        appendCalculation(input, text, missing);
        appendCutoffs(input, text, missing);
        appendSampling(input, text, missing);
        appendPseudopotentials(input, text, missing);
        appendOccupations(input, text, missing);
        appendComposition(cell, text, missing);

        text.append("### Not recorded (fill in before submission)\n\n");
        if (missing.isEmpty()) {
            text.append("- (nothing missing from the transcribed fields)\n");
        } else {
            for (String item : missing) {
                text.append("- ").append(item).append('\n');
            }
        }
        text.append("\n");

        if (citationKeys != null && !citationKeys.isEmpty()) {
            text.append("Registered citation keys: ").append(String.join(", ", citationKeys))
                    .append('\n').append('\n');
        }
        if (bibtex != null && !bibtex.isBlank()) {
            text.append("### References (BibTeX)\n\n```bibtex\n").append(bibtex)
                    .append("\n```\n");
        }
        return new MethodsDraft(text.toString(), missing);
    }

    private static void appendCalculation(QEInput input, StringBuilder text,
                                          List<String> missing) {
        QEValue calculation = namelistValue(input, QEInput.NAMELIST_CONTROL, "calculation");
        if (calculation != null && !calculation.getCharacterValue().isBlank()) {
            text.append("- Calculation type: `").append(stripQuotes(calculation.getCharacterValue()))
                    .append("` (pw.x `calculation` keyword).\n");
        } else {
            missing.add("calculation type (&CONTROL: calculation)");
        }
    }

    private static void appendCutoffs(QEInput input, StringBuilder text,
                                      List<String> missing) {
        QEValue ecutwfc = namelistValue(input, QEInput.NAMELIST_SYSTEM, "ecutwfc");
        QEValue ecutrho = namelistValue(input, QEInput.NAMELIST_SYSTEM, "ecutrho");
        if (ecutwfc != null) {
            StringBuilder line = new StringBuilder();
            line.append(String.format(Locale.ROOT,
                    "- Plane-wave kinetic-energy cutoff `ecutwfc` = %.2f Ry", 
                    ecutwfc.getRealValue()));
            if (ecutrho != null) {
                line.append(String.format(Locale.ROOT, " (charge-density cutoff `ecutrho` "
                        + "= %.2f Ry, ratio %.3f)", ecutrho.getRealValue(),
                        ecutrho.getRealValue() / ecutwfc.getRealValue()));
            }
            line.append(".\n");
            text.append(line);
        } else {
            missing.add("plane-wave cutoff(s) (ecutwfc/ecutrho)");
        }
    }

    private static void appendSampling(QEInput input, StringBuilder text,
                                       List<String> missing) {
        if (input == null) {
            missing.add("Brillouin-zone sampling (K_POINTS)");
            return;
        }
        QEKPoints kpoints = input.getCard(QEKPoints.class);
        if (kpoints == null) {
            missing.add("Brillouin-zone sampling (K_POINTS)");
            return;
        }
        if (kpoints.isGamma()) {
            text.append("- Brillouin-zone sampling: single \u0393 point.\n");
            return;
        }
        if (kpoints.isAutomatic()) {
            int[] grid = kpoints.getKGrid();
            int[] offset = kpoints.getKOffset();
            text.append(String.format(Locale.ROOT,
                    "- Brillouin-zone sampling: %d \u00d7 %d \u00d7 %d Monkhorst-Pack mesh "
                            + "(%s-centred).%n",
                    grid[0], grid[1], grid[2],
                    offset[0] == 0 && offset[1] == 0 && offset[2] == 0 ? "\u0393"
                            : "off-\u0393"));
            return;
        }
        text.append("- Brillouin-zone sampling: explicit list of ")
                .append(kpoints.numKPoints()).append(" k points (see input file).\n");
    }

    private static void appendPseudopotentials(QEInput input, StringBuilder text,
                                               List<String> missing) {
        QEAtomicSpecies species = null;
        if (input != null) {
            species = input.getCard(QEAtomicSpecies.class);
        }
        if (species == null || species.numSpecies() < 1) {
            missing.add("pseudopotentials (ATOMIC_SPECIES entries)");
            return;
        }
        text.append("- Pseudopotentials (file names as recorded; the exchange-correlation "
                + "identity is NOT provable from file names - state the verified functional "
                + "and library citation in the text): ");
        List<String> entries = new ArrayList<>();
        for (int i = 0; i < species.numSpecies(); i++) {
            String name = species.getLabel(i);
            if (name == null || name.isBlank()) {
                name = "species" + (i + 1);
            }
            String pseudo = species.getPseudoName(i);
            entries.add(name + " <- " + (pseudo == null || pseudo.isBlank()
                    ? "(no file recorded)" : pseudo));
        }
        text.append(String.join("; ", entries)).append(".\n");
    }

    private static void appendOccupations(QEInput input, StringBuilder text,
                                          List<String> missing) {
        QEValue occupations = namelistValue(input, QEInput.NAMELIST_SYSTEM, "occupations");
        QEValue degauss = namelistValue(input, QEInput.NAMELIST_SYSTEM, "degauss");
        QEValue smearing = namelistValue(input, QEInput.NAMELIST_SYSTEM, "smearing");
        if (occupations != null) {
            StringBuilder line = new StringBuilder();
            line.append("- Occupations: `").append(stripQuotes(occupations.getCharacterValue()))
                    .append("`");
            if (smearing != null) {
                line.append(" (smearing `").append(stripQuotes(smearing.getCharacterValue())).append("`");
                if (degauss != null) {
                    line.append(String.format(Locale.ROOT, ", degauss %.5f Ry",
                            degauss.getRealValue()));
                }
                line.append(")");
            }
            line.append(".\n");
            text.append(line);
        } else {
            missing.add("occupation/smearing choice (&SYSTEM: occupations, smearing, degauss)");
        }
    }

    private static void appendComposition(Cell cell, StringBuilder text,
                                          List<String> missing) {
        if (cell == null) {
            missing.add("cell composition (no structure in the project)");
            return;
        }
        Atom[] atoms = cell.listAtoms(true);
        if (atoms == null || atoms.length == 0) {
            missing.add("cell composition (empty cell)");
            return;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Atom atom : atoms) {
            if (atom == null) {
                continue;
            }
            String element = atom.getElementName();
            counts.merge(element == null || element.isBlank() ? "?" : element, 1,
                    Integer::sum);
        }
        StringBuilder formula = new StringBuilder();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            formula.append(entry.getKey());
            if (entry.getValue() > 1) {
                formula.append(entry.getValue());
            }
            formula.append(" ");
        }
        text.append("- Cell composition: ").append(formula.toString().trim())
                .append(" (").append(atoms.length).append(" atoms in the simulation cell).\n");
    }

    private static String stripQuotes(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2
                && ((trimmed.startsWith("'") && trimmed.endsWith("'"))
                        || (trimmed.startsWith("\"") && trimmed.endsWith("\"")))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    /** Reads and unwraps a namelist value; null when absent. */
    private static QEValue namelistValue(QEInput input, String namelist, String name) {
        if (input == null) {
            return null;
        }
        QENamelist list = input.getNamelist(namelist);
        if (list == null) {
            return null;
        }
        QEValue value = list.getValue(name);
        if (value instanceof QEValueBuffer buffer) {
            return buffer.getValue();
        }
        return value;
    }
}
