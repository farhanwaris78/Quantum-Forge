/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.ssh;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import quantumforge.hpc.JobRecord;
import quantumforge.hpc.JobState;
import quantumforge.hpc.RemoteJobMonitor.StatusUpdate;

/**
 * Batch-138 (#96 GUI slice): the monitor dialog's display truth, pinned
 * headlessly - exact line shapes, receipt-time stamps, empty-raw quoting,
 * note/update indistinguishability guards and the capped-ring eviction count.
 */
class MonitorLogModelTest {

    private static final Instant AT = Instant.parse("2026-07-20T10:15:30Z");

    private static JobRecord runningRecord() {
        JobRecord record = new JobRecord("job-1", "slurm", "fugaku", "/scratch/qf/job-1");
        record.setSchedulerJobId("123456");
        record.transition(JobState.RUNNING, "poll: R");
        return record;
    }

    @Test
    void schedulerUpdatesRenderWithReceiptStampsAndQuotedRaws() {
        MonitorLogModel model = new MonitorLogModel();
        String line = model.appendUpdate(AT,
                new StatusUpdate(runningRecord(), "R", false, "status=RUNNING raw=R"));
        assertEquals("[2026-07-20T10:15:30Z] state=RUNNING raw='R' - status=RUNNING raw=R",
                line, "the scheduler-observed line shape is exact and owned");
        assertEquals(List.of(line), model.snapshot());
        assertEquals(line, model.text());
    }

    @Test
    void emptyRawStaysQuotedAndTerminalIsMarked() {
        JobRecord record = runningRecord();
        record.transition(JobState.UNKNOWN,
                "job absent per the slurm adapter's documented needle (Invalid job id)");
        MonitorLogModel model = new MonitorLogModel();
        String line = model.appendUpdate(AT, new StatusUpdate(record, "", true,
                "job absent per the slurm adapter's documented needle"));
        assertTrue(line.contains("raw='' (terminal)"),
                "an empty raw is quoted, never reformatted into a fake status: " + line);
        assertTrue(line.startsWith("[2026-07-20T10:15:30Z] state=UNKNOWN"), line);
    }

    @Test
    void dialogNotesArePrefixedSoTheyCanNeverMasqueradeAsStatuses() {
        MonitorLogModel model = new MonitorLogModel();
        String note = model.appendNote(AT,
                "poll now: [MONITOR_ERROR] transport failure (never a status verdict)");
        assertEquals("[2026-07-20T10:15:30Z] * poll now: [MONITOR_ERROR] transport "
                + "failure (never a status verdict)", note,
                "dialog-observed lines carry the * prefix");
        String empty = model.appendNote(AT, "   ");
        assertTrue(empty.contains("(empty note - nothing to show)"),
                "an empty note is shown, not swallowed: " + empty);
    }

    @Test
    void ringEvictionsAreCountedAndDisclosed() {
        MonitorLogModel model = new MonitorLogModel();
        for (int i = 1; i <= MonitorLogModel.MAX_LINES; i++) {
            model.appendNote(AT, "line " + i);
        }
        assertEquals(0, model.getDroppedCount());
        assertEquals("", model.droppedNotice(), "no notice before the first eviction");
        for (int i = MonitorLogModel.MAX_LINES + 1; i <= MonitorLogModel.MAX_LINES + 5; i++) {
            model.appendNote(AT, "line " + i);
        }
        assertEquals(5, model.getDroppedCount());
        assertEquals(MonitorLogModel.MAX_LINES, model.snapshot().size(),
                "the ring never exceeds its owned, stated bound");
        assertTrue(model.snapshot().get(0).endsWith("line 6"),
                "eviction is oldest-first: " + model.snapshot().get(0));
        assertTrue(model.snapshot().get(MonitorLogModel.MAX_LINES - 1).endsWith("line 205"));
        assertEquals("(5 older line(s) dropped - cap 200)", model.droppedNotice(),
                "dropped status lines are disclosed, never invisible");
        assertFalse(model.text().endsWith("line 5"));
    }
}
