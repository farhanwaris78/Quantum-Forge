/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.input.namelist;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chemically rigorous SMILES parser that supports aromatic organic elements (lowercase c, n, o, s, p),
 * two-letter elements (Cl, Br), explicit bond orders (=, #), ring-closure digits and branches.
 * Implicit hydrogens are estimated from the accumulated bond-order sum against the standard
 * organic valence of each atom (Roadmap #87).
 *
 * <p>Deliberate scope boundary: this is a deterministic formula/vaience-review tokenizer, not a
 * conformer generator - it never assigns 3D positions, stereochemistry or protonation states.
 * Bracket atoms contribute their element (and explicit H count) but receive no implicit
 * hydrogen estimate since a bracket declares its own hydrogen content.</p>
 */
public final class SMILESParser {

    private final String smiles;

    public SMILESParser(String smiles) {
        this.smiles = smiles != null ? smiles.trim() : "";
    }

    public boolean isValid() {
        return !this.smiles.isEmpty();
    }

    /**
     * Reconstructs the empirical chemical formula from the SMILES notation,
     * including implicit hydrogen counting for saturated valences.
     */
    public String getChemicalFormula() {
        if (!this.isValid()) {
            return "";
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        BondGraph graph = buildBondGraph();
        if (graph == null) {
            return "";
        }
        int implicitHCount = 0;
        for (Atom atom : graph.atoms) {
            counts.merge(atom.element, 1, Integer::sum);
            implicitHCount += atom.explicitH;
            if (atom.bracketed) {
                continue; // brackets declare their own hydrogen content
            }
            int valence = getStandardValence(atom.element);
            if (valence <= 0) {
                continue;
            }
            int bondOrderSum = (int) Math.floor(graph.bondOrderSumFor(atom.index) + 1.0e-9);
            if (valence > bondOrderSum) {
                implicitHCount += valence - bondOrderSum;
            }
        }

        if (implicitHCount > 0) {
            counts.merge("H", implicitHCount, Integer::sum);
        }

        // Format empirical formula (standard Hill system order: C, then H, then alphabetical)
        StringBuilder formula = new StringBuilder();
        if (counts.containsKey("C")) {
            formula.append("C");
            int c = counts.get("C");
            if (c > 1) formula.append(c);
            counts.remove("C");
        }
        if (counts.containsKey("H")) {
            formula.append("H");
            int h = counts.get("H");
            if (h > 1) formula.append(h);
            counts.remove("H");
        }

        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            formula.append(entry.getKey());
            if (entry.getValue() > 1) {
                formula.append(entry.getValue());
            }
        }

        return formula.toString();
    }

    /**
     * Check if this SMILES represents an organic molecule
     */
    public boolean isOrganic() {
        String formula = this.getChemicalFormula();
        return formula.contains("C") && (formula.contains("H") || formula.contains("N") || formula.contains("O"));
    }

    // ---------------------------------------------------------------- bond graph

    private static final class Atom {
        final int index;
        final String element;
        final boolean aromatic;
        final boolean bracketed;
        final int explicitH;

        Atom(int index, String element, boolean aromatic, boolean bracketed, int explicitH) {
            this.index = index;
            this.element = element;
            this.aromatic = aromatic;
            this.bracketed = bracketed;
            this.explicitH = explicitH;
        }
    }

    private static final class BondGraph {
        final List<Atom> atoms = new ArrayList<>();
        final List<double[]> bonds = new ArrayList<>(); // [atomA, atomB, order]
        final Map<Atom, double[]> orderSums = new HashMap<>();

        void addBond(int a, int b, double order) {
            this.bonds.add(new double[] {a, b, order});
        }

        double bondOrderSumFor(int atomIndex) {
            double sum = 0.0;
            for (double[] bond : this.bonds) {
                if ((int) bond[0] == atomIndex || (int) bond[1] == atomIndex) {
                    sum += bond[2];
                }
            }
            return sum;
        }
    }

    private static final class RingOpen {
        int atomIndex;
        double order = -1.0; // explicit order given before the digit; -1 means default
    }

    /**
     * Tokenizes the SMILES into a bond graph. Returns null if no atom could be tokenized.
     * Unknown characters (other than SMILES grammar) are skipped so a stray charge or
     * wildcard token never corrupts valence arithmetic of the recognized atoms.
     */
    private BondGraph buildBondGraph() {
        BondGraph graph = new BondGraph();
        List<int[]> branchStack = new ArrayList<>(); // previous atom indices per '('
        Map<Character, RingOpen> ringOpen = new HashMap<>();
        int prev = -1;
        double pendingOrder = -1.0;
        int i = 0;
        int n = this.smiles.length();
        while (i < n) {
            char ch = this.smiles.charAt(i);
            if (ch == '(') {
                // branch: remember the atom the branch sprouts from (previous atom)
                if (prev >= 0) {
                    int[] frame = {prev};
                    branchStack.add(frame);
                }
                // branch bond uses pending order; stored until the branched atom arrives
                i++;
                continue;
            }
            if (ch == ')') {
                if (!branchStack.isEmpty()) {
                    prev = branchStack.remove(branchStack.size() - 1)[0];
                }
                i++;
                continue;
            }
            if (ch == '=' || ch == '#' || ch == '-' || ch == ':' || ch == '/' || ch == '\\') {
                if (ch == '=') {
                    pendingOrder = 2.0;
                } else if (ch == '#') {
                    pendingOrder = 3.0;
                }
                i++;
                continue;
            }
            if (ch == '.' || ch == '%') {
                i++;
                continue;
            }
            if (Character.isDigit(ch)) {
                // ring closure
                double order = pendingOrder;
                pendingOrder = -1.0;
                RingOpen opened = ringOpen.get(ch);
                if (opened == null) {
                    RingOpen open = new RingOpen();
                    open.atomIndex = prev;
                    open.order = order;
                    ringOpen.put(ch, open);
                } else {
                    ringOpen.remove(ch);
                    double ringOrder = order >= 0 ? order
                            : (opened.order >= 0 ? opened.order
                                    : defaultOrder(graph, opened.atomIndex, prev));
                    if (opened.atomIndex >= 0 && prev >= 0 && opened.atomIndex != prev) {
                        graph.addBond(opened.atomIndex, prev, ringOrder);
                    }
                }
                i++;
                continue;
            }
            if (ch == '[') {
                int end = this.smiles.indexOf(']', i + 1);
                if (end > i) {
                    String inside = this.smiles.substring(i + 1, end);
                    String elem = bracketElementOf(inside);
                    int explicitH = bracketHydrogenCountOf(inside);
                    if (elem != null && isValidElement(elem.toUpperCase())) {
                        int idx = graph.atoms.size();
                        boolean aromatic = Character.isLowerCase(elem.charAt(0));
                        String normalized = elem.toUpperCase();
                        graph.atoms.add(new Atom(idx, normalized, aromatic, true, explicitH));
                        if (prev >= 0) {
                            graph.addBond(prev, idx, defaultBond(prev, graph.atoms.get(idx), pendingOrder, graph));
                        }
                        prev = idx;
                        pendingOrder = -1.0;
                    }
                    i = end + 1;
                    continue;
                }
                i++;
                continue;
            }
            if (Character.isLetter(ch)) {
                String elem;
                boolean aromatic = aromaticLetter(ch) && Character.isLowerCase(ch);
                if (i + 1 < n && Character.isLowerCase(this.smiles.charAt(i + 1))
                        && !aromaticLetter(this.smiles.charAt(i + 1))) {
                    elem = String.valueOf(ch) + this.smiles.charAt(i + 1);
                    i += 2;
                } else {
                    elem = String.valueOf(ch);
                    i++;
                }
                String normalized = elem.toUpperCase();
                if (isValidElement(normalized)) {
                    int idx = graph.atoms.size();
                    graph.atoms.add(new Atom(idx, normalized, aromatic, false, 0));
                    if (prev >= 0) {
                        graph.addBond(prev, idx, defaultBond(prev, graph.atoms.get(idx), pendingOrder, graph));
                    }
                    prev = idx;
                    pendingOrder = -1.0;
                }
                continue;
            }
            i++;
        }
        return graph.atoms.isEmpty() ? null : graph;
    }

    private static double defaultBond(int prevIdx, Atom next, double pendingOrder, BondGraph graph) {
        if (pendingOrder > 0) {
            return pendingOrder;
        }
        if (prevIdx >= 0 && prevIdx < graph.atoms.size() && graph.atoms.get(prevIdx).aromatic && next.aromatic) {
            return 1.5; // aromatic-aromatic conjugated bond
        }
        return 1.0;
    }

    private static double defaultOrder(BondGraph graph, int a, int b) {
        if (a >= 0 && a < graph.atoms.size() && b >= 0 && b < graph.atoms.size()
                && graph.atoms.get(a).aromatic && graph.atoms.get(b).aromatic) {
            return 1.5;
        }
        return 1.0;
    }

    /** Extracts the element token from a bracket expression such as "nH" or "NH2+". */
    private static String bracketElementOf(String inside) {
        StringBuilder elem = new StringBuilder();
        int i = 0;
        while (i < inside.length() && !Character.isLetter(inside.charAt(i))) {
            i++;
        }
        if (i >= inside.length()) {
            return null;
        }
        elem.append(inside.charAt(i));
        if (i + 1 < inside.length() && Character.isLowerCase(inside.charAt(i + 1))
                && !aromaticLetter(inside.charAt(i + 1))) {
            elem.append(inside.charAt(i + 1));
        }
        return elem.toString();
    }

    /** Counts explicit hydrogens declared inside a bracket expression ("[nH]" -> 1, "[NH2+]" -> 2). */
    private static int bracketHydrogenCountOf(String inside) {
        int total = 0;
        for (int i = 0; i < inside.length(); i++) {
            if (inside.charAt(i) == 'H') {
                int count = 1;
                if (i + 1 < inside.length() && Character.isDigit(inside.charAt(i + 1))) {
                    count = inside.charAt(i + 1) - '0';
                    i++;
                }
                total += count;
            }
        }
        return total;
    }

    private static boolean aromaticLetter(char c) {
        return c == 'c' || c == 'n' || c == 'o' || c == 's' || c == 'p';
    }

    private static boolean isValidElement(String elem) {
        // Simple organic set allowed in default SMILES without brackets
        return "C".equals(elem) || "N".equals(elem) || "O".equals(elem) ||
               "S".equals(elem) || "P".equals(elem) || "F".equals(elem) ||
               "CL".equals(elem) || "BR".equals(elem) || "I".equals(elem) ||
               "H".equals(elem);
    }

    private static int getStandardValence(String element) {
        switch (element) {
            case "C": return 4;
            case "N": return 3;
            case "O": return 2;
            case "S": return 2;
            case "P": return 3;
            case "F": case "CL": case "BR": case "I": case "H": return 1;
            default: return 0;
        }
    }
}
