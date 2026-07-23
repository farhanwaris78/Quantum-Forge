/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.builder.TransformJournal.JournalEntry;
import quantumforge.builder.TransformJournal.JournalSummary;
import quantumforge.operation.OperationResult;

class TransformJournalTest {

    @TempDir
    private Path tempDir;

    @Test
    void entriesChainDeterministicallyWithPythonVerifiedHashes() {
        TransformJournal journal = new TransformJournal();
        JournalEntry first = journal.append("cod:9011998", "supercell",
                new double[]{2.0, 0.0, 0.0, 0.0, 2.0, 0.0, 0.0, 0.0, 2.0},
                List.of("atoms=8"));
        assertEquals("eaf3ad94a184fc3927360fd5fdb11361588fec4f17d9e7d4062875eff3f91e92",
                first.getEntryHash(), "SHA-256 of the canonical body (Python-verified)");
        assertEquals(TransformJournal.GENESIS, first.getParentHash());

        JournalEntry second = journal.append("journal-chain", "strain",
                new double[]{1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.5},
                List.of("axis=c", "magnitude=0.5"));
        assertEquals(first.getEntryHash(), second.getParentHash());
        assertEquals("f464ae338ee61702520a2cf21b0e3a442fabffac995d87facd6950b04f766a34",
                second.getEntryHash(), "SHA-256 chained (Python-verified)");
        assertEquals(2, journal.getEntryCount());

        String rendered = journal.render();
        String expected = "# qf-journal v1\n"
                + "1|cod:9011998|supercell|2.0,0.0,0.0,0.0,2.0,0.0,0.0,0.0,2.0|atoms=8"
                + "|GENESIS|eaf3ad94a184fc3927360fd5fdb11361588fec4f17d9e7d4062875eff3f91e92\n"
                + "2|journal-chain|strain|1.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,1.5"
                + "|axis=c;magnitude=0.5"
                + "|eaf3ad94a184fc3927360fd5fdb11361588fec4f17d9e7d4062875eff3f91e92"
                + "|f464ae338ee61702520a2cf21b0e3a442fabffac995d87facd6950b04f766a34\n";
        assertEquals(expected, rendered, "canonical render is byte-exact");

        OperationResult<JournalSummary> parsed = TransformJournal.parse(rendered);
        assertTrue(parsed.isSuccess(), parsed.toString());
        JournalSummary summary = parsed.getValue().orElseThrow();
        assertEquals(2, summary.getEntryCount());
        assertEquals(2, summary.getMatrixCount());
        assertEquals("cod:9011998", summary.getEntries().get(0).getSourceId());
        assertEquals(2.0, summary.getEntries().get(0).getMatrix()[0], 1e-12);
        assertEquals(1.5, summary.getEntries().get(1).getMatrix()[8], 1e-12);
        assertEquals(List.of("axis=c", "magnitude=0.5"),
                summary.getEntries().get(1).getParameters());
        assertEquals(second.getEntryHash(),
                summary.getEntries().get(1).getEntryHash(), "hash survives the round trip");
    }

    @Test
    void verifyAcceptsFilesAndOptionalFieldsRoundTrip() throws IOException {
        TransformJournal journal = new TransformJournal();
        journal.append("local: Water", "rotate", null, List.of());
        journal.append("local: Water", "symmetrize", null, List.of("tolerance=1e-5"));
        Path file = this.tempDir.resolve("water.qfj");
        Files.writeString(file, journal.render());
        OperationResult<JournalSummary> verified = TransformJournal.verify(file);
        assertTrue(verified.isSuccess(), verified.toString());
        JournalSummary summary = verified.getValue().orElseThrow();
        assertEquals(2, summary.getEntryCount());
        assertEquals(0, summary.getMatrixCount(), "no matrix in these ops");
        assertEquals("symmetrize", summary.getEntries().get(1).getOperation());
        assertEquals("JOURNAL_OK", verified.getCode());
    }

    @Test
    void tamperedAndMalformedJournalsAreRefusedNotRepaired() {
        TransformJournal journal = new TransformJournal();
        journal.append("cod:9011998", "supercell",
                new double[]{2.0, 0.0, 0.0, 0.0, 2.0, 0.0, 0.0, 0.0, 2.0},
                List.of("atoms=8"));
        String rendered = journal.render();

        String tampered = rendered.replace("atoms=8", "atoms=9");
        OperationResult<JournalSummary> tamper = TransformJournal.parse(tampered);
        assertFalse(tamper.isSuccess());
        assertEquals("JOURNAL_HASH", tamper.getCode(), "hash mismatch is refused");

        String badSeq = rendered.replace("1|cod", "2|cod");
        OperationResult<JournalSummary> seq = TransformJournal.parse(badSeq);
        assertFalse(seq.isSuccess());
        assertEquals("JOURNAL_SEQ", seq.getCode());

        OperationResult<JournalSummary> noHeader = TransformJournal.parse("1|a|b|-|-\n");
        assertFalse(noHeader.isSuccess());
        assertEquals("JOURNAL_SYNTAX", noHeader.getCode(), "header is mandatory");

        OperationResult<JournalSummary> badMatrix = TransformJournal.parse(
                "# qf-journal v1\n1|a|b|1.0,2.0,3.0|-|GENESIS|00\n");
        assertFalse(badMatrix.isSuccess());
        assertEquals("JOURNAL_VALUE", badMatrix.getCode());

        OperationResult<JournalSummary> empty = TransformJournal.parse("  \n");
        assertFalse(empty.isSuccess());
        assertEquals("JOURNAL_EMPTY", empty.getCode());

        OperationResult<JournalSummary> missing = TransformJournal.verify(
                this.tempDir.resolve("absent.qfj"));
        assertFalse(missing.isSuccess());
        assertEquals("JOURNAL_IO", missing.getCode());
    }

    @Test
    void unsafeFieldsAreRejectedAtAppendTime() {
        TransformJournal journal = new TransformJournal();
        assertThrows(IllegalArgumentException.class,
                () -> journal.append("bad|source", "op", null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> journal.append("src", "op;p", null, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> journal.append("src", "op", new double[8], List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> journal.append("src", "op", null, List.of("nokey")));
    }
}
