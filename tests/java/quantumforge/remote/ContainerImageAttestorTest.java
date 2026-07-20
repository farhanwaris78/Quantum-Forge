/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.remote.ContainerImageAttestor.Attestation;
import quantumforge.ssh.SshTransport;

/**
 * Batch-149 pins: the read-only, digest-pinned remote image attestation
 * must fail closed, journal its two probe verbs exactly, and never pull,
 * install, execute or write anything.
 */
class ContainerImageAttestorTest {

    private static final String PATH = "/images/qe_7.5.sif";
    private static final String PIN =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    /** Scripted wire: remote content map + read-only verb knobs. */
    private static final class FakeWire implements SshTransport {
        final List<String> steps = new ArrayList<>();
        final Map<String, byte[]> remote = new HashMap<>();
        boolean connected = true;
        boolean testError = false;
        boolean shaFails = false;
        boolean shaGarbage = false;

        @Override public OperationResult<Void> connect() {
            this.connected = true;
            return OperationResult.success("OK", "ok", null);
        }

        @Override public boolean isConnected() { return this.connected; }

        @Override public OperationResult<Integer> exec(String[] command,
                Path stdoutFile, Path stderrFile) {
            this.steps.add(command[0]);
            try {
                switch (command[0]) {
                case "test": {
                    if (this.testError) {
                        return OperationResult.failed("SSH_EXEC_ERROR",
                                "channel died", (Throwable) null);
                    }
                    return this.remote.containsKey(command[2])
                            ? OperationResult.success("SSH_EXEC_OK", "ok", 0)
                            : OperationResult.failed("SSH_EXEC_FAILED", "exited 1",
                                    (Throwable) null);
                }
                case "sha256sum": {
                    if (this.shaFails) {
                        return OperationResult.failed("SSH_EXEC_FAILED",
                                "exited 1", (Throwable) null);
                    }
                    if (this.shaGarbage) {
                        Files.writeString(stdoutFile, "clearly-not-a-hash\n");
                        return OperationResult.success("SSH_EXEC_OK", "ok", 0);
                    }
                    byte[] data = this.remote.get(command[1]);
                    if (data == null) {
                        return OperationResult.failed("SSH_EXEC_FAILED",
                                "exited 1", (Throwable) null);
                    }
                    Files.writeString(stdoutFile,
                            sha(data) + "  " + command[1] + "\n");
                    return OperationResult.success("SSH_EXEC_OK", "ok", 0);
                }
                default:
                    return OperationResult.failed("SSH_EXEC_FAILED",
                            "unknown verb", (Throwable) null);
                }
            } catch (Exception ex) {
                return OperationResult.failed("SSH_EXEC_ERROR", ex.getMessage(),
                        (Throwable) null);
            }
        }

        @Override public OperationResult<Void> uploadFile(Path localFile,
                String remotePath) {
            return OperationResult.unsupported("NO", "read-only in this test");
        }

        @Override public OperationResult<Void> downloadFile(String remotePath,
                Path localFile) {
            return OperationResult.unsupported("NO", "read-only in this test");
        }

        @Override public OperationResult<Void> mkdirRemote(String remotePath) {
            return OperationResult.unsupported("NO", "read-only in this test");
        }

        @Override public void close() {
            this.connected = false;
        }

        private static String sha(byte[] data) throws Exception {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        }

        private static String shaUnchecked(byte[] data) {
            try {
                return sha(data);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    @Test
    void disconnectedOrNullTransportProbesNothing() {
        FakeWire wire = new FakeWire();
        wire.connected = false;
        OperationResult<Attestation> result = ContainerImageAttestor.attest(
                wire, PATH, PIN);
        assertFalse(result.isSuccess());
        assertEquals("CONTAINER_PROBE_UNAVAILABLE", result.getCode());
        assertTrue(result.getMessage().contains("nothing was probed"));
        assertTrue(wire.steps.isEmpty(),
                "an unavailable transport must not even see a probe verb");

        OperationResult<Attestation> nullWire = ContainerImageAttestor.attest(
                null, PATH, PIN);
        assertEquals("CONTAINER_PROBE_UNAVAILABLE", nullWire.getCode());
    }

    @Test
    void blankRelativeTraversalAndPaddedPathsRefuse() {
        FakeWire wire = new FakeWire();
        for (String bad : new String[] {"  ", "images/qe.sif", "/x/../y",
                " /images/qe.sif"}) {
            OperationResult<Attestation> result = ContainerImageAttestor.attest(
                    wire, bad, PIN);
            assertFalse(result.isSuccess(), bad);
            assertEquals("CONTAINER_PROBE_PATH", result.getCode(), bad);
        }
        assertTrue(wire.steps.isEmpty(),
                "every path refusal lands before ANY probe command");
    }

    @Test
    void missingOrMalformedExpectationRefusesBeforeAnyProbe() {
        FakeWire wire = new FakeWire();
        for (String bad : new String[] {null, "", "   ",
                "0123" /* too short */, "z".repeat(64)}) {
            OperationResult<Attestation> result = ContainerImageAttestor.attest(
                    wire, PATH, bad);
            assertFalse(result.isSuccess(), String.valueOf(bad));
            assertEquals("CONTAINER_PROBE_EXPECTATION", result.getCode());
        }
        assertTrue(wire.steps.isEmpty(),
                "measurement without a verdict never sends a probe");
        OperationResult<Attestation> blank = ContainerImageAttestor.attest(
                wire, PATH, "");
        assertTrue(blank.getMessage().contains("never invents"));
    }

    @Test
    void absentFileIsNamedAndNothingIsPulled() {
        FakeWire wire = new FakeWire();
        OperationResult<Attestation> result = ContainerImageAttestor.attest(
                wire, PATH, PIN);
        assertFalse(result.isSuccess());
        assertEquals("CONTAINER_PROBE_ABSENT", result.getCode());
        assertEquals(List.of("test"), wire.steps,
                "exactly one read-only verb was attempted");
        Attestation att = result.getValue().orElseThrow();
        assertEquals(List.of("test -f " + PATH), att.getSteps());
        assertFalse(att.isMatched());
        assertTrue(result.getMessage().contains("nothing is pulled"));
        assertTrue(result.getMessage().contains("DIFFERENT layers"),
                "the layer note travels with the refusal");
    }

    @Test
    void unreadableProbeRefusesBlind() {
        FakeWire wire = new FakeWire();
        wire.testError = true;
        OperationResult<Attestation> result = ContainerImageAttestor.attest(
                wire, PATH, PIN);
        assertFalse(result.isSuccess());
        assertEquals("CONTAINER_PROBE_UNREADABLE", result.getCode());
        assertEquals(List.of("test -f " + PATH),
                result.getValue().orElseThrow().getSteps());
    }

    @Test
    void checksumFailureAndGarbageOutputRefuse() {
        FakeWire wire = new FakeWire();
        wire.remote.put(PATH, "image-bytes".getBytes(StandardCharsets.UTF_8));
        wire.shaFails = true;
        OperationResult<Attestation> failed = ContainerImageAttestor.attest(
                wire, PATH, PIN);
        assertFalse(failed.isSuccess());
        assertEquals("CONTAINER_PROBE_CHECKSUM", failed.getCode());

        wire = new FakeWire();
        wire.remote.put(PATH, "image-bytes".getBytes(StandardCharsets.UTF_8));
        wire.shaGarbage = true;
        OperationResult<Attestation> garbage = ContainerImageAttestor.attest(
                wire, PATH, PIN);
        assertFalse(garbage.isSuccess());
        assertEquals("CONTAINER_PROBE_CHECKSUM", garbage.getCode());
        assertTrue(garbage.getMessage().contains("never guessed"));
        assertEquals(List.of("test -f " + PATH, "sha256sum " + PATH),
                garbage.getValue().orElseThrow().getSteps(),
                "both verbs are journalled even when the answer is garbage");
    }

    @Test
    void mismatchCarriesBothHashesAndPartialTruth() {
        FakeWire wire = new FakeWire();
        wire.remote.put(PATH, "image-bytes".getBytes(StandardCharsets.UTF_8));
        OperationResult<Attestation> result = ContainerImageAttestor.attest(
                wire, PATH, PIN);
        assertFalse(result.isSuccess());
        assertEquals("CONTAINER_ATTEST_MISMATCH", result.getCode());
        Attestation att = result.getValue().orElseThrow();
        assertFalse(att.isMatched());
        assertEquals(PIN, att.getExpectedSha256());
        assertFalse(att.getActualSha256().isEmpty());
        assertTrue(result.getMessage().contains(att.getActualSha256()),
                "the measured hash is named, not hidden");
        assertTrue(result.getMessage().contains("NOTHING proceeds by default"));
        assertTrue(result.getMessage().contains("DIFFERENT layers"));
    }

    @Test
    void attestOkVerdictIsExactAndReadOnly() {
        FakeWire wire = new FakeWire();
        byte[] payload = "image-bytes".getBytes(StandardCharsets.UTF_8);
        wire.remote.put(PATH, payload);
        OperationResult<Attestation> result = ContainerImageAttestor.attest(
                wire, PATH, FakeWire.shaUnchecked(payload));
        assertTrue(result.isSuccess(), result.toString());
        assertEquals("CONTAINER_ATTEST_OK", result.getCode());
        Attestation att = result.getValue().orElseThrow();
        assertTrue(att.isMatched());
        assertEquals(List.of("test -f " + PATH, "sha256sum " + PATH),
                att.getSteps());
        assertEquals(List.of("test", "sha256sum"), wire.steps,
                "no pull verb, no exec verb, no rm - exactly the two probes");
        assertTrue(result.getMessage().contains("read-only probes only"));
        assertTrue(result.getMessage().contains("nothing was pulled"));
        assertTrue(result.getMessage().contains("DIFFERENT layers"),
                "the file-bytes/registry-digest layer note is in the verdict");
    }

    @Test
    void uppercaseExpectationNormalizesBeforeGrammarCheck() {
        FakeWire wire = new FakeWire();
        byte[] payload = "image-bytes".getBytes(StandardCharsets.UTF_8);
        wire.remote.put(PATH, payload);
        String upper = FakeWire.shaUnchecked(payload).toUpperCase(Locale.ROOT);
        OperationResult<Attestation> result = ContainerImageAttestor.attest(
                wire, PATH, upper);
        assertTrue(result.isSuccess(), result.toString());
        assertEquals(FakeWire.shaUnchecked(payload),
                result.getValue().orElseThrow().getExpectedSha256(),
                "analyst hex is normalized, never compared case-sensitively");
    }
}
