/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.input;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import quantumforge.input.card.QECard;
import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValue;

/**
 * Utility to perform side-by-side structural differences between two Quantum ESPRESSO
 * input configurations (Roadmap #44).
 */
public final class QEInputDiffPreview {

    public enum ChangeType {
        ADDED,
        REMOVED,
        MODIFIED
    }

    public static final class DiffItem {
        private final String section; // Namelist name or Card name
        private final String key;     // Parameter name or identifier
        private final ChangeType type;
        private final String oldValue;
        private final String newValue;

        public DiffItem(String section, String key, ChangeType type, String oldValue, String newValue) {
            this.section = Objects.requireNonNull(section);
            this.key = Objects.requireNonNull(key);
            this.type = Objects.requireNonNull(type);
            this.oldValue = oldValue == null ? "" : oldValue;
            this.newValue = newValue == null ? "" : newValue;
        }

        public String getSection() { return this.section; }
        public String getKey() { return this.key; }
        public ChangeType getType() { return this.type; }
        public String getOldValue() { return this.oldValue; }
        public String getNewValue() { return this.newValue; }

        @Override
        public String toString() {
            switch (type) {
                case ADDED:
                    return String.format("[+] %s: set %s = %s", section, key, newValue);
                case REMOVED:
                    return String.format("[-] %s: removed %s (was %s)", section, key, oldValue);
                case MODIFIED:
                    return String.format("[~] %s: changed %s from %s to %s", section, key, oldValue, newValue);
                default:
                    return "";
            }
        }
    }

    private QEInputDiffPreview() {
        // Utility
    }

    /**
     * Compares two QE inputs and generates a list of parameter diffs.
     */
    public static List<DiffItem> compare(QEInput base, QEInput modified) {
        List<DiffItem> diffs = new ArrayList<>();
        if (base == null || modified == null) {
            return diffs;
        }

        // 1. Compare Namelists
        for (String nmlKey : QEInput.listNamelistKeys()) {
            QENamelist baseNml = base.getNamelist(nmlKey);
            QENamelist modNml = modified.getNamelist(nmlKey);

            if (baseNml == null && modNml == null) {
                continue;
            }

            if (baseNml == null && modNml != null) {
                for (QEValue val : modNml.listQEValues()) {
                    diffs.add(new DiffItem(nmlKey, val.getName(), ChangeType.ADDED, null, val.getValueString()));
                }
                continue;
            }

            if (baseNml != null && modNml == null) {
                for (QEValue val : baseNml.listQEValues()) {
                    diffs.add(new DiffItem(nmlKey, val.getName(), ChangeType.REMOVED, val.getValueString(), null));
                }
                continue;
            }

            // Compare key-value pairs
            Map<String, QEValue> baseVals = new HashMap<>();
            for (QEValue val : baseNml.listQEValues()) {
                baseVals.put(val.getName().toLowerCase(), val);
            }

            Map<String, QEValue> modVals = new HashMap<>();
            for (QEValue val : modNml.listQEValues()) {
                modVals.put(val.getName().toLowerCase(), val);
            }

            // Keys in base
            for (Map.Entry<String, QEValue> entry : baseVals.entrySet()) {
                String key = entry.getValue().getName();
                QEValue baseVal = entry.getValue();
                QEValue modVal = modVals.get(entry.getKey());

                if (modVal == null) {
                    diffs.add(new DiffItem(nmlKey, key, ChangeType.REMOVED, baseVal.getValueString(), null));
                } else if (!baseVal.getValueString().trim().equalsIgnoreCase(modVal.getValueString().trim())) {
                    diffs.add(new DiffItem(nmlKey, key, ChangeType.MODIFIED, baseVal.getValueString(), modVal.getValueString()));
                }
            }

            // Keys only in modified
            for (Map.Entry<String, QEValue> entry : modVals.entrySet()) {
                String key = entry.getValue().getName();
                if (!baseVals.containsKey(entry.getKey())) {
                    diffs.add(new DiffItem(nmlKey, key, ChangeType.ADDED, null, entry.getValue().getValueString()));
                }
            }
        }

        // 2. Compare Cards (String representations)
        for (String cardKey : QEInput.listCardKeys()) {
            QECard baseCard = base.getCard(cardKey);
            QECard modCard = modified.getCard(cardKey);

            if (baseCard == null && modCard == null) {
                continue;
            }

            String baseStr = baseCard != null ? baseCard.toString().trim() : "";
            String modStr = modCard != null ? modCard.toString().trim() : "";

            if (!baseStr.equalsIgnoreCase(modStr)) {
                if (baseStr.isEmpty() && !modStr.isEmpty()) {
                    diffs.add(new DiffItem("CARD", cardKey, ChangeType.ADDED, null, "Card populated"));
                } else if (!baseStr.isEmpty() && modStr.isEmpty()) {
                    diffs.add(new DiffItem("CARD", cardKey, ChangeType.REMOVED, "Card populated", null));
                } else {
                    diffs.add(new DiffItem("CARD", cardKey, ChangeType.MODIFIED, "Lines: " + getLineCount(baseStr), "Lines: " + getLineCount(modStr)));
                }
            }
        }

        return diffs;
    }

    /**
     * Generates a side-by-side styled textual report of the differences.
     */
    public static String generateReport(QEInput base, QEInput modified) {
        List<DiffItem> items = compare(base, modified);
        if (items.isEmpty()) {
            return "No differences found. Configuration is identical.";
        }

        StringBuilder report = new StringBuilder("Quantum ESPRESSO Input Diff Preview\n");
        report.append("=========================================\n");
        for (DiffItem item : items) {
            report.append(item.toString()).append('\n');
        }
        return report.toString();
    }

    private static int getLineCount(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.split("\r\n|\r|\n").length;
    }
}
