/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import quantumforge.com.path.QEPath;
import quantumforge.input.QEInput;
import quantumforge.input.validation.QEInputValidator;
import quantumforge.input.validation.ValidationIssue;
import quantumforge.input.validation.ValidationSeverity;
import quantumforge.project.Project;

/**
 * Extended dry-run checks beyond pure input semantics: binaries, directories,
 * free disk space, and command DAG integrity.
 */
public final class DryRunPreflight {

    private static final String DOC = "https://www.quantum-espresso.org/Doc/INPUT_PW.html";

    public static final class Report {
        private final List<ValidationIssue> issues;
        private final QECommandDag dag;
        private final QEExecutableProfile profile;

        public Report(List<ValidationIssue> issues, QECommandDag dag, QEExecutableProfile profile) {
            this.issues = List.copyOf(issues);
            this.dag = dag;
            this.profile = profile;
        }

        public List<ValidationIssue> getIssues() { return this.issues; }
        public QECommandDag getDag() { return this.dag; }
        public QEExecutableProfile getProfile() { return this.profile; }
        public boolean hasErrors() { return QEInputValidator.hasErrors(this.issues); }
    }

    private DryRunPreflight() {
        // Utility.
    }

    public static Report run(Project project, RunningType type, int numProcesses) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (project == null) {
            issues.add(issue(ValidationSeverity.ERROR, "PROJECT_NULL",
                    "No project was supplied for dry-run."));
            return new Report(issues, null, QEExecutableProfile.probeConfigured());
        }

        RunningType type2 = type == null ? RunningType.SCF : type;
        QEExecutableProfile profile = QEExecutableProfile.probeConfigured();
        if (!profile.hasCorePw()) {
            issues.add(issue(ValidationSeverity.ERROR, "PW_MISSING",
                    "pw.x was not found in the configured QE bin directory or PATH. "
                            + "Set Menu → Path to the directory containing pw.x."));
        } else if (profile.getVersion() != null && !profile.getVersion().isBlank()) {
            issues.add(issue(ValidationSeverity.INFO, "PW_VERSION",
                    "Detected pw.x version " + profile.getVersion() + "."));
        }
        for (String diagnostic : profile.getDiagnostics()) {
            issues.add(issue(ValidationSeverity.WARNING, "QE_PROFILE", diagnostic));
        }

        QEInput input = new FXQEInputFactory(type2).getQEInput(project);
        if (input == null) {
            issues.add(issue(ValidationSeverity.ERROR, "INPUT_NULL",
                    "Could not build a QE input for " + type2 + "."));
        } else {
            issues.addAll(new QEInputValidator().validate(input));
        }

        String dirPath = project.getDirectoryPath();
        if (dirPath == null || dirPath.isBlank()) {
            issues.add(issue(ValidationSeverity.ERROR, "PROJECT_DIR_MISSING",
                    "Project has no directory; save the project before running."));
        } else {
            Path dir = Path.of(dirPath);
            if (!Files.isDirectory(dir)) {
                issues.add(issue(ValidationSeverity.ERROR, "PROJECT_DIR_INVALID",
                        "Project directory does not exist: " + dir));
            } else if (!Files.isWritable(dir)) {
                issues.add(issue(ValidationSeverity.ERROR, "PROJECT_DIR_READONLY",
                        "Project directory is not writable: " + dir));
            } else {
                try {
                    QEScratchStoragePolicy policy = new QEScratchStoragePolicy();
                    long estimatedScratchSize = (input != null) ? policy.estimateScratchSize(project.getCell(), input) : 0L;
                    List<String> warnings = new ArrayList<>();
                    boolean spaceOk = policy.verifySpace(dir, estimatedScratchSize, warnings);
                    for (String warning : warnings) {
                        issues.add(issue(spaceOk ? ValidationSeverity.WARNING : ValidationSeverity.ERROR, "SCRATCH_PREDICTION", warning));
                    }
                    if (spaceOk && estimatedScratchSize > 1024L * 1024L) {
                        issues.add(issue(ValidationSeverity.INFO, "SCRATCH_ESTIMATE",
                            String.format(Locale.ROOT, "Estimated wave-function scratch footprint: %.2f MB.", estimatedScratchSize / (1024.0 * 1024.0))));
                    }
                } catch (Exception ex) {
                    try {
                        long free = Files.getFileStore(dir).getUsableSpace();
                        if (free < 50L * 1024L * 1024L) {
                            issues.add(issue(ValidationSeverity.WARNING, "DISK_LOW",
                                    String.format(Locale.ROOT,
                                            "Less than 50 MiB free on the project filesystem (%,d bytes).", free)));
                        }
                    } catch (Exception exc) {
                        issues.add(issue(ValidationSeverity.WARNING, "DISK_CHECK_FAILED",
                                "Could not query free disk space: " + exc.getMessage()));
                    }
                }
            }
        }

        String mpi = QEPath.getMPIPath();
        if (numProcesses > 1) {
            if ((mpi == null || mpi.isBlank()) && !profile.isMpiLauncherPresent()) {
                issues.add(issue(ValidationSeverity.ERROR, "MPI_MISSING",
                        "numProcesses=" + numProcesses + " but no mpirun/mpiexec was found. "
                                + "Set the MPI path or use 1 process."));
            } else if (mpi != null && !mpi.isBlank()
                    && !Files.isDirectory(Path.of(mpi)) && !new File(mpi).isFile()) {
                issues.add(issue(ValidationSeverity.WARNING, "MPI_PATH",
                        "Configured MPI path may be invalid: " + mpi));
            }
        }

        QECommandDag dag = null;
        try {
            String inp = project.getInpFileName();
            dag = QECommandDag.build(type2, inp == null ? "espresso.in" : inp, Math.max(1, numProcesses));
            if (dag.size() == 0) {
                issues.add(issue(ValidationSeverity.ERROR, "DAG_EMPTY",
                        "Command DAG has no stages for " + type2 + "."));
            } else {
                String ids = dag.getStages().stream().map(QECommandStage::getId)
                        .collect(Collectors.joining(", "));
                issues.add(issue(ValidationSeverity.INFO, "DAG_STAGES",
                        "Workflow DAG has " + dag.size() + " stage(s): " + ids));
            }
        } catch (UnsupportedOperationException ex) {
            issues.add(issue(ValidationSeverity.ERROR, "DAG_UNSUPPORTED", ex.getMessage()));
        } catch (RuntimeException ex) {
            issues.add(issue(ValidationSeverity.ERROR, "DAG_BUILD_FAILED",
                    "Failed to build command DAG: " + ex.getMessage()));
        }

        if (dirPath != null && !dirPath.isBlank()) {
            var restart = RestartManager.assess(Path.of(dirPath), project.getPrefixName());
            restart.getValue().ifPresent(assessment -> {
                if (assessment.isRestartSafe()) {
                    issues.add(issue(ValidationSeverity.INFO, "RESTART_AVAILABLE",
                            "Restart artifacts look usable ("
                                    + assessment.getRecommendedRestartMode() + ")."));
                } else {
                    issues.add(issue(ValidationSeverity.INFO, "RESTART_FRESH",
                            "No complete restart directory; from_scratch is recommended."));
                }
            });
        }

        return new Report(issues, dag, profile);
    }

    private static ValidationIssue issue(ValidationSeverity severity, String code, String message) {
        return new ValidationIssue(severity, code, message, DOC);
    }
}
