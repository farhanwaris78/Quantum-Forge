/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.com.secrets;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-memory secret store with optional OS-keyring backends.
 *
 * <p>Default is memory-only: secrets never touch {@code .properties}. Platform
 * keyring adapters are attempted when {@link #preferOsKeyring(boolean)} is
 * enabled; failures fall back to memory without throwing to callers.</p>
 */
public final class SecretStore {

    public static final String KEY_MATERIALS_API = "materials.api_key";
    public static final String KEY_PROXY_PASSWORD = "proxy.password";
    public static final String KEY_SSH_PASSWORD_PREFIX = "ssh.password.";

    private static final SecretStore INSTANCE = new SecretStore();

    private final Map<String, char[]> memory = new ConcurrentHashMap<>();
    private volatile boolean preferOsKeyring;
    private volatile OsKeyringBackend backend;

    private SecretStore() {
        this.preferOsKeyring = false;
        this.backend = detectBackend();
    }

    public static SecretStore getInstance() {
        return INSTANCE;
    }

    public void preferOsKeyring(boolean prefer) {
        this.preferOsKeyring = prefer;
        if (prefer && this.backend == null) {
            this.backend = detectBackend();
        }
    }

    public boolean isOsKeyringAvailable() {
        return this.backend != null && this.backend.isAvailable();
    }

    public String describeBackend() {
        if (this.preferOsKeyring && isOsKeyringAvailable()) {
            return this.backend.name();
        }
        return "memory-only";
    }

    public void put(String key, char[] secret) {
        Objects.requireNonNull(key, "key");
        if (secret == null || secret.length == 0) {
            delete(key);
            return;
        }
        char[] copy = secret.clone();
        this.memory.put(key, copy);
        if (this.preferOsKeyring && isOsKeyringAvailable()) {
            try {
                this.backend.store(key, copy);
            } catch (RuntimeException ignored) {
                // Memory remains authoritative for this process.
            }
        }
    }

    public void putString(String key, String secret) {
        if (secret == null) {
            delete(key);
            return;
        }
        put(key, secret.toCharArray());
    }

    public Optional<char[]> get(String key) {
        if (key == null) {
            return Optional.empty();
        }
        char[] memoryValue = this.memory.get(key);
        if (memoryValue != null) {
            return Optional.of(memoryValue.clone());
        }
        if (this.preferOsKeyring && isOsKeyringAvailable()) {
            try {
                char[] fromOs = this.backend.load(key);
                if (fromOs != null && fromOs.length > 0) {
                    this.memory.put(key, fromOs.clone());
                    return Optional.of(fromOs);
                }
            } catch (RuntimeException ignored) {
                // Fall through.
            }
        }
        return Optional.empty();
    }

    public Optional<String> getString(String key) {
        return get(key).map(String::new);
    }

    public void delete(String key) {
        if (key == null) {
            return;
        }
        char[] old = this.memory.remove(key);
        wipe(old);
        if (this.preferOsKeyring && isOsKeyringAvailable()) {
            try {
                this.backend.delete(key);
            } catch (RuntimeException ignored) {
                // Best effort.
            }
        }
    }

    public static void wipe(char[] secret) {
        if (secret != null) {
            java.util.Arrays.fill(secret, '\0');
        }
    }

    static OsKeyringBackend detectBackend() {
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        // Real D-Bus/Keychain/Credential Manager bindings require native libraries.
        // We register a null-safe placeholder that reports unavailable unless a
        // future native adapter is injected for tests.
        if (os.contains("linux") || os.contains("mac") || os.contains("win")) {
            return new UnavailableKeyringBackend(os.contains("mac") ? "keychain"
                    : os.contains("win") ? "credential-manager" : "libsecret");
        }
        return new UnavailableKeyringBackend("none");
    }

    /** Visible for tests. */
    void injectBackendForTests(OsKeyringBackend backend) {
        this.backend = backend;
    }

    /** Visible for tests. */
    void clearMemoryForTests() {
        for (char[] value : this.memory.values()) {
            wipe(value);
        }
        this.memory.clear();
    }

    public interface OsKeyringBackend {
        String name();
        boolean isAvailable();
        void store(String key, char[] secret);
        char[] load(String key);
        void delete(String key);
    }

    static final class UnavailableKeyringBackend implements OsKeyringBackend {
        private final String name;

        UnavailableKeyringBackend(String name) {
            this.name = name;
        }

        @Override public String name() { return this.name + " (unavailable)"; }
        @Override public boolean isAvailable() { return false; }
        @Override public void store(String key, char[] secret) { }
        @Override public char[] load(String key) { return null; }
        @Override public void delete(String key) { }
    }

    /** In-memory fake used by unit tests to exercise the OS path. */
    public static final class MemoryKeyringBackend implements OsKeyringBackend {
        private final Map<String, char[]> data = new ConcurrentHashMap<>();

        @Override public String name() { return "test-memory-keyring"; }
        @Override public boolean isAvailable() { return true; }
        @Override public void store(String key, char[] secret) {
            this.data.put(key, secret.clone());
        }
        @Override public char[] load(String key) {
            char[] value = this.data.get(key);
            return value == null ? null : value.clone();
        }
        @Override public void delete(String key) {
            char[] old = this.data.remove(key);
            wipe(old);
        }
    }
}
