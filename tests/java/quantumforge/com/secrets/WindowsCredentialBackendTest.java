package quantumforge.com.secrets;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class WindowsCredentialBackendTest {

    @Test
    void detectNeverThrows() {
        WindowsCredentialBackend backend = WindowsCredentialBackend.detect();
        assertNotNull(backend);
        assertNotNull(backend.name());
        // On non-Windows hosts this is unavailable; that is correct.
    }
}
