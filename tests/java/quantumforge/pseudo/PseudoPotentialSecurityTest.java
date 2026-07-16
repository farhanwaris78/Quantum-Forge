package quantumforge.pseudo;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PseudoPotentialSecurityTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsDoctypeInsteadOfResolvingExternalEntities() throws Exception {
        Path upf = temporaryDirectory.resolve("malicious.upf");
        Files.writeString(upf, "<!DOCTYPE UPF [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]>\n"
                + "<UPF version=\"2.0.1\"><PP_INFO>#xxe;</PP_INFO></UPF>\n");
        PseudoPotential potential = new PseudoPotential(upf.toFile());
        assertFalse(potential.isAvairable());
    }
}
