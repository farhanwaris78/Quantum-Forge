package quantumforge.symmetry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import quantumforge.operation.OperationResult;

class QEAsinteropServiceTest {

    @Test
    void testServiceFailsClosedWhenPythonIsMissing() {
        // Point to non-existent executable to force failure
        QEAsinteropService service = new QEAsinteropService(null, Path.of("missing.py"));
        assertFalse(service.isAvailable());

        OperationResult<String> res = service.convert("cif", "xyz", "data");
        assertFalse(res.isSuccess(), "Service must fail closed when unavailable");
        assertTrue(res.getCode().contains("UNAVAILABLE") || res.getMessage().contains("available"));
    }
}
