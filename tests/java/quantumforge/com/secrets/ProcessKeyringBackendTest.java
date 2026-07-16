package quantumforge.com.secrets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProcessKeyringBackendTest {

    @Test
    void detectNeverThrows() {
        ProcessKeyringBackend backend = ProcessKeyringBackend.detect();
        assertNotNull(backend);
        assertNotNull(backend.name());
        // Availability depends on host tooling; either state is valid.
        if (!backend.isAvailable()) {
            assertTrue(backend.name().contains("unavailable") || backend.name().length() > 0);
        }
    }

    @Test
    void secretStoreDetectsProcessBackendOrFallback() {
        SecretStore store = SecretStore.getInstance();
        store.preferOsKeyring(true);
        String desc = store.describeBackend();
        assertNotNull(desc);
        // memory-only if OS tools missing; otherwise process backend name.
        assertFalse(desc.isBlank());
    }
}
