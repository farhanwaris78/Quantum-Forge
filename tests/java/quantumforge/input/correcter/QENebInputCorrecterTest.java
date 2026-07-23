package quantumforge.input.correcter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import quantumforge.input.QEInput;
import quantumforge.input.QEInputReader;
import quantumforge.input.namelist.QENamelist;

class QENebInputCorrecterTest {

    private static class MockQEInput extends QEInput {
        @Override
        protected void setupNamelists(QEInputReader reader) throws IOException {
            this.setupNamelist(NAMELIST_CONTROL, reader);
            this.setupNamelist("PATH", reader);
        }

        @Override
        protected void setupCards(QEInputReader reader) throws IOException {
            // No-op
        }

        @Override
        protected quantumforge.input.correcter.QEInputCorrecter createInputCorrector() {
            return null;
        }

        @Override
        public void reload() {}

        @Override
        public QEInput copy() {
            return null;
        }
    }

    @Test
    void testNebCorrecterAppliesClimbingImageParameters() {
        MockQEInput input = new MockQEInput();
        QENebInputCorrecter correcter = new QENebInputCorrecter(input);

        // 1. Test Auto climbing image
        correcter.setClimbingImageEnabled(true);
        correcter.setAutoScheme(true);
        correcter.correctInput();

        QENamelist path = input.getNamelist("PATH");
        assertNotNull(path);
        assertEquals("'neb'", path.getValue("string_method").getCharacterValue());
        assertEquals("'Auto'", path.getValue("CI_scheme").getCharacterValue());
        assertTrue(path.getValue("climbing_image") == null, "Auto scheme should not declare custom index");

        // 2. Test Manual climbing image index 3
        correcter.setAutoScheme(false);
        correcter.setManualClimbingImageIndex(3);
        correcter.correctInput();

        assertEquals("'Manual'", path.getValue("CI_scheme").getCharacterValue());
        assertEquals(3, path.getValue("climbing_image").getIntegerValue());
    }
}
