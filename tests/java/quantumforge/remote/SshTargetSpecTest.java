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
}
