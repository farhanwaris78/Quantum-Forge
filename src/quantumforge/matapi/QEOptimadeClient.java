/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.matapi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import quantumforge.atoms.model.Cell;
import quantumforge.atoms.model.exception.ZeroVolumCellException;

/**
 * Standard-compliant OPTIMADE (Open Databases Integration for Materials Design) API client.
 * Connects to unified materials databases (Materials Cloud, COD, AFLOW), implements in-memory
 * cached query responses, and parses standard structures to build 3D crystal Cells (Roadmap #117).
 */
public final class QEOptimadeClient {

    public enum Provider {
        MATERIALS_CLOUD("Materials Cloud", "https://api.materialscloud.org/optimade/v1/"),
        COD("Crystallography Open Database", "https://www.crystallography.net/cod/optimade/v1/"),
        AFLOW("AFLOW", "http://aflow.org/API/optimade/v1/");

        private final String label;
        private final String baseUrl;

        Provider(String label, String url) {
            this.label = label;
            this.baseUrl = url;
        }

        public String getLabel() { return this.label; }
        public String getBaseUrl() { return this.baseUrl; }
    }

    public static final class OptimadeStructure {
        private final String id;
        private final String formula;
        private final double[][] lattice;
        private final double[][] positions;
        private final String[] species;

        public OptimadeStructure(String id, String formula, double[][] lattice, double[][] positions, String[] species) {
            this.id = id;
            this.formula = formula;
            this.lattice = lattice;
            this.positions = positions;
            this.species = species;
        }

        public String getId() { return this.id; }
        public String getFormula() { return this.formula; }
        public double[][] getLattice() { return this.lattice; }
        public double[][] getPositions() { return this.positions; }
        public String[] getSpecies() { return this.species; }

        /**
         * Reconstructs a complete 3D unit Cell from the OPTIMADE attributes.
         */
        public Cell buildCell() throws ZeroVolumCellException {
            Cell cell = new Cell(this.lattice);
            for (int i = 0; i < this.species.length; i++) {
                cell.addAtom(this.species[i], this.positions[i][0], this.positions[i][1], this.positions[i][2]);
            }
            return cell;
        }
    }

    // In-memory query cache to prevent rate-limit violations and conserve bandwidth
    private static final Map<String, String> QUERY_CACHE = new HashMap<>();

    private QEOptimadeClient() {
        // Utility
    }

    /**
     * Executes an OPTIMADE query against the specified database provider.
     * 
     * @param provider the API endpoint provider
     * @param filter standard OPTIMADE filter query, e.g. "elements HAS 'Si' AND nelements=1"
     */
    public static List<OptimadeStructure> queryStructures(Provider provider, String filter) throws IOException {
        List<OptimadeStructure> results = new ArrayList<>();
        if (provider == null || filter == null || filter.isBlank()) {
            return results;
        }

        String encodedFilter = URLEncoder.encode(filter.trim(), StandardCharsets.UTF_8);
        String urlString = provider.getBaseUrl() + "structures?filter=" + encodedFilter;

        String jsonResponse;
        synchronized (QUERY_CACHE) {
            if (QUERY_CACHE.containsKey(urlString)) {
                jsonResponse = QUERY_CACHE.get(urlString);
            } else {
                jsonResponse = fetchUrlString(urlString);
                if (jsonResponse != null && !jsonResponse.isBlank()) {
                    QUERY_CACHE.put(urlString, jsonResponse);
                }
            }
        }

        if (jsonResponse == null || jsonResponse.isBlank()) {
            return results;
        }

        // Parse standard OPTIMADE v1 JSON payload
        try {
            JsonObject root = new Gson().fromJson(jsonResponse, JsonObject.class);
            if (root.has("data")) {
                JsonArray dataArray = root.getAsJsonArray("data");
                for (JsonElement el : dataArray) {
                    JsonObject item = el.getAsJsonObject();
                    String id = item.get("id").getAsString();
                    JsonObject attrs = item.getAsJsonObject("attributes");

                    String formula = attrs.has("chemical_formula_anonymous") 
                        ? attrs.get("chemical_formula_anonymous").getAsString() : "unknown";

                    // Extract 3D Lattice Vectors
                    JsonArray latArr = attrs.getAsJsonArray("lattice_vectors");
                    double[][] lattice = new double[3][3];
                    for (int i = 0; i < 3; i++) {
                        JsonArray vec = latArr.get(i).getAsJsonArray();
                        lattice[i][0] = vec.get(0).getAsDouble();
                        lattice[i][1] = vec.get(1).getAsDouble();
                        lattice[i][2] = vec.get(2).getAsDouble();
                    }

                    // Extract Cartesian Positions
                    JsonArray posArr = attrs.getAsJsonArray("cartesian_site_positions");
                    int numAtoms = posArr.size();
                    double[][] positions = new double[numAtoms][3];
                    for (int i = 0; i < numAtoms; i++) {
                        JsonArray pos = posArr.get(i).getAsJsonArray();
                        positions[i][0] = pos.get(0).getAsDouble();
                        positions[i][1] = pos.get(1).getAsDouble();
                        positions[i][2] = pos.get(2).getAsDouble();
                    }

                    // Extract Species names
                    JsonArray specArr = attrs.getAsJsonArray("species_at_sites");
                    String[] species = new String[numAtoms];
                    for (int i = 0; i < numAtoms; i++) {
                        species[i] = specArr.get(i).getAsString();
                    }

                    results.add(new OptimadeStructure(id, formula, lattice, positions, species));
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse standard OPTIMADE JSON payload: " + e.getMessage(), e);
        }

        return results;
    }

    private static String fetchUrlString(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("Accept", "application/json");

        int status = conn.getResponseCode();
        if (status != 200) {
            conn.disconnect();
            throw new IOException("API HTTP failure: status " + status);
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } finally {
            conn.disconnect();
        }

        return sb.toString();
    }

    public static void clearCache() {
        synchronized (QUERY_CACHE) {
            QUERY_CACHE.clear();
        }
    }
}
