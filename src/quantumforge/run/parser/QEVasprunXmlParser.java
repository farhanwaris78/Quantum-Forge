/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.run.parser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import quantumforge.project.property.ProjectProperty;

/**
 * XXE-hardened XML parser for VASP vasprun.xml results, extracting total electronic
 * free energy, Fermi level, and relaxed atomic coordinate arrays securely (Roadmap #111).
 */
public final class QEVasprunXmlParser extends LogParser {

    public static final class VasprunResults {
        private final double totalEnergyEv;
        private final double fermiEnergyEv;
        private final double[][] finalLattice; // 3x3 in Angstroms
        private final double[][] finalFractionalCoords; // [numAtoms][3]

        public VasprunResults(double total, double fermi, double[][] lattice, double[][] coords) {
            if (!Double.isFinite(total) || !Double.isFinite(fermi)) {
                throw new IllegalArgumentException("vasprun energy values must be finite");
            }
            if (lattice == null || lattice.length != 3 || coords == null || coords.length == 0) {
                throw new IllegalArgumentException("vasprun final structure is incomplete");
            }
            this.totalEnergyEv = total;
            this.fermiEnergyEv = fermi;
            this.finalLattice = copyThreeByThree(lattice, "lattice");
            this.finalFractionalCoords = copyRows(coords, "fractional coordinates");
        }

        public double getTotalEnergyEv() { return this.totalEnergyEv; }
        public double getFermiEnergyEv() { return this.fermiEnergyEv; }
        public double[][] getFinalLattice() {
            double[][] out = new double[3][3];
            for (int i = 0; i < 3; i++) {
                System.arraycopy(this.finalLattice[i], 0, out[i], 0, 3);
            }
            return out;
        }
        public double[][] getFinalFractionalCoords() {
            return copyRows(this.finalFractionalCoords, "fractional coordinates");
        }

        private static double[][] copyThreeByThree(double[][] source, String label) {
            if (source.length != 3) {
                throw new IllegalArgumentException(label + " must be 3 by 3");
            }
            return copyRows(source, label);
        }

        private static double[][] copyRows(double[][] source, String label) {
            double[][] out = new double[source.length][3];
            for (int i = 0; i < source.length; i++) {
                if (source[i] == null || source[i].length != 3) {
                    throw new IllegalArgumentException(label + " row " + i + " must have 3 values");
                }
                for (int j = 0; j < 3; j++) {
                    if (!Double.isFinite(source[i][j])) {
                        throw new IllegalArgumentException(label + " contains a non-finite value");
                    }
                    out[i][j] = source[i][j];
                }
            }
            return out;
        }
    }

    private VasprunResults results = null;

    public QEVasprunXmlParser(ProjectProperty property) {
        super(property);
    }

    public VasprunResults getResults() { return this.results; }

    @Override
    public void parse(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        this.results = null;

        try {
            // Read and wrap vasprun.xml securely
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            InputStream inputStream = new ByteArrayInputStream(fileBytes);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Disallow DTDs and XML Entity expansion to prevent XXE (XML External Entity) attacks
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(inputStream);
            doc.getDocumentElement().normalize();

            // 1. Extract total free energy: <i name="e_fr_energy"> -12.435 </i>
            Double totalEnergy = null;
            NodeList listI = doc.getElementsByTagName("i");
            for (int i = 0; i < listI.getLength(); i++) {
                Node node = listI.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element el = (Element) node;
                    if ("e_fr_energy".equals(el.getAttribute("name"))) {
                        totalEnergy = parseFinite(el.getTextContent(), "e_fr_energy");
                        break;
                    }
                }
            }

            // 2. Extract Fermi energy: <i name="efermi"> 4.321 </i>
            Double fermiEnergy = null;
            for (int i = 0; i < listI.getLength(); i++) {
                Node node = listI.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element el = (Element) node;
                    if ("efermi".equals(el.getAttribute("name"))) {
                        fermiEnergy = parseFinite(el.getTextContent(), "efermi");
                        break;
                    }
                }
            }

            // 3. Extract final lattice vectors: inside <varray name="basis"> of the last <structure>
            double[][] lattice = null;
            NodeList varrays = doc.getElementsByTagName("varray");
            for (int i = varrays.getLength() - 1; i >= 0; i--) {
                Element varray = (Element) varrays.item(i);
                if ("basis".equals(varray.getAttribute("name"))) {
                    lattice = parseVectorRows(varray.getElementsByTagName("v"), 3, "final basis");
                    break;
                }
            }

            // 4. Extract final fractional positions: inside <varray name="positions">
            List<double[]> posList = new ArrayList<>();
            for (int i = varrays.getLength() - 1; i >= 0; i--) {
                Element varray = (Element) varrays.item(i);
                if ("positions".equals(varray.getAttribute("name"))) {
                    NodeList rows = varray.getElementsByTagName("v");
                    for (int j = 0; j < rows.getLength(); j++) {
                        posList.add(parseVector(rows.item(j).getTextContent(), "final fractional position"));
                    }
                    break;
                }
            }

            double[][] positions = new double[posList.size()][3];
            for (int i = 0; i < posList.size(); i++) {
                positions[i] = posList.get(i);
            }

            if (totalEnergy == null || fermiEnergy == null || lattice == null || positions.length == 0) {
                throw new IOException("vasprun.xml has no complete final energy, Fermi level, lattice, and positions");
            }
            this.results = new VasprunResults(totalEnergy, fermiEnergy, lattice, positions);

        } catch (Exception e) {
            throw new IOException("Failed to parse secure VASP vasprun.xml: " + e.getMessage(), e);
        }
    }

    private static double parseFinite(String text, String label) throws IOException {
        try {
            double value = Double.parseDouble(text.trim());
            if (!Double.isFinite(value)) {
                throw new NumberFormatException("non-finite");
            }
            return value;
        } catch (RuntimeException ex) {
            throw new IOException("Invalid " + label + " in vasprun.xml", ex);
        }
    }

    private static double[] parseVector(String text, String label) throws IOException {
        String[] tokens = text == null ? new String[0] : text.trim().split("\\s+");
        if (tokens.length != 3) {
            throw new IOException(label + " must contain exactly three numeric values");
        }
        return new double[] {parseFinite(tokens[0], label), parseFinite(tokens[1], label),
                parseFinite(tokens[2], label)};
    }

    private static double[][] parseVectorRows(NodeList rows, int expectedRows, String label) throws IOException {
        if (rows == null || rows.getLength() != expectedRows) {
            throw new IOException(label + " must contain exactly " + expectedRows + " vectors");
        }
        double[][] parsed = new double[expectedRows][3];
        for (int row = 0; row < expectedRows; row++) {
            parsed[row] = parseVector(rows.item(row).getTextContent(), label + " row " + row);
        }
        return parsed;
    }
}
