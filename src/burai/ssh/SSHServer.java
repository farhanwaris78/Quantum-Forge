/*
 * Copyright (C) 2018 Satomichi Nishihara
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package burai.ssh;

import java.util.List;

public class SSHServer {

    private static final String WORD_JOB_SCRIPT = "JOB_SCRIPT";
    private static final String WORD_QE_COMMAND = "QUANTUM_ESPRESSO_COMMAND";
    private static final String WORD_NUM_CPUS = "NCPU";
    private static final String WORD_NUM_MPIS = "NMPI";
    private static final String WORD_NUM_OMPS = "NOMP";

    private static final int DEFAULT_PORT = 22;

    /**
     * Job scheduler types for HPC cluster submission
     */
    public static final String SCHEDULER_NONE = "none";
    public static final String SCHEDULER_PBS = "pbs";
    public static final String SCHEDULER_SLURM = "slurm";
    public static final String SCHEDULER_PJM = "pjm";
    public static final String SCHEDULER_SGE = "sge";

    private String title;

    private String host;

    private String port;

    private String user;

    private String password;

    private String keyPath;

    private String schedulerType;

    private String queueName;

    private String walltime;

    private String groupList;

    private String jobCommand;

    private String jobScript;

    public SSHServer(String title) {
        if (title == null || title.isEmpty()) {
            throw new IllegalArgumentException("title is empty.");
        }

        this.title = title;
        this.host = null;
        this.port = Integer.toString(DEFAULT_PORT);
        this.user = null;
        this.password = null;
        this.keyPath = null;
        this.schedulerType = SCHEDULER_NONE;
        this.queueName = "default";
        this.walltime = "0:30:00";
        this.groupList = "";
        this.initializeJobCommand();
        this.initializeJobScript();
    }

    public String getTitle() {
        return this.title;
    }

    public String getHost() {
        return this.host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPort() {
        return this.port;
    }

    public int intPort() {
        int iport = 0;

        try {
            iport = this.port == null ? DEFAULT_PORT : Integer.parseInt(this.port);

        } catch (NumberFormatException e) {
            iport = DEFAULT_PORT;
        }

        return iport;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getUser() {
        return this.user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getKeyPath() {
        return this.keyPath;
    }

    public void setKeyPath(String keyPath) {
        this.keyPath = keyPath;
    }

    public String getSchedulerType() {
        return this.schedulerType;
    }

    public void setSchedulerType(String schedulerType) {
        if (schedulerType != null) {
            this.schedulerType = schedulerType;
            this.initializeJobCommand();
            this.initializeJobScript();
        }
    }

    public String getQueueName() {
        return this.queueName;
    }

    public void setQueueName(String queueName) {
        if (queueName != null && !queueName.isEmpty()) {
            this.queueName = queueName;
        }
    }

    public String getWalltime() {
        return this.walltime;
    }

    public void setWalltime(String walltime) {
        if (walltime != null && !walltime.isEmpty()) {
            this.walltime = walltime;
        }
    }

    public String getGroupList() {
        return this.groupList;
    }

    public void setGroupList(String groupList) {
        this.groupList = groupList;
    }

    public String getJobCommand() {
        return this.getJobCommand(null);
    }

    public String getJobCommand(String scriptName) {
        String jobCommand_ = this.jobCommand;

        String scriptName_ = scriptName == null ? null : scriptName.trim();
        if (scriptName_ != null && (!scriptName_.isEmpty())) {
            jobCommand_ = jobCommand_.replaceAll("\\$" + WORD_JOB_SCRIPT, scriptName_);
            jobCommand_ = jobCommand_.replaceAll("\\$\\(" + WORD_JOB_SCRIPT + "\\)", scriptName_);
            jobCommand_ = jobCommand_.replaceAll("\\$\\{" + WORD_JOB_SCRIPT + "\\}", scriptName_);
        }

        return jobCommand_;
    }

    public void setJobCommand(String jobCommand) {
        this.jobCommand = jobCommand;
    }

    private void initializeJobCommand() {
        if (SCHEDULER_PBS.equals(this.schedulerType) || SCHEDULER_SGE.equals(this.schedulerType)) {
            this.jobCommand = "qsub ${" + WORD_JOB_SCRIPT + "}";
        } else if (SCHEDULER_SLURM.equals(this.schedulerType)) {
            this.jobCommand = "sbatch ${" + WORD_JOB_SCRIPT + "}";
        } else if (SCHEDULER_PJM.equals(this.schedulerType)) {
            this.jobCommand = "pjsub ${" + WORD_JOB_SCRIPT + "}";
        } else {
            this.jobCommand = "sh ${" + WORD_JOB_SCRIPT + "}";
        }
    }

    public String getJobScript() {
        return this.getJobScript((String) null, -1, -1);
    }

    public String getJobScript(String qeCommand, int numMPI, int numOMP) {
        String jobScript_ = this.jobScript;

        String qeCommand_ = qeCommand == null ? null : qeCommand.trim();
        if (qeCommand_ != null && (!qeCommand_.isEmpty())) {
            jobScript_ = jobScript_.replaceAll("\\$" + WORD_QE_COMMAND, qeCommand_);
            jobScript_ = jobScript_.replaceAll("\\$\\(" + WORD_QE_COMMAND + "\\)", qeCommand_);
            jobScript_ = jobScript_.replaceAll("\\$\\{" + WORD_QE_COMMAND + "\\}", qeCommand_);
        }

        String strMPI = numMPI < 1 ? null : Integer.toString(numMPI);
        if (strMPI != null && (!strMPI.isEmpty())) {
            jobScript_ = jobScript_.replaceAll("\\$" + WORD_NUM_MPIS, strMPI);
            jobScript_ = jobScript_.replaceAll("\\$\\(" + WORD_NUM_MPIS + "\\)", strMPI);
            jobScript_ = jobScript_.replaceAll("\\$\\{" + WORD_NUM_MPIS + "\\}", strMPI);
        }

        String strOMP = numOMP < 1 ? null : Integer.toString(numOMP);
        if (strOMP != null && (!strOMP.isEmpty())) {
            jobScript_ = jobScript_.replaceAll("\\$" + WORD_NUM_OMPS, strOMP);
            jobScript_ = jobScript_.replaceAll("\\$\\(" + WORD_NUM_OMPS + "\\)", strOMP);
            jobScript_ = jobScript_.replaceAll("\\$\\{" + WORD_NUM_OMPS + "\\}", strOMP);
        }

        int numCPU = Math.max(0, numMPI) * Math.max(0, numOMP);
        String strCPU = numCPU < 1 ? null : Integer.toString(numCPU);
        if (strCPU != null && (!strCPU.isEmpty())) {
            jobScript_ = jobScript_.replaceAll("\\$" + WORD_NUM_CPUS, strCPU);
            jobScript_ = jobScript_.replaceAll("\\$\\(" + WORD_NUM_CPUS + "\\)", strCPU);
            jobScript_ = jobScript_.replaceAll("\\$\\{" + WORD_NUM_CPUS + "\\}", strCPU);
        }

        return jobScript_;
    }

    public String getJobScript(List<String> qeCommands, int numMPI, int numOMP) {
        if (qeCommands == null) {
            return this.getJobScript((String) null, numMPI, numOMP);
        }

        StringBuilder strBuilder = new StringBuilder();
        for (String qeCommand : qeCommands) {
            if (qeCommand == null) {
                continue;
            }
            strBuilder.append(qeCommand);
            strBuilder.append(System.lineSeparator());
        }

        return this.getJobScript(strBuilder.toString(), numMPI, numOMP);
    }

    public void setJobScript(String jobScript) {
        this.jobScript = jobScript;
    }

    private void initializeJobScript() {
        StringBuilder strBuilder = new StringBuilder();
        strBuilder.append("#!/bin/sh");
        strBuilder.append(System.lineSeparator());

        if (SCHEDULER_PBS.equals(this.schedulerType) || SCHEDULER_SGE.equals(this.schedulerType)) {
            // PBS/Torque or SGE script
            strBuilder.append("#PBS -q " + (this.queueName != null ? this.queueName : "QUEUE"));
            strBuilder.append(System.lineSeparator());
            strBuilder.append("#PBS -l select=1:ncpus=${" + WORD_NUM_CPUS + "}:mpiprocs=${" + WORD_NUM_MPIS + "}:ompthreads=${" + WORD_NUM_OMPS + "}");
            strBuilder.append(System.lineSeparator());
            strBuilder.append("#PBS -l walltime=" + (this.walltime != null ? this.walltime : "0:30:00"));
            strBuilder.append(System.lineSeparator());
            if (this.groupList != null && !this.groupList.isEmpty()) {
                strBuilder.append("#PBS -W group_list=" + this.groupList);
                strBuilder.append(System.lineSeparator());
            }
            strBuilder.append(System.lineSeparator());
            strBuilder.append("if [ ! -z \"${PBS_O_WORKDIR}\" ]; then");
            strBuilder.append(System.lineSeparator());
            strBuilder.append("  cd ${PBS_O_WORKDIR}");
            strBuilder.append(System.lineSeparator());
            strBuilder.append("fi");
            strBuilder.append(System.lineSeparator());

        } else if (SCHEDULER_SLURM.equals(this.schedulerType)) {
            // SLURM script
            strBuilder.append("#SBATCH -p " + (this.queueName != null ? this.queueName : "QUEUE"));
            strBuilder.append(System.lineSeparator());
            strBuilder.append("#SBATCH -n ${" + WORD_NUM_CPUS + "}");
            strBuilder.append(System.lineSeparator());
            strBuilder.append("#SBATCH --ntasks=${" + WORD_NUM_MPIS + "}");
            strBuilder.append(System.lineSeparator());
            strBuilder.append("#SBATCH --cpus-per-task=${" + WORD_NUM_OMPS + "}");
            strBuilder.append(System.lineSeparator());
            strBuilder.append("#SBATCH -t " + (this.walltime != null ? this.walltime : "0:30:00"));
            strBuilder.append(System.lineSeparator());
            if (this.groupList != null && !this.groupList.isEmpty()) {
                strBuilder.append("#SBATCH -A " + this.groupList);
                strBuilder.append(System.lineSeparator());
            }
            strBuilder.append(System.lineSeparator());
            strBuilder.append("cd ${SLURM_SUBMIT_DIR}");
            strBuilder.append(System.lineSeparator());

        } else if (SCHEDULER_PJM.equals(this.schedulerType)) {
            // PJM (Fugaku) script
            strBuilder.append("#PJM -L \"rscgrp=" + (this.queueName != null ? this.queueName : "large") + "\"");
            strBuilder.append(System.lineSeparator());
            strBuilder.append("#PJM -L \"node=1\"");
            strBuilder.append(System.lineSeparator());
            strBuilder.append("#PJM -L \"elapse=" + (this.walltime != null ? this.walltime : "0:30:00") + "\"");
            strBuilder.append(System.lineSeparator());
            strBuilder.append("#PJM --mpi \"max-proc-per-node=${" + WORD_NUM_MPIS + "}\"");
            strBuilder.append(System.lineSeparator());
            if (this.groupList != null && !this.groupList.isEmpty()) {
                strBuilder.append("#PJM -g " + this.groupList);
                strBuilder.append(System.lineSeparator());
            }
            strBuilder.append(System.lineSeparator());

        } else {
            // No scheduler - direct execution
            strBuilder.append("# Local execution");
            strBuilder.append(System.lineSeparator());
        }

        strBuilder.append(System.lineSeparator());
        strBuilder.append("export OMP_NUM_THREADS=${" + WORD_NUM_OMPS + "}");
        strBuilder.append(System.lineSeparator());
        strBuilder.append(System.lineSeparator());
        strBuilder.append("${" + WORD_QE_COMMAND + "}");
        strBuilder.append(System.lineSeparator());
        strBuilder.append(System.lineSeparator());

        this.jobScript = strBuilder.toString();
    }

    @Override
    public String toString() {
        return this.title;
    }

    @Override
    public int hashCode() {
        return this.title.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (this.getClass() != obj.getClass()) {
            return false;
        }

        SSHServer other = (SSHServer) obj;

        return this.title.equals(other.title);
    }
}
