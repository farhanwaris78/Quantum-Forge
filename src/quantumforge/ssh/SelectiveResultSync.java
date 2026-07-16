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
 */
public final class SelectiveResultSync {

    public static final class SyncReport {
        private final List<String> downloaded = new ArrayList<>();
        private final List<String> missingRequired = new ArrayList<>();
        private final List<String> missingOptional = new ArrayList<>();
        private final List<String> failed = new ArrayList<>();

        public List<String> getDownloaded() { return List.copyOf(this.downloaded); }
        public List<String> getMissingRequired() { return List.copyOf(this.missingRequired); }
        public List<String> getMissingOptional() { return List.copyOf(this.missingOptional); }
        public List<String> getFailed() { return List.copyOf(this.failed); }

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
                this.checksumCache.load();
            }
            String remoteBase = RemotePathGuard.resolveUnderRoot(this.stagingRoot, remoteJobRelativeDir);
            SyncReport report = new SyncReport();
            int skipped = 0;
            for (ResultSyncManifest.Entry entry : manifest.getEntries()) {
                if (entry.getPriority() == ResultSyncManifest.Priority.LARGE_OPTIONAL && !includeLarge) {
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
                if (this.checksumCache != null
                        && this.checksumCache.isUpToDate(localFile, entry.getRelativePath())) {
                    skipped++;
                    report.downloaded.add(entry.getRelativePath() + " (cache-hit)");
                    continue;
                }
                Path parent = localFile.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                OperationResult<Void> dl = this.transport.downloadFile(remotePath, localFile);
                if (dl.isSuccess()) {
                    report.downloaded.add(entry.getRelativePath());
                    if (this.checksumCache != null) {
                        this.checksumCache.record(localFile, entry.getRelativePath());
                    }
                } else {
                    if (entry.getPriority() == ResultSyncManifest.Priority.REQUIRED) {
                        report.missingRequired.add(entry.getRelativePath());
                    } else {
                        report.missingOptional.add(entry.getRelativePath());
                    }
                    AppLog.warn("sync", "Missing/failed " + entry.getRelativePath()
                            + ": " + dl.getMessage());
                }
            }
            if (this.checksumCache != null) {
                this.checksumCache.save();
            }
            if (!report.isComplete()) {
                return OperationResult.failed("SYNC_INCOMPLETE",
                        "Required files missing: " + report.getMissingRequired(), null);
            }
            return OperationResult.success("SYNC_OK",
                    "Downloaded/kept " + report.getDownloaded().size()
                            + " file(s) (cache hits ≈ " + skipped + ").", report);
        } catch (Exception ex) {
            return OperationResult.failed("SYNC_ERROR", "Selective sync failed: " + ex.getMessage(), ex);
        }
    }
}
