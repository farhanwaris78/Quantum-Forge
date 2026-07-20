/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.remote.OptimadeStructuresParser.OptimadeResponse;
import quantumforge.remote.OptimadeStructuresParser.Structure;

class OptimadeStructuresParserTest {

    private static final String FIXTURE = "{"
            + "\"meta\": {\"data_returned\": 2, \"provider\": {\"name\": \"Materials Cloud\"}},"
            + "\"data\": ["
            + "  {\"id\": \"mpf-1\", \"type\": \"structures\","
            + "   \"attributes\": {\"chemical_formula_reduced\": \"Si\", \"nsites\": 2,"
            + "                   \"elements\": [\"Si\"],"
            + "                   \"lattice_vectors\": [[1,0,0],[0,1,0],[0,0,1]]}},"
            + "  {\"id\": \"odbx-42\", \"attributes\": {\"nsites\": 8}}"
            + "]}";

    @Test
    void fixtureParsesWithExactFieldsAndFileClaimProvenance() {
        OperationResult<OptimadeResponse> result = OptimadeStructuresParser.parse(FIXTURE);
        assertTrue(result.isSuccess(), result.toString());
        OptimadeResponse response = result.getValue().orElseThrow();
        assertEquals(2, response.getStructures().size());
        assertEquals(Integer.valueOf(2), response.getDataReturnedClaim());
        assertEquals("Materials Cloud", response.getProviderNameClaim());

        Structure first = response.getStructures().get(0);
        assertEquals("mpf-1", first.getId());
        assertEquals("Si", first.getFormula());
        assertTrue(first.hasFormula());
        assertEquals(Integer.valueOf(2), first.getNsites());
        assertEquals(List.of("Si"), first.getElements());
        assertTrue(first.hasLattice(), "lattice present - OPTIMADE units are nm, stated");

        Structure second = response.getStructures().get(1);
        assertEquals("odbx-42", second.getId());
        assertEquals(Structure.FORMULA_MISSING, second.getFormula(),
                "absent formula is an explicit sentinel, never an invented value");
        assertFalse(second.hasFormula());
        assertEquals(Integer.valueOf(8), second.getNsites());
        assertTrue(second.getElements().isEmpty());
        assertFalse(second.hasLattice());
    }

    @Test
    void malformedJsonRefusesCompletely() {
        OperationResult<OptimadeResponse> result = OptimadeStructuresParser.parse(
                "{\"data\": [ {\"id\": \"x\"}, ");
        assertFalse(result.isSuccess());
        assertEquals("OPTIMADE_JSON", result.getCode(),
                "a truncated download is never parsed in part");
    }

    @Test
    void shapeViolationsRefuse() {
        assertEquals("OPTIMADE_SHAPE", OptimadeStructuresParser.parse(
                "[]").getCode(), "root must be a JSON object");
        assertEquals("OPTIMADE_SHAPE", OptimadeStructuresParser.parse(
                "{\"data\": {}}").getCode(), "data must be an array");
        assertEquals("OPTIMADE_SHAPE", OptimadeStructuresParser.parse(
                "{\"data\": [{\"attributes\": {}}]}").getCode(),
                "id is REQUIRED by JSON:API and never invented");
        assertEquals("OPTIMADE_SHAPE", OptimadeStructuresParser.parse(
                "{\"data\": [{\"id\": \"a\", \"attributes\": {\"nsites\": \"2\"}}]}").getCode(),
                "wrong-typed optional fields refuse instead of coercing");
        assertEquals("OPTIMADE_SHAPE", OptimadeStructuresParser.parse(
                "{\"data\": [{\"id\": \"a\", \"attributes\": {\"elements\": [3]}}]}").getCode());
        assertEquals("OPTIMADE_SHAPE", OptimadeStructuresParser.parse(
                "{\"meta\": {\"data_returned\": \"2\"}, \"data\": []}").getCode(),
                "even the provenance claim must be correctly typed");
    }

    @Test
    void emptyDataParsesAsAnHonestEmptyCollection() {
        OperationResult<OptimadeResponse> result = OptimadeStructuresParser.parse(
                "{\"data\": []}");
        assertTrue(result.isSuccess(), result.toString());
        assertTrue(result.getValue().orElseThrow().getStructures().isEmpty());
    }
}
