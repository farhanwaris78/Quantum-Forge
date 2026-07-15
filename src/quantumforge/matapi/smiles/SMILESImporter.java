/*
 * Copyright (C) 2025 QuantumForge Team
 */
package quantumforge.matapi.smiles;

/**
 * SMILES-based molecule importer.
 * 
 * NanoLabo supports SMILES notation for molecule searching
 * in PubChem and importing molecular structures from
 * SMILES strings.
 * 
 * Examples:
 * - "CN=C=O" → methyl isocyanate
 * - "CCO" → ethanol
 * - "c1ccccc1" → benzene
 * - "CC(=O)O" → acetic acid
 */
public class SMILESImporter {

    private String smiles;
    private String molecularFormula;
    private String iupacName;
    private double molecularWeight;

    public SMILESImporter(String smiles) {
        this.smiles = smiles != null ? smiles.trim() : "";
    }

    public boolean isValid() {
        return this.smiles != null && !this.smiles.isEmpty();
    }

    /**
     * Look up the molecule from PubChem using SMILES
     */
    public boolean lookupFromPubChem() {
        if (!this.isValid()) return false;

        try {
            String encoded = java.net.URLEncoder.encode(this.smiles, "UTF-8");
            String url = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound" +
                "/smiles/" + encoded + "/property/" +
                "MolecularFormula,MolecularWeight,IUPACName/JSON";

            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                new java.net.URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);

            if (conn.getResponseCode() == 200) {
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                // Parse JSON response (simplified)
                String json = response.toString();
                if (json.contains("MolecularFormula")) {
                    this.molecularFormula = extractJSONValue(json, "MolecularFormula");
                }
                if (json.contains("MolecularWeight")) {
                    String mw = extractJSONValue(json, "MolecularWeight");
                    try { this.molecularWeight = Double.parseDouble(mw); } catch (Exception e) {}
                }
                if (json.contains("IUPACName")) {
                    this.iupacName = extractJSONValue(json, "IUPACName");
                }
            }
            conn.disconnect();
            return true;

        } catch (Exception e) {
            return false;
        }
    }

    private String extractJSONValue(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) {
            search = "\"" + key + "\":";
            start = json.indexOf(search);
            if (start < 0) return "";
            start = json.indexOf("\"", start + search.length());
            if (start < 0) return "";
            start++;
        } else {
            start += search.length();
        }
        int end = json.indexOf("\"", start);
        return end > start ? json.substring(start, end) : "";
    }

    public String getSmiles() { return this.smiles; }
    public String getMolecularFormula() { return this.molecularFormula; }
    public String getIupacName() { return this.iupacName; }
    public double getMolecularWeight() { return this.molecularWeight; }

    /**
     * Fetch 3D structure in SDF format
     */
    public String fetchSDF3D() {
        if (!this.isValid()) return null;
        try {
            String encoded = java.net.URLEncoder.encode(this.smiles, "UTF-8");
            String url = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound" +
                "/smiles/" + encoded + "/SDF?record_type=3d";
            
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                new java.net.URL(url).openConnection();
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() == 200) {
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
                return sb.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SMILES: ").append(this.smiles);
        if (this.molecularFormula != null) {
            sb.append("\nFormula: ").append(this.molecularFormula);
        }
        if (this.molecularWeight > 0) {
            sb.append("\nMW: ").append(String.format("%.2f", this.molecularWeight));
        }
        if (this.iupacName != null) {
            sb.append("\nIUPAC: ").append(this.iupacName);
        }
        return sb.toString();
    }
}
