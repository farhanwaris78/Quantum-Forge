/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.viewer.run;

import quantumforge.run.RunningNode;
import quantumforge.ssh.SSHJob;

public class RunEvent {

    private RunningNode runningNode;

    private SSHJob sshJob;

    private RunEvent() {
        this.runningNode = null;
        this.sshJob = null;
    }

    public RunEvent(RunningNode runningNode) {
        this();
        this.runningNode = runningNode;
    }

    public RunEvent(SSHJob sshJob) {
        this();
        this.sshJob = sshJob;
    }

    public RunningNode getRunningNode() {
        return this.runningNode;
    }

    public SSHJob getSSHJob() {
        return this.sshJob;
    }
}
