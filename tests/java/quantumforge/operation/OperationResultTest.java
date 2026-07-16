package quantumforge.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.project.ConfigUpdater;

class OperationResultTest {
    @Test
    void distinguishesSuccessFailureAndUnsupportedWithoutBooleanAmbiguity() {
        OperationResult<Integer> success = OperationResult.success("COUNT", "files copied", 3);
        assertTrue(success.isSuccess());
        assertEquals(3, success.getValue().orElseThrow());

        OperationResult<Integer> unsupported = OperationResult.unsupported("NO_SFTP", "not implemented");
        assertFalse(unsupported.isSuccess());
        assertEquals(OperationStatus.UNSUPPORTED, unsupported.getStatus());
        assertTrue(unsupported.getValue().isEmpty());
    }

    @Test
    void destructiveGeometryPrototypeFailsWithoutChangingAnything() {
        OperationResult<Void> result = new ConfigUpdater(null).updateFromOutputResult();
        assertEquals(OperationStatus.FAILED, result.getStatus());
        assertFalse(new ConfigUpdater(null).updateFromOutput());
    }

    @Test
    void enforcesDiagnosticCodeAndMessage() {
        assertThrows(IllegalArgumentException.class,
                () -> OperationResult.failed("", "message", null));
        assertThrows(IllegalArgumentException.class,
                () -> OperationResult.success("OK", "", 1));
    }
}
