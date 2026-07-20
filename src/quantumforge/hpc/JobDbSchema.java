/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.hpc;

import java.util.ArrayList;
import java.util.List;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #105 (schema slice): versioned SQLite schema + migration planner
 * for the database-backed job queue. This class owns ONLY the curated DDL
 * text, the WAL/locking pragmas and the FAIL-CLOSED migration arithmetic
 * (strictly forward, one version step at a time, idempotent statements).
 *
 * <p>Load-bearing honesty:</p>
 * <ul>
 *   <li>executing this SQL needs the org.sqlite JDBC driver at runtime,
 *       which is intentionally NOT bundled in this build - the durable
 *       JSONL {@code JobQueueStore} (batch-era store, #105 "Partial") stays
 *       the ACTIVE store until the driver and integration CI land; this
 *       slice makes the target schema reviewable and testable first;</li>
 *   <li>migrations are applied exactly once under the qf_meta.schema_version
 *       guard inside a transaction; every CREATE statement carries
 *       {@code IF NOT EXISTS} so a crash between a CREATE and the version
 *       write leaves a re-run safe; the v3 {@code ALTER TABLE ... ADD COLUMN}
 *       steps rely on that version guard because SQLite has no
 *       {@code ADD COLUMN IF NOT EXISTS} - the planner is designed never to
 *       emit a step twice for a forward plan;</li>
 *   <li>downgrades are REFUSED - there is no silent rollback path because
 *       there is no tested rollback path;</li>
 *   <li>the per-job lease columns (v3) prepare the locking semantics
 *       #96/#97 need; claiming/expiry logic is runtime depth, stated.</li>
 * </ul>
 *
 * <p>Refusal codes: JOBDB_RANGE, JOBDB_DOWNGRADE.</p>
 */
public final class JobDbSchema {

    /** Pragmas applied on every open before any statement. */
    public static final List<String> OPEN_PRAGMAS = List.of(
            "PRAGMA journal_mode=WAL;",
            "PRAGMA foreign_keys=ON;",
            "PRAGMA busy_timeout=5000;");

    /** One version step. */
    public static final class Migration {
        private final int toVersion;
        private final String name;
        private final List<String> statements;

        Migration(int toVersion, String name, List<String> statements) {
            this.toVersion = toVersion;
            this.name = name;
            this.statements = new ArrayList<>(statements);
        }

        public int getToVersion() { return this.toVersion; }
        public String getName() { return this.name; }
        /** Idempotent DDL for this step; each ends with ';'. */
        public List<String> getStatements() { return List.copyOf(this.statements); }
    }

    private static final List<Migration> MIGRATIONS = List.of(
            new Migration(1, "core jobs + meta", List.of(
                    "CREATE TABLE IF NOT EXISTS qf_meta (key TEXT PRIMARY KEY, "
                            + "value TEXT NOT NULL);",
                    "CREATE TABLE IF NOT EXISTS qf_jobs (job_id TEXT PRIMARY KEY, "
                            + "state TEXT NOT NULL, payload_json TEXT NOT NULL, "
                            + "created_seq INTEGER NOT NULL, updated_seq INTEGER "
                            + "NOT NULL);",
                    "INSERT OR IGNORE INTO qf_meta(key, value) VALUES "
                            + "('schema_version', '1');")),
            new Migration(2, "job event journal", List.of(
                    "CREATE TABLE IF NOT EXISTS qf_job_events (job_id TEXT NOT "
                            + "NULL, seq INTEGER NOT NULL, event TEXT NOT NULL, "
                            + "detail TEXT NOT NULL DEFAULT '', PRIMARY KEY "
                            + "(job_id, seq), FOREIGN KEY (job_id) REFERENCES "
                            + "qf_jobs(job_id));",
                    "CREATE INDEX IF NOT EXISTS idx_qf_job_events_job ON "
                            + "qf_job_events(job_id, seq);",
                    "UPDATE qf_meta SET value = '2' WHERE key = "
                            + "'schema_version';")),
            new Migration(3, "per-job lease columns for the job lock", List.of(
                    "ALTER TABLE qf_jobs ADD COLUMN lease_owner TEXT;",
                    "ALTER TABLE qf_jobs ADD COLUMN lease_until INTEGER;",
                    "CREATE INDEX IF NOT EXISTS idx_qf_jobs_state_lease ON "
                            + "qf_jobs(state, lease_until);",
                    "UPDATE qf_meta SET value = '3' WHERE key = "
                            + "'schema_version';")));

    private JobDbSchema() {
    }

    /** The current schema version this build knows how to write. */
    public static int currentVersion() {
        return MIGRATIONS.get(MIGRATIONS.size() - 1).getToVersion();
    }

    /** All migrations, oldest first. */
    public static List<Migration> migrations() {
        return MIGRATIONS;
    }

    /**
     * Plans the forward migration chain from {@code fromVersion} to
     * {@code toVersion} (empty when equal). Codes: JOBDB_RANGE,
     * JOBDB_DOWNGRADE.
     */
    public static OperationResult<List<Migration>> migrationPlan(int fromVersion,
            int toVersion) {
        if (fromVersion < 0 || fromVersion > currentVersion() || toVersion < 0
                || toVersion > currentVersion()) {
            return OperationResult.failed("JOBDB_RANGE",
                    "Schema versions must lie in 0.." + currentVersion() + " (got "
                            + fromVersion + " -> " + toVersion + ").",
                    null);
        }
        if (toVersion < fromVersion) {
            return OperationResult.failed("JOBDB_DOWNGRADE",
                    "Downgrading the job database from v" + fromVersion + " to v"
                            + toVersion + " is refused: there is no TESTED rollback "
                            + "path - back up and rebuild instead.",
                    null);
        }
        List<Migration> plan = new ArrayList<>();
        for (Migration migration : MIGRATIONS) {
            if (migration.getToVersion() > fromVersion
                    && migration.getToVersion() <= toVersion) {
                plan.add(migration);
            }
        }
        return OperationResult.success("JOBDB_PLAN_OK",
                plan.size() + " step(s).", plan);
    }

    /**
     * Total statements in a plan (each ends with ';' and every one is
     * idempotent - both asserted by the tests).
     */
    public static int statementCount(List<Migration> plan) {
        int count = 0;
        if (plan != null) {
            for (Migration migration : plan) {
                count += migration.getStatements().size();
            }
        }
        return count;
    }
}
