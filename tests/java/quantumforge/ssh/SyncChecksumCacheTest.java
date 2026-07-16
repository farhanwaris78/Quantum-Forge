package quantumforge.ssh;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SyncChecksumCacheTest {

    @TempDir
    Path tempDir;

    @Test
    void detectsUnchangedFiles() throws Exception {
        Path cacheFile = tempDir.resolve("sync.sha256");
        Path data = tempDir.resolve("espresso.log.scf");
        Files.writeString(data, "JOB DONE\n", StandardCharsets.UTF_8);
        SyncChecksumCache cache = new SyncChecksumCache(cacheFile);
        assertFalse(cache.isUpToDate(data, "espresso.log.scf"));
        cache.record(data, "espresso.log.scf");
        cache.save();
        assertTrue(cache.isUpToDate(data, "espresso.log.scf"));

        Files.writeString(data, "CHANGED\n", StandardCharsets.UTF_8);
        assertFalse(cache.isUpToDate(data, "espresso.log.scf"));
    }
}
