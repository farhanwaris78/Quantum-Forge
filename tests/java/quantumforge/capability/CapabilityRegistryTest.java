package quantumforge.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import quantumforge.app.extension.ExtensionManager;

class CapabilityRegistryTest {
    @Test
    void exposesHonestStableCapabilityMatrix() {
        assertTrue(CapabilityRegistry.list().size() >= 15);
        assertEquals(CapabilityStatus.PARTIAL,
                CapabilityRegistry.get(CapabilityRegistry.SSH_HPC).getStatus());
        assertEquals(CapabilityStatus.EXPERIMENTAL,
                CapabilityRegistry.get(CapabilityRegistry.VASP).getStatus());
        assertFalse(CapabilityRegistry.get(CapabilityRegistry.QE_INPUT).isProductionSupported());
        assertNotNull(CapabilityRegistry.createReport());
        assertTrue(CapabilityRegistry.createReport().contains("Required next:"));
    }

    @Test
    void experimentalExtensionSketchesAreNotExposedToTheProductionGui() {
        assertTrue(ExtensionManager.getInstance().getExtensions().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> ExtensionManager.getInstance().getExtensions().add(null));
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
