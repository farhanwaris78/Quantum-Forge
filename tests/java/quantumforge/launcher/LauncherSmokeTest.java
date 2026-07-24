package quantumforge.launcher;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import quantumforge.app.QEFXMain;
import quantumforge.com.env.Environments;
import quantumforge.ver.Version;

class LauncherSmokeTest {

    @Test
    void versionCommandDoesNotStartTheGui() {
        PrintStream previous = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(bytes, true, StandardCharsets.UTF_8));
            QuantumForgeLauncher.main(new String[] {"--version"});
        } finally {
            System.setOut(previous);
        }
        assertTrue(bytes.toString(StandardCharsets.UTF_8)
                .startsWith(Version.VERSION_NAME + " " + Version.VERSION));
    }

    @Test
    void capabilityCommandDoesNotStartTheGui() {
        PrintStream previous = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(bytes, true, StandardCharsets.UTF_8));
            QuantumForgeLauncher.main(new String[] {"--capabilities"});
        } finally {
            System.setOut(previous);
        }
        String report = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(report.contains("SSH and HPC schedulers"));
        assertTrue(report.contains("[Unavailable]"));
    }

    @Test
    void criticalPackagedResourcesArePresent() {
        assertNotNull(QEFXMain.class.getResource("QEFXMain.fxml"));
        assertNotNull(QEFXMain.class.getResource("QEFXApplication.css"));
        assertNotNull(QEFXMain.class.getResource("resource/image/icon_256.png"));
        assertNotNull(Environments.class.getResource("Environments.unix.prop"));
        assertNotNull(Environments.class.getResource("Environments.win.prop"));
    }
}
