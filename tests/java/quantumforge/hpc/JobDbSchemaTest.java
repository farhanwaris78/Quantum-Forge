/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.hpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import quantumforge.hpc.JobDbSchema.Migration;
import quantumforge.operation.OperationResult;

class JobDbSchemaTest {

    @Test
    void schemaIsVersionedAndWalPrepared() {
        assertEquals(3, JobDbSchema.currentVersion());
        assertEquals(3, JobDbSchema.migrations().size());
        assertEquals(List.of(1, 2, 3),
                JobDbSchema.migrations().stream().map(Migration::getToVersion).toList(),
                "strictly ordered one-step versions");
        assertTrue(JobDbSchema.OPEN_PRAGMAS.contains("PRAGMA journal_mode=WAL;"),
                "WAL mode is the queue durability requirement");
        assertTrue(JobDbSchema.OPEN_PRAGMAS.contains("PRAGMA foreign_keys=ON;"));
        int createCount = 0;
        for (Migration migration : JobDbSchema.migrations()) {
            for (String statement : migration.getStatements()) {
                assertTrue(statement.endsWith(";"),
                        "every statement terminates: " + statement);
                if (statement.startsWith("CREATE ")) {
                    createCount += 1;
                    assertTrue(statement.contains("IF NOT EXISTS"),
                            "crash-safe re-runs: " + statement);
                }
            }
        }
        assertTrue(createCount >= 3, "core tables + index all guarded");
        assertTrue(JobDbSchema.migrations().get(2).getStatements().stream()
                .anyMatch(s -> s.startsWith("ALTER TABLE qf_jobs ADD COLUMN")),
                "v3 adds the lease columns the job lock needs");
    }

    @Test
    void migrationPlansAreExactAndForwardOnly() {
        OperationResult<List<Migration>> fresh = JobDbSchema.migrationPlan(0, 3);
        assertTrue(fresh.isSuccess(), fresh.toString());
        assertEquals(3, fresh.getValue().orElseThrow().size());
        assertEquals(10, JobDbSchema.statementCount(fresh.getValue().orElseThrow()),
                "v1(3) + v2(3) + v3(4): exact statement count");

        OperationResult<List<Migration>> middle = JobDbSchema.migrationPlan(1, 3);
        assertTrue(middle.isSuccess());
        assertEquals(List.of(2, 3), middle.getValue().orElseThrow().stream()
                .map(Migration::getToVersion).toList());

        OperationResult<List<Migration>> noop = JobDbSchema.migrationPlan(2, 2);
        assertTrue(noop.isSuccess());
        assertTrue(noop.getValue().orElseThrow().isEmpty(),
                "same version is an honest empty plan, not an error");

        OperationResult<List<Migration>> down = JobDbSchema.migrationPlan(3, 1);
        assertFalse(down.isSuccess());
        assertEquals("JOBDB_DOWNGRADE", down.getCode(),
                "no silent rollback without a tested rollback path");

        OperationResult<List<Migration>> range = JobDbSchema.migrationPlan(0, 9);
        assertFalse(range.isSuccess());
        assertEquals("JOBDB_RANGE", range.getCode());
    }
}
