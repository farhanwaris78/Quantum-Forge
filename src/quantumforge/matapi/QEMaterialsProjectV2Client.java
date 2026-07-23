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
import java.util.List;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import quantumforge.atoms.model.Cell;
import quantumforge.atoms.model.exception.ZeroVolumCellException;

/**
 * Modern Materials Project REST API v2 (mp-api) client.
 * Replaces the deprecated REST v1 endpoints with a secure, header-authenticated,
 * rate-limited query client and Pymatgen structure deserializer (Roadmap #116).
 */
public final class QEMaterialsProjectV2Client {

    private static final String API_BASE_URL = "https://api.materialsproject.org/summary/";

    public static final class MpStructure {
        private final String materialId;
        private final double[][] lattice;
        private final double[][] fractionalCoords;
        private final String[] elements;

        public MpStructure(String id, double[][] lattice, double[][] coords, String[] elements) {
            this.materialId = Objects.requireNonNull(id, "materialId");
            this.lattice = lattice;
            this.fractionalCoords = coords;
            this.elements = elements;
        }

        public String getMaterialId() { return this.materialId; }
        public double[][] getLattice() { return this.lattice; }
        public double[][] getFractionalCoords() { return this.fractionalCoords; }
        public String[] getElements() { return this.elements; }

        /**
         * Reconstructs a complete 3D unit Cell from Pymatgen-deserialized coordinates.
         */
        public Cell buildCell() throws ZeroVolumCellException {
            Cell cell = new Cell(this.lattice);
            for (int i = 0; i < this.elements.length; i++) {
                // Convert fractional coords to Cartesian for Cell.addAtom
                double s0 = this.fractionalCoords[i][0];
                double s1 = this.fractionalCoords[i][1];
                double s2 = this.fractionalCoords[i][2];

                double cx = s0 * this.lattice[0][0] + s1 * this.lattice[1][0] + s2 * this.lattice[2][0];
                double cy = s0 * this.lattice[0][1] + s1 * this.lattice[1][1] + s2 * this.lattice[2][1];
                double cz = s0 * this.lattice[0][2] + s1 * this.lattice[1][2] + s2 * this.lattice[2][2];

                cell.addAtom(new quantumforge.atoms.model.Atom(this.elements[i], cx, cy, cz));
            }
            return cell;
        }
    }

    private QEMaterialsProjectV2Client() {
        // Utility
    }

    /**
     * Queries the Materials Project summary endpoint for a specific chemical formula.
     * Passes the API Key securely inside the 'X-API-KEY' header.
     */
    public static List<MpStructure> queryFormula(String formula, String apiKey) throws IOException {
        List<MpStructure> results = new ArrayList<>();
        if (formula == null || formula.isBlank() || apiKey == null || apiKey.isBlank()) {
            return results;
        }

        String encodedFormula = URLEncoder.encode(formula.trim(), StandardCharsets.UTF_8);
        String urlString = API_BASE_URL + "?formula=" + encodedFormula + "&_fields=material_id,structure";

        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("X-API-KEY", apiKey.trim()); // Modern API Key Header authentication

        int status = conn.getResponseCode();
        if (status != 200) {
            conn.disconnect();
            throw new IOException("Materials Project API v2 HTTP failure: status " + status);
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
            }
        } finally {
            conn.disconnect();
        }

        // Parse modern Pymatgen-serialized structure summary JSON payload
        try {
            JsonObject root = new Gson().fromJson(response.toString(), JsonObject.class);
            if (root.has("data")) {
                JsonArray dataArray = root.getAsJsonArray("data");
                for (JsonElement el : dataArray) {
                    JsonObject item = el.getAsJsonObject();
                    String id = item.get("material_id").getAsString();
                    JsonObject struct = item.getAsJsonObject("structure");

                    // 1. Parse Lattice Matrix
                    JsonObject latObj = struct.getAsJsonObject("lattice");
                    JsonArray latArr = latObj.getAsJsonArray("matrix");
                    double[][] lattice = new double[3][3];
                    for (int i = 0; i < 3; i++) {
                        JsonArray vec = latArr.get(i).getAsJsonArray();
                        lattice[i][0] = vec.get(0).getAsDouble();
                        lattice[i][1] = vec.get(1).getAsDouble();
                        lattice[i][2] = vec.get(2).getAsDouble();
                    }

                    // 2. Parse Atomic Sites
                    JsonArray sites = struct.getAsJsonArray("sites");
                    int numAtoms = sites.size();
                    double[][] coords = new double[numAtoms][3];
                    String[] elements = new String[numAtoms];

                    for (int i = 0; i < numAtoms; i++) {
                        JsonObject site = sites.get(i).getAsJsonObject();
                        
                        // Fractional coordinates (abc)
                        JsonArray abc = site.getAsJsonArray("abc");
                        coords[i][0] = abc.get(0).getAsDouble();
                        coords[i][1] = abc.get(1).getAsDouble();
                        coords[i][2] = abc.get(2).getAsDouble();

                        // Element symbol from species list
                        JsonArray species = site.getAsJsonArray("species");
                        JsonObject firstSpecies = species.get(0).getAsJsonObject();
                        elements[i] = firstSpecies.get("element").getAsString();
                    }

                    results.add(new MpStructure(id, lattice, coords, elements));
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse standard Pymatgen structure JSON: " + e.getMessage(), e);
        }

        return results;
    }
}
