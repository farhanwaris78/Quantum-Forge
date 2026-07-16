package quantumforge.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

class QEExecutableProfileTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsVersionFromCommonBanners() {
        assertEquals("7.5", QEExecutableProfile.extractVersion("Program PWSCF v.7.5 starts on ..."));
        assertEquals("7.3.1", QEExecutableProfile.extractVersion("Quantum ESPRESSO v.7.3.1"));
        assertEquals("", QEExecutableProfile.extractVersion("no version here"));
    }

    @Test
    void probeReportsMissingDirectory() {
        QEExecutableProfile profile = QEExecutableProfile.probe(null);
        assertFalse(profile.hasCorePw());
        assertFalse(profile.getDiagnostics().isEmpty());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void probeFindsPwExecutableOnUnix() throws Exception {
        Path bin = tempDir.resolve("qe-bin");
        Files.createDirectories(bin);
        Path pw = bin.resolve("pw.x");
        Files.writeString(pw, "#!/bin/sh\necho 'Program PWSCF v.7.5'\n");
        Set<PosixFilePermission> perms = EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE);
        Files.setPosixFilePermissions(pw, perms);

        QEExecutableProfile profile = QEExecutableProfile.probe(bin);
        assertTrue(profile.hasCorePw());
        assertEquals(bin, profile.getBinDirectory());
        assertTrue(profile.getExecutables().containsKey("pw.x"));
        // Version extraction may succeed if the shell script runs; either is acceptable.
        assertTrue(profile.getVersion().isEmpty() || profile.getVersion().startsWith("7."));
    }
}
