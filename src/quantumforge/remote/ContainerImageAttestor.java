/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.remote;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import quantumforge.operation.OperationResult;
import quantumforge.ssh.SshTransport;

/**
 * Roadmap #103 (metrology slice, batch 149): READ-ONLY, digest-pinned
 * attestation of a container image FILE on a connected remote host. This is
 * the "probe, then re-attest before exec" half of the container story: a
 * future launch step may require a fresh {@code CONTAINER_ATTEST_OK}
 * attestation, but this class itself pulls nothing, executes nothing, and
 * writes nothing - its entire remote footprint is {@code test -f} and
 * {@code sha256sum}.
 *
 * <p>Layer honesty (load-bearing): a registry manifest digest - the
 * {@code @sha256:} pin inside {@code name:tag@sha256:<digest>} - and the
 * sha256 of an on-disk image FILE are DIFFERENT layers. Apptainer/singularity
 * rebuild or convert images, so a downloaded {@code .sif}'s file hash does
 * NOT reproduce the registry digest. This class therefore attests the file
 * bytes against an EXPECTED FILE HASH THE ANALYST SUPPLIES (typically the
 * output of a previous attestation they recorded) and claims NOTHING about
 * the registry digest. The two layers are named in every verdict.</p>
 *
 * <p>Fail-closed rules:</p>
 * <ul>
 *   <li>no connected transport -&gt; CONTAINER_PROBE_UNAVAILABLE (nothing was
 *       probed);</li>
 *   <li>blank / relative / traversal-carrying path -&gt; CONTAINER_PROBE_PATH
 *       (attestation against an etymologically unclear location is
 *       unreviewable);</li>
 *   <li>blank or non-grammar expected hash -&gt; CONTAINER_PROBE_EXPECTATION:
 *       measurement without a pin is measurement without a verdict - this
 *       build never invents one;</li>
 *   <li>absent file -&gt; CONTAINER_PROBE_ABSENT (nothing is pulled and
 *       nothing is installed to make the probe pass);</li>
 *   <li>unreadable probe or garbage checksum output -&gt; refused, never
 *       guessed;</li>
 *   <li>hash mismatch -&gt; CONTAINER_ATTEST_MISMATCH: the bytes are NOT what
 *       was pinned; NOTHING proceeds by default - the discrepancy is the
 *       analyst's to resolve, then re-attest.</li>
 * </ul>
 *
 * <p>Every failure payload carries the {@link Attestation} accumulated so
 * far (command journal included), so a report can state exactly how far the
 * probe went. Codes: CONTAINER_PROBE_UNAVAILABLE, CONTAINER_PROBE_PATH,
 * CONTAINER_PROBE_EXPECTATION, CONTAINER_PROBE_ABSENT,
 * CONTAINER_PROBE_UNREADABLE, CONTAINER_PROBE_CHECKSUM,
 * CONTAINER_PROBE_ERROR, CONTAINER_ATTEST_MISMATCH, CONTAINER_ATTEST_OK.</p>
 */
public final class ContainerImageAttestor {

    /** The completed (or halted) attestation view with its command journal. */
    public static final class Attestation {
        private final String absolutePath;
        private final String expectedSha256;
        private final String actualSha256;
        private final boolean matched;
        private final List<String> steps;

        Attestation(String absolutePath, String expectedSha256, String actualSha256,
                boolean matched, List<String> steps) {
            this.absolutePath = absolutePath;
            this.expectedSha256 = expectedSha256;
            this.actualSha256 = actualSha256;
            this.matched = matched;
            this.steps = new ArrayList<>(steps);
        }

        public String getAbsolutePath() { return this.absolutePath; }
        public String getExpectedSha256() { return this.expectedSha256; }
        /** Empty when no checksum was computed (early refusals). */
        public String getActualSha256() { return this.actualSha256; }
        public boolean isMatched() { return this.matched; }
        /** Remote commands executed so far, in order (read-only verbs only). */
        public List<String> getSteps() { return List.copyOf(this.steps); }
    }

    /** Layer-distinction sentence shared by the verdict messages. */
    static final String LAYER_NOTE = " The registry manifest digest (the"
            + " profile's @sha256: pin) and the on-disk file sha256 are"
            + " DIFFERENT layers - this attestation covers the FILE bytes"
            + " only and implies nothing about the registry digest.";

    private ContainerImageAttestor() {
        // Utility
    }

    /**
     * Attests one remote image file against the analyst-pinned file sha256.
     * Read-only: executes exactly {@code test -f} then {@code sha256sum}.
     */
    public static OperationResult<Attestation> attest(SshTransport transport,
            String absolutePath, String expectedSha256Hex) {
        if (transport == null || !transport.isConnected()) {
            return OperationResult.unsupported("CONTAINER_PROBE_UNAVAILABLE",
                    "No secure SSH transport is connected; nothing was probed,"
                            + " pulled, executed or written.");
        }
        List<String> steps = new ArrayList<>();
        if (absolutePath == null || absolutePath.isBlank()) {
            return OperationResult.failed("CONTAINER_PROBE_PATH",
                    "the image path is blank - attestation against nothing is"
                            + " unreviewable; supply the absolute on-disk path.",
                    partial("", "", steps));
        }
        String path = absolutePath.trim();
        if (path.length() != absolutePath.length() || !path.startsWith("/")
                || path.contains("..") || path.indexOf('\0') >= 0
                || path.indexOf('\n') >= 0) {
            return OperationResult.failed("CONTAINER_PROBE_PATH",
                    "the image path must be ABSOLUTE, un-padded and free of"
                            + " '..' - a location this build cannot name exactly is"
                            + " unreviewable: '" + absolutePath + "'.",
                    partial(path, "", steps));
        }
        String expected = expectedSha256Hex == null ? ""
                : expectedSha256Hex.trim().toLowerCase(Locale.ROOT);
        if (expected.isEmpty()) {
            return OperationResult.failed("CONTAINER_PROBE_EXPECTATION",
                    "attestation without a pinned file sha256 is measurement"
                            + " without a verdict - supply the hash recorded at"
                            + " download/build time; this build never invents one.",
                    partial(path, expected, steps));
        }
        if (!expected.matches("[0-9a-f]{64}")) {
            return OperationResult.failed("CONTAINER_PROBE_EXPECTATION",
                    "the pinned file hash must be exactly 64 lowercase hex"
                            + " characters (sha256) - got '" + expectedSha256Hex
                            + "'.",
                    partial(path, expected, steps));
        }
        Path out = null;
        Path err = null;
        try {
            out = Files.createTempFile("qf-att-", ".out");
            err = Files.createTempFile("qf-att-", ".err");
            OperationResult<Integer> probe = transport.exec(
                    new String[] {"test", "-f", path}, out, err);
            steps.add("test -f " + path);
            if (!probe.isSuccess()) {
                if (!"SSH_EXEC_FAILED".equals(probe.getCode())) {
                    return OperationResult.failed("CONTAINER_PROBE_UNREADABLE",
                            "the existence probe itself failed ("
                                    + probe.getMessage() + ") - refusing to"
                                    + " proceed blind.",
                            partial(path, expected, steps));
                }
                // SSH_EXEC_FAILED (exit != 0) is the documented absent shape.
                return OperationResult.failed("CONTAINER_PROBE_ABSENT",
                        "no image file at " + path + " - nothing is pulled and"
                                + " nothing is installed to make this pass; the"
                                + " launch side stays blocked until the file"
                                + " exists AND attests." + LAYER_NOTE,
                        partial(path, expected, steps));
            }
            OperationResult<Integer> hash = transport.exec(
                    new String[] {"sha256sum", path}, out, err);
            steps.add("sha256sum " + path);
            if (!hash.isSuccess()) {
                return OperationResult.failed("CONTAINER_PROBE_CHECKSUM",
                        "the checksum command failed (" + hash.getMessage()
                                + ") - the bytes cannot be attested and nothing is"
                                + " guessed.",
                        partial(path, expected, steps));
            }
            String hashOut = Files.isRegularFile(out)
                    ? Files.readString(out, StandardCharsets.UTF_8).trim() : "";
            String actual = hashOut.isBlank() ? ""
                    : hashOut.split("\\s+")[0].toLowerCase(Locale.ROOT);
            if (!actual.matches("[0-9a-f]{64}")) {
                return OperationResult.failed("CONTAINER_PROBE_CHECKSUM",
                        "the checksum output was unparseable ('"
                                + (hashOut.isBlank() ? "<empty>" : hashOut)
                                + "') - an unreadable checksum is refused, never"
                                + " guessed.",
                        partial(path, expected, steps));
            }
            if (!expected.equals(actual)) {
                return OperationResult.failed("CONTAINER_ATTEST_MISMATCH",
                        "the on-disk bytes are NOT what was pinned (pinned "
                                + expected + ", measured " + actual + "; file "
                                + path + ") - NOTHING proceeds by default: the"
                                + " discrepancy is the analyst's to resolve, then"
                                + " re-attest." + LAYER_NOTE,
                        new Attestation(path, expected, actual, false, steps));
            }
            return OperationResult.success("CONTAINER_ATTEST_OK",
                    "image file bytes attest against the pinned sha256 at "
                            + path + " - read-only probes only (test -f,"
                            + " sha256sum); nothing was pulled, executed or"
                            + " written." + LAYER_NOTE,
                    new Attestation(path, expected, actual, true, steps));
        } catch (IOException | RuntimeException ex) {
            return OperationResult.failed("CONTAINER_PROBE_ERROR",
                    "attestation failed unexpectedly: " + ex.getMessage(),
                    partial(path, expected, steps), ex);
        }
    }

    private static Attestation partial(String path, String expected, List<String> steps) {
        return new Attestation(path, expected, "", false, steps);
    }
}
