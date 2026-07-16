package quantumforge.com.secrets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SecretStoreTest {

    @AfterEach
    void cleanup() {
        SecretStore store = SecretStore.getInstance();
        store.clearMemoryForTests();
        store.preferOsKeyring(false);
        store.injectBackendForTests(SecretStore.detectBackend());
    }

    @Test
    void memoryDefaultDoesNotRequireOsKeyring() {
        SecretStore store = SecretStore.getInstance();
        store.putString(SecretStore.KEY_MATERIALS_API, "secret-value");
        assertEquals("secret-value", store.getString(SecretStore.KEY_MATERIALS_API).orElseThrow());
        assertTrue(store.describeBackend().contains("memory"));
        store.delete(SecretStore.KEY_MATERIALS_API);
        assertTrue(store.getString(SecretStore.KEY_MATERIALS_API).isEmpty());
    }

    @Test
    void osBackendPathWorksWithInjectedFake() {
        SecretStore store = SecretStore.getInstance();
        SecretStore.MemoryKeyringBackend backend = new SecretStore.MemoryKeyringBackend();
        store.injectBackendForTests(backend);
        store.preferOsKeyring(true);
        assertTrue(store.isOsKeyringAvailable());
        store.putString("ssh.password.host1", "pw");
        store.clearMemoryForTests();
        // Reload from fake OS backend into memory.
        assertEquals("pw", store.getString("ssh.password.host1").orElseThrow());
    }

    @Test
    void wipeClearsArray() {
        char[] secret = "abc".toCharArray();
        SecretStore.wipe(secret);
        assertEquals('\0', secret[0]);
        assertEquals('\0', secret[2]);
        assertFalse(new String(secret).contains("a"));
    }
}
