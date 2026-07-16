package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;

class QeXmlResultParserTest {

    @Test
    void parsesSyntheticDataFileSchema() {
        Path xml = Path.of("tests/fixtures/qe/data-file-schema.xml");
        OperationResult<QeXmlResultParser.QeXmlResults> result = QeXmlResultParser.parseFile(xml);
        assertTrue(result.isSuccess(), result.toString());
        QeXmlResultParser.QeXmlResults data = result.getValue().orElseThrow();
        assertTrue(data.getTotalEnergyRy().isPresent());
        assertEquals(-15.85245678, data.getTotalEnergyRy().get(), 1.0e-8);
        assertTrue(data.getFermiEnergyEv().isPresent());
        // 0.2405 Ha * 27.211... ≈ 6.54 eV
        assertEquals(0.2405 * 27.211386245988, data.getFermiEnergyEv().get(), 1.0e-6);
        assertTrue(data.getScfConverged().orElse(false));
        assertEquals(2, data.getNat().orElse(-1));
        assertTrue(data.getTotalForce().isPresent());
        assertEquals(0.00012, data.getTotalForce().get(), 1e-12);
        assertTrue(data.getStressRyBohr3().isPresent());
    }

    @Test
    void missingFileFailsClosed() {
        OperationResult<QeXmlResultParser.QeXmlResults> result =
                QeXmlResultParser.parseFile(Path.of("tests/fixtures/qe/nope.xml"));
        assertFalse(result.isSuccess());
        assertTrue(result.getCode().contains("MISSING") || result.getMessage().toLowerCase().contains("not found"));
    }
}
