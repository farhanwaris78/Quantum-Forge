package quantumforge.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CapabilityRegistryTest {
    @Test
    void exposesHonestStableCapabilityMatrix() {
        assertTrue(CapabilityRegistry.list().size() >= 15);
        assertEquals(CapabilityStatus.UNAVAILABLE,
                CapabilityRegistry.get(CapabilityRegistry.SSH_HPC).getStatus());
        assertEquals(CapabilityStatus.EXPERIMENTAL,
                CapabilityRegistry.get(CapabilityRegistry.VASP).getStatus());
        assertFalse(CapabilityRegistry.get(CapabilityRegistry.QE_INPUT).isProductionSupported());
        assertNotNull(CapabilityRegistry.createReport());
        assertTrue(CapabilityRegistry.createReport().contains("Required next:"));
    }

    @Test
    void unavailableScientificExceptionContainsRemediation() {
        ScientificFeatureUnavailableException error = assertThrows(
                ScientificFeatureUnavailableException.class,
                () -> { throw new ScientificFeatureUnavailableException(
                        CapabilityRegistry.ADVANCED_SCIENCE, "Weyl search"); });
        assertTrue(error.getMessage().contains("Weyl search"));
        assertTrue(error.getMessage().contains("reviewed plugins"));
    }
}
