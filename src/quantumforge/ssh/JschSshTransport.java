/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.ssh;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import quantumforge.com.log.AppLog;
import quantumforge.operation.OperationResult;

/**
 * JSch-backed transport with strict host-key verification.
 *
 * <p>Unknown hosts are rejected unless {@link SshConnectionConfig#isAcceptNewHostKeys()}
 * is true, in which case the fingerprint is persisted after a successful connect.
 * Changed keys are always rejected.</p>
 */
public final class JschSshTransport implements SshTransport {

    private final SshConnectionConfig config;
    private final KnownHostsStore knownHosts;
    private final char[] sessionPassword;
    private Session session;

    public JschSshTransport(SshConnectionConfig config, KnownHostsStore knownHosts) {
        this(config, knownHosts, null);
    }

    public JschSshTransport(SshConnectionConfig config, KnownHostsStore knownHosts,
                            char[] sessionPassword) {
        this.config = Objects.requireNonNull(config, "config");
        this.knownHosts = Objects.requireNonNull(knownHosts, "knownHosts");
        this.sessionPassword = sessionPassword == null ? null : sessionPassword.clone();
    }

    @Override
    public OperationResult<Void> connect() {
        if (this.session != null && this.session.isConnected()) {
            return OperationResult.success("SSH_ALREADY_CONNECTED", "Session already connected.", null);
        }
        try {
            this.knownHosts.load();
            JSch jsch = new JSch();
            if (this.config.getPrivateKeyPath() != null
                    && Files.isRegularFile(this.config.getPrivateKeyPath())) {
                jsch.addIdentity(this.config.getPrivateKeyPath().toString());
            }

            // Default JSch policy: reject unknown host keys unless we explicitly
            // allow accepting new keys (still fingerprint-checked after connect).
            Properties props = new Properties();
            props.put("StrictHostKeyChecking", "yes");
            props.put("PreferredAuthentications", "publickey,keyboard-interactive,password");

            // If we already know the fingerprint, write a temporary OpenSSH known_hosts
            // line is not required: we verify after connect using HostKey from Session.
            // For first-time accept mode, temporarily relax JSch check then re-validate.
            if (this.config.isAcceptNewHostKeys()) {
                props.put("StrictHostKeyChecking", "no");
            }

            Session candidate = jsch.getSession(
                    this.config.getUser(), this.config.getHost(), this.config.getPort());
            if (this.sessionPassword != null && this.sessionPassword.length > 0) {
                candidate.setPassword(new String(this.sessionPassword));
            }
            candidate.setConfig(props);
            int timeoutMs = (int) Math.min(Integer.MAX_VALUE, this.config.getConnectTimeout().toMillis());
            candidate.setTimeout(timeoutMs);

            try {
                candidate.connect(timeoutMs);
            } catch (JSchException ex) {
                String message = String.valueOf(ex.getMessage());
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("reject hostkey") || lower.contains("unknownhostkey")
                        || lower.contains("hostkey has been changed")) {
                    return OperationResult.failed("SSH_HOST_KEY_REJECTED",
                            "SSH host key rejected: " + message, ex);
                }
                return OperationResult.failed("SSH_CONNECT_FAILED",
                        "SSH connect failed: " + message, ex);
            }

            HostKey hostKey = candidate.getHostKey();
            if (hostKey == null) {
                candidate.disconnect();
                return OperationResult.failed("SSH_HOST_KEY_MISSING",
                        "Server did not present a host key.", null);
            }
            String fingerprint = KnownHostsStore.fingerprintSha256FromBase64(hostKey.getKey());
            KnownHostsStore.Decision decision = this.knownHosts.verify(
                    this.config.getHost(), this.config.getPort(), hostKey.getType(), fingerprint);
            if (decision == KnownHostsStore.Decision.REJECT_CHANGED) {
                candidate.disconnect();
                return OperationResult.failed("SSH_HOST_KEY_CHANGED",
                        "Host key for " + this.config.getHost() + " changed ("
                                + hostKey.getType() + " " + fingerprint
                                + "). Refusing connection (possible MITM).", null);
            }
            if (decision == KnownHostsStore.Decision.REJECT_UNKNOWN) {
                if (!this.config.isAcceptNewHostKeys()) {
                    candidate.disconnect();
                    return OperationResult.failed("SSH_HOST_KEY_UNKNOWN",
                            "Unknown host key " + hostKey.getType() + " " + fingerprint
                                    + ". Accept it explicitly (acceptNewHostKeys) before connecting.",
                            null);
                }
                this.knownHosts.accept(this.config.getHost(), this.config.getPort(),
                        hostKey.getType(), fingerprint);
            }

            this.session = candidate;
            AppLog.info("ssh", "Connected to " + this.config.getUser() + "@"
                    + this.config.getHost() + ":" + this.config.getPort()
                    + " fingerprint=" + fingerprint);
            return OperationResult.success("SSH_CONNECTED", "SSH session established.", null);
        } catch (IOException | RuntimeException | JSchException ex) {
            return OperationResult.failed("SSH_CONNECT_ERROR",
                    "SSH setup failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean isConnected() {
        return this.session != null && this.session.isConnected();
    }

    @Override
    public OperationResult<Integer> exec(String[] command, Path stdoutFile, Path stderrFile) {
        if (!isConnected()) {
            return OperationResult.failed("SSH_NOT_CONNECTED", "Not connected.", null);
        }
        if (command == null || command.length == 0) {
            return OperationResult.failed("SSH_COMMAND_EMPTY", "Remote command is empty.", null);
        }
        String remote = ShellQuotes.join(command);
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) this.session.openChannel("exec");
            channel.setCommand(remote);
            InputStream in = channel.getInputStream();
            InputStream err = channel.getErrStream();
            channel.connect((int) Math.min(Integer.MAX_VALUE,
                    this.config.getConnectTimeout().toMillis()));
            byte[] stdout = in.readAllBytes();
            byte[] stderr = err.readAllBytes();
            while (!channel.isClosed()) {
                Thread.sleep(50L);
            }
            int code = channel.getExitStatus();
            if (stdoutFile != null) {
                Files.write(stdoutFile, stdout);
            }
            if (stderrFile != null) {
                Files.write(stderrFile, stderr);
            }
            if (code != 0) {
                return OperationResult.failed("SSH_EXEC_FAILED",
                        "Remote command exited " + code + ": " + remote, null);
            }
            return OperationResult.success("SSH_EXEC_OK", "Remote command completed.", code);
        } catch (Exception ex) {
            return OperationResult.failed("SSH_EXEC_ERROR",
                    "Remote exec failed: " + ex.getMessage(), ex);
        } finally {
            if (channel != null) {
                channel.disconnect();
            }
        }
    }

    @Override
    public OperationResult<Void> uploadFile(Path localFile, String remotePath) {
        if (!isConnected()) {
            return OperationResult.failed("SSH_NOT_CONNECTED", "Not connected.", null);
        }
        if (localFile == null || !Files.isRegularFile(localFile)) {
            return OperationResult.failed("SSH_LOCAL_MISSING", "Local file missing: " + localFile, null);
        }
        if (remotePath == null || remotePath.isBlank() || remotePath.contains("..")) {
            return OperationResult.failed("SSH_REMOTE_PATH_INVALID",
                    "Remote path rejected: " + remotePath, null);
        }
        ChannelSftp sftp = null;
        try {
            sftp = (ChannelSftp) this.session.openChannel("sftp");
            sftp.connect((int) Math.min(Integer.MAX_VALUE,
                    this.config.getConnectTimeout().toMillis()));
            String tmp = remotePath + ".part." + ProcessHandle.current().pid();
            try (InputStream in = Files.newInputStream(localFile)) {
                sftp.put(in, tmp);
            }
            try {
                sftp.rm(remotePath);
            } catch (SftpException ignored) {
                // Destination may not exist yet.
            }
            sftp.rename(tmp, remotePath);
            return OperationResult.success("SSH_UPLOAD_OK",
                    "Uploaded " + localFile.getFileName(), null);
        } catch (Exception ex) {
            return OperationResult.failed("SSH_UPLOAD_FAILED",
                    "SFTP upload failed: " + ex.getMessage(), ex);
        } finally {
            if (sftp != null) {
                sftp.disconnect();
            }
        }
    }

    @Override
    public OperationResult<Void> downloadFile(String remotePath, Path localFile) {
        if (!isConnected()) {
            return OperationResult.failed("SSH_NOT_CONNECTED", "Not connected.", null);
        }
        if (remotePath == null || remotePath.isBlank() || remotePath.contains("..")) {
            return OperationResult.failed("SSH_REMOTE_PATH_INVALID",
                    "Remote path rejected: " + remotePath, null);
        }
        ChannelSftp sftp = null;
        try {
            sftp = (ChannelSftp) this.session.openChannel("sftp");
            sftp.connect((int) Math.min(Integer.MAX_VALUE,
                    this.config.getConnectTimeout().toMillis()));
            Path parent = localFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = localFile.resolveSibling(localFile.getFileName() + ".part");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                sftp.get(remotePath, out);
            }
            try {
                Files.move(tmp, localFile, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(tmp, localFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return OperationResult.success("SSH_DOWNLOAD_OK", "Downloaded " + remotePath, null);
        } catch (Exception ex) {
            return OperationResult.failed("SSH_DOWNLOAD_FAILED",
                    "SFTP download failed: " + ex.getMessage(), ex);
        } finally {
            if (sftp != null) {
                sftp.disconnect();
            }
        }
    }

    @Override
    public OperationResult<Void> mkdirRemote(String remotePath) {
        if (!isConnected()) {
            return OperationResult.failed("SSH_NOT_CONNECTED", "Not connected.", null);
        }
        if (remotePath == null || remotePath.isBlank() || remotePath.contains("..")) {
            return OperationResult.failed("SSH_REMOTE_PATH_INVALID",
                    "Remote path rejected: " + remotePath, null);
        }
        ChannelSftp sftp = null;
        try {
            sftp = (ChannelSftp) this.session.openChannel("sftp");
            sftp.connect((int) Math.min(Integer.MAX_VALUE,
                    this.config.getConnectTimeout().toMillis()));
            mkdirs(sftp, remotePath);
            return OperationResult.success("SSH_MKDIR_OK",
                    "Ensured remote directory " + remotePath, null);
        } catch (Exception ex) {
            return OperationResult.failed("SSH_MKDIR_FAILED",
                    "Remote mkdir failed: " + ex.getMessage(), ex);
        } finally {
            if (sftp != null) {
                sftp.disconnect();
            }
        }
    }

    private static void mkdirs(ChannelSftp sftp, String remotePath) throws SftpException {
        String normalized = remotePath.replaceAll("/{2,}", "/");
        if (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        String[] parts = normalized.split("/");
        StringBuilder current = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                if (current.length() == 0) {
                    current.append('/');
                }
                continue;
            }
            if (current.length() == 0) {
                current.append('/').append(part);
            } else if (current.length() == 1 && current.charAt(0) == '/') {
                current.append(part);
            } else {
                current.append('/').append(part);
            }
            String path = current.toString();
            try {
                sftp.stat(path);
            } catch (SftpException ex) {
                sftp.mkdir(path);
            }
        }
    }

    @Override
    public void close() {
        if (this.session != null) {
            this.session.disconnect();
            this.session = null;
        }
        if (this.sessionPassword != null) {
            java.util.Arrays.fill(this.sessionPassword, '\0');
        }
    }
}
