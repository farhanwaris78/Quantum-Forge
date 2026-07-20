/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;

/**
 * Batch-145 (#96 reconnect-retry slice): only transient codes retry, the
 * backoff is geometric and capped, consent codes are never hammered, and the
 * provenance line tells the truth about how hard the connect tried.
 */
class SSHConnectRetryTest {

    private static final SSHServer SERVER = new SSHServer("retry-target");

    private static OperationResult<SshTransport> ok() {
        return OperationResult.success("SSH_CONNECTED", "ok", null);
    }

    private static OperationResult<SshTransport> fail(String code) {
        return OperationResult.failed(code, code + " boom", null);
    }

    /** Script: answers codes in order; the LAST one repeats if exhausted. */
    private static final class ScriptedConnector
            implements Function<SSHServer, OperationResult<SshTransport>> {
        final List<String> script = new ArrayList<>();
        int calls = 0;

        ScriptedConnector(String... codes) {
            this.script.addAll(List.of(codes));
        }

        @Override public OperationResult<SshTransport> apply(SSHServer server) {
            this.calls++;
            String code = this.script.get(Math.min(this.calls - 1, this.script.size() - 1));
            return "SSH_CONNECTED".equals(code) ? ok() : fail(code);
        }
    }

    private static final class RecordingSleeper implements SSHConnectRetry.ConnectSleeper {
        final List<Long> slept = new ArrayList<>();
        int throwOnCall = -1; // 1-based; -1 = never

        @Override public void sleep(Duration delay) throws InterruptedException {
            if (this.throwOnCall == this.slept.size() + 1) {
                throw new InterruptedException("wake");
            }
            this.slept.add(Long.valueOf(delay.toMillis()));
        }
    }

    @Test
    void successFirstTryIsOneAttemptAndNoSleeps() {
        ScriptedConnector connector = new ScriptedConnector("SSH_CONNECTED");
        RecordingSleeper sleeper = new RecordingSleeper();
        SSHConnectRetry.RetryOutcome outcome = SSHConnectRetry.connectWithRetry(SERVER,
                connector, 3, Duration.ofSeconds(1), Duration.ofSeconds(10), sleeper);
        assertTrue(outcome.getFinal().isSuccess());
        assertEquals(1, outcome.getAttemptCount());
        assertFalse(outcome.wasRetried());
        assertFalse(outcome.wasInterrupted());
        assertTrue(sleeper.slept.isEmpty(), "a sleep never precedes success");
        assertEquals("connect attempts: 1 (codes: SSH_CONNECTED)",
                outcome.provenanceLine(), "single-attempt truth, exact");
    }

    @Test
    void transientFailuresRetryWithGeometricBackoffThenReportProvenance() {
        ScriptedConnector connector = new ScriptedConnector(
                "SSH_CONNECT_FAILED", "SSH_CONNECT_FAILED", "SSH_CONNECTED");
        RecordingSleeper sleeper = new RecordingSleeper();
        SSHConnectRetry.RetryOutcome outcome = SSHConnectRetry.connectWithRetry(SERVER,
                connector, 3, Duration.ofSeconds(1), Duration.ofSeconds(10), sleeper);
        assertTrue(outcome.getFinal().isSuccess(), outcome.toString());
        assertEquals(3, outcome.getAttemptCount());
        assertTrue(outcome.wasRetried());
        assertEquals(List.of(1000L, 2000L), sleeper.slept,
                "geometric 1s -> 2s, and never a sleep after the last attempt");
        assertEquals(List.of("SSH_CONNECT_FAILED", "SSH_CONNECT_FAILED", "SSH_CONNECTED"),
                outcome.getAttempts().stream().map(OperationResult::getCode).toList());
        assertEquals("connect attempts: 3 (codes: SSH_CONNECT_FAILED, "
                        + "SSH_CONNECT_FAILED, SSH_CONNECTED); backoff delays(ms): "
                        + "[1000, 2000]",
                outcome.provenanceLine(), "the review surface line, exact");
    }

    @Test
    void persistentFailureStopsAtTheBoundWithTheCodesListed() {
        ScriptedConnector connector = new ScriptedConnector("SSH_CONNECT_ERROR");
        RecordingSleeper sleeper = new RecordingSleeper();
        SSHConnectRetry.RetryOutcome outcome = SSHConnectRetry.connectWithRetry(SERVER,
                connector, 2, Duration.ofSeconds(1), Duration.ofSeconds(10), sleeper);
        assertFalse(outcome.getFinal().isSuccess());
        assertEquals(2, outcome.getAttemptCount(), "the bound is the bound");
        assertEquals(List.of(1000L), outcome.getDelaysMillis());
        assertEquals("SSH_CONNECT_ERROR", outcome.getFinal().getCode(),
                "the final typed code passes through unrelabeled");
        assertEquals("connect attempts: 2 (codes: SSH_CONNECT_ERROR, "
                        + "SSH_CONNECT_ERROR); backoff delays(ms): [1000]",
                outcome.provenanceLine());
    }

    @Test
    void hostKeyAndAuthCodesNeverRetry() {
        for (String code : List.of("SSH_HOST_KEY_CHANGED", "SSH_HOST_KEY_UNKNOWN",
                "SSH_HOST_KEY_REJECTED", "SSH_HOST_KEY_MISSING", "SSH_AUTH_FAILED")) {
            ScriptedConnector connector = new ScriptedConnector(code);
            RecordingSleeper sleeper = new RecordingSleeper();
            SSHConnectRetry.RetryOutcome outcome = SSHConnectRetry.connectWithRetry(SERVER,
                    connector, 5, Duration.ofSeconds(1), Duration.ofSeconds(10), sleeper);
            assertEquals(1, outcome.getAttemptCount(),
                    code + " must never be auto-retried - hammering a consent or auth "
                            + "surface would masquerade as consent");
            assertTrue(sleeper.slept.isEmpty());
            assertFalse(outcome.wasRetried());
        }
        assertTrue(SSHConnectRetry.isRetriable("SSH_CONNECT_FAILED"));
        assertTrue(SSHConnectRetry.isRetriable("SSH_CONNECT_ERROR"));
        assertFalse(SSHConnectRetry.isRetriable("SSH_HOST_KEY_CHANGED"));
        assertFalse(SSHConnectRetry.isRetriable(null),
                "an absent code is not retriable either");
    }

    @Test
    void maxAttemptsOneIsAnHonestSingleAttempt() {
        ScriptedConnector connector = new ScriptedConnector("SSH_CONNECT_FAILED");
        RecordingSleeper sleeper = new RecordingSleeper();
        SSHConnectRetry.RetryOutcome outcome = SSHConnectRetry.connectWithRetry(SERVER,
                connector, 1, Duration.ofSeconds(1), Duration.ofSeconds(10), sleeper);
        assertEquals(1, outcome.getAttemptCount());
        assertTrue(sleeper.slept.isEmpty(), "no retry means no sleep even when retriable");
    }

    @Test
    void theDelayCapBitesExactly() {
        ScriptedConnector connector = new ScriptedConnector("SSH_CONNECT_FAILED");
        RecordingSleeper sleeper = new RecordingSleeper();
        SSHConnectRetry.connectWithRetry(SERVER, connector, 3, Duration.ofSeconds(3),
                Duration.ofSeconds(4), sleeper);
        assertEquals(List.of(3000L, 4000L), sleeper.slept,
                "the second delay would double to 6s - the 4s cap must bite");
    }

    @Test
    void interruptionStopsTheLoopAndRestoresTheFlag() {
        ScriptedConnector connector = new ScriptedConnector("SSH_CONNECT_FAILED",
                "SSH_CONNECTED");
        RecordingSleeper sleeper = new RecordingSleeper();
        sleeper.throwOnCall = 1;
        SSHConnectRetry.RetryOutcome outcome = SSHConnectRetry.connectWithRetry(SERVER,
                connector, 3, Duration.ofSeconds(1), Duration.ofSeconds(10), sleeper);
        assertTrue(outcome.wasInterrupted());
        assertEquals(1, outcome.getAttemptCount(),
                "the wake stops the loop before the next attempt");
        assertTrue(outcome.getDelaysMillis().isEmpty(),
                "a sleep that never completed is not recorded as taken");
        assertFalse(outcome.getFinal().isSuccess());
        assertTrue(outcome.provenanceLine().contains("retry loop interrupted early"),
                outcome.provenanceLine());
        assertTrue(Thread.interrupted(), "the interrupt flag is restored for the caller");
    }

    @Test
    void invalidParametersRefuseLoudly() {
        ScriptedConnector connector = new ScriptedConnector("SSH_CONNECTED");
        RecordingSleeper sleeper = new RecordingSleeper();
        assertThrows(NullPointerException.class, () -> SSHConnectRetry.connectWithRetry(
                null, connector, 1, Duration.ofSeconds(1), Duration.ofSeconds(1), sleeper));
        assertThrows(NullPointerException.class, () -> SSHConnectRetry.connectWithRetry(
                SERVER, null, 1, Duration.ofSeconds(1), Duration.ofSeconds(1), sleeper));
        assertThrows(NullPointerException.class, () -> SSHConnectRetry.connectWithRetry(
                SERVER, connector, 1, null, Duration.ofSeconds(1), sleeper));
        assertThrows(NullPointerException.class, () -> SSHConnectRetry.connectWithRetry(
                SERVER, connector, 1, Duration.ofSeconds(1), null, sleeper));
        assertThrows(NullPointerException.class, () -> SSHConnectRetry.connectWithRetry(
                SERVER, connector, 1, Duration.ofSeconds(1), Duration.ofSeconds(1), null));
        IllegalArgumentException zero = assertThrows(IllegalArgumentException.class,
                () -> SSHConnectRetry.connectWithRetry(SERVER, connector, 0,
                        Duration.ofSeconds(1), Duration.ofSeconds(1), sleeper));
        assertTrue(zero.getMessage().contains("1 means an honest single attempt"),
                zero.getMessage());
        assertThrows(IllegalArgumentException.class, () -> SSHConnectRetry.connectWithRetry(
                SERVER, connector, 2, Duration.ofSeconds(10), Duration.ofSeconds(1),
                sleeper));
        assertThrows(IllegalArgumentException.class, () -> SSHConnectRetry.connectWithRetry(
                SERVER, connector, 2, Duration.ofSeconds(-1), Duration.ofSeconds(1),
                sleeper));
        assertThrows(NullPointerException.class, () -> SSHConnectRetry.connectWithRetry(
                SERVER, server -> null, 2, Duration.ofSeconds(1), Duration.ofSeconds(1),
                sleeper), "a null connector result is a programming error, never retried");
    }
}
