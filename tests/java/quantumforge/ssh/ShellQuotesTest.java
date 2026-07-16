package quantumforge.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShellQuotesTest {

    @Test
    void singleQuotesAndEscapes() {
        assertEquals("''", ShellQuotes.single(null));
        assertEquals("'abc'", ShellQuotes.single("abc"));
        assertTrue(ShellQuotes.single("a'b").contains("\"'\""));
    }

    @Test
    void joinTokens() {
        assertEquals("'sbatch' '/tmp/job.sh'", ShellQuotes.join("sbatch", "/tmp/job.sh"));
    }
}
