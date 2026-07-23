/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.remote.SyncManifestBuilder.SyncManifest;

class SyncManifestBuilderTest {

    @Test
    void fullManifestRendersRolesAndIntentNotFacts() {
        OperationResult<SyncManifest> result = SyncManifestBuilder.validate(
                "pw.out, xml/data-file-schema.xml", "*.dat", "wfc1.dat", "*.core, tmp");
        assertTrue(result.isSuccess(), result.toString());
        SyncManifest manifest = result.getValue().orElseThrow();
        String block = manifest.render();
        assertTrue(block.contains("# qf-sync-manifest v1"), block);
        assertTrue(block.contains("required = pw.out, xml/data-file-schema.xml\n"), block);
        assertTrue(block.contains("optional = *.dat\n"), block);
        assertTrue(block.contains("large_on_demand = wfc1.dat\n"), block);
        assertTrue(block.contains("excluded = *.core, tmp\n"), block);
        assertTrue(block.contains("UNKNOWN until first fetch"), block + " | " + "sizes/checksums are intent, not fabricated facts");
        assertTrue(block.contains("TOP-LEVEL"), block);
    }

    @Test
    void emptyOptionalRolesRenderAsNoneDeclared() {
        OperationResult<SyncManifest> result = SyncManifestBuilder.validate(
                "pw.out", "", "", "");
        assertTrue(result.isSuccess(), result.toString());
        String block = result.getValue().orElseThrow().render();
        assertTrue(block.contains("required = pw.out\n"), block);
        assertTrue(block.contains("# optional = (none declared)\n"), block);
        assertTrue(block.contains("# large_on_demand = (none declared)\n"), block);
        assertTrue(block.contains("# excluded = (none declared)\n"), block);
    }

    @Test
    void oneRolePerNameIsEnforcedAcrossAndInsideLists() {
        OperationResult<SyncManifest> cross = SyncManifestBuilder.validate(
                "pw.out", "pw.out", "", "");
        assertFalse(cross.isSuccess());
        assertEquals("SYNC_DUPLICATE", cross.getCode(),
                "required AND excluded for the same name would resolve silently");
        assertEquals("SYNC_DUPLICATE", SyncManifestBuilder.validate(
                "a.dat", "b.dat", "c.dat", "b.dat").getCode());
        assertEquals("SYNC_DUPLICATE", SyncManifestBuilder.validate(
                "pw.out, pw.out", "", "", "").getCode(),
                "even inside one role a name appears exactly once");
    }

    @Test
    void grammarViolationsRefuseRatherThanMisinterpret() {
        assertEquals("SYNC_ENTRY", SyncManifestBuilder.validate(
                "../secret", "", "", "").getCode());
        assertEquals("SYNC_ENTRY", SyncManifestBuilder.validate(
                "/abs/path", "", "", "").getCode());
        assertEquals("SYNC_ENTRY", SyncManifestBuilder.validate(
                "dir sub/file.dat", "", "", "").getCode(),
                "whitespace never reaches a sync line");
        assertEquals("SYNC_ENTRY", SyncManifestBuilder.validate(
                "a$(id).dat", "", "", "").getCode());
        assertEquals("SYNC_ENTRY", SyncManifestBuilder.validate(
                "**/*.dat", "", "", "").getCode(),
                "recursive globs refuse - they are not silently narrowed");
        assertEquals("SYNC_ENTRY", SyncManifestBuilder.validate(
                "core.*", "", "", "").getCode(),
                "a trailing star reads as a literal that never exists - refused");
        assertEquals("SYNC_ENTRY", SyncManifestBuilder.validate(
                "*.", "", "", "").getCode());
    }

    @Test
    void emptyRequiredListIsCeremonialAndRefuses() {
        OperationResult<SyncManifest> result = SyncManifestBuilder.validate(
                "", "out.dat", "", "");
        assertFalse(result.isSuccess());
        assertEquals("SYNC_REQUIRED", result.getCode(),
                "a manifest that fetches nothing essential is ceremonial");
        OperationResult<SyncManifest> blank = SyncManifestBuilder.validate(
                "  ", "", "", "");
        assertEquals("SYNC_REQUIRED", blank.getCode());
    }

    @Test
    void literalDraftCompilesToTheTypedRuntimeManifest() {
        OperationResult<SyncManifest> validated = SyncManifestBuilder.validate(
                "pw.log, pw.save/data-file-schema.xml", "pw.err", "density.dat",
                "core.dump");
        assertTrue(validated.isSuccess(), validated.toString());
        OperationResult<quantumforge.hpc.ResultSyncManifest> bridge =
                validated.getValue().orElseThrow().toRuntimeManifest();
        assertTrue(bridge.isSuccess(), bridge.toString());
        assertEquals("SYNC_BRIDGE_OK", bridge.getCode());
        quantumforge.hpc.ResultSyncManifest runtime = bridge.getValue().orElseThrow();
        assertEquals(4, runtime.getEntries().size(),
                "2 required + 1 optional + 1 large - the excluded name never compiles");
        assertEquals(java.util.List.of("pw.log", "pw.save/data-file-schema.xml"),
                runtime.requiredPaths());
        assertEquals(quantumforge.hpc.ResultSyncManifest.Priority.LARGE_OPTIONAL,
                runtime.getEntries().get(3).getPriority());
        assertEquals(quantumforge.hpc.ResultSyncManifest.Priority.OPTIONAL,
                runtime.getEntries().get(2).getPriority());
        for (quantumforge.hpc.ResultSyncManifest.Entry entry : runtime.getEntries()) {
            assertFalse(entry.getRelativePath().contains("core.dump"),
                    "excluded names are NEVER transferred - they never appear at all");
        }
        assertTrue(bridge.getMessage().contains("NEVER transferred"), bridge.getMessage());
    }

    @Test
    void wildcardIntentRefusesToCompileToAFetchablePath() {
        OperationResult<SyncManifest> validated = SyncManifestBuilder.validate(
                "pw.log", "*.dat", "", "");
        assertTrue(validated.isSuccess(),
                "the wildcard is legal draft INTENT - the DRAFT validates");
        OperationResult<quantumforge.hpc.ResultSyncManifest> bridge =
                validated.getValue().orElseThrow().toRuntimeManifest();
        assertFalse(bridge.isSuccess(),
                "a compiled '*.dat' would make the runtime try to download a file"
                        + " literally named '*.dat' - the silent-miss trap, refused");
        assertEquals("SYNC_MANIFEST_PATH", bridge.getCode());
        assertTrue(bridge.getMessage().contains("draft INTENT"), bridge.getMessage());
        assertTrue(bridge.getMessage().contains("resolve it to literal names"),
                bridge.getMessage());
    }
}
