/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.ssh;

import java.nio.file.Files;
import java.nio.file.Path;

import quantumforge.operation.OperationResult;

/**
 * SFTP transfer helper with path guards and fail-closed defaults.
 */
public class SSHFileTransfer {

    private final SSHServer server;
    private SshTransport transport;
    private String stagingRoot = "/tmp/quantumforge";
    private boolean continuousFetchEnabled = true;
    private boolean alwaysOffline = false;
    private int downloadInterval = 5;

    public SSHFileTransfer(SSHServer server) {
        this.server = server;
    }

    public void setTransport(SshTransport transport) {
        this.transport = transport;
    }

    public void setStagingRoot(String stagingRoot) {
        this.stagingRoot = RemotePathGuard.normalizeStagingRoot(stagingRoot);
    }

    public void setContinuousFetch(boolean enabled) {
        this.continuousFetchEnabled = enabled;
    }

    public void setAlwaysOffline(boolean offline) {
        this.alwaysOffline = offline;
    }

    public void setDownloadInterval(int minutes) {
        this.downloadInterval = Math.max(1, minutes);
    }

    public OperationResult<Integer> downloadAllFilesResult(String remoteDir, String localDir) {
        if (this.transport == null || !this.transport.isConnected()) {
            return OperationResult.unsupported("SSH_DOWNLOAD_UNAVAILABLE",
                    "No secure SFTP transport is connected; no files were downloaded.");
        }
        if (remoteDir == null || remoteDir.contains("..") || localDir == null || localDir.isBlank()) {
            return OperationResult.failed("SSH_PATH_INVALID", "Remote/local path rejected.", null);
        }
        // Selective sync is not yet implemented; refuse broad recursive download.
        return OperationResult.unsupported("SSH_BULK_DOWNLOAD_UNAVAILABLE",
                "Bulk remote directory download is disabled until a selective manifest sync exists. "
                        + "Use downloadFile for required parser inputs.");
    }

    public OperationResult<Void> downloadFileResult(String remoteRelative, Path localFile) {
        if (this.transport == null || !this.transport.isConnected()) {
            return OperationResult.unsupported("SSH_DOWNLOAD_UNAVAILABLE",
                    "No secure SFTP transport is connected; no files were downloaded.");
        }
        try {
            String remote = RemotePathGuard.resolveUnderRoot(this.stagingRoot, remoteRelative);
            return this.transport.downloadFile(remote, localFile);
        } catch (RuntimeException ex) {
            return OperationResult.failed("SSH_PATH_INVALID", ex.getMessage(), ex);
        }
    }

    public OperationResult<Void> uploadFileResult(Path localFile, String remoteRelative) {
        if (this.transport == null || !this.transport.isConnected()) {
            return OperationResult.unsupported("SSH_UPLOAD_UNAVAILABLE",
                    "No secure SFTP transport is connected; no files were uploaded.");
        }
        if (localFile == null || !Files.isRegularFile(localFile)) {
            return OperationResult.failed("SSH_LOCAL_MISSING", "Local file missing.", null);
        }
        try {
            String remote = RemotePathGuard.resolveUnderRoot(this.stagingRoot, remoteRelative);
            return this.transport.uploadFile(localFile, remote);
        } catch (RuntimeException ex) {
            return OperationResult.failed("SSH_PATH_INVALID", ex.getMessage(), ex);
        }
    }

    /** @deprecated use the typed result method. */
    @Deprecated
    public boolean downloadAllFiles(String remoteDir, String localDir) {
        return this.downloadAllFilesResult(remoteDir, localDir).isSuccess();
    }

    public OperationResult<Integer> deleteAllOnServerResult(String remoteDir) {
        return OperationResult.unsupported("SSH_DELETE_UNAVAILABLE",
                "Remote deletion is disabled until canonical-path validation and confirmation exist.");
    }

    /** @deprecated use the typed result method. */
    @Deprecated
    public boolean deleteAllOnServer(String remoteDir) {
        return this.deleteAllOnServerResult(remoteDir).isSuccess();
    }

    public void startContinuousFetch(String remoteDir, String localDir) {
        if (!this.continuousFetchEnabled || this.alwaysOffline) {
            return;
        }
        throw new UnsupportedOperationException(
                "Continuous SSH result transfer is not implemented in this release.");
    }

    public boolean isContinuousFetchEnabled() {
        return this.continuousFetchEnabled;
    }

    public boolean isAlwaysOffline() {
        return this.alwaysOffline;
    }

    public int getDownloadInterval() {
        return this.downloadInterval;
    }

    public SSHServer getServer() {
        return this.server;
    }
}
