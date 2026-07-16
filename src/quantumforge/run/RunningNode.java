/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.run;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import quantumforge.app.QEFXMain;
import quantumforge.com.file.AtomicFileWriter;
import quantumforge.com.file.FileTools;
import quantumforge.com.log.AppLog;
import quantumforge.com.path.QEPath;
import quantumforge.input.QEInput;
import quantumforge.input.validation.QEInputValidator;
import quantumforge.input.validation.ValidationIssue;
import quantumforge.project.Project;
import quantumforge.run.parser.LogParser;

public class RunningNode implements Runnable {

    private static final RunningType DEFAULT_TYPE = RunningType.SCF;

    private boolean alive;

    private Project project;

    private RunningStatus status;

    private List<RunningStatusChanged> onStatusChangedList;

    private RunningType type;

    private int numProcesses;

    private int numThreads;

    private Process objProcess;

    private volatile boolean cancelled;

    private String jobId;

    public RunningNode(Project project) {
        if (project == null) {
            throw new IllegalArgumentException("project is null.");
        }

        this.alive = true;
        this.cancelled = false;

        this.project = project;

        this.status = RunningStatus.IDLE;
        this.onStatusChangedList = null;

        this.type = null;
        this.numProcesses = 1;
        this.numThreads = 1;

        this.objProcess = null;
        this.jobId = null;
    }

    public Project getProject() {
        return this.project;
    }

    public synchronized RunningStatus getStatus() {
        return this.status;
    }

    protected synchronized void setStatus(RunningStatus status) {
        if (status == null) {
            return;
        }

        this.status = status;

        if (this.onStatusChangedList != null) {
            for (RunningStatusChanged onStatusChanged : this.onStatusChangedList) {
                if (onStatusChanged != null) {
                    onStatusChanged.onRunningStatusChanged(this.status);
                }
            }
        }
    }

    public synchronized void addOnStatusChanged(RunningStatusChanged onStatusChanged) {
        if (onStatusChanged != null) {
            if (this.onStatusChangedList == null) {
                this.onStatusChangedList = new ArrayList<RunningStatusChanged>();
            }

            this.onStatusChangedList.add(onStatusChanged);
        }
    }

    public synchronized void removeOnStatusChanged(RunningStatusChanged onStatusChanged) {
        if (onStatusChanged != null) {
            if (this.onStatusChangedList != null) {
                this.onStatusChangedList.remove(onStatusChanged);
            }
        }
    }

    public synchronized RunningType getType() {
        return this.type;
    }

    public synchronized void setType(RunningType type) {
        this.type = type;
    }

    public synchronized int getNumProcesses() {
        return this.numProcesses;
    }

    public synchronized void setNumProcesses(int numProcesses) {
        this.numProcesses = numProcesses;
    }

    public synchronized int getNumThreads() {
        return this.numThreads;
    }

    public synchronized void setNumThreads(int numThreads) {
        this.numThreads = numThreads;
    }

    public synchronized void stop() {
        this.alive = false;
        this.cancelled = true;

        if (this.objProcess != null) {
            // Prefer QE graceful exit file first, then process-tree kill.
            this.requestGracefulExit();
            ProcessTreeKiller.stop(this.objProcess, Duration.ofSeconds(5), Duration.ofSeconds(5));
        }
    }

    public synchronized boolean wasCancelled() {
        return this.cancelled;
    }

    @Override
    public void run() {
        synchronized (this) {
            if (!this.alive) {
                return;
            }
        }

        File directory = this.getDirectory();
        if (directory == null) {
            return;
        }

        this.jobId = AppLog.newJobId();
        AppLog.info("run", "Starting calculation job " + this.jobId + " in " + directory.getPath());

        RunningType type2 = null;
        int numProcesses2 = -1;
        int numThreads2 = -1;

        synchronized (this) {
            type2 = this.type;
            numProcesses2 = this.numProcesses;
            numThreads2 = this.numThreads;
        }

        if (type2 == null) {
            type2 = DEFAULT_TYPE;
        }
        if (numProcesses2 < 1) {
            numProcesses2 = 1;
        }
        if (numThreads2 < 1) {
            numThreads2 = 1;
        }

        QEExecutableProfile profile = QEExecutableProfile.probeConfigured();
        if (!profile.hasCorePw()) {
            AppLog.warn("run", "pw.x is missing from the configured QE profile; launch will likely fail.");
        }

        QEInput input = new FXQEInputFactory(type2).getQEInput(this.project);
        if (input == null) {
            return;
        }
        List<ValidationIssue> validationIssues = new QEInputValidator().validate(input);
        if (QEInputValidator.hasErrors(validationIssues)) {
            this.showValidationDialog(validationIssues);
            return;
        }

        String inpName = this.project.getInpFileName();
        inpName = inpName == null ? null : inpName.trim();
        File inpFile = (inpName == null || inpName.isEmpty()) ? null : new File(directory, inpName);
        if (inpFile == null) {
            return;
        }

        List<String[]> commandList = type2.getCommandList(inpName, numProcesses2);
        if (commandList == null || commandList.isEmpty()) {
            return;
        }

        List<RunningCondition> conditionList = type2.getConditionList();
        if (conditionList == null || conditionList.size() < commandList.size()) {
            return;
        }

        List<InputEditor> inputEditorList = type2.getInputEditorList(this.project);
        if (inputEditorList == null || inputEditorList.size() < commandList.size()) {
            return;
        }

        List<String> logNameList = type2.getLogNameList(this.project);
        if (logNameList == null || logNameList.size() < commandList.size()) {
            return;
        }

        List<String> errNameList = type2.getErrNameList(this.project);
        if (errNameList == null || errNameList.size() < commandList.size()) {
            return;
        }

        List<LogParser> parserList = type2.getParserList(this.project);
        if (parserList == null || parserList.size() < commandList.size()) {
            return;
        }

        List<PostOperation> postList = type2.getPostList();
        if (postList == null || postList.size() < commandList.size()) {
            return;
        }

        this.deleteExitFile(directory);

        ProcessBuilder builder = null;
        boolean errOccurred = false;
        boolean wasCancelled = false;

        for (int i = 0; i < commandList.size(); i++) {
            synchronized (this) {
                if (!this.alive) {
                    wasCancelled = true;
                    break;
                }
            }

            String[] command = commandList.get(i);
            if (command == null || command.length < 1) {
                continue;
            }

            RunningCondition condition = conditionList.get(i);
            if (condition == null) {
                continue;
            }

            InputEditor inputEditor = inputEditorList.get(i);
            if (inputEditor == null) {
                continue;
            }

            String logName = logNameList.get(i);
            logName = logName == null ? null : logName.trim();
            if (logName == null || logName.isEmpty()) {
                continue;
            }

            String errName = errNameList.get(i);
            errName = errName == null ? null : errName.trim();
            if (errName == null || errName.isEmpty()) {
                continue;
            }

            LogParser parser = parserList.get(i);
            if (parser == null) {
                continue;
            }

            PostOperation post = postList.get(i);
            if (post == null) {
                continue;
            }

            QEInput input2 = inputEditor.editInput(input);
            if (input2 == null) {
                continue;
            }

            if (!condition.toRun(this.project, input2)) {
                continue;
            }

            boolean inpStatus = this.writeQEInput(input2, inpFile);
            if (!inpStatus) {
                continue;
            }

            File logFile = new File(directory, logName);
            File errFile = new File(directory, errName);
            this.deleteLogFiles(logFile, errFile);

            builder = new ProcessBuilder();
            builder.directory(directory);
            builder.command(command);
            builder.redirectOutput(logFile);
            builder.redirectError(errFile);
            builder.environment().put("OMP_NUM_THREADS", Integer.toString(numThreads2));
            this.setPathToBuilder(builder);

            RunManifest manifest = new RunManifest(this.jobId, type2.name() + "-stage-" + i,
                    Arrays.asList(command), directory.toPath());
            manifest.setQeVersion(profile.getVersion());
            manifest.putEnvironment("OMP_NUM_THREADS", Integer.toString(numThreads2));
            if (command.length > 0) {
                Path executable = Path.of(command[0]);
                if (!executable.isAbsolute() && profile.getBinDirectory() != null) {
                    Path candidate = profile.getBinDirectory().resolve(command[0]);
                    if (java.nio.file.Files.isRegularFile(candidate)) {
                        executable = candidate;
                    }
                }
                manifest.setExecutable(executable);
            }
            manifest.setInput(inpFile.toPath());

            try {
                synchronized (this) {
                    this.objProcess = builder.start();
                }

                parser.startParsing(logFile);

                if (this.objProcess != null) {
                    int exit = this.objProcess.waitFor();
                    manifest.setOutputs(logFile.toPath(), errFile.toPath());
                    if (this.wasCancelled()) {
                        wasCancelled = true;
                        manifest.finish(exit, "CANCELLED");
                        break;
                    }
                    if (exit != 0) {
                        errOccurred = true;
                        manifest.finish(exit, "FAILED");
                        break;
                    }
                    manifest.finish(exit, "COMPLETED");
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                wasCancelled = true;
                manifest.finish(-1, "CANCELLED");
                break;
            } catch (Exception e) {
                AppLog.error("run", "Stage failed for job " + this.jobId, e);
                errOccurred = true;
                manifest.finish(-1, "FAILED");
                break;

            } finally {
                synchronized (this) {
                    this.objProcess = null;
                }

                parser.endParsing();
                try {
                    manifest.appendToProject(directory.toPath());
                } catch (IOException ex) {
                    AppLog.warn("run", "Could not write run manifest: " + ex.getMessage());
                }
            }

            if (!errOccurred && !wasCancelled) {
                post.operate(this.project);
            }
        }

        if (wasCancelled) {
            AppLog.info("run", "Job " + this.jobId + " cancelled");
            this.setStatus(RunningStatus.IDLE);
        } else if (!errOccurred) {
            type2.setProjectStatus(this.project);
            AppLog.info("run", "Job " + this.jobId + " completed");
        } else {
            AppLog.error("run", "Job " + this.jobId + " failed");
            this.showErrorDialog(builder);
        }
    }

    private void requestGracefulExit() {
        File directory = this.getDirectory();
        if (directory == null) {
            return;
        }
        String exitName = this.project.getExitFileName();
        if (exitName == null || exitName.trim().isEmpty()) {
            return;
        }
        try {
            AtomicFileWriter.writeUtf8(Path.of(directory.getPath(), exitName.trim()), "1\n");
            AppLog.info("run", "Wrote QE EXIT file " + exitName);
        } catch (IOException ex) {
            AppLog.warn("run", "Could not write QE EXIT file: " + ex.getMessage());
        }
    }

    private File getDirectory() {
        String dirPath = this.project.getDirectoryPath();
        if (dirPath == null) {
            return null;
        }

        File dirFile = new File(dirPath);
        try {
            if (!dirFile.isDirectory()) {
                return null;
            }

        } catch (Exception e) {
            AppLog.error("run", "Cannot access project directory", e);
            return null;
        }

        return dirFile;
    }

    private boolean writeQEInput(QEInput input, File file) {
        if (input == null || file == null) {
            return false;
        }

        String strInput = input.toString();
        if (strInput == null) {
            return false;
        }
        if (!strInput.endsWith("\n")) {
            strInput = strInput + System.lineSeparator();
        }

        try {
            AtomicFileWriter.writeUtf8(file.toPath(), strInput);
            return true;
        } catch (IOException e) {
            AppLog.error("run", "Failed to write QE input " + file, e);
            return false;
        }
    }

    private void deleteExitFile(File directory) {
        if (directory == null) {
            return;
        }

        String exitName = this.project.getExitFileName();
        exitName = exitName == null ? null : exitName.trim();
        if (exitName != null && (!exitName.isEmpty())) {
            try {
                File exitFile = new File(directory, exitName);
                if (exitFile.exists()) {
                    FileTools.deleteAllFiles(exitFile, false);
                }
            } catch (Exception e) {
                AppLog.warn("run", "Could not delete EXIT file: " + e.getMessage());
            }
        }
    }

    private void deleteLogFiles(File logFile, File errFile) {
        try {
            if (logFile != null && logFile.exists()) {
                FileTools.deleteAllFiles(logFile, false);
            }

            if (errFile != null && errFile.exists()) {
                FileTools.deleteAllFiles(errFile, false);
            }

        } catch (Exception e) {
            AppLog.warn("run", "Could not delete previous log files: " + e.getMessage());
        }
    }

    private void setPathToBuilder(ProcessBuilder builder) {
        if (builder == null) {
            return;
        }

        String qePath = QEPath.getPath();
        String mpiPath = QEPath.getMPIPath();

        String orgPath = builder.environment().get("PATH");
        if (orgPath == null) {
            orgPath = builder.environment().get("Path");
        }
        if (orgPath == null) {
            orgPath = builder.environment().get("path");
        }

        String path = null;

        if (qePath != null && !(qePath.isEmpty())) {
            path = path == null ? qePath : (path + File.pathSeparator + qePath);
        }

        if (mpiPath != null && !(mpiPath.isEmpty())) {
            path = path == null ? mpiPath : (path + File.pathSeparator + mpiPath);
        }

        if (orgPath != null && !(orgPath.isEmpty())) {
            path = path == null ? orgPath : (path + File.pathSeparator + orgPath);
        }

        if (path != null && !(path.isEmpty())) {
            builder.environment().put("PATH", path);
            builder.environment().put("Path", path);
            builder.environment().put("path", path);
        }
    }

    private void showValidationDialog(List<ValidationIssue> issues) {
        StringBuilder message = new StringBuilder();
        for (ValidationIssue issue : issues) {
            if (issue != null) {
                message.append(issue).append(System.lineSeparator());
            }
        }
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.ERROR);
            QEFXMain.initializeDialogOwner(alert);
            alert.setTitle("Quantum ESPRESSO input preflight failed");
            alert.setHeaderText("Calculation was not started because the generated input has errors.");
            alert.setContentText(message.toString());
            alert.setResizable(true);
            alert.getDialogPane().setPrefWidth(820.0);
            alert.showAndWait();
        });
    }

    private void showErrorDialog(ProcessBuilder buider) {
        File dirFile = buider == null ? null : buider.directory();
        String dirStr = dirFile == null ? null : dirFile.getPath();

        if (dirStr != null) {
            dirStr = dirStr.trim();
        }

        final String message1;
        if (dirStr == null || dirStr.isEmpty()) {
            message1 = "ERROR in running the project.";
        } else {
            message1 = "ERROR in running the project: " + dirStr;
        }

        String cmdStr = null;
        List<String> cmdList = buider == null ? null : buider.command();
        if (cmdList != null) {
            for (String cmd : cmdList) {
                if (cmd != null) {
                    cmd = cmd.trim();
                }
                if (cmd == null || cmd.isEmpty()) {
                    continue;
                }
                if (cmdStr == null) {
                    cmdStr = cmd;
                } else {
                    cmdStr = cmdStr + " " + cmd;
                }
            }
        }

        if (cmdStr != null) {
            cmdStr = cmdStr.trim();
        }

        final String message2;
        if (cmdStr == null || cmdStr.isEmpty()) {
            message2 = "NO COMMAND.";
        } else {
            message2 = "COMMAND: " + cmdStr;
        }

        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.ERROR);
            QEFXMain.initializeDialogOwner(alert);
            alert.setHeaderText(message1);
            alert.setContentText(message2);
            alert.showAndWait();
        });
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }
}
