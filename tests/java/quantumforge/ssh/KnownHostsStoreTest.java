package quantumforge.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KnownHostsStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void unknownHostIsRejectedUntilAccepted() throws Exception {
        Path file = tempDir.resolve("known_hosts.qf");
        KnownHostsStore store = new KnownHostsStore(file);
        store.load();
        assertEquals(KnownHostsStore.Decision.REJECT_UNKNOWN,
                store.verify("cluster.example", 22, "ssh-ed25519", "abc123"));
        store.accept("cluster.example", 22, "ssh-ed25519", "abc123");
        assertEquals(KnownHostsStore.Decision.ACCEPT,
                store.verify("cluster.example", 22, "ssh-ed25519", "abc123"));
        assertTrue(Files.isRegularFile(file));
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("abc123"));
    }

    @Test
    void changedKeyIsRejected() throws Exception {
        Path file = tempDir.resolve("known_hosts.qf");
        KnownHostsStore store = new KnownHostsStore(file);
        store.accept("h", 22, "ssh-rsa", "oldfp");
        assertEquals(KnownHostsStore.Decision.REJECT_CHANGED,
                store.verify("h", 22, "ssh-rsa", "newfp"));
    }

    @Test
    void fingerprintFromBytesIsStable() {
        byte[] key = "test-host-key".getBytes(StandardCharsets.UTF_8);
        String a = KnownHostsStore.fingerprintSha256(key);
        String b = KnownHostsStore.fingerprintSha256(key);
        assertEquals(a, b);
        assertEquals(64, a.length());
    }
}
