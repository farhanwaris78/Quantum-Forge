package quantumforge.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RemotePathGuardTest {

    @Test
    void resolvesUnderRoot() {
        assertEquals("/scratch/u/jobs/a1",
                RemotePathGuard.resolveUnderRoot("/scratch/u", "jobs/a1"));
    }

    @Test
    void rejectsTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> RemotePathGuard.resolveUnderRoot("/scratch/u", "../etc/passwd"));
        assertThrows(IllegalArgumentException.class,
                () -> RemotePathGuard.resolveUnderRoot("/scratch/u", "/absolute"));
    }

    @Test
    void uniqueJobDirectoryIsSafe() {
        String path = RemotePathGuard.uniqueJobDirectory("/tmp/qf", "AbC 123!");
        assertTrue(path.startsWith("/tmp/qf/jobs/"));
        assertTrue(path.contains("abc_123_"));
    }
}
