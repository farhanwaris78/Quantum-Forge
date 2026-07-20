/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.remote.MpSummaryParser.SummaryDoc;

class MpSummaryParserTest {

    private static final String FIXTURE = "{\"data\": ["
            + " {\"material_id\": \"mp-149\", \"formula_pretty\": \"Si\", \"nsites\": 2,"
            + "  \"band_gap\": 0.61, \"energy_above_hull\": 0.0, \"is_stable\": true},"
            + " {\"material_id\": \"mp-13\", \"formula_pretty\": \"Fe\","
            + "  \"energy_above_hull\": 0.0},"
            + " {\"material_id\": \"mvc-1115\", \"nsites\": 3, \"band_gap\": 2.5}"
            + "]}";

    @Test
    void fixtureParsesFieldForFieldWithHonestSentinels() {
        OperationResult<List<SummaryDoc>> result = MpSummaryParser.parse(FIXTURE);
        assertTrue(result.isSuccess(), result.toString());
        List<SummaryDoc> docs = result.getValue().orElseThrow();
        assertEquals(3, docs.size());

        SummaryDoc si = docs.get(0);
        assertEquals("mp-149", si.getMaterialId());
        assertEquals("Si", si.getFormulaPretty());
        assertEquals(Integer.valueOf(2), si.getNsites());
        assertEquals(0.61, si.getBandGapEv(), 1e-12);
        assertEquals(0.0, si.getEnergyAboveHullEvPerAtom(), 1e-12);
        assertEquals(Boolean.TRUE, si.getIsStable());

        SummaryDoc fe = docs.get(1);
        assertEquals("mp-13", fe.getMaterialId());
        assertEquals("Fe", fe.getFormulaPretty());
        assertTrue(fe.getNsites() == null, "absent nsites stays absent - never invented");
        assertTrue(fe.getBandGapEv() == null,
                "a missing band gap is NOT a zero gap - it stays an explicit absence");
        assertTrue(fe.getIsStable() == null);

        SummaryDoc third = docs.get(2);
        assertEquals("mvc-1115", third.getMaterialId());
        assertEquals(MpSummaryParser.NOT_SUPPLIED, third.getFormulaPretty(),
                "absent formula_pretty prints the sentinel, not an empty cell");
        assertEquals(2.5, third.getBandGapEv(), 1e-12);
    }

    @Test
    void malformedJsonRefusesCompletely() {
        OperationResult<List<SummaryDoc>> result = MpSummaryParser.parse(
                "{\"data\": [ {\"material_id\": \"mp-1\"}, ");
        assertFalse(result.isSuccess());
        assertEquals("MP_JSON", result.getCode());
    }

    @Test
    void shapeViolationsRefuse() {
        assertEquals("MP_SHAPE", MpSummaryParser.parse("[]").getCode(),
                "root must be a JSON object");
        assertEquals("MP_SHAPE", MpSummaryParser.parse("{\"data\": {}}").getCode(),
                "data must be an array");
        assertEquals("MP_SHAPE", MpSummaryParser.parse(
                "{\"data\": [{\"formula_pretty\": \"Si\"}]}").getCode(),
                "material_id is required and never invented");
        assertEquals("MP_SHAPE", MpSummaryParser.parse(
                "{\"data\": [{\"material_id\": \"mp-1\", \"nsites\": 2.5}]}").getCode(),
                "a site COUNT cannot be fractional - refused, not rounded");
        assertEquals("MP_SHAPE", MpSummaryParser.parse(
                "{\"data\": [{\"material_id\": \"mp-1\", \"band_gap\": \"0.61\"}]}").getCode(),
                "string-typed numerics refuse instead of coercing");
        OperationResult<List<SummaryDoc>> nan = MpSummaryParser.parse(
                "{\"data\": [{\"material_id\": \"mp-1\", \"band_gap\": NaN}]}");
        assertFalse(nan.isSuccess(),
                "non-finite values must never reach a report - MP_SHAPE when the reader "
                        + "accepts the literal (lenient gson) or MP_JSON when the reader "
                        + "rejects it at syntax level (strict gson); BOTH are fail-closed");
        assertTrue(nan.getCode().startsWith("MP_"), nan.getCode());
        assertEquals("MP_SHAPE", MpSummaryParser.parse(
                "{\"data\": [{\"material_id\": \"mp-1\", \"is_stable\": \"yes\"}]}").getCode());
    }

    @Test
    void emptyDataIsAnHonestEmptyCollection() {
        OperationResult<List<SummaryDoc>> result = MpSummaryParser.parse("{\"data\": []}");
        assertTrue(result.isSuccess(), result.toString());
        assertTrue(result.getValue().orElseThrow().isEmpty());
    }
}
