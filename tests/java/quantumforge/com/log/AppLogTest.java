package quantumforge.com.log;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppLogTest {

    @TempDir
    Path tempDir;

    @Test
    void redactsSecretsAndWritesStructuredRecords() throws Exception {
        Path log = tempDir.resolve("quantumforge.log");
        AppLog.configure(log);
        AppLog.setJobId("abcd1234");
        AppLog.info("test", "proxy password=super-secret token value");
        AppLog.error("test", "Authorization: Bearer abc.def.ghi failed", new IllegalStateException("boom"));

        String content = Files.readString(log, StandardCharsets.UTF_8);
        assertTrue(content.contains("INFO"));
        assertTrue(content.contains("job=abcd1234"));
        assertTrue(content.contains("component=test"));
        assertTrue(content.contains("***"));
        assertFalse(content.contains("super-secret"));
        assertFalse(content.contains("abc.def.ghi"));
        assertTrue(content.contains("ERROR"));
    }

    @Test
    void redactHelperMasksBearerTokens() {
        String redacted = AppLog.redact("Authorization: Bearer secret-token-value");
        assertTrue(redacted.contains("***"));
        assertFalse(redacted.contains("secret-token-value"));
    }
}
