/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import quantumforge.project.Project;

/**
 * Typed Quantum ESPRESSO stage graph (SCF → NSCF/bands → post-processing).
 *
 * <p>Stages declare artifact requirements so a later resume can skip completed
 * predecessors when their outputs already exist. This models the DAG; actual
 * resume decisions still depend on file presence on disk.</p>
 */
public final class QECommandDag {

    private final RunningType runningType;
    private final List<QECommandStage> stages;

    private QECommandDag(RunningType runningType, List<QECommandStage> stages) {
        this.runningType = Objects.requireNonNull(runningType, "runningType");
        this.stages = Collections.unmodifiableList(new ArrayList<>(stages));
    }

    public RunningType getRunningType() {
        return this.runningType;
    }

    public List<QECommandStage> getStages() {
        return this.stages;
    }

    public int size() {
        return this.stages.size();
    }

    /**
     * Build the DAG for a workflow using the same stage count/order as
     * {@link RunningType#getCommandList(String, int)}.
     */
    public static QECommandDag build(RunningType type, String inputFileName, int numProc) {
        Objects.requireNonNull(type, "type");
        List<String[]> commands = type.getCommandList(inputFileName, numProc);
        if (commands == null) {
            commands = List.of();
        }
        List<QECommandStage> stages = new ArrayList<>();
        switch (type) {
        case SCF:
            stages.add(stage("scf", QECommandStage.Kind.PW_SCF, "Self-consistent field",
                    List.of(), List.of("charge-density", "wavefunctions"),
                    first(commands, 0), false));
            break;
        case OPTIMIZ:
            stages.add(stage("relax", QECommandStage.Kind.PW_RELAX, "Ionic/cell relaxation",
                    List.of(), List.of("charge-density", "wavefunctions", "final-geometry"),
                    first(commands, 0), false));
            break;
        case MD:
            stages.add(stage("md", QECommandStage.Kind.PW_MD, "Molecular dynamics",
                    List.of(), List.of("charge-density", "trajectory"),
                    first(commands, 0), false));
            break;
        case DOS:
            stages.add(stage("scf", QECommandStage.Kind.PW_SCF, "SCF",
                    List.of(), List.of("charge-density", "wavefunctions"),
                    first(commands, 0), false));
            stages.add(stage("nscf", QECommandStage.Kind.PW_NSCF, "Non-SCF dense mesh",
                    List.of("charge-density"), List.of("nscf-wavefunctions"),
                    first(commands, 1), false));
            stages.add(stage("dos", QECommandStage.Kind.DOS, "Total DOS (dos.x)",
                    List.of("nscf-wavefunctions"), List.of("dos-data"),
                    first(commands, 2), false));
            stages.add(stage("projwfc", QECommandStage.Kind.PROJWFC, "Projected DOS (projwfc.x)",
                    List.of("nscf-wavefunctions"), List.of("pdos-data"),
                    first(commands, 3), true));
            break;
        case BAND:
            stages.add(stage("scf", QECommandStage.Kind.PW_SCF, "SCF",
                    List.of(), List.of("charge-density", "wavefunctions"),
                    first(commands, 0), false));
            stages.add(stage("bands-pw", QECommandStage.Kind.PW_BANDS, "Band-structure NSCF",
                    List.of("charge-density"), List.of("band-wavefunctions"),
                    first(commands, 1), false));
            stages.add(stage("bands-up", QECommandStage.Kind.BANDS_X, "bands.x (spin up / total)",
                    List.of("band-wavefunctions"), List.of("bands-dat"),
                    first(commands, 2), false));
            stages.add(stage("bands-down", QECommandStage.Kind.BANDS_X, "bands.x (spin down)",
                    List.of("band-wavefunctions"), List.of("bands-dat-down"),
                    first(commands, 3), true));
            break;
        case NEB:
            stages.add(stage("scf", QECommandStage.Kind.PW_SCF, "Optional SCF precursor",
                    List.of(), List.of("charge-density", "wavefunctions"),
                    first(commands, 0), true));
            stages.add(stage("neb", QECommandStage.Kind.NEB_X, "NEB path optimization (neb.x)",
                    List.of(), List.of("neb-path", "neb-barrier"),
                    first(commands, 1), false));
            break;
        case PHONON:
            stages.add(stage("scf", QECommandStage.Kind.PW_SCF, "SCF",
                    List.of(), List.of("charge-density", "wavefunctions"),
                    first(commands, 0), false));
            stages.add(stage("ph", QECommandStage.Kind.PH_X, "DFPT phonons (ph.x)",
                    List.of("charge-density"), List.of("dynamical-matrices"),
                    first(commands, 1), false));
            stages.add(stage("q2r", QECommandStage.Kind.Q2R, "Fourier transform (q2r.x)",
                    List.of("dynamical-matrices"), List.of("force-constants"),
                    first(commands, 2), false));
            stages.add(stage("matdyn", QECommandStage.Kind.MATDYN, "Dispersion/DOS (matdyn.x)",
                    List.of("force-constants"), List.of("phonon-bands", "phonon-dos"),
                    first(commands, 3), false));
            break;
        case CONVERGE:
            throw new UnsupportedOperationException(
                    "Convergence DAG requires a configured parameter sweep and is not implemented.");
        default:
            break;
        }
        validateDependencies(stages);
        return new QECommandDag(type, stages);
    }

    public static QECommandDag build(Project project, RunningType type, int numProc) {
        String input = project == null ? "espresso.in" : project.getInpFileName();
        return build(type, input, numProc);
    }

    /**
     * Return stages that still need to run given a set of already-available artifact tags.
     */
    public List<QECommandStage> remaining(Set<String> availableArtifacts) {
        Set<String> have = new LinkedHashSet<>(
                availableArtifacts == null ? Set.of() : availableArtifacts);
        List<QECommandStage> todo = new ArrayList<>();
        for (QECommandStage stage : this.stages) {
            boolean satisfied = have.containsAll(stage.getRequires());
            boolean alreadyDone = have.containsAll(stage.getProduces()) && !stage.getProduces().isEmpty();
            if (alreadyDone) {
                continue;
            }
            if (!satisfied) {
                if (stage.isOptional()) {
                    continue;
                }
                // Required stage missing prerequisites: still schedule it so the runner fails loudly.
                todo.add(stage);
                continue;
            }
            todo.add(stage);
            have.addAll(stage.getProduces());
        }
        return Collections.unmodifiableList(todo);
    }

    public Map<String, List<String>> dependencyMap() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (QECommandStage stage : this.stages) {
            map.put(stage.getId(), stage.getRequires());
        }
        return Collections.unmodifiableMap(map);
    }

    public String describe() {
        StringBuilder out = new StringBuilder();
        out.append("QECommandDag ").append(this.runningType.name()).append(" stages=")
                .append(this.stages.size()).append('\n');
        for (QECommandStage stage : this.stages) {
            out.append("  - ").append(stage.getId())
                    .append(" (").append(stage.getKind()).append(')')
                    .append(stage.isOptional() ? " optional" : "")
                    .append(" requires=").append(stage.getRequires())
                    .append(" produces=").append(stage.getProduces())
                    .append('\n');
        }
        return out.toString();
    }

    private static QECommandStage stage(String id, QECommandStage.Kind kind, String label,
                                        List<String> requires, List<String> produces,
                                        String[] command, boolean optional) {
        return new QECommandStage(id, kind, label, requires, produces, command, optional);
    }

    private static String[] first(List<String[]> commands, int index) {
        if (commands == null || index < 0 || index >= commands.size()) {
            return new String[0];
        }
        String[] command = commands.get(index);
        return command == null ? new String[0] : command;
    }

    private static void validateDependencies(List<QECommandStage> stages) {
        Set<String> produced = new LinkedHashSet<>();
        for (QECommandStage stage : stages) {
            for (String req : stage.getRequires()) {
                if (!produced.contains(req) && !stage.isOptional()) {
                    // Soft validation only: optional stages may require artifacts that
                    // appear later only after non-optional predecessors. Required stages
                    // must see prior producers.
                    boolean ok = produced.contains(req);
                    if (!ok) {
                        // Allow forward references only for optional stages.
                        // For required stages this indicates a modelling error.
                        throw new IllegalStateException("Stage " + stage.getId()
                                + " requires '" + req + "' before it is produced in the DAG");
                    }
                }
            }
            produced.addAll(stage.getProduces());
        }
    }

    public static String artifactTagForLog(String logFileName) {
        if (logFileName == null) {
            return "";
        }
        String name = logFileName.toLowerCase(Locale.ROOT);
        if (name.contains("nscf")) {
            return "nscf-wavefunctions";
        }
        if (name.contains("band")) {
            return "band-wavefunctions";
        }
        if (name.contains("dos")) {
            return "dos-data";
        }
        if (name.contains("proj")) {
            return "pdos-data";
        }
        if (name.contains("scf") || name.contains("espresso.log")) {
            return "charge-density";
        }
        return "";
    }
}
