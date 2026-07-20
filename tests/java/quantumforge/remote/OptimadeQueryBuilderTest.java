/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.remote.OptimadeQueryBuilder.OptimadeQuery;

class OptimadeQueryBuilderTest {

    @Test
    void buildsTheExactPercentEncodedUrl() {
        OperationResult<OptimadeQuery> result = OptimadeQueryBuilder.build(
                "https://optimade.materialsproject.org/v1", "Si,O", 3, 20, 20);
        assertTrue(result.isSuccess(), result.toString());
        OptimadeQuery query = result.getValue().orElseThrow();
        assertEquals("https://optimade.materialsproject.org/v1",
                query.getNormalizedBase());
        assertEquals("elements HAS ALL \"Si\",\"O\" AND nelements<=3 AND nsites<=20",
                query.getFilter());
        assertEquals(List.of("elements HAS ALL \"Si\",\"O\"", "nelements<=3",
                "nsites<=20"), query.getClauses());
        assertEquals(20, query.getPageLimit());
        assertEquals("https://optimade.materialsproject.org/v1/structures?filter="
                + "elements%20HAS%20ALL%20%22Si%22%2C%22O%22%20AND%20nelements%3C%3D3"
                + "%20AND%20nsites%3C%3D20&page_limit=20&page_offset=0",
                query.getUrl(), "Python-verified encoding; nothing was fetched");
    }

    @Test
    void defaultsAndNormalizationHold() {
        OperationResult<OptimadeQuery> result = OptimadeQueryBuilder.build(
                "HTTPS://OPTIMADE.EXAMPLE.org/v1/", "Fe", 0, 0, 0);
        assertTrue(result.isSuccess(), result.toString());
        OptimadeQuery query = result.getValue().orElseThrow();
        assertEquals("https://optimade.example.org/v1", query.getNormalizedBase(),
                "scheme/host lowered, trailing slash stripped");
        assertEquals("elements HAS ALL \"Fe\"", query.getFilter(),
                "zero caps omit their clauses");
        assertEquals(20, query.getPageLimit(), "0 maps to the default page limit");
        assertTrue(query.getUrl().contains("nelements") == false,
                "no omitted clause leaks into the URL");
    }

    @Test
    void unsafeBasesAndInputsFailClosed() {
        OperationResult<OptimadeQuery> creds = OptimadeQueryBuilder.build(
                "https://user:pw@example.org/v1", "Si", 0, 0, 0);
        assertFalse(creds.isSuccess());
        assertEquals("OPTIMADE_BASE", creds.getCode(),
                "credentials in a logged URL are refused");

        OperationResult<OptimadeQuery> wrongRoot = OptimadeQueryBuilder.build(
                "https://example.org/v2", "Si", 0, 0, 0);
        assertFalse(wrongRoot.isSuccess());
        assertEquals("OPTIMADE_BASE", wrongRoot.getCode());

        OperationResult<OptimadeQuery> ftp = OptimadeQueryBuilder.build(
                "ftp://example.org/v1", "Si", 0, 0, 0);
        assertFalse(ftp.isSuccess());
        assertEquals("OPTIMADE_BASE", ftp.getCode());

        OperationResult<OptimadeQuery> queryString = OptimadeQueryBuilder.build(
                "https://example.org/v1?x=1", "Si", 0, 0, 0);
        assertFalse(queryString.isSuccess());
        assertEquals("OPTIMADE_BASE", queryString.getCode());

        OperationResult<OptimadeQuery> noElements = OptimadeQueryBuilder.build(
                "https://example.org/v1", "  ", 0, 0, 0);
        assertFalse(noElements.isSuccess());
        assertEquals("OPTIMADE_ELEMENT", noElements.getCode());

        OperationResult<OptimadeQuery> badSymbol = OptimadeQueryBuilder.build(
                "https://example.org/v1", "Si,X1", 0, 0, 0);
        assertFalse(badSymbol.isSuccess());
        assertEquals("OPTIMADE_ELEMENT", badSymbol.getCode(),
                "never quoted into a filter blindly");

        OperationResult<OptimadeQuery> tooMany = OptimadeQueryBuilder.build(
                "https://example.org/v1", "H,He,Li,Be,B,C,N,O,F", 0, 0, 0);
        assertFalse(tooMany.isSuccess());
        assertEquals("OPTIMADE_ELEMENT", tooMany.getCode());

        OperationResult<OptimadeQuery> badCap = OptimadeQueryBuilder.build(
                "https://example.org/v1", "Si", -1, 0, 0);
        assertFalse(badCap.isSuccess());
        assertEquals("OPTIMADE_VALUE", badCap.getCode());

        OperationResult<OptimadeQuery> badLimit = OptimadeQueryBuilder.build(
                "https://example.org/v1", "Si", 0, 0, 1001);
        assertFalse(badLimit.isSuccess());
        assertEquals("OPTIMADE_VALUE", badLimit.getCode());
    }
}
