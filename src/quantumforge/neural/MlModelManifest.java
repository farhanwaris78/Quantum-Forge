/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.neural;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import quantumforge.atoms.element.ElementUtil;
import quantumforge.input.validation.ValidationIssue;
import quantumforge.input.validation.ValidationSeverity;

/**
 * Machine-learning model manifest (Roadmap #139) with a fail-closed validation
 * and a domain-of-applicability element gate (Roadmap #140, partial).
 *
 * <p>A manifest is a UTF-8 text file of {@code key: value} lines describing one
 * MLP artefact: {@code name}, {@code version}, {@code license}, {@code citation},
 * {@code cutoff_angstrom}, {@code species} (comma-separated element symbols),
 * and {@code sha256} (the 64-hex hash of the model file). Validation is static
 * and local: a manifest that cannot prove provenance (missing/ambiguous licence
 * or missing model hash) is a blocking finding, because a model whose origin is
 * unknown must never produce confident-looking energies. Unknown element
 * symbols and non-finite cutoffs are blocking; oversized cutoffs are warnings.</p>
 */
public final class MlModelManifest {

    /** Documentation anchor given to findings. */
    public static final String DOCS = "docs/ROADMAP.md (items 136-140)";

    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");
    private static final double CUTOFF_WARNING_ANGSTROM = 10.0;
    private static final Pattern KEY_VALUE = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\s*[:=]\\s*(.*)$");

    private final String name;
    private final String version;
    private final String license;
    private final String citation;
    private final double cutoffAngstrom;
    private final List<String> species;
    private final String sha256;

    private MlModelManifest(String name, String version, String license, String citation,
                            double cutoffAngstrom, List<String> species, String sha256) {
        this.name = name;
        this.version = version;
        this.license = license;
        this.citation = citation;
        this.cutoffAngstrom = cutoffAngstrom;
        this.species = List.copyOf(species);
        this.sha256 = sha256;
    }

    public String getName() { return this.name; }
    public String getVersion() { return this.version; }
    public String getLicense() { return this.license; }
    public String getCitation() { return this.citation; }
    public double getCutoffAngstrom() { return this.cutoffAngstrom; }
    public List<String> getSpecies() { return this.species; }
    public String getSha256() { return this.sha256; }

    /** Validation outcome: parsed fields (possibly partial) plus findings. */
    public static final class ManifestReport {
        private final MlModelManifest manifest;
        private final List<ValidationIssue> issues;
        private final List<String> unparsedLines;

        private ManifestReport(MlModelManifest manifest, List<ValidationIssue> issues,
                               List<String> unparsedLines) {
            this.manifest = manifest;
            this.issues = List.copyOf(issues);
            this.unparsedLines = List.copyOf(unparsedLines);
        }

        public MlModelManifest getManifest() { return this.manifest; }
        public List<ValidationIssue> getIssues() { return this.issues; }
        public List<String> getUnparsedLines() { return this.unparsedLines; }
        public long errorCount() {
            return this.issues.stream()
                    .filter(issue -> issue.getSeverity() == ValidationSeverity.ERROR).count();
        }
        public boolean isUsable() {
            return this.manifest != null && errorCount() == 0L;
        }
    }

    /**
     * Parses and validates a manifest file. Malformed/unreadable manifests never
     * throw: they produce a {@code ManifestReport} with a blocking issue.
     */
    public static ManifestReport parse(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return new ManifestReport(null, List.of(new ValidationIssue(
                    ValidationSeverity.ERROR, "ML_MANIFEST_MISSING",
                    "The model manifest file does not exist: " + file, DOCS)), List.of());
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return new ManifestReport(null, List.of(new ValidationIssue(
                    ValidationSeverity.ERROR, "ML_MANIFEST_UNREADABLE",
                    "Could not read the manifest: " + ex.getMessage(), DOCS)), List.of());
        }
        return parseLines(lines);
    }

    /** Parses and validates manifest content already in memory. */
    public static ManifestReport parseLines(List<String> lines) {
        Map<String, String> values = new LinkedHashMap<>();
        List<String> unparsed = new ArrayList<>();
        Set<String> recognized = Set.of("name", "version", "license", "licence",
                "citation", "cutoff_angstrom", "species", "sha256", "backend", "notes");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            java.util.regex.Matcher matcher = KEY_VALUE.matcher(trimmed);
            if (!matcher.matches()) {
                unparsed.add(trimmed);
                continue;
            }
            String key = matcher.group(1).toLowerCase(Locale.ROOT);
            String value = matcher.group(2).trim();
            if (!recognized.contains(key)) {
                unparsed.add(trimmed);
                continue;
            }
            if ("licence".equals(key)) {
                key = "license";
            }
            values.merge(key, value, (a, b) -> a + " " + b);
        }

        List<ValidationIssue> issues = new ArrayList<>();
        String name = values.getOrDefault("name", "");
        String version = values.getOrDefault("version", "");
        String license = values.getOrDefault("license", "");
        String citation = values.getOrDefault("citation", "");
        String cutoffText = values.getOrDefault("cutoff_angstrom", "");
        String speciesText = values.getOrDefault("species", "");
        String sha256 = values.getOrDefault("sha256", "");

        if (name.isBlank()) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, "ML_NAME_MISSING",
                    "The manifest has no 'name:' field.", DOCS));
        }
        if (version.isBlank()) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, "ML_VERSION_MISSING",
                    "The manifest has no 'version:' field; an unversioned model cannot be "
                            + "reproduced.", DOCS));
        }
        if (license.isBlank()) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, "ML_LICENSE_MISSING",
                    "The manifest has no 'license:' field; legal use/redistribution is "
                            + "unknown and must be proven before running the model.",
                    DOCS));
        } else if (license.equalsIgnoreCase("unknown") || license.equalsIgnoreCase("none")
                || license.equalsIgnoreCase("proprietary?")) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, "ML_LICENSE_AMBIGUOUS",
                    "License value '" + license + "' is not a license; state the actual terms "
                            + "(e.g. MIT, Apache-2.0, CC-BY-4.0, or an explicit proprietary "
                            + "agreement).", DOCS));
        }
        if (citation.isBlank()) {
            issues.add(new ValidationIssue(ValidationSeverity.WARNING, "ML_CITATION_MISSING",
                    "No 'citation:' field; results would not credit the model authors.",
                    DOCS));
        }

        double cutoff = Double.NaN;
        if (cutoffText.isBlank()) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, "ML_CUTOFF_MISSING",
                    "No 'cutoff_angstrom:' field; the interaction range is unknown.", DOCS));
        } else {
            try {
                cutoff = Double.parseDouble(cutoffText.replace('D', 'E').replace('d', 'E'));
            } catch (NumberFormatException ex) {
                issues.add(new ValidationIssue(ValidationSeverity.ERROR, "ML_CUTOFF_NONNUMERIC",
                        "cutoff_angstrom '" + cutoffText + "' is not a number.", DOCS));
            }
            if (Double.isFinite(cutoff)) {
                if (!(cutoff > 0.0)) {
                    issues.add(new ValidationIssue(ValidationSeverity.ERROR,
                            "ML_CUTOFF_NONPOSITIVE",
                            "cutoff_angstrom must be positive (got " + cutoff + ").", DOCS));
                } else if (cutoff > CUTOFF_WARNING_ANGSTROM) {
                    issues.add(new ValidationIssue(ValidationSeverity.WARNING,
                            "ML_CUTOFF_LARGE",
                            String.format(Locale.ROOT,
                                    "cutoff_angstrom %.4f exceeds %.1f Angstrom - verify the "
                                            + "model really has this interaction range.",
                                    cutoff, CUTOFF_WARNING_ANGSTROM), DOCS));
                }
            } else if (issues.stream().noneMatch(i -> i.getCode().equals("ML_CUTOFF_NONNUMERIC"))) {
                issues.add(new ValidationIssue(ValidationSeverity.ERROR, "ML_CUTOFF_NONFINITE",
                        "cutoff_angstrom must be finite (got " + cutoffText + ").", DOCS));
            }
        }

        List<String> species = new ArrayList<>();
        if (speciesText.isBlank()) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, "ML_SPECIES_MISSING",
                    "No 'species:' field; the supported chemistry is unknown.", DOCS));
        } else {
            Set<String> unique = new LinkedHashSet<>();
            for (String token : speciesText.split(",")) {
                String symbol = token.trim();
                if (symbol.isEmpty()) {
                    continue;
                }
                if (ElementUtil.getAtomicNumber(symbol) <= 0) {
                    issues.add(new ValidationIssue(ValidationSeverity.ERROR,
                            "ML_SPECIES_UNKNOWN",
                            "Species token '" + symbol + "' is not a known element symbol.",
                            DOCS));
                } else {
                    unique.add(symbol);
                }
            }
            species.addAll(unique);
            if (species.isEmpty()) {
                issues.add(new ValidationIssue(ValidationSeverity.ERROR, "ML_SPECIES_EMPTY",
                        "The species list contains no valid element symbol.", DOCS));
            }
        }

        if (sha256.isBlank() || !SHA256.matcher(sha256).matches()) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, "ML_HASH_MISSING",
                    "No valid 'sha256:' (64 hex characters) model-file hash; bit-for-bit "
                            + "provenance of the weights is unverifiable.",
                    DOCS));
        }

        MlModelManifest manifest = new MlModelManifest(name, version, license, citation,
                cutoff, species, sha256);
        return new ManifestReport(manifest, issues, unparsed);
    }

    /**
     * Domain-of-applicability gate (Roadmap #140, element level): returns, sorted,
     * the project elements that the manifest species list does not cover. Covering
     * an element is necessary, not sufficient - coordination/density/descriptor
     * distance gates remain future work and are not implied by an empty result.
     */
    public List<String> elementsOutsideDomain(Iterable<String> projectElements) {
        Set<String> covered = new LinkedHashSet<>(this.species);
        Set<String> missing = new LinkedHashSet<>();
        if (projectElements != null) {
            for (String element : projectElements) {
                if (element != null && !element.isBlank() && !covered.contains(element.trim())) {
                    missing.add(element.trim());
                }
            }
        }
        List<String> sorted = new ArrayList<>(missing);
        sorted.sort(String::compareTo);
        return sorted;
    }
}
