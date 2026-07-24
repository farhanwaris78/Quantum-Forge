/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.matapi;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * PubChem API integration for molecular data retrieval.
 * 
 * Allows searching and importing molecular structures from PubChem,
 * complementing the Materials Project API for crystalline materials.
 * This provides access to organic molecules, biomolecules, and
 * chemical compounds not available in Materials Project.
 */
public class PubChemAPI {

    private static final String PUBCHEM_BASE_URL = "https://pubchem.ncbi.nlm.nih.gov/rest/pug";
    private static final String PUBCHEM_SDF_URL = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound";
    private static final int CONNECTION_TIMEOUT = 10000;

    /**
     * Search PubChem by name and return matching CIDs
     */
    public static List<String> searchByName(String query) {
        List<String> cids = new ArrayList<String>();

        try {
            String encodedQuery = URLEncoder.encode(query, "UTF-8");
            String urlStr = PUBCHEM_BASE_URL + "/compound/name/" + encodedQuery + "/cids/JSON";

            HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECTION_TIMEOUT);
            conn.setReadTimeout(CONNECTION_TIMEOUT);

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
                if (json.has("IdentifierList")) {
                    JsonObject idList = json.get("IdentifierList").getAsJsonObject();
                    if (idList.has("CID")) {
                        JsonArray cidArray = idList.get("CID").getAsJsonArray();
                        for (JsonElement element : cidArray) {
                            cids.add(element.getAsString());
                        }
                    }
                }
            }
            conn.disconnect();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return cids;
    }

    /**
     * Get compound properties as a formatted string
     */
    public static String getCompoundProperties(String cid) {
        StringBuilder properties = new StringBuilder();

        try {
            String urlStr = PUBCHEM_BASE_URL + "/compound/cid/" + cid + "/property/" +
                "MolecularFormula,MolecularWeight,CanonicalSMILES,IUPACName,Charge/JSON";

            HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECTION_TIMEOUT);
            conn.setReadTimeout(CONNECTION_TIMEOUT);

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
                if (json.has("PropertyTable")) {
                    JsonObject propTable = json.get("PropertyTable").getAsJsonObject();
                    if (propTable.has("Properties")) {
                        JsonArray props = propTable.get("Properties").getAsJsonArray();
                        if (props.size() > 0) {
                            JsonObject prop = props.get(0).getAsJsonObject();
                            if (prop.has("MolecularFormula")) {
                                properties.append("Formula: ")
                                    .append(prop.get("MolecularFormula").getAsString())
                                    .append("\n");
                            }
                            if (prop.has("MolecularWeight")) {
                                properties.append("MW: ")
                                    .append(prop.get("MolecularWeight").getAsString())
                                    .append("\n");
                            }
                            if (prop.has("IUPACName")) {
                                properties.append("IUPAC: ")
                                    .append(prop.get("IUPACName").getAsString())
                                    .append("\n");
                            }
                            if (prop.has("CanonicalSMILES")) {
                                properties.append("SMILES: ")
                                    .append(prop.get("CanonicalSMILES").getAsString())
                                    .append("\n");
                            }
                        }
                    }
                }
            }
            conn.disconnect();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return properties.toString();
    }

    /**
     * Get SDF (Structure Data Format) for a compound
     */
    public static String getCompoundSDF(String cid) {
        try {
            String urlStr = PUBCHEM_SDF_URL + "/cid/" + cid + "/SDF";

            HttpURLConnection conn = (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECTION_TIMEOUT);
            conn.setReadTimeout(CONNECTION_TIMEOUT);

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                    response.append("\n");
                }
                reader.close();
                conn.disconnect();
                return response.toString();
            }
            conn.disconnect();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}
