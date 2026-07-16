/*
 * Copyright (C) 2025 QuantumForge Team
 */
package quantumforge.ssh;

import quantumforge.operation.OperationResult;

/**
 * SSH file transfer manager for remote file operations.
 * 
 * NanoLabo provides:
 * - Continuous file fetching from remote servers
 * - Download all files from server
 * - Delete all files on server
 * - Auto-download of result files
 * - Offline mode with scheduled downloads
 * 
 * This is essential for remote job workflow management.
 */
public class SSHFileTransfer {

    private SSHServer server;
    private boolean continuousFetchEnabled;
    private boolean alwaysOffline;
    private int downloadInterval; // minutes

    public SSHFileTransfer(SSHServer server) {
        this.server = server;
        this.continuousFetchEnabled = true;
        this.alwaysOffline = false;
        this.downloadInterval = 5;
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

    /**
     * Download all result files from remote project directory
     */
    public OperationResult<Integer> downloadAllFilesResult(String remoteDir, String localDir) {
        return OperationResult.unsupported("SSH_DOWNLOAD_UNAVAILABLE",
                "No secure SFTP transport is implemented; no files were downloaded.");
    }

    /** @deprecated use the typed result method. */
    @Deprecated
    public boolean downloadAllFiles(String remoteDir, String localDir) {
        return this.downloadAllFilesResult(remoteDir, localDir).isSuccess();
    }

    /**
     * Delete all files on remote server to free space
     */
    public OperationResult<Integer> deleteAllOnServerResult(String remoteDir) {
        return OperationResult.unsupported("SSH_DELETE_UNAVAILABLE",
                "Remote deletion is disabled until canonical-path validation and confirmation exist.");
    }

    /** @deprecated use the typed result method. */
    @Deprecated
    public boolean deleteAllOnServer(String remoteDir) {
        return this.deleteAllOnServerResult(remoteDir).isSuccess();
    }

    /**
     * Start continuous file fetching
     */
    public void startContinuousFetch(String remoteDir, String localDir) {
        if (!this.continuousFetchEnabled || this.alwaysOffline) return;
        throw new UnsupportedOperationException(
                "Continuous SSH result transfer is not implemented in this release.");
    }

    // Getters
    public boolean isContinuousFetchEnabled() { return this.continuousFetchEnabled; }
    public boolean isAlwaysOffline() { return this.alwaysOffline; }
    public int getDownloadInterval() { return this.downloadInterval; }
}
