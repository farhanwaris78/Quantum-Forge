/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.hpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import quantumforge.hpc.PoolDivisorMath.PoolAudit;
import quantumforge.operation.OperationResult;

class PoolDivisorMathTest {

    @Test
    void exactMeshDivisibilityIsRigorous() {
        OperationResult<PoolAudit> result = PoolDivisorMath.audit(32L,
                new int[] {4, 4, 4}, 0);
        assertTrue(result.isSuccess(), result.toString());
        PoolAudit audit = result.getValue().orElseThrow();
        assertEquals(64L, audit.getMeshPoints());
        assertEquals(List.of(1L, 2L, 4L, 8L, 16L, 32L, 64L), audit.getMeshDivisors());
        assertEquals(List.of(1L, 2L, 4L, 8L, 16L, 32L), audit.getAdmissiblePools(),
                "p must divide N=64 AND 32, with p <= 32");
        assertEquals(Long.valueOf(32L), audit.getRecommended());
        assertEquals(Long.valueOf(1L), audit.getRanksPerPool());
        assertFalse(audit.hasCurrentPools(), "no -nk audit requested");
    }

    @Test
    void awkwardRankSetNarrowsToDivisorsOfBoth() {
        PoolAudit audit = PoolDivisorMath.audit(24L, new int[] {4, 4, 4}, 0)
                .getValue().orElseThrow();
        assertEquals(List.of(1L, 2L, 4L, 8L), audit.getAdmissiblePools(),
                "24 % 16 != 0 trims the tail");
        assertEquals(Long.valueOf(8L), audit.getRecommended());
        assertEquals(Long.valueOf(3L), audit.getRanksPerPool());
    }

    @Test
    void awkwardMeshPrunesHarderThanRanks() {
        PoolAudit audit = PoolDivisorMath.audit(64L, new int[] {2, 3, 5}, 0)
                .getValue().orElseThrow();
        assertEquals(30L, audit.getMeshPoints());
        assertEquals(List.of(1L, 2L, 3L, 5L, 6L, 10L, 15L, 30L), audit.getMeshDivisors());
        assertEquals(List.of(1L, 2L), audit.getAdmissiblePools(),
                "64 % 3, 64 % 5, ... all fail - honest pruning, no rounding");
        assertEquals(Long.valueOf(2L), audit.getRecommended());
        assertEquals(Long.valueOf(32L), audit.getRanksPerPool());
    }

    @Test
    void currentPoolAuditDetectsEveryMisconfiguration() {
        PoolAudit good = PoolDivisorMath.audit(24L, new int[] {4, 4, 4}, 8)
                .getValue().orElseThrow();
        assertTrue(good.hasCurrentPools());
        assertTrue(good.isCurrentPoolsValid(), "-nk 8 passes N=64 and R=24");

        PoolAudit notDivisorN = PoolDivisorMath.audit(24L, new int[] {4, 4, 4}, 5)
                .getValue().orElseThrow();
        assertFalse(notDivisorN.isCurrentPoolsValid(), "64 % 5 != 0");

        PoolAudit morePoolsThanPoints = PoolDivisorMath.audit(32L, new int[] {2, 2, 2}, 32)
                .getValue().orElseThrow();
        assertFalse(morePoolsThanPoints.isCurrentPoolsValid(),
                "8 % 32 != 0: more pools than k points");
        assertEquals(List.of(1L, 2L, 4L, 8L), morePoolsThanPoints.getAdmissiblePools());
        assertEquals(Long.valueOf(8L), morePoolsThanPoints.getRecommended());

        PoolAudit notDivisorR = PoolDivisorMath.audit(24L, new int[] {4, 4, 4}, 16)
                .getValue().orElseThrow();
        assertFalse(notDivisorR.isCurrentPoolsValid(), "24 % 16 != 0");
    }

    @Test
    void degenerateCasesRefuseClosed() {
        assertEquals("POOL_MESH", PoolDivisorMath.audit(8L, new int[] {0, 4, 4}, 0)
                .getCode(), "grid entries must be >= 1");
        assertEquals("POOL_RANKS", PoolDivisorMath.audit(0L, new int[] {4, 4, 4}, 0)
                .getCode(), "zero ranks");
        assertEquals("POOL_MESH", PoolDivisorMath.audit(8L, null, 0).getCode(),
                "a missing grid is not a uniform mesh");
        PoolAudit single = PoolDivisorMath.audit(1L, new int[] {1, 1, 1}, 0)
                .getValue().orElseThrow();
        assertEquals(Long.valueOf(1L), single.getRecommended());
        PoolAudit oversubscribed = PoolDivisorMath.audit(3L, new int[] {1, 1, 1}, 0)
                .getValue().orElseThrow();
        assertEquals(Long.valueOf(1L), oversubscribed.getRecommended(),
                "N=1 with R=3 falls back to 1 pool of 3 ranks - pw.x valid, idle capacity "
                        + "is stated by the report, never hidden");
    }

    @Test
    void divisorListIsCompleteAndSorted() {
        assertEquals(List.of(1L, 2L, 3L, 4L, 6L, 8L, 9L, 12L, 18L, 24L, 27L, 36L, 54L,
                72L, 108L, 216L), PoolDivisorMath.divisorsOf(216L));
    }
}
