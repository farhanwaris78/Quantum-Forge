/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.hpc;

import java.util.ArrayList;
import java.util.List;

import quantumforge.operation.OperationResult;

/**
 * Rigorous k-point pool (-nk / -npool) divisor audit (Roadmap #102 data
 * layer): pw.x REQUIRES the pool count p to divide the number k of k points
 * being distributed, and the total rank count R must split evenly into pools
 * (R % p == 0, subgroup size r = R/p). This class works on the EXACT uniform
 * automatic mesh N = n1*n2*n3 read from the live input - no IBZ guessing -
 * so the "pool divisors of N" list is a rigorous superset window: a usable p
 * must divide N here, and must ALSO divide the (unknown-offline) irreducible
 * count pw.x actually distributes. The symmetry caveat is stated, never
 * hidden. Recommendations are the largest admissible p; -nb/-nt/-nd are
 * advisory text only (their optima depend on measured timing, per the QE
 * parallelization docs), never fabricated numbers.
 */
public final class PoolDivisorMath {

    /** Rank bound keeps every divisor product inside exact long arithmetic. */
    public static final long MAX_RANKS = 1_048_576L;
    /** Mesh bound keeps N = n1*n2*n3 far inside exact long arithmetic. */
    public static final long MAX_MESH_POINTS = 1_000_000_000L;

    /** Immutable audit bundle. */
    public static final class PoolAudit {
        private final long meshPoints;        // N = n1*n2*n3
        private final long totalRanks;        // R
        private final List<Long> meshDivisors;   // ALL divisors of N (ranked)
        private final List<Long> admissiblePools; // p in divisors with p<=R, R%p==0, r>=1... r=R/p>=1
        private final Long recommended;       // largest admissible, or null
        private final Long ranksPerPool;      // R/p when recommended, else null
        private final boolean currentPoolsValid;  // only meaningful when currentPoolsGiven
        private final int currentPools;       // the -nk value audited (0 = none given)

        PoolAudit(long meshPoints, long totalRanks, List<Long> meshDivisors,
                List<Long> admissiblePools, Long recommended, Long ranksPerPool,
                boolean currentPoolsValid, int currentPools) {
            this.meshPoints = meshPoints;
            this.totalRanks = totalRanks;
            this.meshDivisors = meshDivisors;
            this.admissiblePools = admissiblePools;
            this.recommended = recommended;
            this.ranksPerPool = ranksPerPool;
            this.currentPoolsValid = currentPoolsValid;
            this.currentPools = currentPools;
        }

        public long getMeshPoints() { return this.meshPoints; }
        public long getTotalRanks() { return this.totalRanks; }
        public List<Long> getMeshDivisors() { return List.copyOf(this.meshDivisors); }
        public List<Long> getAdmissiblePools() { return List.copyOf(this.admissiblePools); }
        public Long getRecommended() { return this.recommended; }
        public Long getRanksPerPool() { return this.ranksPerPool; }
        public boolean hasCurrentPools() { return this.currentPools > 0; }
        public int getCurrentPools() { return this.currentPools; }
        public boolean isCurrentPoolsValid() { return this.currentPoolsValid; }
    }

    private PoolDivisorMath() { }

    /**
     * Audits pool divisibility for a uniform mesh. Codes: POOL_MESH (grid
     * entries &lt; 1 or N beyond the bound), POOL_RANKS (R &lt; 1 or beyond the
     * bound).
     *
     * @param totalRanks total MPI ranks R for the run
     * @param grid automatic K_POINTS grid {n1, n2, n3}
     * @param currentPools the -nk value to audit, or 0 to skip the audit
     */
    public static OperationResult<PoolAudit> audit(long totalRanks, int[] grid,
            int currentPools) {
        if (grid == null || grid.length != 3) {
            return OperationResult.failed("POOL_MESH",
                    "The k-point grid must be the three integers of an automatic mesh.",
                    null);
        }
        long n = 1L;
        for (int i = 0; i < 3; i++) {
            if (grid[i] < 1) {
                return OperationResult.failed("POOL_MESH",
                        "Every automatic-grid entry must be >= 1, got " + grid[i] + ".",
                        null);
            }
            n *= grid[i];
            if (n > MAX_MESH_POINTS) {
                return OperationResult.failed("POOL_MESH",
                        "The uniform mesh exceeds the " + MAX_MESH_POINTS
                                + "-point audit bound; use QE's own pool diagnostics.",
                        null);
            }
        }
        if (totalRanks < 1L || totalRanks > MAX_RANKS) {
            return OperationResult.failed("POOL_RANKS",
                    "Total MPI ranks must be in 1.." + MAX_RANKS + ", got " + totalRanks
                            + ".",
                    null);
        }
        List<Long> divisors = divisorsOf(n);
        List<Long> admissible = new ArrayList<>();
        for (Long p : divisors) {
            if (p.longValue() <= totalRanks && totalRanks % p.longValue() == 0L) {
                admissible.add(p);
            }
        }
        Long recommended = null;
        Long ranksPerPool = null;
        if (!admissible.isEmpty()) {
            recommended = admissible.get(admissible.size() - 1);
            ranksPerPool = Long.valueOf(totalRanks / recommended.longValue());
        }
        boolean currentValid = false;
        if (currentPools > 0) {
            currentValid = n % currentPools == 0L
                    && totalRanks % currentPools == 0L
                    && (long) currentPools <= totalRanks;
        }
        return OperationResult.success("POOL_OK", "Pool audit complete.",
                new PoolAudit(n, totalRanks, divisors, admissible, recommended,
                        ranksPerPool, currentValid, currentPools));
    }

    /** All divisors of n in ascending order. */
    static List<Long> divisorsOf(long n) {
        List<Long> result = new ArrayList<>();
        for (long d = 1L; d * d <= n; d++) {
            if (n % d == 0L) {
                result.add(Long.valueOf(d));
                if (d * d != n) {
                    result.add(Long.valueOf(n / d));
                }
            }
        }
        result.sort(Long::compareTo);
        return result;
    }
}
