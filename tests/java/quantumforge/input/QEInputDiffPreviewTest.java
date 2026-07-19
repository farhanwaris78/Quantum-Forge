package quantumforge.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import quantumforge.input.QEInputDiffPreview.ChangeType;
import quantumforge.input.QEInputDiffPreview.DiffItem;
import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValueBase;

class QEInputDiffPreviewTest {

    private static class MockQEInput extends QEInput {
        @Override
        protected void setupNamelists(QEInputReader reader) throws IOException {
            this.setupNamelist(NAMELIST_CONTROL, reader);
            this.setupNamelist(NAMELIST_SYSTEM, reader);
        }

        @Override
        protected void setupCards(QEInputReader reader) throws IOException {
            // No-op for mock
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

        @Override
        public boolean isValid() {
            return true;
        }
    }

    @Test
    void testCompareIdentifiesAddedRemovedAndModifiedParameters() {
        MockQEInput base = new MockQEInput();
        MockQEInput modified = new MockQEInput();

        QENamelist baseSystem = base.getNamelist(QEInput.NAMELIST_SYSTEM);
        QENamelist modSystem = modified.getNamelist(QEInput.NAMELIST_SYSTEM);

        // ecutwfc modified from 30.0 to 45.0
        baseSystem.setValue(QEValueBase.getInstance("ecutwfc", "30.0"));
        modSystem.setValue(QEValueBase.getInstance("ecutwfc", "45.0"));

        // nat removed in modified
        baseSystem.setValue(QEValueBase.getInstance("nat", "4"));

        // ibrav added in modified
        modSystem.setValue(QEValueBase.getInstance("ibrav", "0"));

        List<DiffItem> diffs = QEInputDiffPreview.compare(base, modified);
        assertNotNull(diffs);
        assertEquals(3, diffs.size());

        boolean hasEcutwfc = false;
        boolean hasNat = false;
        boolean hasIbrav = false;

        for (DiffItem item : diffs) {
            assertEquals(QEInput.NAMELIST_SYSTEM, item.getSection());
            if ("ecutwfc".equals(item.getKey())) {
                assertEquals(ChangeType.MODIFIED, item.getType());
                assertEquals("30.0", item.getOldValue());
                assertEquals("45.0", item.getNewValue());
                hasEcutwfc = true;
            } else if ("nat".equals(item.getKey())) {
                assertEquals(ChangeType.REMOVED, item.getType());
                assertEquals("4", item.getOldValue());
                hasNat = true;
            } else if ("ibrav".equals(item.getKey())) {
                assertEquals(ChangeType.ADDED, item.getType());
                assertEquals("0", item.getNewValue());
                hasIbrav = true;
            }
        }

        assertTrue(hasEcutwfc);
        assertTrue(hasNat);
        assertTrue(hasIbrav);

        String report = QEInputDiffPreview.generateReport(base, modified);
        assertNotNull(report);
        assertTrue(report.contains("[~] SYSTEM: changed ecutwfc from 30.0 to 45.0"));
        assertTrue(report.contains("[-] SYSTEM: removed nat (was 4)"));
        assertTrue(report.contains("[+] SYSTEM: set ibrav = 0"));
    }

    @Test
    void treatsEquivalentFortranNumericSpellingsAsUnchanged() {
        MockQEInput base = new MockQEInput();
        MockQEInput modified = new MockQEInput();
        base.getNamelist(QEInput.NAMELIST_SYSTEM).setValue(QEValueBase.getInstance("ecutwfc", "3D1"));
        modified.getNamelist(QEInput.NAMELIST_SYSTEM).setValue(QEValueBase.getInstance("ecutwfc", "30.0"));
        assertTrue(QEInputDiffPreview.compare(base, modified).isEmpty());
    }
}
