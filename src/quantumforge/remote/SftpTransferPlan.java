/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.remote;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #92 (plan slice): fail-closed preparation of ONE safe SFTP
 * staging step (upload direction only). Nothing transfers from this build -
 * the plan pins down everything the runtime execution must honor.
 *
 * <p>Safety semantics (the #92 "safe staging" points, made structural):</p>
 * <ul>
 *   <li>the LOCAL file must exist, be a regular file, stay under 4 GiB and
 *       be resolved INSIDE the project directory (absolute paths and {@code
 *       ..} escapes are refused); its SHA-256 and byte size are pinned AT
 *       DRAFT TIME, so the post-transfer integrity check has a checksum
 *       target that cannot drift between planning and execution;</li>
 *   <li>the REMOTE path must be absolute POSIX, free of whitespace, quotes,
 *       expansion and command-separator characters, and must not climb with
 *       {@code ..} segments; a remote directory marker (trailing '/') is
 *       refused - the remote FILE name must be explicit, never guessed;</li>
 *   <li>overwrite semantics are EXPLICIT per plan: overwriteAllowed=false
 *       (the default at the GUI) means the runtime MUST refuse when the
 *       remote exists - flipping it is a deliberate analyst act, and the
 *       plan text prints it in uppercase both ways;</li>
 *   <li>the sha256-verify-after-transfer step is declared MANDATORY in the
 *       plan payload; executing the transfer itself (SFTP channel, host-key
 *       checked per the SSH draft) is the remaining #92 runtime depth.</li>
 * </ul>
 *
 * <p>Refusal codes: SFTP_LOCAL_PATH, SFTP_LOCAL_IO, SFTP_TOO_LARGE,
 * SFTP_REMOTE_PATH.</p>
 */
public final class SftpTransferPlan {

    /** Local file size cap for one staging step. */
    public static final long MAX_FILE_BYTES = 4L * 1024L * 1024L * 1024L;

    /** The prepared, reviewed step. */
    public static final class TransferStep {
        private final String localName;
        private final long localBytes;
        private final String localSha256;
        private final String remotePath;
        private final boolean overwriteAllowed;

        TransferStep(String localName, long localBytes, String localSha256,
                String remotePath, boolean overwriteAllowed) {
            this.localName = localName;
            this.localBytes = localBytes;
            this.localSha256 = localSha256;
            this.remotePath = remotePath;
            this.overwriteAllowed = overwriteAllowed;
        }

        /** Project-relative local file (never absolute). */
        public String getLocalName() { return this.localName; }
        public long getLocalBytes() { return this.localBytes; }
        /** SHA-256 hex pinned at draft time - the integrity target. */
        public String getLocalSha256() { return this.localSha256; }
        public String getRemotePath() { return this.remotePath; }
        public boolean isOverwriteAllowed() { return this.overwriteAllowed; }

        /** Human-readable plan block, all decisions spelled out. */
        public String render() {
            StringBuilder text = new StringBuilder();
            text.append("# SFTP staging plan (QuantumForge, Roadmap #92) - REVIEW "
                    + "before execution\n");
            text.append("# direction UPLOAD (download staging is runtime depth); "
                    + "execute ONLY over the host-key-checked SSH channel from the "
                    + "SSH_CONFIG_DRAFT.\n");
            text.append("local_file      = ").append(this.localName).append('\n');
            text.append("local_bytes     = ").append(this.localBytes).append('\n');
            text.append("local_sha256    = ").append(this.localSha256)
                    .append("   # pinned at draft time\n");
            text.append("remote_path     = ").append(this.remotePath).append('\n');
            text.append("overwrite       = ")
                    .append(this.overwriteAllowed ? "ALLOWED" : "REFUSE-IF-EXISTS")
                    .append('\n');
            text.append("verify_after    = sha256 MUST match local_sha256 "
                    + "(mandatory)\n");
            text.append("transfer_status = NOT STARTED - planning slice only\n");
            return text.toString();
        }
    }

    private SftpTransferPlan() {
    }

    /**
     * Prepares one reviewed step. Codes: SFTP_LOCAL_PATH, SFTP_LOCAL_IO,
     * SFTP_TOO_LARGE, SFTP_REMOTE_PATH.
     */
    public static OperationResult<TransferStep> prepare(Path projectDir,
            String localName, String remotePath, boolean overwriteAllowed) {
        if (projectDir == null || !Files.isDirectory(projectDir)) {
            return OperationResult.failed("SFTP_LOCAL_PATH",
                    "The project directory is unavailable.", null);
        }
        if (localName == null || localName.isBlank()) {
            return OperationResult.failed("SFTP_LOCAL_PATH",
                    "A project-relative local file name is required.", null);
        }
        String name = localName.trim();
        if (Path.of(name).isAbsolute() || name.contains("..")
                || containsUnsafe(name)) {
            return OperationResult.failed("SFTP_LOCAL_PATH",
                    "The local file must be project-relative with no absolute "
                            + "prefix, no '..' segments and no unsafe characters "
                            + "(got '" + name + "').",
                    null);
        }
        Path resolved = projectDir.resolve(name).normalize();
        if (!resolved.startsWith(projectDir.normalize())) {
            return OperationResult.failed("SFTP_LOCAL_PATH",
                    "The local file escapes the project directory after "
                            + "normalization - refused.",
                    null);
        }
        if (!Files.isRegularFile(resolved)) {
            return OperationResult.failed("SFTP_LOCAL_IO",
                    "The local file '" + name + "' does not exist inside the "
                            + "project or is not a regular file - nothing is "
                            + "staged silently.",
                    null);
        }
        long size;
        try {
            size = Files.size(resolved);
        } catch (IOException ex) {
            return OperationResult.failed("SFTP_LOCAL_IO",
                    "Could not stat '" + name + "': " + ex.getMessage(), null);
        }
        if (size > MAX_FILE_BYTES) {
            return OperationResult.failed("SFTP_TOO_LARGE",
                    "'" + name + "' is " + size + " bytes; one staging step is "
                            + "capped at " + MAX_FILE_BYTES + " bytes.",
                    null);
        }
        String sha256;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            sha256 = toHex(digest.digest(Files.readAllBytes(resolved)));
        } catch (IOException ex) {
            return OperationResult.failed("SFTP_LOCAL_IO",
                    "Hashing '" + name + "' failed: " + ex.getMessage(), null);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the JDK", ex);
        }
        if (remotePath == null || remotePath.isBlank()) {
            return OperationResult.failed("SFTP_REMOTE_PATH",
                    "A remote absolute POSIX path is required.", null);
        }
        String remote = remotePath.trim();
        if (!remote.startsWith("/") || remote.endsWith("/") || remote.contains("//")
                || containsUnsafe(remote)) {
            return OperationResult.failed("SFTP_REMOTE_PATH",
                    "The remote path must be an explicit absolute POSIX FILE path "
                            + "with no trailing '/', no '//' and no unsafe "
                            + "characters (got '" + remote + "').",
                    null);
        }
        for (String segment : remote.split("/")) {
            if (segment.equals("..")) {
                return OperationResult.failed("SFTP_REMOTE_PATH",
                        "The remote path climbs with a '..' segment - refused "
                                + "(no parent-directory escapes).",
                        null);
            }
        }
        return OperationResult.success("SFTP_PLAN_OK", "Step prepared.",
                new TransferStep(name, size, sha256, remote, overwriteAllowed));
    }

    private static boolean containsUnsafe(String value) {
        for (int idx = 0; idx < value.length(); idx += 1) {
            char ch = value.charAt(idx);
            if (Character.isWhitespace(ch) || ch == '"' || ch == '\'' || ch == '$'
                    || ch == '`' || ch == ';' || ch == '|' || ch == '\\') {
                return true;
            }
        }
        return false;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format(Locale.ROOT, "%02x", b));
        }
        return hex.toString();
    }
}
