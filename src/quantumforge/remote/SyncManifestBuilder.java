/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.remote;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #98 (draft slice): a SELECTIVE result-sync manifest - the declared
 * INTENT of which remote artifacts a future sync must fetch. Nothing
 * downloads from this build; the manifest records roles, and its grammar
 * makes the silent-bandwidth-waste and silent-miss errors structural refuses:
 *
 * <ul>
 *   <li>entries are project-RELATIVE POSIX paths or the single owned wildcard
 *       shape {@code *.ext} (top-level-only, stated in the render - recursive
 *       {@code **} patterns are refused rather than misinterpreted)
 *       (SYNC_ENTRY);</li>
 *   <li>each entry is required, optional, large-on-demand or EXCLUDED - and
 *       may appear in EXACTLY ONE role; cross-role duplicates refuse
 *       (SYNC_DUPLICATE) because 'required' vs 'excluded' for the same name
 *       would resolve silently otherwise;</li>
 *   <li>at least one REQUIRED entry is mandatory (SYNC_REQUIRED) - a
 *       manifest that fetches nothing essential is ceremonial;</li>
 *   <li>sizes and checksums are declared UNKNOWN until the first fetch -
 *       the manifest never fabricates them; the checksum cache fills at sync
 *       time and is verified against parser prerequisites at parse time
 *       (stated in the footer).</li>
 * </ul>
 */
public final class SyncManifestBuilder {

    private SyncManifestBuilder() {
    }

    /** One validated manifest. */
    public static final class SyncManifest {
        private final List<String> required;
        private final List<String> optional;
        private final List<String> largeOnDemand;
        private final List<String> excluded;

        SyncManifest(List<String> required, List<String> optional,
                List<String> largeOnDemand, List<String> excluded) {
            this.required = required;
            this.optional = optional;
            this.largeOnDemand = largeOnDemand;
            this.excluded = excluded;
        }

        public List<String> getRequired() { return this.required; }
        public List<String> getOptional() { return this.optional; }
        public List<String> getLargeOnDemand() { return this.largeOnDemand; }
        public List<String> getExcluded() { return this.excluded; }

        /** The owned qf-sync-manifest v1 block. */
        public String render() {
            StringBuilder text = new StringBuilder();
            text.append("# qf-sync-manifest v1 (QuantumForge, Roadmap #98 draft slice)\n");
            text.append("# Selective result-sync INTENT - nothing downloads from this\n");
            text.append("# build. '*.ext' matches TOP-LEVEL files with that extension\n");
            text.append("# (recursive '**' refused by grammar). Sizes/checksums are\n");
            text.append("# UNKNOWN until first fetch - the manifest records intent, not\n");
            text.append("# facts; the checksum cache fills at sync time and is verified\n");
            text.append("# against parser prerequisites at parse time.\n");
            appendLine(text, "required", this.required);
            appendLine(text, "optional", this.optional);
            appendLine(text, "large_on_demand", this.largeOnDemand);
            appendLine(text, "excluded", this.excluded);
            return text.toString();
        }

        private static void appendLine(StringBuilder text, String key, List<String> values) {
            if (values.isEmpty()) {
                text.append("# ").append(key).append(" = (none declared)\n");
            } else {
                text.append(key).append(" = ").append(String.join(", ", values))
                        .append('\n');
            }
        }
    }

    /** Validates one manifest. Codes: SYNC_ENTRY/SYNC_DUPLICATE/SYNC_REQUIRED. */
    public static OperationResult<SyncManifest> validate(String requiredCsv,
            String optionalCsv, String largeCsv, String excludedCsv) {
        OperationResult<List<String>> required = parseList(requiredCsv);
        if (!required.isSuccess()) {
            return OperationResult.failed(required.getCode(), required.getMessage(), null);
        }
        OperationResult<List<String>> optional = parseList(optionalCsv);
        if (!optional.isSuccess()) {
            return OperationResult.failed(optional.getCode(), optional.getMessage(), null);
        }
        OperationResult<List<String>> large = parseList(largeCsv);
        if (!large.isSuccess()) {
            return OperationResult.failed(large.getCode(), large.getMessage(), null);
        }
        OperationResult<List<String>> excluded = parseList(excludedCsv);
        if (!excluded.isSuccess()) {
            return OperationResult.failed(excluded.getCode(), excluded.getMessage(), null);
        }
        if (required.getValue().orElseThrow().isEmpty()) {
            return OperationResult.failed("SYNC_REQUIRED",
                    "At least one REQUIRED entry is mandatory - a manifest that fetches "
                            + "nothing essential is ceremonial. (Parsers' prerequisites "
                            + "belong here.)",
                    null);
        }
        Set<String> seen = new LinkedHashSet<>();
        String[] roles = {"required", "optional", "large_on_demand", "excluded"};
        List<List<String>> lists = List.of(required.getValue().orElseThrow(),
                optional.getValue().orElseThrow(), large.getValue().orElseThrow(),
                excluded.getValue().orElseThrow());
        for (int role = 0; role < lists.size(); role++) {
            for (String entry : lists.get(role)) {
                if (!seen.add(entry)) {
                    return OperationResult.failed("SYNC_DUPLICATE",
                            "'" + entry + "' appears under more than one role (one of "
                                    + String.join("/", roles) + ") - a name must have "
                                    + "EXACTLY ONE role or the sync would resolve the "
                                    + "ambiguity silently.",
                            null);
                }
            }
        }
        return OperationResult.success("SYNC_OK", "Sync manifest draft validated.",
                new SyncManifest(required.getValue().orElseThrow(),
                        optional.getValue().orElseThrow(), large.getValue().orElseThrow(),
                        excluded.getValue().orElseThrow()));
    }

    /** Parses one csv list. Every entry must be grammar-clean and unique inside its list. */
    private static OperationResult<List<String>> parseList(String csv) {
        List<String> entries = new ArrayList<>();
        String text = csv == null ? "" : csv.trim();
        if (text.isEmpty()) {
            return OperationResult.success("SYNC_OK", "empty list", List.of());
        }
        Set<String> inList = new LinkedHashSet<>();
        for (String token : text.split(",", -1)) {
            String entry = token.trim();
            if (entry.startsWith("*.")) {
                String ext = entry.substring(2);
                if (!ext.matches("[A-Za-z0-9]{1,16}")) {
                    return OperationResult.failed("SYNC_ENTRY",
                            "wildcard entry '" + entry + "' is outside the owned shape "
                                    + "'*.ext' (1..16 alphanumerics, TOP-LEVEL match only - "
                                    + "recursive patterns are refused, not misinterpreted).",
                            null);
                }
            } else {
                if (entry.contains("*")) {
                    return OperationResult.failed("SYNC_ENTRY",
                            "'*' is only meaningful in the owned '*.ext' leading shape; '"
                                    + entry + "' would otherwise read as a literal name "
                                    + "that never exists - a silent-miss trap. Refused, "
                                    + "not reinterpreted.",
                            null);
                }
                if (entry.startsWith("/") || entry.contains("..") || entry.contains("//")
                        || entry.contains("\\") || entry.contains("\"")
                        || entry.contains("'") || entry.contains("$") || entry.contains("`")
                        || entry.contains(";") || entry.contains("|") || entry.isBlank()
                        || entry.chars().anyMatch(Character::isWhitespace)
                        || entry.length() > 200) {
                    return OperationResult.failed("SYNC_ENTRY",
                            "entry '" + entry + "' violates the relative-POSIX grammar (no "
                                    + "absolute paths, no '..', no whitespace/quotes/"
                                    + "separators, max 200 chars) - sync targets must be "
                                    + "literal and unambiguous.",
                            null);
                }
            }
            if (!inList.add(entry)) {
                return OperationResult.failed("SYNC_DUPLICATE",
                        "'" + entry + "' is listed twice in the same role.", null);
            }
            entries.add(entry);
        }
        return OperationResult.success("SYNC_OK", "parsed",
                List.copyOf(entries));
    }
}
