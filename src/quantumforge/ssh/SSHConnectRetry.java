/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.ssh;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #96 (reconnect-retry slice, batch 145): bounded, honest connect
 * retry - a COMBINATOR that wraps any connector (the batch-139 chain's
 * injected {@code Function<SSHServer, OperationResult<SshTransport>>}
 * shape) without changing a single ownership rule of the chain itself.
 *
 * <p>Why it exists: a flaky cluster previously killed an entire submission
 * on one transient connect failure. The fix cannot be a blind retry hammer,
 * so every rule is stated and pinned:</p>
 * <ul>
 *   <li>ONLY transient transport codes retry ({@value #CODE_CONNECT_FAILED},
 *       {@value #CODE_CONNECT_ERROR} - the wire's own generic connect
 *       failures). Host-key codes and authentication refusals NEVER retry:
 *       hammering a consent surface would masquerade as consent, and a
 *       changed host key must look changed every single time;</li>
 *   <li>bounded: at most {@code maxAttempts} attempts (1 = honest single
 *       attempt, i.e. no retry at all); delays run geometric from
 *       {@code initialDelay}, doubling, capped at {@code maxDelay}, and a
 *       sleep happens only BETWEEN attempts - never after the last;</li>
 *   <li>interruption is not an error and not swallowed: the loop stops, the
 *       interrupt flag is restored, {@link RetryOutcome#wasInterrupted()} is
 *       true and the attempts so far stand as the truth;</li>
 *   <li>the connector returning a null result is a programming error (NPE),
 *       exactly the batch-139 chain's doctrine;</li>
 *   <li>every attempt is recorded with its typed result - the
 *       {@link RetryOutcome#provenanceLine()} is what review surfaces show,
 *       so "it eventually failed" is never a mystery about how hard we tried.</li>
 * </ul>
 */
public final class SSHConnectRetry {

    /** The transient connect-failure code that may retry (generic form). */
    public static final String CODE_CONNECT_FAILED = "SSH_CONNECT_FAILED";
    /** The transient connect-failure code that may retry (error form). */
    public static final String CODE_CONNECT_ERROR = "SSH_CONNECT_ERROR";

    /** Default bound: three attempts total (two retries). */
    public static final int DEFAULT_MAX_ATTEMPTS = 3;
    /** Default first inter-attempt delay. */
    public static final Duration DEFAULT_INITIAL_DELAY = Duration.ofSeconds(1);
    /** Default inter-attempt delay cap. */
    public static final Duration DEFAULT_MAX_DELAY = Duration.ofSeconds(10);

    private SSHConnectRetry() {
    }

    /** Inter-attempt wait, injectable so tests pin delays without sleeping. */
    public interface ConnectSleeper {
        void sleep(Duration delay) throws InterruptedException;
    }

    /** The production sleeper: a plain interruptible Thread.sleep. */
    public static final ConnectSleeper THREAD_SLEEPER =
            delay -> Thread.sleep(delay.toMillis());

    /** True ONLY for the two transient transport codes this owner retries. */
    public static boolean isRetriable(String code) {
        return CODE_CONNECT_FAILED.equals(code) || CODE_CONNECT_ERROR.equals(code);
    }

    /** Full record of a retry run: every attempt's typed result, in order. */
    public static final class RetryOutcome {
        private final List<OperationResult<SshTransport>> attempts;
        private final List<Long> delaysMillis;
        private final boolean interrupted;

        RetryOutcome(List<OperationResult<SshTransport>> attempts, List<Long> delaysMillis,
                boolean interrupted) {
            this.attempts = Collections.unmodifiableList(new ArrayList<>(attempts));
            this.delaysMillis = Collections.unmodifiableList(new ArrayList<>(delaysMillis));
            this.interrupted = interrupted;
        }

        /** How many connect attempts actually ran (1-based truth). */
        public int getAttemptCount() {
            return this.attempts.size();
        }

        /** True when more than one attempt ran. */
        public boolean wasRetried() {
            return this.attempts.size() > 1;
        }

        /** True when the loop stopped because the thread was interrupted. */
        public boolean wasInterrupted() {
            return this.interrupted;
        }

        /** The last attempt's typed result - the one callers act on. */
        public OperationResult<SshTransport> getFinal() {
            return this.attempts.get(this.attempts.size() - 1);
        }

        /** Every attempt's typed result in order; never null, size >= 1. */
        public List<OperationResult<SshTransport>> getAttempts() {
            return this.attempts;
        }

        /** The inter-attempt sleeps actually taken, in milliseconds. */
        public List<Long> getDelaysMillis() {
            return this.delaysMillis;
        }

        /** One honest sentence describing how hard the connect tried. */
        public String provenanceLine() {
            StringBuilder line = new StringBuilder("connect attempts: ")
                    .append(this.attempts.size()).append(" (codes: ");
            for (int i = 0; i < this.attempts.size(); i++) {
                if (i > 0) {
                    line.append(", ");
                }
                line.append(this.attempts.get(i).getCode());
            }
            line.append(')');
            if (!this.delaysMillis.isEmpty()) {
                line.append("; backoff delays(ms): ").append(this.delaysMillis);
            }
            if (this.interrupted) {
                line.append("; retry loop interrupted early");
            }
            return line.toString();
        }
    }

    /**
     * Run the connector at most {@code maxAttempts} times, retrying ONLY
     * transient codes with geometric capped backoff. Never throws for
     * expected connector failures - those live in the outcome.
     */
    public static RetryOutcome connectWithRetry(SSHServer server,
            Function<SSHServer, OperationResult<SshTransport>> connector, int maxAttempts,
            Duration initialDelay, Duration maxDelay, ConnectSleeper sleeper) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(connector, "connector - retry wraps one, never builds one");
        Objects.requireNonNull(sleeper, "sleeper - waits are deliberate, never hidden");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1 (got "
                    + maxAttempts + ") - 1 means an honest single attempt, no retry");
        }
        Objects.requireNonNull(initialDelay, "initialDelay");
        Objects.requireNonNull(maxDelay, "maxDelay");
        if (initialDelay.isNegative() || maxDelay.isNegative()
                || initialDelay.compareTo(maxDelay) > 0) {
            throw new IllegalArgumentException("delays must be non-negative with initial "
                    + "<= max (got " + initialDelay + " vs " + maxDelay + ")");
        }
        List<OperationResult<SshTransport>> attempts = new ArrayList<>();
        List<Long> delays = new ArrayList<>();
        boolean interrupted = false;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            OperationResult<SshTransport> result = Objects.requireNonNull(
                    connector.apply(server),
                    "connector returned a null result on attempt " + attempt
                            + " - retry refuses to guess");
            attempts.add(result);
            if (result.isSuccess() || !isRetriable(result.getCode())
                    || attempt == maxAttempts) {
                break; // success, or a code retry must never touch, or the bound
            }
            // 2^(attempt-1), clamped so absurd bounds cannot overflow the shift.
            Duration delay = initialDelay
                    .multipliedBy(1L << Math.min(attempt - 1, 30));
            if (delay.compareTo(maxDelay) > 0) {
                delay = maxDelay;
            }
            try {
                sleeper.sleep(delay);
                delays.add(Long.valueOf(delay.toMillis()));
            } catch (InterruptedException wake) {
                Thread.currentThread().interrupt();
                interrupted = true;
                break;
            }
        }
        return new RetryOutcome(attempts, delays, interrupted);
    }
}
