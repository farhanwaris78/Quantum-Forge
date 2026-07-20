/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.remote.SshTargetSpec.SshTarget;

class SshTargetSpecTest {

    @Test
    void validTargetRendersAHardenedStanza() {
        OperationResult<SshTarget> result = SshTargetSpec.validate(
                "hpc-cluster", "cluster.univ.edu", "farhan", 22,
                "~/.ssh/id_ed25519_qe");
        assertTrue(result.isSuccess(), result.toString());
        SshTarget target = result.getValue().orElseThrow();
        String stanza = target.stanza();
        assertTrue(stanza.contains("Host hpc-cluster\n"), stanza);
        assertTrue(stanza.contains("    HostName cluster.univ.edu\n"), stanza);
        assertTrue(stanza.contains("    User farhan\n"), stanza);
        assertTrue(stanza.contains("    Port 22\n"), stanza);
        assertTrue(stanza.contains("    IdentityFile ~/.ssh/id_ed25519_qe\n"), stanza);
        assertTrue(stanza.contains("    IdentitiesOnly yes\n"), stanza);
        assertTrue(stanza.contains("    PasswordAuthentication no\n"), stanza,
                "password auth disabled BY DESIGN - the spec has no password field");
        assertTrue(stanza.contains("    BatchMode yes\n"), stanza);
        assertTrue(stanza.contains("host-key pinning"), stanza);
    }

    @Test
    void keylessTargetIsHonestAboutOfferingAgentKeys() {
        OperationResult<SshTarget> result = SshTargetSpec.validate(
                "devbox", "dev.local", "root", 2222, "");
        assertTrue(result.isSuccess(), result.toString());
        String stanza = result.getValue().orElseThrow().stanza();
        assertTrue(stanza.contains("IdentityFile unset"), stanza);
        assertTrue(stanza.contains("    IdentitiesOnly no\n"), stanza);
        assertTrue(stanza.contains("    PasswordAuthentication no\n"), stanza);
    }

    @Test
    void unsafeFieldsFailClosed() {
        OperationResult<SshTarget> host = SshTargetSpec.validate(
                "a", "bad host!", "user", 22, "");
        assertFalse(host.isSuccess());
        assertEquals("SSH_HOST", host.getCode());

        OperationResult<SshTarget> user = SshTargetSpec.validate(
                "a", "h.edu", "0bad", 22, "");
        assertFalse(user.isSuccess());
        assertEquals("SSH_USER", user.getCode(), "POSIX logname rules");

        OperationResult<SshTarget> port = SshTargetSpec.validate(
                "a", "h.edu", "user", 0, "");
        assertFalse(port.isSuccess());
        assertEquals("SSH_PORT", port.getCode());

        OperationResult<SshTarget> port2 = SshTargetSpec.validate(
                "a", "h.edu", "user", 65536, "");
        assertFalse(port2.isSuccess());
        assertEquals("SSH_PORT", port2.getCode());

        OperationResult<SshTarget> alias = SshTargetSpec.validate(
                "bad alias", "h.edu", "user", 22, "");
        assertFalse(alias.isSuccess());
        assertEquals("SSH_ALIAS", alias.getCode());

        OperationResult<SshTarget> key = SshTargetSpec.validate(
                "a", "h.edu", "user", 22, "/keys/$(whoami)");
        assertFalse(key.isSuccess());
        assertEquals("SSH_KEY_PATH", key.getCode(),
                "expansion characters refused, never quoted around");

        OperationResult<SshTarget> key2 = SshTargetSpec.validate(
                "a", "h.edu", "user", 22, "/keys/my key");
        assertFalse(key2.isSuccess());
        assertEquals("SSH_KEY_PATH", key2.getCode(), "whitespace refused");
    }

    @Test
    void targetCompilesToTheRuntimeConfigWithFailClosedDefaults() {
        SshTarget target = SshTargetSpec.validate(
                "hpc-cluster", "login.hpc.edu", "farhan", 22, "~/.ssh/id_ed25519")
                .getValue().orElseThrow();
        OperationResult<quantumforge.ssh.SshConnectionConfig> bridge =
                target.toConnectionConfig("~/.ssh/known_hosts", null);
        assertTrue(bridge.isSuccess(), bridge.toString());
        assertEquals("SSH_BRIDGE_OK", bridge.getCode());
        quantumforge.ssh.SshConnectionConfig config = bridge.getValue().orElseThrow();
        assertEquals("login.hpc.edu", config.getHost());
        assertEquals(22, config.getPort());
        assertEquals("farhan", config.getUser());
        assertEquals("~/.ssh/id_ed25519", config.getPrivateKeyPath().toString());
        assertEquals("~/.ssh/known_hosts", config.getKnownHostsPath().toString());
        assertFalse(config.isAcceptNewHostKeys(),
                "unknown-host acceptance is never enabled silently by a bridge");
        assertEquals(15000L, config.getConnectTimeout().toMillis(),
                "null timeout takes the builder's documented 15 s default");
        assertTrue(bridge.getMessage().contains("NO connection"), bridge.getMessage());

        OperationResult<quantumforge.ssh.SshConnectionConfig> explicit =
                target.toConnectionConfig("~/.ssh/known_hosts",
                        java.time.Duration.ofSeconds(7));
        assertEquals(7000L, explicit.getValue().orElseThrow()
                .getConnectTimeout().toMillis(), "an explicit timeout is honored");
    }

    @Test
    void bridgeRefusesAgentAuthAndBlankKnownHosts() {
        SshTarget agent = SshTargetSpec.validate(
                "hpc-cluster", "login.hpc.edu", "farhan", 22, "")
                .getValue().orElseThrow();
        OperationResult<quantumforge.ssh.SshConnectionConfig> noKey =
                agent.toConnectionConfig("~/.ssh/known_hosts", null);
        assertFalse(noKey.isSuccess(),
                "agent/default-key auth is a draft-level legal intent but NOT a"
                        + " compilable one in this build");
        assertEquals("SSH_IDENTITY_MISSING", noKey.getCode());
        assertTrue(noKey.getMessage().contains("no agent support"), noKey.getMessage());

        SshTarget keyed = SshTargetSpec.validate(
                "hpc-cluster", "login.hpc.edu", "farhan", 22, "~/.ssh/id_ed25519")
                .getValue().orElseThrow();
        OperationResult<quantumforge.ssh.SshConnectionConfig> noKnownHosts =
                keyed.toConnectionConfig("   ", null);
        assertFalse(noKnownHosts.isSuccess());
        assertEquals("SSH_KNOWN_HOSTS", noKnownHosts.getCode(),
                "fail-closed host keys means a blank known_hosts never compiles");
        assertTrue(noKnownHosts.getMessage().contains("accept-on-first-use"),
                noKnownHosts.getMessage());
    }
}
