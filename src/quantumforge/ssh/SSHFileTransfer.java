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

    /**
     * Batch-136 (Roadmap #92 verified-download slice): hash-verified download -
     * the mirror of {@link #uploadVerifiedResult}, with the hash verified on
     * BOTH sides because a download has two distinct wrongs to refuse:
     * <ol>
     *   <li>WRONG SOURCE - {@code sha256sum <remote>} MUST equal the pinned
     *       hex BEFORE any byte moves: a mismatch refuses as
     *       TRANSFER_SOURCE_MISMATCH with 'NOTHING was downloaded' stated, an
     *       absent source (the documented exit != 0 shape) refuses as
     *       TRANSFER_SOURCE_MISSING, an unparseable hash refuses as
     *       TRANSFER_SOURCE_UNREADABLE, and a dead probe refuses as
     *       TRANSFER_PROBE_UNREADABLE - the transfer never proceeds blind;</li>
     *   <li>CORRUPTION IN FLIGHT - bytes land only at our own scratch name
     *       {@code <localFile>.qftmp}; the local sha256 of that temp MUST
     *       match the pin (the remote pre-check cannot see corruption
     *       introduced between host and disk) - a mismatch removes ONLY the
     *       temp and fails SFTP_VERIFY_MISMATCH with 'NOTHING was renamed
     *       into place' stated; a failed download likewise cleans its
     *       partial temp;</li>
     *   <li>a verified temp renames into place (ATOMIC_MOVE where the
     *       filesystem offers it, otherwise a stated plain rename); any
     *       rename failure preserves the verified temp for forensics and is
     *       named (SFTP_LOCAL_RENAME).</li>
     * </ol>
     * The overwrite posture is PRE-CHECKED locally BEFORE any byte moves
     * (TRANSFER_LOCAL_EXISTS), the destination directory MUST exist already
     * (TRANSFER_LOCAL_DIR - a transfer never mkdir's silently), and the pin
     * grammar is identical to the upload side (TRANSFER_HASH_GRAMMAR).
     */
    public OperationResult<Void> downloadVerifiedResult(String remoteRelative, Path localFile,
            String expectedSha256Hex, boolean overwriteAllowed) {
        if (this.transport == null || !this.transport.isConnected()) {
            return OperationResult.unsupported("SSH_DOWNLOAD_UNAVAILABLE",
                    "No secure SFTP transport is connected; no files were downloaded.");
        }
        if (localFile == null) {
            return OperationResult.failed("TRANSFER_LOCAL_DIR",
                    "a local destination path is required.", null);
        }
        Path parent = localFile.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return OperationResult.failed("TRANSFER_LOCAL_DIR",
                    "the destination directory must exist already - a verified transfer"
                            + " creates no directories silently: "
                            + (parent == null ? "<none>" : parent), null);
        }
        if (!overwriteAllowed && Files.exists(localFile)) {
            return OperationResult.failed("TRANSFER_LOCAL_EXISTS",
                    "local file already exists and overwrite is REFUSE-IF-EXISTS by plan: "
                            + localFile + " - refusing BEFORE any byte moves.", null);
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
        Path temp = localFile.resolveSibling(localFile.getFileName() + ".qftmp");
        Path out = null;
        Path err = null;
        try {
            out = Files.createTempFile("qf-dnl-", ".out");
            err = Files.createTempFile("qf-dnl-", ".err");
            // (1) wrong-source pre-check BEFORE any byte moves.
            OperationResult<Integer> probe = this.transport.exec(
                    new String[] {"sha256sum", remote}, out, err);
            if (!probe.isSuccess()) {
                if ("SSH_EXEC_FAILED".equals(probe.getCode())) {
                    return OperationResult.failed("TRANSFER_SOURCE_MISSING",
                            "the pinned source is absent or unreadable on the remote ("
                                    + remote
                                    + ") - refusing BEFORE downloading anything.", null);
                }
                return OperationResult.failed("TRANSFER_PROBE_UNREADABLE",
                        "the remote hash pre-check itself failed ("
                                + probe.getMessage()
                                + ") - refusing to proceed blind.", null);
            }
            String probeOut = Files.isRegularFile(out) ? Files.readString(out).trim() : "";
            String remoteSha = probeOut.isBlank() ? ""
                    : probeOut.split("\\s+")[0].toLowerCase(java.util.Locale.ROOT);
            if (!remoteSha.matches("[0-9a-f]{64}")) {
                return OperationResult.failed("TRANSFER_SOURCE_UNREADABLE",
                        "the remote source produced no parseable sha256 (got '"
                                + (probeOut.isBlank() ? "<empty>" : probeOut)
                                + "') - refusing BEFORE downloading anything.", null);
            }
            if (!sha.equals(remoteSha)) {
                return OperationResult.failed("TRANSFER_SOURCE_MISMATCH",
                        "the remote source does not match the pinned sha256 (pinned "
                                + sha + ", remote computed " + remoteSha
                                + ") - NOTHING was downloaded.", null);
            }
            // (2) corruption-in-flight post-check: sink to our own temp name only.
            OperationResult<Void> download = this.transport.downloadFile(remote, temp);
            if (!download.isSuccess()) {
                Files.deleteIfExists(temp);
                return OperationResult.failed(download.getCode(),
                        download.getMessage() + " - the partial temp file was removed"
                                + " and NOTHING was renamed into place.", (Throwable) null);
            }
            String localSha;
            try {
                localSha = SyncChecksumCache.sha256(temp);
            } catch (java.io.IOException hashFail) {
                Files.deleteIfExists(temp);
                return OperationResult.failed("TRANSFER_LOCAL_UNREADABLE",
                        "could not hash the downloaded temp file (" + hashFail.getMessage()
                                + ") - the temp was removed and NOTHING was renamed"
                                + " into place.", hashFail);
            }
            if (!sha.equals(localSha)) {
                Files.deleteIfExists(temp);
                return OperationResult.failed("SFTP_VERIFY_MISMATCH",
                        "downloaded bytes do not match the pinned sha256 (pinned " + sha
                                + ", local computed " + localSha + ") - the temp file was"
                                + " removed and NOTHING was renamed into place.", null);
            }
            // (3) rename into place: any failure preserves the verified temp.
            boolean atomic;
            try {
                atomic = moveVerifiedTemp(temp, localFile, overwriteAllowed);
            } catch (java.io.IOException renameFail) {
                return OperationResult.failed("SFTP_LOCAL_RENAME",
                        "verified download could not be renamed into place ("
                                + renameFail.getMessage()
                                + ") - the verified temp file is preserved at " + temp
                                + " for forensics.", renameFail);
            }
            return OperationResult.success("TRANSFER_VERIFIED",
                    "Downloaded, hash-verified (remote source pre-check + local"
                            + " post-verify) and renamed"
                            + (atomic ? " atomically"
                                    : " (plain rename - this filesystem offers no atomic"
                                    + " move)")
                            + " into place: " + localFile, null);
        } catch (Exception ex) {
            return OperationResult.failed("SFTP_TRANSFER_ERROR",
                    "verified download failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Rename a verified temp into place; returns true when the filesystem
     * performed an ATOMIC_MOVE, false when it fell back to a plain rename
     * (the fallback is STATED by the caller's success message).
     */
    private static boolean moveVerifiedTemp(Path temp, Path dest, boolean replace)
            throws java.io.IOException {
        try {
            if (replace) {
                Files.move(temp, dest, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(temp, dest, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            }
            return true;
        } catch (java.nio.file.AtomicMoveNotSupportedException notSupported) {
            if (replace) {
                Files.move(temp, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(temp, dest);
            }
            return false;
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
