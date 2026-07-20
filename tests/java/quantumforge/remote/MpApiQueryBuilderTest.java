/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.remote.MpApiQueryBuilder.MpQuery;

class MpApiQueryBuilderTest {

    @Test
    void buildsTheSummaryUrlWithoutKeyMaterial() {
        OperationResult<MpQuery> result = MpApiQueryBuilder.build(
                "https://api.materialsproject.org/", "mp-149, mp-13", "mytok42abc");
        assertTrue(result.isSuccess(), result.toString());
        MpQuery query = result.getValue().orElseThrow();
        assertEquals("https://api.materialsproject.org", query.getNormalizedBase());
        assertEquals(List.of("mp-149", "mp-13"), query.getMaterialIds(),
                "analyst order preserved exactly");
        assertEquals("https://api.materialsproject.org/materials/summary/"
                + "?material_ids=mp-149,mp-13", query.getUrl(),
                "raw comma list; no key material in the URL");
        assertTrue(query.isApiKeyProvided());
        assertFalse(query.getUrl().contains("mytok42abc"),
                "the key never lands in a logged URL");
    }

    @Test
    void keyIsOptionalButHeaderNamedAndSanityChecked() {
        OperationResult<MpQuery> noKey = MpApiQueryBuilder.build(
                "https://api.materialsproject.org", "mvc-5736", null);
        assertTrue(noKey.isSuccess(), noKey.toString());
        assertFalse(noKey.getValue().orElseThrow().isApiKeyProvided());
        assertEquals("https://api.materialsproject.org/materials/summary/"
                + "?material_ids=mvc-5736", noKey.getValue().orElseThrow().getUrl(),
                "mvc- task ids are in the owned subset");

        OperationResult<MpQuery> dirtyKey = MpApiQueryBuilder.build(
                "https://api.materialsproject.org", "mp-149", "tok\n\r en");
        assertFalse(dirtyKey.isSuccess());
        assertEquals("MP_KEY", dirtyKey.getCode(),
                "whitespace in a key is an injection guard, not a typo fix");
    }

    @Test
    void unsafeBasesAndIdsFailClosed() {
        OperationResult<MpQuery> http = MpApiQueryBuilder.build(
                "http://api.materialsproject.org", "mp-149", null);
        assertFalse(http.isSuccess());
        assertEquals("MP_BASE", http.getCode(), "Materials Project is TLS-only");

        OperationResult<MpQuery> creds = MpApiQueryBuilder.build(
                "https://user:pw@api.materialsproject.org", "mp-149", null);
        assertFalse(creds.isSuccess());
        assertEquals("MP_BASE", creds.getCode());

        OperationResult<MpQuery> deep = MpApiQueryBuilder.build(
                "https://api.materialsproject.org/materials", "mp-149", null);
        assertFalse(deep.isSuccess());
        assertEquals("MP_BASE", deep.getCode(), "API root only");

        OperationResult<MpQuery> noIds = MpApiQueryBuilder.build(
                "https://api.materialsproject.org", "  ", null);
        assertFalse(noIds.isSuccess());
        assertEquals("MP_ID", noIds.getCode());

        OperationResult<MpQuery> badId = MpApiQueryBuilder.build(
                "https://api.materialsproject.org", "mp-", null);
        assertFalse(badId.isSuccess());
        assertEquals("MP_ID", badId.getCode(), "never interpolated blindly");

        OperationResult<MpQuery> badPrefix = MpApiQueryBuilder.build(
                "https://api.materialsproject.org", "xyz-149", null);
        assertFalse(badPrefix.isSuccess());
        assertEquals("MP_ID", badPrefix.getCode());

        OperationResult<MpQuery> tooMany = MpApiQueryBuilder.build(
                "https://api.materialsproject.org",
                java.util.stream.IntStream.rangeClosed(1, 21)
                        .mapToObj(i -> "mp-" + i)
                        .reduce((a, b) -> a + "," + b).orElse(""),
                null);
        assertFalse(tooMany.isSuccess());
        assertEquals("MP_ID", tooMany.getCode());
    }
}
