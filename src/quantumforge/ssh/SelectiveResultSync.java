/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.ssh;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import quantumforge.com.log.AppLog;
import quantumforge.hpc.ResultSyncManifest;
import quantumforge.operation.OperationResult;

/**
 * Manifest-driven selective SFTP download of required/optional result files.
 *
 * <p>Batch-128 honesty hardening (Roadmap #98 runtime slice):</p>
 * <ul>
 *   <li>a partial sync ATTACHES its report on every verdict - the caller can
 *       always see what DID download, what is missing, and what failed;</li>
 *   <li>path-rejection / local-escape events land in {@code failed} and are
 *       NAMED in the verdict message - a security refusal is never silent;</li>
 *   <li>skipped LARGE_OPTIONAL entries are NAMED in the report (declared but
 *       deliberately not fetched is information, not noise);</li>
 *   <li>a mid-loop transport death stops the walk as SYNC_TRANSPORT with the
 *       partial report - a dead channel must not flood every remaining entry
 *       into "missing";</li>
 *   <li>the checksum cache is an OPTIMIZATION: load/save/probe failures
 *       degrade to warnings, never to a failed sync verdict.</li>
 * </ul>
 */
public final class SelectiveResultSync {

    public static final class SyncReport {
        private final List<String> downloaded = new ArrayList<>();
        private final List<String> missingRequired = new ArrayList<>();
        private final List<String> missingOptional = new ArrayList<>();
        private final List<String> failed = new ArrayList<>();
        private final List<String> skippedLarge = new ArrayList<>();

        public List<String> getDownloaded() { return List.copyOf(this.downloaded); }
        public List<String> getMissingRequired() { return List.copyOf(this.missingRequired); }
        public List<String> getMissingOptional() { return List.copyOf(this.missingOptional); }
        public List<String> getFailed() { return List.copyOf(this.failed); }
        /** LARGE_OPTIONAL entries declared in the manifest but intentionally not fetched. */
        public List<String> getSkippedLarge() { return List.copyOf(this.skippedLarge); }

        public boolean isComplete() {
            return this.missingRequired.isEmpty() && this.failed.isEmpty();
        }
    }

    private final SshTransport transport;
    private final String stagingRoot;
    private SyncChecksumCache checksumCache;

    public SelectiveResultSync(SshTransport transport, String stagingRoot) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.stagingRoot = RemotePathGuard.normalizeStagingRoot(stagingRoot);
    }

    public void setChecksumCache(SyncChecksumCache checksumCache) {
        this.checksumCache = checksumCache;
    }

    public OperationResult<SyncReport> sync(String remoteJobRelativeDir, Path localDir,
                                            ResultSyncManifest manifest, boolean includeLarge) {
        // The legacy posture: no pins, downloads NOT hash-verified - stated in
        // the verdict message, never implied (batch 146).
        return this.sync(remoteJobRelativeDir, localDir, manifest, includeLarge,
                java.util.Map.of());
    }

    /**
     * Batch-146 (Roadmap #92 sync-manifest hash pinning): same walk, but every
     * manifest entry whose relative path carries a pin in
     * {@code pinsByRelativePath} downloads through
     * {@link SSHFileTransfer#downloadVerifiedResult} (the batch-136 two-sided
     * sha256: wrong-source pre-check + in-flight post-verify + atomic rename).
     * Honesty rules on top of the batch-128 set:
     * <ul>
     *   <li>an EXPLICIT null pins map is an NPE - pass
     *       {@code Map.of()} for the unpinned posture; null would hide
     *       intent;</li>
     *   <li>a refused/missing PINNED entry still sorts by priority when the
     *       cause is absence (TRANSFER_SOURCE_MISSING -&gt; missing
     *       required/optional), but every OTHER refusal (mismatch, grammar,
     *       probe, local) lands in {@code failed} with its typed code named -
     *       a verification refusal is a security finding, never a quiet
     *       'missing';</li>
     *   <li>the verdict message STATES the posture either way: how many
     *       files were hash-verified against supplied pins, or that
     *       downloads were NOT hash-verified;</li>
     *   <li>pin-verified entries allow overwrite of their previous local
     *       copy (that is what a sync update IS); the checksum cache
     *       short-circuit still runs first.</li>
     * </ul>
     */
    public OperationResult<SyncReport> sync(String remoteJobRelativeDir, Path localDir,
                                            ResultSyncManifest manifest,
                                            boolean includeLarge,
                                            java.util.Map<String, String> pinsByRelativePath) {
        java.util.Objects.requireNonNull(pinsByRelativePath,
                "pins map is required - pass Map.of() for the unpinned posture; null "
                        + "would hide intent");
        if (!this.transport.isConnected()) {
            return OperationResult.unsupported("SSH_NOT_CONNECTED",
                    "No connected SFTP transport; nothing was downloaded.");
        }
        if (localDir == null) {
            return OperationResult.failed("LOCAL_DIR_MISSING", "Local directory is null.", null);
        }
        if (manifest == null || manifest.getEntries().isEmpty()) {
            return OperationResult.failed("MANIFEST_EMPTY", "Result sync manifest is empty.", null);
        }
        try {
            Files.createDirectories(localDir);
            if (this.checksumCache != null) {
                try {
                    this.checksumCache.load();
                } catch (Exception cacheEx) {
                    // A cache is an optimization - never let it sink a sync.
                    AppLog.warn("sync", "checksum cache load degraded to warn-only: "
                            + cacheEx.getMessage());
                }
            }
            String remoteBase = RemotePathGuard.resolveUnderRoot(this.stagingRoot, remoteJobRelativeDir);
            SSHFileTransfer verified = pinsByRelativePath.isEmpty() ? null
                    : new SSHFileTransfer(new SSHServer("sync-pins"));
            if (verified != null) {
                verified.setTransport(this.transport);
                verified.setStagingRoot(this.stagingRoot);
            }
            int verifiedCount = 0;
            SyncReport report = new SyncReport();
            for (ResultSyncManifest.Entry entry : manifest.getEntries()) {
                if (entry.getPriority() == ResultSyncManifest.Priority.LARGE_OPTIONAL && !includeLarge) {
                    // Declared but deliberately not fetched - named, not hidden.
                    report.skippedLarge.add(entry.getRelativePath());
                    continue;
                }
                String remotePath = remoteBase + "/" + entry.getRelativePath();
                if (remotePath.contains("..")) {
                    report.failed.add(entry.getRelativePath() + " (path rejected)");
                    continue;
                }
                Path localFile = localDir.resolve(entry.getRelativePath()).normalize();
                if (!localFile.startsWith(localDir.normalize())) {
                    report.failed.add(entry.getRelativePath() + " (local escape)");
                    continue;
                }
                if (this.checksumCache != null) {
                    try {
                        if (this.checksumCache.isUpToDate(localFile, entry.getRelativePath())) {
                            report.downloaded.add(entry.getRelativePath() + " (cache-hit)");
                            continue;
                        }
                    } catch (Exception cacheEx) {
                        AppLog.warn("sync", "checksum probe degraded to warn-only for "
                                + entry.getRelativePath() + ": " + cacheEx.getMessage());
                    }
                }
                Path parent = localFile.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                String pin = pinsByRelativePath.get(entry.getRelativePath());
                OperationResult<Void> dl;
                String marker = "";
                if (pin != null) {
                    dl = verified.downloadVerifiedResult(
                            remoteJobRelativeDir + "/" + entry.getRelativePath(),
                            localFile, pin, true);
                    if (dl.isSuccess()) {
                        verifiedCount++;
                        marker = " (pin-verified)";
                    }
                } else {
                    dl = this.transport.downloadFile(remotePath, localFile);
                }
                if (dl.isSuccess()) {
                    report.downloaded.add(entry.getRelativePath() + marker);
                    if (this.checksumCache != null) {
                        try {
                            this.checksumCache.record(localFile, entry.getRelativePath());
                        } catch (Exception cacheEx) {
                            AppLog.warn("sync", "checksum record degraded to warn-only for "
                                    + entry.getRelativePath() + ": " + cacheEx.getMessage());
                        }
                    }
                } else {
                    if (!this.transport.isConnected()) {
                        // The channel died mid-walk: STOP. Flooding every
                        // remaining entry into "missing" would fabricate
                        // absence evidence that does not exist.
                        AppLog.warn("sync", "transport died at " + entry.getRelativePath());
                        return OperationResult.failed("SYNC_TRANSPORT",
                                "Transport disconnected mid-sync at '"
                                        + entry.getRelativePath() + "' after "
                                        + report.getDownloaded().size()
                                        + " download(s) - remaining entries were NOT"
                                        + " probed and are NOT declared missing.",
                                report, null);
                    }
                    if (pin != null && !"TRANSFER_SOURCE_MISSING".equals(dl.getCode())) {
                        // A verification refusal is a security finding, never a
                        // quiet 'missing' (batch 146).
                        report.failed.add(entry.getRelativePath()
                                + " (verification refused: " + dl.getCode() + ")");
                        AppLog.warn("sync", "Pinned entry refused " + entry.getRelativePath()
                                + ": [" + dl.getCode() + "] " + dl.getMessage());
                    } else if (entry.getPriority() == ResultSyncManifest.Priority.REQUIRED) {
                        report.missingRequired.add(entry.getRelativePath());
                        AppLog.warn("sync", "Missing/failed " + entry.getRelativePath()
                                + ": " + dl.getMessage());
                    } else {
                        report.missingOptional.add(entry.getRelativePath());
                        AppLog.warn("sync", "Missing/failed " + entry.getRelativePath()
                                + ": " + dl.getMessage());
                    }
                }
            }
            if (this.checksumCache != null) {
                try {
                    this.checksumCache.save();
                } catch (Exception cacheEx) {
                    AppLog.warn("sync", "checksum cache save degraded to warn-only: "
                            + cacheEx.getMessage());
                }
            }
            String posture = pinsByRelativePath.isEmpty()
                    ? " Downloads were NOT hash-verified (no pins supplied)."
                    : " " + verifiedCount + " of them were hash-verified against the"
                    + " supplied pins.";
            if (!report.isComplete()) {
                // Attach the report: the whole truth of the partial sync. The
                // message names every REQUIRED miss AND every security-grade
                // failure - a path rejection must never hide behind the count.
                StringBuilder detail = new StringBuilder("Required files missing: "
                        + report.getMissingRequired());
                if (!report.getFailed().isEmpty()) {
                    detail.append("; SECURITY/failed entries: ").append(report.getFailed());
                }
                detail.append(posture);
                return OperationResult.failed("SYNC_INCOMPLETE", detail.toString(),
                        report, null);
            }
            return OperationResult.success("SYNC_OK",
                    "Downloaded/kept " + report.getDownloaded().size()
                            + " file(s), skipped " + report.getSkippedLarge().size()
                            + " declared large file(s)." + posture, report);
        } catch (Exception ex) {
            return OperationResult.failed("SYNC_ERROR", "Selective sync failed: " + ex.getMessage(), ex);
        }
    }
}
