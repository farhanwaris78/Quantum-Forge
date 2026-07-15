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

import quantumforge.app.QEFXMainController;
import quantumforge.app.project.QEFXProjectController;
import quantumforge.run.RunningManager;
import quantumforge.run.RunningNode;
import quantumforge.ssh.SSHJob;

public class RunAction {

    private QEFXProjectController controller;

    public RunAction(QEFXProjectController controller) {
        if (controller == null) {
            throw new IllegalArgumentException("controller is null.");
        }

        this.controller = controller;
    }

    public void runCalculation(RunEvent runEvent) {
        if (runEvent == null) {
            return;
        }

        if (runEvent.getRunningNode() != null) {
            this.runOnLocalMachine(runEvent.getRunningNode());

        } else if (runEvent.getSSHJob() != null) {
            this.runOnSSHServer(runEvent.getSSHJob());
        }
    }

    private void runOnLocalMachine(RunningNode runningNode) {
        if (runningNode == null) {
            return;
        }

        RunningManager.getInstance().addNode(runningNode);

        QEFXMainController mainController = this.controller.getMainController();
        if (mainController == null) {
            return;
        }

        mainController.offerOnHomeTabSelected(explorerFacade -> {
            if (explorerFacade != null && (!explorerFacade.isCalculatingMode())) {
                explorerFacade.setCalculatingMode();
            }
        });

        mainController.showHome();
    }

    private void runOnSSHServer(SSHJob sshJob) {
        if (sshJob == null) {
            return;
        }

        sshJob.postJobToServer();
    }
}
