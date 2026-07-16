/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.ssh;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import quantumforge.app.QEFXMain;
import quantumforge.com.env.Environments;
import quantumforge.com.log.AppLog;
import quantumforge.operation.OperationResult;
import quantumforge.ssh.JschSshTransport;
import quantumforge.ssh.KnownHostsStore;
import quantumforge.ssh.SSHServer;
import quantumforge.ssh.SshConnectionConfig;
import quantumforge.ssh.SshTransport;

/**
 * GUI/helper for first-time host-key acceptance and secure connect.
 */
public final class HostKeyAcceptance {

    private HostKeyAcceptance() {
        // Utility.
    }

    public static Path defaultKnownHostsPath() {
        String base = Environments.getSSHDataPath();
        if (base == null || base.isBlank()) {
            base = System.getProperty("user.home", ".") + "/.quantumforge/.ssh";
        }
        return Path.of(base, "known_hosts.qf");
    }

    public static KnownHostsStore openStore() throws IOException {
        Path path = defaultKnownHostsPath();
        java.nio.file.Files.createDirectories(path.getParent());
        KnownHostsStore store = new KnownHostsStore(path);
        store.load();
        return store;
    }

    /**
     * Connect with fail-closed host keys. If the host is unknown, ask the user
     * once to accept the fingerprint (only when {@code allowPrompt} is true).
     */
    public static OperationResult<SshTransport> connectInteractive(SSHServer server,
                                                                   boolean allowPrompt) {
        if (server == null) {
            return OperationResult.failed("SERVER_NULL", "SSH server is null.", null);
        }
        if (server.getHost() == null || server.getHost().isBlank()
                || server.getUser() == null || server.getUser().isBlank()) {
            return OperationResult.failed("SERVER_INCOMPLETE",
                    "Host and user are required before connecting.", null);
        }
        try {
            KnownHostsStore store = openStore();
            Path keyPath = null;
            if (server.getKeyPath() != null && !server.getKeyPath().isBlank()) {
                keyPath = Path.of(server.getKeyPath());
            }
            char[] password = null;
            if (server.getPassword() != null && !server.getPassword().isBlank()) {
                password = server.getPassword().toCharArray();
            }

            // First attempt: never auto-accept.
            SshConnectionConfig strict = SshConnectionConfig.builder()
                    .host(server.getHost())
                    .port(server.intPort())
                    .user(server.getUser())
                    .privateKeyPath(keyPath)
                    .knownHostsPath(store.getPath())
                    .acceptNewHostKeys(false)
                    .build();
            JschSshTransport transport = new JschSshTransport(strict, store, password);
            OperationResult<Void> first = transport.connect();
            if (first.isSuccess()) {
                return OperationResult.success("SSH_CONNECTED", first.getMessage(), transport);
            }

            if (!"SSH_HOST_KEY_UNKNOWN".equals(first.getCode())
                    && !"SSH_HOST_KEY_REJECTED".equals(first.getCode())) {
                transport.close();
                return OperationResult.failed(first.getCode(), first.getMessage(), null);
            }
            if (!allowPrompt) {
                transport.close();
                return OperationResult.failed(first.getCode(),
                        first.getMessage() + " (interactive acceptance disabled)", null);
            }

            // Prompt user; on YES, reconnect with acceptNewHostKeys.
            String details = first.getMessage();
            Alert alert = new Alert(AlertType.CONFIRMATION);
            QEFXMain.initializeDialogOwner(alert);
            alert.setTitle("Unknown SSH host key");
            alert.setHeaderText("Accept host key for " + server.getUser() + "@"
                    + server.getHost() + ":" + server.intPort() + "?");
            alert.setContentText(details
                    + "\n\nOnly accept if this fingerprint matches your cluster documentation.");
            Optional<ButtonType> answer = alert.showAndWait();
            if (answer.isEmpty() || answer.get() != ButtonType.OK) {
                transport.close();
                return OperationResult.cancelled("SSH_HOST_KEY_DECLINED",
                        "User declined the unknown host key.");
            }

            transport.close();
            SshConnectionConfig accept = SshConnectionConfig.builder()
                    .host(server.getHost())
                    .port(server.intPort())
                    .user(server.getUser())
                    .privateKeyPath(keyPath)
                    .knownHostsPath(store.getPath())
                    .acceptNewHostKeys(true)
                    .build();
            JschSshTransport second = new JschSshTransport(accept, store, password);
            OperationResult<Void> connected = second.connect();
            if (!connected.isSuccess()) {
                second.close();
                return OperationResult.failed(connected.getCode(), connected.getMessage(), null);
            }
            AppLog.info("ssh-ui", "User accepted host key for " + server.getHost());
            return OperationResult.success("SSH_CONNECTED_ACCEPTED",
                    "Connected after host-key acceptance.", second);
        } catch (Exception ex) {
            return OperationResult.failed("SSH_CONNECT_UI_ERROR",
                    "Interactive SSH connect failed: " + ex.getMessage(), ex);
        }
    }
}
