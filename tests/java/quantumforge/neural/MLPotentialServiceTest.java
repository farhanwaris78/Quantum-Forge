package quantumforge.neural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Regression coverage for the Roadmap-#136 rename (GNNFroceField ->
 * MLPotentialService): the registry behaviour is preserved and the renamed type
 * keeps its descriptor-only contract (no execution, no availability claim).
 */
class MLPotentialServiceTest {

    @Test
    void testRegistryIntactAfterRename() {
        MLPotentialService service = new MLPotentialService();
        assertTrue(service.getAvailableFields().length >= 8,
                "The descriptor registry must keep its entries after the rename");
        MLPotentialService.MLPotentialDescriptor mace = service.getField("MACE");
        assertNotNull(mace);
        assertEquals("mace", mace.pythonPackage);
        assertEquals("MACE-MP-0b", mace.modelFile);
        assertNull(service.getField("NoSuchModel"), "Unknown names resolve to null");
    }
}
