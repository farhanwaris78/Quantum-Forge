/*
 * Copyright (C) 2025 QuantumForge Team
 */
package quantumforge.ssh;

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
    public boolean downloadAllFiles(String remoteDir, String localDir) {
        // Fail closed: the previous implementation returned success without
        // opening an SFTP connection or transferring a byte.
        return false;
    }

    /**
     * Delete all files on remote server to free space
     */
    public boolean deleteAllOnServer(String remoteDir) {
        // Remote deletion remains deliberately disabled until canonical-path
        // validation, a real SSH session, and an explicit confirmation exist.
        return false;
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
