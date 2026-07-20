/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.remote;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import quantumforge.operation.OperationResult;

/**
 * Roadmap #117 (parse slice): fail-closed reader for a LOCAL, ALREADY-SAVED
 * OPTIMADE JSON:API {@code /structures} response. NOTHING is fetched by this
 * class - the fetch/pagination/cache work remains #117 runtime depth; the
 * only provenance available is the artifact the user placed in the project,
 * and the report says exactly that.
 *
 * <p>Honesty rules (fail-closed, never coerced):</p>
 * <ul>
 *   <li>malformed JSON refuses (OPTIMADE_JSON) - a half-written download is
 *       never parsed as if it were a response;</li>
 *   <li>a root that is not a JSON object, a missing/non-array {@code data}
 *       member, an entry without a string {@code id} (id is REQUIRED by
 *       JSON:API), or any inspected field carrying the WRONG JSON TYPE
 *       refuses with OPTIMADE_SHAPE - values are never guessed from a
 *       wrong-typed sibling;</li>
 *   <li>fields absent-but-optional (formula, nsites, elements) are reported
 *       as an explicit "(not supplied)" - never replaced by an invented
 *       value; the chemical formula preferred is
 *       {@code chemical_formula_reduced} and the naming says so;</li>
 *   <li>OPTIMADE lattice_vectors units are nm by specification - when a
 *       lattice is present the report states that unit, it never silently
 *       re-bases to Angstrom;</li>
 *   <li>{@code meta.data_returned} and {@code meta.provider.name}, when
 *       present and correctly typed, are echoed as provenance ONLY - they
 *       are claims made by the file, not verified facts.</li>
 * </ul>
 */
public final class OptimadeStructuresParser {

    /** Refuse pathological pastes: 8 MiB of JSON per artifact. */
    public static final long MAX_BYTES = 8L * 1024L * 1024L;

    /** One structure entry, fields optional except the JSON:API id. */
    public static final class Structure {
        private final String id;
        private final String formula;      // chemical_formula_reduced, or explicit sentinel
        private final boolean formulaGiven;
        private final Integer nsites;      // null = not supplied
        private final List<String> elements;  // empty = not supplied
        private final boolean latticeGiven;

        Structure(String id, String formula, boolean formulaGiven, Integer nsites,
                List<String> elements, boolean latticeGiven) {
            this.id = id;
            this.formula = formula;
            this.formulaGiven = formulaGiven;
            this.nsites = nsites;
            this.elements = elements;
            this.latticeGiven = latticeGiven;
        }

        public String getId() { return this.id; }
        /** Formula shown when none was supplied by the file. */
        public static final String FORMULA_MISSING = "(not supplied)";
        public String getFormula() { return this.formula; }
        public boolean hasFormula() { return this.formulaGiven; }
        /** null when the file did not supply nsites. */
        public Integer getNsites() { return this.nsites; }
        public List<String> getElements() { return this.elements; }
        public boolean hasLattice() { return this.latticeGiven; }
    }

    /** The parsed response plus the provenance the FILE claims. */
    public static final class OptimadeResponse {
        private final List<Structure> structures;
        private final Integer dataReturned;    // meta.data_returned claim, nullable
        private final String providerName;     // meta.provider.name claim, never null

        OptimadeResponse(List<Structure> structures, Integer dataReturned, String providerName) {
            this.structures = structures;
            this.dataReturned = dataReturned;
            this.providerName = providerName;
        }

        public List<Structure> getStructures() { return this.structures; }
        public Integer getDataReturnedClaim() { return this.dataReturned; }
        /** Empty string when the file carried no provider claim. */
        public String getProviderNameClaim() { return this.providerName; }
    }

    private OptimadeStructuresParser() {
    }

    /** Reads + parses a local artifact. Codes: OPTIMADE_IO / OPTIMADE_JSON / OPTIMADE_SHAPE. */
    public static OperationResult<OptimadeResponse> parseFile(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return OperationResult.failed("OPTIMADE_IO",
                    "The response artifact does not exist or is not a regular file.", null);
        }
        try {
            long size = Files.size(file);
            if (size > MAX_BYTES) {
                return OperationResult.failed("OPTIMADE_IO",
                        "The artifact is " + size + " bytes; the fail-closed cap per "
                                + "unfetched response is " + MAX_BYTES + " bytes.",
                        null);
            }
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            return OperationResult.failed("OPTIMADE_IO",
                    "Could not read the artifact: " + ex.getMessage(), null);
        }
    }

    /** Parses one response body. Codes: OPTIMADE_JSON / OPTIMADE_SHAPE. */
    public static OperationResult<OptimadeResponse> parse(String body) {
        JsonElement root;
        try {
            root = JsonParser.parseString(body == null ? "" : body);
        } catch (JsonSyntaxException ex) {
            return OperationResult.failed("OPTIMADE_JSON",
                    "Malformed JSON (a truncated download is never parsed in part): "
                            + ex.getMessage(),
                    null);
        }
        if (!root.isJsonObject()) {
            return OperationResult.failed("OPTIMADE_SHAPE",
                    "An OPTIMADE response root must be a JSON object.", null);
        }
        JsonObject doc = root.getAsJsonObject();
        Integer dataReturned = null;
        String providerName = "";
        JsonElement meta = doc.get("meta");
        if (meta != null && !meta.isJsonNull()) {
            if (!meta.isJsonObject()) {
                return OperationResult.failed("OPTIMADE_SHAPE",
                        "'meta' must be an object when present.", null);
            }
            JsonObject metaObj = meta.getAsJsonObject();
            JsonElement returned = metaObj.get("data_returned");
            if (returned != null && !returned.isJsonNull()) {
                if (!returned.isJsonPrimitive() || !returned.getAsJsonPrimitive().isNumber()) {
                    return OperationResult.failed("OPTIMADE_SHAPE",
                            "'meta.data_returned' must be a number when present - no coercion.",
                            null);
                }
                dataReturned = Integer.valueOf(returned.getAsInt());
            }
            JsonElement provider = metaObj.get("provider");
            if (provider != null && !provider.isJsonNull()) {
                if (!provider.isJsonObject()) {
                    return OperationResult.failed("OPTIMADE_SHAPE",
                            "'meta.provider' must be an object when present.", null);
                }
                JsonElement pname = provider.getAsJsonObject().get("name");
                if (pname != null && !pname.isJsonNull()) {
                    if (!pname.isJsonPrimitive() || !pname.getAsJsonPrimitive().isString()) {
                        return OperationResult.failed("OPTIMADE_SHAPE",
                                "'meta.provider.name' must be a string when present.", null);
                    }
                    providerName = pname.getAsString();
                }
            }
        }
        JsonElement data = doc.get("data");
        if (data == null || !data.isJsonArray()) {
            return OperationResult.failed("OPTIMADE_SHAPE",
                    "'data' must be present and be an array (JSON:API collection "
                            + "responses only; single-entry responses are refused, not "
                            + "silently wrapped).",
                    null);
        }
        JsonArray entries = data.getAsJsonArray();
        List<Structure> structures = new ArrayList<>();
        int idx = 0;
        for (JsonElement entry : entries) {
            idx += 1;
            if (!entry.isJsonObject()) {
                return OperationResult.failed("OPTIMADE_SHAPE",
                        "data[" + (idx - 1) + "] is not an object.", null);
            }
            JsonObject obj = entry.getAsJsonObject();
            JsonElement idEl = obj.get("id");
            if (idEl == null || !idEl.isJsonPrimitive()
                    || !idEl.getAsJsonPrimitive().isString()
                    || idEl.getAsString().isBlank()) {
                return OperationResult.failed("OPTIMADE_SHAPE",
                        "data[" + (idx - 1) + "] has no non-blank string 'id' - ids are "
                                + "REQUIRED by JSON:API and are never invented.",
                        null);
            }
            String id = idEl.getAsString();
            JsonElement attrsEl = obj.get("attributes");
            String formula = Structure.FORMULA_MISSING;
            boolean formulaGiven = false;
            Integer nsites = null;
            List<String> elements = List.of();
            boolean latticeGiven = false;
            if (attrsEl != null && !attrsEl.isJsonNull()) {
                if (!attrsEl.isJsonObject()) {
                    return OperationResult.failed("OPTIMADE_SHAPE",
                            "data[" + (idx - 1) + "].attributes must be an object.", null);
                }
                JsonObject attrs = attrsEl.getAsJsonObject();
                JsonElement formulaEl = attrs.get("chemical_formula_reduced");
                if (formulaEl != null && !formulaEl.isJsonNull()) {
                    if (!formulaEl.isJsonPrimitive()
                            || !formulaEl.getAsJsonPrimitive().isString()) {
                        return OperationResult.failed("OPTIMADE_SHAPE",
                                "chemical_formula_reduced of '" + id
                                        + "' must be a string - no coercion.",
                                null);
                    }
                    formula = formulaEl.getAsString();
                    formulaGiven = true;
                }
                JsonElement nsitesEl = attrs.get("nsites");
                if (nsitesEl != null && !nsitesEl.isJsonNull()) {
                    if (!nsitesEl.isJsonPrimitive() || !nsitesEl.getAsJsonPrimitive().isNumber()) {
                        return OperationResult.failed("OPTIMADE_SHAPE",
                                "nsites of '" + id + "' must be a number - not '"
                                        + nsitesEl + "'.",
                                null);
                    }
                    nsites = Integer.valueOf(nsitesEl.getAsInt());
                }
                JsonElement elementsEl = attrs.get("elements");
                if (elementsEl != null && !elementsEl.isJsonNull()) {
                    if (!elementsEl.isJsonArray()) {
                        return OperationResult.failed("OPTIMADE_SHAPE",
                                "elements of '" + id + "' must be an array.", null);
                    }
                    List<String> el = new ArrayList<>();
                    for (JsonElement symbol : elementsEl.getAsJsonArray()) {
                        if (!symbol.isJsonPrimitive() || !symbol.getAsJsonPrimitive().isString()) {
                            return OperationResult.failed("OPTIMADE_SHAPE",
                                    "elements of '" + id + "' must contain strings only.",
                                    null);
                        }
                        el.add(symbol.getAsString());
                    }
                    elements = List.copyOf(el);
                }
                JsonElement latticeEl = attrs.get("lattice_vectors");
                if (latticeEl != null && !latticeEl.isJsonNull()) {
                    if (!latticeEl.isJsonArray() || latticeEl.getAsJsonArray().size() != 3) {
                        return OperationResult.failed("OPTIMADE_SHAPE",
                                "lattice_vectors of '" + id + "' must be a 3-vector of rows "
                                        + "(OPTIMADE units are nm) when supplied.",
                                null);
                    }
                    latticeGiven = true;
                }
            }
            structures.add(new Structure(id, formula, formulaGiven, nsites, elements,
                    latticeGiven));
        }
        return OperationResult.success("OPTIMADE_OK", "Parsed " + structures.size()
                + " structure(s) from the local artifact.", new OptimadeResponse(
                List.copyOf(structures), dataReturned, providerName));
    }
}
