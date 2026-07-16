package quantumforge.app.ssh;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class HostKeyAcceptanceTest {

    @Test
    void defaultKnownHostsPathIsUnderQuantumforge() {
        Path path = HostKeyAcceptance.defaultKnownHostsPath();
        assertNotNull(path);
        assertTrue(path.toString().contains("known_hosts.qf")
                || path.getFileName().toString().equals("known_hosts.qf"));
    }
}
