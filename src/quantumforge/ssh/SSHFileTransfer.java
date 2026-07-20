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

    /**
     * Batch-133 (Roadmap #92 verified-upload slice): hash-verified upload with
     * the temp-upload-&gt;verify-&gt;atomic-rename pattern the SftpTransferPlan
     * family mandates. The sequence is honest at every step:
     * <ol>
     *   <li>when overwrite is not allowed, an existence pre-check
     *       ({@code test -e}) refuses as TRANSFER_EXISTS before any byte moves
     *       - a remote hit is never clobbered to find out afterwards;</li>
     *   <li>the payload uploads to {@code <remote>.qftmp} - exclusively our
     *       own scratch name, never a user file;</li>
     *   <li>{@code sha256sum} on the temp file MUST equal the pinned hex - a
     *       mismatch deletes only our temp and fails as
     *       SFTP_VERIFY_MISMATCH (an interrupted/corrupted transfer can never
     *       masquerade as complete);</li>
     *   <li>a verified temp renames atomically ({@code mv -f}) to the final
     *       path; a rename failure leaves the temp for forensics and is
     *       named in the message.</li>
     * </ol>
     * Bulk download and remote deletion remain disabled by design.
     */
    public OperationResult<Void> uploadVerifiedResult(Path localFile, String remoteRelative,
            String expectedSha256Hex, boolean overwriteAllowed) {
        if (this.transport == null || !this.transport.isConnected()) {
            return OperationResult.unsupported("SSH_UPLOAD_UNAVAILABLE",
                    "No secure SFTP transport is connected; no files were uploaded.");
        }
        if (localFile == null || !Files.isRegularFile(localFile)) {
            return OperationResult.failed("SSH_LOCAL_MISSING", "Local file missing.", null);
        }
        String sha = expectedSha256Hex == null ? "" : expectedSha256Hex.trim()
                .toLowerCase(java.util.Locale.ROOT);
        if (!sha.matches("[0-9a-f]{64}")) {
            return OperationResult.failed("TRANSFER_HASH_GRAMMAR",
                    "the verification target must be sha256:<64 lowercase hex> - a"
                            + " transfer without a pinned hash is unreviewable.", null);
        }
        String remote;
        try {
            remote = RemotePathGuard.resolveUnderRoot(this.stagingRoot, remoteRelative);
        } catch (RuntimeException ex) {
            return OperationResult.failed("SSH_PATH_INVALID", ex.getMessage(), ex);
        }
        String temp = remote + ".qftmp";
        Path out = null;
        Path err = null;
        try {
            out = Files.createTempFile("qf-upl-", ".out");
            err = Files.createTempFile("qf-upl-", ".err");
            if (!overwriteAllowed) {
                OperationResult<Integer> probe = this.transport.exec(
                        new String[] {"test", "-e", remote}, out, err);
                if (probe.isSuccess()) {
                    return OperationResult.failed("TRANSFER_EXISTS",
                            "remote file already exists and overwrite is REFUSE-IF-EXISTS"
                                    + " by plan: " + remote, null);
                }
                if (!"SSH_EXEC_FAILED".equals(probe.getCode())) {
                    return OperationResult.failed("TRANSFER_PROBE_UNREADABLE",
                            "the overwrite pre-check itself failed ("
                                    + probe.getMessage() + ") - refusing to proceed"
                                    + " blind.", null);
                }
                // SSH_EXEC_FAILED with exit != 0 is the documented absent shape
                // for test -e; proceed.
            }
            OperationResult<Void> upload = this.transport.uploadFile(localFile, temp);
            if (!upload.isSuccess()) {
                return OperationResult.failed(upload.getCode(), upload.getMessage(), (Throwable) null);
            }
            OperationResult<Integer> hash = this.transport.exec(
                    new String[] {"sha256sum", temp}, out, err);
            String hashOut = Files.isRegularFile(out)
                    ? Files.readString(out).trim() : "";
            String remoteSha = hash.isSuccess() && !hashOut.isBlank()
                    ? hashOut.split("\\s+")[0].toLowerCase(java.util.Locale.ROOT) : "";
            if (!sha.equals(remoteSha)) {
                // Cleanup scope is exactly our own temp - nothing else, ever.
                this.transport.exec(new String[] {"rm", "-f", temp}, out, err);
                return OperationResult.failed("SFTP_VERIFY_MISMATCH",
                        "uploaded bytes do not match the pinned sha256 (pinned " + sha
                                + ", remote computed '"
                                + (remoteSha.isEmpty() ? "<unreadable>" : remoteSha)
                                + "') - the temp file was removed and NOTHING was"
                                + " renamed into place.", null);
            }
            OperationResult<Integer> rename = this.transport.exec(
                    new String[] {"mv", "-f", temp, remote}, out, err);
            if (!rename.isSuccess()) {
                return OperationResult.failed("SFTP_RENAME",
                        "verified upload could not be renamed into place ("
                                + rename.getMessage() + ") - the verified temp file is"
                                + " preserved at " + temp + " for forensics.", null);
            }
            return OperationResult.success("TRANSFER_VERIFIED",
                    "Uploaded, hash-verified and atomically renamed: " + remote, null);
        } catch (Exception ex) {
            return OperationResult.failed("SFTP_TRANSFER_ERROR",
                    "verified upload failed: " + ex.getMessage(), ex);
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
