/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.hpc;

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

import quantumforge.input.validation.ValidationIssue;
import quantumforge.input.validation.ValidationSeverity;

/**
 * Static, deterministic validation of a {@link SiteProfile} YAML-like file
 * (Roadmap #94) plus explicit honesty checks for the Apptainer/Singularity
 * container block (Roadmap #103).
 *
 * <p>The validator connects nothing and probes nothing: every finding is
 * derived from the file text. The scheduler id must resolve through the
 * typed-adapter registry ({@code slurm}, {@code pbs}, {@code pjm},
 * {@code sge}); site vernacular ({@code torque}, {@code uge}, {@code ge}) is
 * canonicalized by {@link SiteProfile#canonicalScheduler(String)} before the
 * registry probe, so this class keeps NO second scheduler-name list of its
 * own. Staging/scratch roots and modules are warned about rather than
 * guessed; and a container image without a pinned {@code sha256:} digest is a
 * reproducibility warning, never silently accepted, because tag-only images are
 * mutable. The host-MPI ABI constraint of hybrid container execution is stated
 * explicitly whenever a container block is present.</p>
 */
public final class SiteProfileValidator {

    /** Documentation anchors given to findings. */
    public static final String DOCS_ROADMAP =
            "docs/FUTURE_ROADMAP.md (items 94 and 103)";
    public static final String DOCS_EXAMPLE =
            "packaging/sites/example-slurm.yaml";

    // Deliberately NO private scheduler-name list here: the canonical set is
    // owned by SchedulerAdapters and the vernacular alias table by
    // SiteProfile.canonicalScheduler. A second list is exactly the drift that
    // previously sidelined pjm.
    private static final Set<String> KNOWN_LAUNCHERS =
            Set.of("srun", "mpirun", "mpiexec", "mpiexec.hydra", "jsrun", "aprun", "orterun");
    private static final Set<String> KNOWN_KEYS = Set.of(
            "id", "scheduler", "staging_root", "scratch_root", "partition",
            "default_partition", "account", "default_account", "walltime", "nodes",
            "ntasks", "cpus_per_task", "mpi_launcher", "max_array_size",
            "container_image", "container_digest", "container_bind", "container_mpi_hybrid");
    private static final Set<String> REPEATABLE_KEYS = Set.of("module", "env", "container_bind");
    private static final Pattern SHA256_DIGEST = Pattern.compile("sha256:[0-9a-fA-F]{64}");

    private SiteProfileValidator() {
        // Utility
    }

    /** Validation outcome: the parsed profile (null when unreadable) plus findings. */
    public static final class SiteProfileReport {
        private final SiteProfile profile;
        private final List<ValidationIssue> issues;
        private final Map<String, List<String>> rawKeys;

        private SiteProfileReport(SiteProfile profile, List<ValidationIssue> issues,
                                  Map<String, List<String>> rawKeys) {
            this.profile = profile;
            this.issues = List.copyOf(issues);
            this.rawKeys = new LinkedHashMap<>(rawKeys);
        }

        /** The typed profile, or null when the file could not be loaded at all. */
        public SiteProfile getProfile() { return this.profile; }
        public List<ValidationIssue> getIssues() { return this.issues; }
        public Map<String, List<String>> getRawKeys() { return this.rawKeys; }
        public List<String> rawValues(String key) {
            return this.rawKeys.getOrDefault(key, List.of());
        }

        public long errorCount() {
            return this.issues.stream()
                    .filter(issue -> issue.getSeverity() == ValidationSeverity.ERROR).count();
        }

        public List<String> containerValues(String keySuffix) {
            return rawValues("container_" + keySuffix);
        }
    }

    /**
     * Reads and validates the profile file. This method never throws for a
     * malformed/unreadable profile: such files produce a report whose only
     * blocking finding is {@code SITE_UNREADABLE}.
     */
    public static SiteProfileReport validate(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return new SiteProfileReport(null, List.of(
                    new ValidationIssue(ValidationSeverity.ERROR, "SITE_MISSING",
                            "The site profile file does not exist or is not a regular file: "
                                    + file, DOCS_EXAMPLE)), Map.of());
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return new SiteProfileReport(null, List.of(
                    new ValidationIssue(ValidationSeverity.ERROR, "SITE_UNREADABLE",
                            "Could not read the site profile: " + ex.getMessage(),
                            DOCS_EXAMPLE)), Map.of());
        }

        Map<String, List<String>> rawKeys = scanRawKeys(lines);
        List<ValidationIssue> issues = new ArrayList<>();

        SiteProfile profile = null;
        try {
            profile = SiteProfile.load(file);
        } catch (IOException | RuntimeException ex) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, "SITE_UNREADABLE",
                    "The profile could not be parsed (numeric fields must be plain "
                            + "integers, one 'key: value' pair per line): " + ex.getMessage(),
                    DOCS_EXAMPLE));
        }

        if (profile != null) {
            validateTyped(profile, issues);
        }
        validateUnknownKeys(rawKeys, issues);
        validateContainerBlock(rawKeys, issues);
        validateEnvironmentVariables(rawKeys, issues);
        return new SiteProfileReport(profile, issues, rawKeys);
    }

    /** Raw lowercased-key scan; repeated keys (module/env/container_bind) keep order. */
    private static Map<String, List<String>> scanRawKeys(List<String> lines) {
        Map<String, List<String>> raw = new LinkedHashMap<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int colon = trimmed.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = trimmed.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = trimmed.substring(colon + 1).trim();
            if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            raw.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return raw;
    }

    private static void validateTyped(SiteProfile profile, List<ValidationIssue> issues) {
        if (profile.getId().isBlank()) {
            issues.add(new ValidationIssue(ValidationSeverity.WARNING, "SITE_ID_EMPTY",
                    "The profile has no id; remote job records should name their site.",
                    DOCS_ROADMAP));
        }
        // The profile already canonicalized vernacular aliases at load
        // (torque -> pbs, uge/ge -> sge), so the honest check is the registry
        // probe itself - the single owner of "which schedulers exist".
        if (SchedulerAdapters.forName(profile.getScheduler()).isEmpty()) {
            issues.add(new ValidationIssue(ValidationSeverity.ERROR, "SITE_SCHEDULER_UNKNOWN",
                    "Scheduler '" + profile.getScheduler() + "' has no typed adapter "
                            + "(supported: " + SchedulerAdapters.supportedNames()
                            + "; site vernacular torque/uge/ge is canonicalized to pbs/sge "
                            + "at load); generated scripts would otherwise be free-form guesses.",
                    DOCS_EXAMPLE));
        }
        if (profile.getStagingRoot().isEmpty()) {
            issues.add(new ValidationIssue(ValidationSeverity.WARNING, "SITE_STAGING_EMPTY",
                    "No staging_root: remote result sync would have no canonical upload root.",
                    DOCS_ROADMAP));
        }
        if (profile.getScratchRoot().isEmpty()) {
            issues.add(new ValidationIssue(ValidationSeverity.WARNING, "SITE_SCRATCH_EMPTY",
                    "No scratch_root: QE jobs would default to home-directory I/O, which is "
                            + "slow on clusters and can hit quota limits.",
                    DOCS_ROADMAP));
        }
        if (!KNOWN_LAUNCHERS.contains(profile.getMpiLauncher())) {
            issues.add(new ValidationIssue(ValidationSeverity.WARNING, "SITE_LAUNCHER_UNKNOWN",
                    "MPI launcher '" + profile.getMpiLauncher() + "' is not one of the common "
                            + "launchers srun/mpirun/mpiexec/jsrun; verify the site's launcher "
                            + "before submitting.",
                    DOCS_EXAMPLE));
        }
        if (profile.getModules().isEmpty()) {
            issues.add(new ValidationIssue(ValidationSeverity.WARNING, "SITE_MODULES_EMPTY",
                    "No 'module:' lines: the generated script cannot load a QE environment; "
                            + "confirm binaries come from another source.",
                    DOCS_EXAMPLE));
        }
    }

    private static void validateUnknownKeys(Map<String, List<String>> rawKeys,
                                            List<ValidationIssue> issues) {
        for (String key : rawKeys.keySet()) {
            if (!KNOWN_KEYS.contains(key) && !REPEATABLE_KEYS.contains(key)) {
                issues.add(new ValidationIssue(ValidationSeverity.WARNING, "SITE_UNKNOWN_KEY",
                        "Unknown profile key '" + key + "' - a misspelled key is silently "
                                + "ignored by the loader; known keys include scheduler, "
                                + "staging_root, scratch_root, partition, account, walltime, "
                                + "nodes, ntasks, cpus_per_task, mpi_launcher, module, env and "
                                + "the container_* block.",
                        DOCS_EXAMPLE));
            }
        }
    }

    /**
     * Container support honesty (Roadmap #103): a tag-only image is a mutable
     * reference, so a pinned {@code container_digest: sha256:<64 hex>} is required
     * for reproducibility; binds must be absolute; and the host-MPI ABI constraint
     * of hybrid MPI is stated whenever any container block exists.
     */
    private static void validateContainerBlock(Map<String, List<String>> rawKeys,
                                               List<ValidationIssue> issues) {
        Set<String> containerKeys = new LinkedHashSet<>(rawKeys.keySet());
        containerKeys.removeIf(key -> !key.startsWith("container_"));
        if (containerKeys.isEmpty()) {
            return;
        }
        List<String> images = rawKeys.getOrDefault("container_image", List.of());
        List<String> digests = rawKeys.getOrDefault("container_digest", List.of());
        List<String> binds = rawKeys.getOrDefault("container_bind", List.of());

        if (images.isEmpty() || images.get(0).isBlank()) {
            issues.add(new ValidationIssue(ValidationSeverity.WARNING,
                    "SITE_CONTAINER_INCOMPLETE",
                    "A container_* block exists but declares no container_image; remove the "
                            + "block or name the image explicitly.",
                    DOCS_ROADMAP));
            return;
        }
        if (digests.isEmpty() || !SHA256_DIGEST.matcher(digests.get(0)).matches()) {
            issues.add(new ValidationIssue(ValidationSeverity.WARNING,
                    "SITE_CONTAINER_DIGEST_MISSING",
                    "The container image is not pinned to an immutable 'sha256:<64 hex>' "
                            + "digest; tag-only images are mutable, so jobs are not bit-for-bit "
                            + "reproducible against this profile.",
                    DOCS_ROADMAP));
        }
        for (String bind : binds) {
            if (bind.isBlank()) {
                continue;
            }
            String source = bind.contains(":") ? bind.substring(0, bind.indexOf(':')) : bind;
            if (!source.startsWith("/")) {
                issues.add(new ValidationIssue(ValidationSeverity.ERROR,
                        "SITE_CONTAINER_BIND_RELATIVE",
                        "Container bind '" + bind + "' is not an absolute host path; relative "
                                + "binds resolve differently per job directory.",
                        DOCS_ROADMAP));
            } else if ("/".equals(source) || "/home".equals(source)
                    || "/scratch".equals(source) || "/tmp".equals(source)) {
                issues.add(new ValidationIssue(ValidationSeverity.WARNING,
                        "SITE_CONTAINER_BIND_BROAD",
                        "Container bind '" + bind + "' exposes a whole top-level tree; bind the "
                                + "narrowest project/scratch subtree instead.",
                        DOCS_ROADMAP));
            }
        }
        issues.add(new ValidationIssue(ValidationSeverity.INFO, "SITE_CONTAINER_MPI_ABI",
                "Container execution with hybrid MPI requires ABI compatibility between the "
                        + "image MPI and the host interconnect libraries; this profile cannot "
                        + "prove that statically - run the site sanity job first.",
                DOCS_ROADMAP));
    }

    private static void validateEnvironmentVariables(Map<String, List<String>> rawKeys,
                                                     List<ValidationIssue> issues) {
        for (Map.Entry<String, List<String>> entry : rawKeys.entrySet()) {
            for (String value : entry.getValue()) {
                if (value.contains("$USER") || value.contains("${USER}")) {
                    issues.add(new ValidationIssue(ValidationSeverity.INFO, "SITE_ENV_USER",
                            "Key '" + entry.getKey() + "' references $USER; expansion happens "
                                    + "in the scheduler shell, so verify it on the login node.",
                            DOCS_EXAMPLE));
                    return; // one informational note is enough
                }
            }
        }
    }
}
