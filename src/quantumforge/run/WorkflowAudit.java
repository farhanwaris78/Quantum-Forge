/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #104 (audit slice): a strictly READ-ONLY structural audit of an
 * exported workflow script ({@code .quantumforge.workflow.sh} or any peer
 * artifact written by {@link WorkflowExporter}). The export's contract is
 * "usable when QuantumForge is unavailable" - so the audit answers, from the
 * artifact's own text:
 *
 * <ul>
 *   <li>is it recognizable as a QuantumForge export at all (fail closed on
 *       random shell text - auditing a stranger file would fabricate trust);</li>
 *   <li>the ordered stage census with OPTIONAL flags and per-stage command
 *       presence, incl. which REQUIRED stages would abort at runtime
 *       ("No command recorded" -&gt; {@code exit 2});</li>
 *   <li>safety/identity markers: shebang, {@code set -euo pipefail}, generator
 *       line, workflow-type line, SLURM block presence; and</li>
 *   <li>a SYNC verdict against the project's CURRENT stage list (subsequence
 *       arithmetic, order-sensitive): IN_SYNC / BEHIND_CONFIG / AHEAD_OF_CONFIG
 *       / DIVERGED / NOT_COMPARABLE - naming the differing stage ids.</li>
 * </ul>
 *
 * <p>Nothing is rewritten, refreshed, or re-exported: the audit READS. To
 * refresh a stale artifact, the user re-runs the export deliberately. Codes:
 * WORKFLOW_FILE / WORKFLOW_SHAPE / WORKFLOW_OK.</p>
 */
public final class WorkflowAudit {

    /** Bounded read: audit never scans more than this many bytes. */
    public static final long MAX_FILE_BYTES = 1_000_000L;
    /** Differing stage ids listed at most this many per side; rest counted. */
    public static final int MAX_LISTED = 12;

    /** Sync verdict between the artifact's stage list and the current config. */
    public enum SyncVerdict {
        IN_SYNC, BEHIND_CONFIG, AHEAD_OF_CONFIG, DIVERGED, NOT_COMPARABLE
    }

    /** One exported stage, structurally reconstructed from the script text. */
    public static final class StageVerdict {
        private final int index;
        private final String id;
        private final String label;
        private final boolean optional;
        private final boolean hasCommand;      // false when the "No command recorded" sentinel is present
        private final boolean abortsWhenEmpty; // required stage without command -> exit 2 at runtime

        StageVerdict(int index, String id, String label, boolean optional,
                     boolean hasCommand, boolean abortsWhenEmpty) {
            this.index = index;
            this.id = id;
            this.label = label;
            this.optional = optional;
            this.hasCommand = hasCommand;
            this.abortsWhenEmpty = abortsWhenEmpty;
        }

        public int getIndex() { return this.index; }
        public String getId() { return this.id; }
        public String getLabel() { return this.label; }
        public boolean isOptional() { return this.optional; }
        public boolean hasCommand() { return this.hasCommand; }
        public boolean isAbortsWhenEmpty() { return this.abortsWhenEmpty; }
    }

    /** Whole-artifact audit value. */
    public static final class Audit {
        private final Path file;
        private final boolean shebang;
        private final boolean setOptions;      // set -euo pipefail
        private final boolean slurmBlock;
        private final String generatorLine;    // "" when absent
        private final String workflowType;     // "" when absent
        private final List<StageVerdict> stages;

        Audit(Path file, boolean shebang, boolean setOptions, boolean slurmBlock,
              String generatorLine, String workflowType, List<StageVerdict> stages) {
            this.file = file;
            this.shebang = shebang;
            this.setOptions = setOptions;
            this.slurmBlock = slurmBlock;
            this.generatorLine = generatorLine == null ? "" : generatorLine;
            this.workflowType = workflowType == null ? "" : workflowType;
            this.stages = List.copyOf(stages);
        }

        public Path getFile() { return this.file; }
        public boolean hasShebang() { return this.shebang; }
        public boolean hasSetOptions() { return this.setOptions; }
        public boolean hasSlurmBlock() { return this.slurmBlock; }
        public String getGeneratorLine() { return this.generatorLine; }
        public String getWorkflowType() { return this.workflowType; }
        public List<StageVerdict> getStages() { return this.stages; }
        public List<String> stageIds() {
            List<String> ids = new ArrayList<>();
            for (StageVerdict stage : this.stages) {
                ids.add(stage.getId());
            }
            return ids;
        }
        public List<String> abortStageIds() {
            List<String> ids = new ArrayList<>();
            for (StageVerdict stage : this.stages) {
                if (stage.isAbortsWhenEmpty()) {
                    ids.add(stage.getId());
                }
            }
            return ids;
        }
    }

    private static final Pattern STAGE_PATTERN =
            Pattern.compile("# Stage (\\d+): (.*?)( \\(optional\\))?$");
    private static final Pattern STAGE_ID_PATTERN =
            Pattern.compile("echo \"==> ([A-Za-z0-9._-]+)\"$");
    private static final Pattern GENERATED_PATTERN =
            Pattern.compile("# Generated by (.+)$");
    private static final Pattern WORKFLOW_PATTERN =
            Pattern.compile("# Workflow: ([A-Z_]+)$");

    private WorkflowAudit() {
        // Utility.
    }

    /** Audit {@code file} without modifying it (or anything else). */
    public static OperationResult<Audit> audit(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return OperationResult.failed("WORKFLOW_FILE",
                    "No such workflow artifact - audit needs an exported script.", null);
        }
        final long size;
        try {
            size = Files.size(file);
        } catch (IOException ex) {
            return OperationResult.failed("WORKFLOW_FILE",
                    "Could not stat the workflow artifact: " + ex.getMessage(), ex);
        }
        if (size > MAX_FILE_BYTES) {
            return OperationResult.failed("WORKFLOW_FILE",
                    "Workflow artifact is " + size + " bytes, above the bounded-read cap of "
                            + MAX_FILE_BYTES + " - refusing an unbounded scan.", null);
        }
        final List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return OperationResult.failed("WORKFLOW_FILE",
                    "Could not read the workflow artifact: " + ex.getMessage(), ex);
        }

        boolean shebang = !lines.isEmpty() && lines.get(0).startsWith("#!");
        boolean strict = false;
        boolean slurm = false;
        String generator = "";
        String workflowType = "";
        for (String line : lines) {
            if (line.equals("set -euo pipefail")) {
                strict = true;
            } else if (line.startsWith("#SBATCH ")) {
                slurm = true;
            } else {
                Matcher generated = GENERATED_PATTERN.matcher(line);
                if (generated.find()) {
                    generator = generated.group(1);
                }
                Matcher workflow = WORKFLOW_PATTERN.matcher(line);
                if (workflow.find()) {
                    workflowType = workflow.group(1);
                }
            }
        }

        List<StageVerdict> stages = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Matcher stageM = STAGE_PATTERN.matcher(lines.get(i));
            if (!stageM.find()) {
                continue;
            }
            int index = Integer.parseInt(stageM.group(1));
            String label = stageM.group(2);
            boolean optional = stageM.group(3) != null;
            String id = "";
            boolean noCommand = false;
            boolean exitSentinel = false;
            int end = Math.min(lines.size(), i + 32);  // bounded look-ahead per stage
            for (int j = i + 1; j < end; j++) {
                if (STAGE_PATTERN.matcher(lines.get(j)).find()) {
                    break;
                }
                Matcher idM = STAGE_ID_PATTERN.matcher(lines.get(j));
                if (idM.find()) {
                    id = idM.group(1);
                } else if (lines.get(j).contains("No command recorded for stage")) {
                    noCommand = true;
                } else if (lines.get(j).equals("exit 2")) {
                    exitSentinel = true;
                }
            }
            boolean hasCommand = !noCommand;
            stages.add(new StageVerdict(index, id, label, optional, hasCommand,
                    exitSentinel && !optional));
        }

        if (stages.isEmpty() && !shebang && generator.isBlank() && workflowType.isBlank()) {
            return OperationResult.failed("WORKFLOW_SHAPE",
                    "No shebang, generator line, workflow-type line, or stage markers - "
                            + "this does not look like a QuantumForge export artifact, and "
                            + "auditing a stranger file would fabricate trust.", null);
        }
        return OperationResult.success("WORKFLOW_OK",
                "Audited " + stages.size() + " exported stage(s).",
                new Audit(file, shebang, strict, slurm, generator, workflowType, stages));
    }

    /**
     * Sync the artifact's stage ids against the CURRENT configuration's ids.
     * Order-sensitive subsequence arithmetic; {@code null} expected ids means
     * the current DAG could not be rebuilt honestly - NOT_COMPARABLE, never a
     * fake IN_SYNC.
     */
    public static SyncVerdict sync(List<String> artifactIds, List<String> expectedIds) {
        if (expectedIds == null) {
            return SyncVerdict.NOT_COMPARABLE;
        }
        List<String> artifact = artifactIds == null ? List.of() : artifactIds;
        if (artifact.equals(expectedIds)) {
            return SyncVerdict.IN_SYNC;
        }
        boolean artifactIsSubsequence = isSubsequence(artifact, expectedIds);
        boolean expectedIsSubsequence = isSubsequence(expectedIds, artifact);
        if (artifactIsSubsequence && !expectedIsSubsequence) {
            return SyncVerdict.BEHIND_CONFIG;   // current config added stages the artifact lacks
        }
        if (expectedIsSubsequence && !artifactIsSubsequence) {
            return SyncVerdict.AHEAD_OF_CONFIG; // artifact carries stages the config dropped
        }
        return SyncVerdict.DIVERGED;            // reordered or mutually exclusive members
    }

    /** Stages in {@code expected} that the artifact lacks (named, order kept). */
    public static List<String> missingFromArtifact(List<String> artifactIds,
                                                   List<String> expectedIds) {
        List<String> missing = new ArrayList<>();
        if (expectedIds == null) {
            return missing;
        }
        List<String> artifact = artifactIds == null ? List.of() : artifactIds;
        for (String id : expectedIds) {
            if (!artifact.contains(id)) {
                missing.add(id);
            }
        }
        return missing;
    }

    /** Stages in the artifact that the current configuration no longer has. */
    public static List<String> extraInArtifact(List<String> artifactIds,
                                               List<String> expectedIds) {
        List<String> extra = new ArrayList<>();
        if (expectedIds == null) {
            return artifactIds == null ? extra : new ArrayList<>(artifactIds);
        }
        for (String id : artifactIds == null ? List.<String>of() : artifactIds) {
            if (!expectedIds.contains(id)) {
                extra.add(id);
            }
        }
        return extra;
    }

    private static boolean isSubsequence(List<String> needle, List<String> haystack) {
        int i = 0;
        for (String item : haystack) {
            if (i < needle.size() && needle.get(i).equals(item)) {
                i++;
            }
        }
        return i == needle.size();
    }
}
