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
 * Roadmap #116 (parse slice): fail-closed reader for a LOCAL, ALREADY-SAVED
 * Materials Project mp-api v2 {@code /materials/summary} response body.
 * NOTHING is fetched here - retrieval (key-safe, via the MP_QUERY_DRAFT from
 * the query-builder slice) remains #116 runtime depth. The artifact the user
 * placed in the project is the only provenance, and the report states that.
 *
 * <p>Honesty rules (fail-closed, never coerced):</p>
 * <ul>
 *   <li>malformed JSON refuses (MP_JSON) - truncated downloads are never
 *       parsed in part;</li>
 *   <li>a non-object root, a missing/non-array {@code data} member, an entry
 *       without a non-blank string {@code material_id}, or any inspected
 *       field carrying the WRONG JSON TYPE refuses as MP_SHAPE;</li>
 *   <li>non-finite numbers (NaN/Infinity, which strict JSON forbids) refuse
 *       as MP_SHAPE - they must never reach a report;</li>
 *   <li>absent-but-optional fields ({@code formula_pretty}, {@code nsites},
 *       {@code band_gap}, {@code energy_above_hull}, {@code is_stable}) are
 *       reported as explicit "(not supplied)" - a missing band gap is NOT a
 *       zero gap and is never rendered as 0.0;</li>
 *   <li>units are stated every time a value prints: band_gap in eV and
 *       energy_above_hull in eV/atom, per the mp-api summary schema.</li>
 * </ul>
 */
public final class MpSummaryParser {

    /** Refuse pathological pastes: 8 MiB of JSON per artifact. */
    public static final long MAX_BYTES = 8L * 1024L * 1024L;

    /** Sentinel printed when a field was not supplied by the file. */
    public static final String NOT_SUPPLIED = "(not supplied)";

    /** One summary document; only material_id is mandatory. */
    public static final class SummaryDoc {
        private final String materialId;
        private final String formulaPretty;    // sentinel when absent
        private final Integer nsites;          // null when absent
        private final Double bandGapEv;        // null when absent; NaN/Inf refused
        private final Double energyAboveHullEvPerAtom;  // null when absent
        private final Boolean isStable;        // null when absent

        SummaryDoc(String materialId, String formulaPretty, Integer nsites,
                Double bandGapEv, Double energyAboveHullEvPerAtom, Boolean isStable) {
            this.materialId = materialId;
            this.formulaPretty = formulaPretty;
            this.nsites = nsites;
            this.bandGapEv = bandGapEv;
            this.energyAboveHullEvPerAtom = energyAboveHullEvPerAtom;
            this.isStable = isStable;
        }

        public String getMaterialId() { return this.materialId; }
        public String getFormulaPretty() { return this.formulaPretty; }
        public Integer getNsites() { return this.nsites; }
        public Double getBandGapEv() { return this.bandGapEv; }
        public Double getEnergyAboveHullEvPerAtom() { return this.energyAboveHullEvPerAtom; }
        public Boolean getIsStable() { return this.isStable; }
    }

    private MpSummaryParser() {
    }

    /** Reads + parses a local artifact. Codes: MP_IO / MP_JSON / MP_SHAPE. */
    public static OperationResult<List<SummaryDoc>> parseFile(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return OperationResult.failed("MP_IO",
                    "The response artifact does not exist or is not a regular file.", null);
        }
        try {
            long size = Files.size(file);
            if (size > MAX_BYTES) {
                return OperationResult.failed("MP_IO",
                        "The artifact is " + size + " bytes; the fail-closed cap per "
                                + "unfetched response is " + MAX_BYTES + " bytes.",
                        null);
            }
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            return OperationResult.failed("MP_IO",
                    "Could not read the artifact: " + ex.getMessage(), null);
        }
    }

    /** Parses one summary response body. Codes: MP_JSON / MP_SHAPE. */
    public static OperationResult<List<SummaryDoc>> parse(String body) {
        JsonElement root;
        try {
            root = JsonParser.parseString(body == null ? "" : body);
        } catch (JsonSyntaxException ex) {
            return OperationResult.failed("MP_JSON",
                    "Malformed JSON (a truncated download is never parsed in part): "
                            + ex.getMessage(),
                    null);
        }
        if (!root.isJsonObject()) {
            return OperationResult.failed("MP_SHAPE",
                    "An mp-api summary response root must be a JSON object.", null);
        }
        JsonElement data = root.getAsJsonObject().get("data");
        if (data == null || !data.isJsonArray()) {
            return OperationResult.failed("MP_SHAPE",
                    "'data' must be present and be an array of summary documents.",
                    null);
        }
        JsonArray entries = data.getAsJsonArray();
        List<SummaryDoc> docs = new ArrayList<>();
        int idx = 0;
        for (JsonElement entry : entries) {
            if (!entry.isJsonObject()) {
                return OperationResult.failed("MP_SHAPE",
                        "data[" + idx + "] is not an object.", null);
            }
            JsonObject obj = entry.getAsJsonObject();
            String materialId = stringField(obj, "material_id", true, idx);
            if (materialId == null) {
                return OperationResult.failed("MP_SHAPE",
                        "data[" + idx + "] has no non-blank string 'material_id' - ids "
                                + "are the retrieval handle and are never invented.",
                        null);
            }
            String formulaPretty = stringField(obj, "formula_pretty", false, idx);
            if (formulaPretty == null && obj.has("formula_pretty")) {
                return wrongType("formula_pretty", "a string");
            }
            Integer nsites = intField(obj, "nsites", idx);
            if (nsites == null && obj.has("nsites")) {
                return wrongType("nsites", "a number");
            }
            Double bandGap = doubleField(obj, "band_gap", idx);
            if (bandGap == null && obj.has("band_gap")) {
                return wrongType("band_gap", "a finite number (eV)");
            }
            Double eah = doubleField(obj, "energy_above_hull", idx);
            if (eah == null && obj.has("energy_above_hull")) {
                return wrongType("energy_above_hull", "a finite number (eV/atom)");
            }
            Boolean stable = boolField(obj, "is_stable", idx);
            if (stable == null && obj.has("is_stable")) {
                return wrongType("is_stable", "a boolean");
            }
            docs.add(new SummaryDoc(materialId,
                    formulaPretty == null || formulaPretty.isBlank()
                            ? NOT_SUPPLIED : formulaPretty,
                    nsites, bandGap, eah, stable));
            idx += 1;
        }
        return OperationResult.success("MP_OK",
                "Parsed " + docs.size() + " summary document(s) from the local artifact.",
                List.copyOf(docs));
    }

    private static OperationResult<List<SummaryDoc>> wrongType(String field, String want) {
        return OperationResult.failed("MP_SHAPE",
                "'" + field + "' must be " + want + " when present - no coercion.", null);
    }

    /** null = absent; returns null also when required-and-absent (caller decides). */
    private static String stringField(JsonObject obj, String name, boolean required, int idx) {
        JsonElement el = obj.get(name);
        if (el == null || el.isJsonNull()) {
            return null;
        }
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
            return null;
        }
        String value = el.getAsString();
        if (required && value.isBlank()) {
            return null;
        }
        return value;
    }

    private static Integer intField(JsonObject obj, String name, int idx) {
        JsonElement el = obj.get(name);
        if (el == null || el.isJsonNull()) {
            return null;
        }
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        double value = el.getAsDouble();
        if (!Double.isFinite(value) || value != Math.rint(value)) {
            return null;  // nsites is a count - a non-integer is a wrong type
        }
        return Integer.valueOf((int) value);
    }

    private static Double doubleField(JsonObject obj, String name, int idx) {
        JsonElement el = obj.get(name);
        if (el == null || el.isJsonNull()) {
            return null;
        }
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        double value = el.getAsDouble();
        return Double.isFinite(value) ? Double.valueOf(value) : null;
    }

    private static Boolean boolField(JsonObject obj, String name, int idx) {
        JsonElement el = obj.get(name);
        if (el == null || el.isJsonNull()) {
            return null;
        }
        if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isBoolean()) {
            return null;
        }
        return Boolean.valueOf(el.getAsBoolean());
    }
}
