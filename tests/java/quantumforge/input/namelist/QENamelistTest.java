package quantumforge.input.namelist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QENamelistTest {

    @Test
    void parsesTypedQuantumEspressoValues() {
        QENamelist system = new QENamelist("system");
        assertTrue(system.setValue("ibrav = 2"));
        assertTrue(system.setValue("ecutwfc = 6.0d1"));
        assertTrue(system.setValue("noncolin = .true."));
        assertTrue(system.setValue("occupations = 'smearing'"));

        assertEquals(2, system.getValue("ibrav").getIntegerValue());
        assertEquals(60.0, system.getValue("ecutwfc").getRealValue(), 1.0e-12);
        assertTrue(system.getValue("noncolin").getLogicalValue());
        assertEquals("smearing", system.getValue("occupations").getCharacterValue());
        assertNotNull(system.toString());

        assertTrue(system.removeValue("noncolin"));
        assertFalse(system.removeValue("does_not_exist"));
    }
}
