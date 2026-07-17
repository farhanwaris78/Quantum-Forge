/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.run.parser;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
            this.totalEnergyEv = total;
            this.fermiEnergyEv = fermi;
            this.finalLattice = new double[3][3];
            for (int i = 0; i < 3; i++) {
                System.arraycopy(lattice[i], 0, this.finalLattice[i], 0, 3);
            }
            this.finalFractionalCoords = new double[coords.length][3];
            for (int i = 0; i < coords.length; i++) {
                System.arraycopy(coords[i], 0, this.finalFractionalCoords[i], 0, 3);
            }
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
            double[][] out = new double[finalFractionalCoords.length][3];
            for (int i = 0; i < finalFractionalCoords.length; i++) {
                System.arraycopy(this.finalFractionalCoords[i], 0, out[i], 0, 3);
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
            double totalEnergy = 0.0;
            NodeList listI = doc.getElementsByTagName("i");
            for (int i = 0; i < listI.getLength(); i++) {
                Node node = listI.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element el = (Element) node;
                    if ("e_fr_energy".equals(el.getAttribute("name"))) {
                        totalEnergy = Double.parseDouble(el.getTextContent().trim());
                        break;
                    }
                }
            }

            // 2. Extract Fermi energy: <i name="efermi"> 4.321 </i>
            double fermiEnergy = 0.0;
            for (int i = 0; i < listI.getLength(); i++) {
                Node node = listI.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element el = (Element) node;
                    if ("efermi".equals(el.getAttribute("name"))) {
                        fermiEnergy = Double.parseDouble(el.getTextContent().trim());
                        break;
                    }
                }
            }

            // 3. Extract final lattice vectors: inside <varray name="basis"> of the last <structure>
            double[][] lattice = new double[3][3];
            NodeList varrays = doc.getElementsByTagName("varray");
            for (int i = varrays.getLength() - 1; i >= 0; i--) {
                Element varray = (Element) varrays.item(i);
                if ("basis".equals(varray.getAttribute("name"))) {
                    NodeList rows = varray.getElementsByTagName("v");
                    for (int j = 0; j < 3; j++) {
                        String[] tokens = rows.item(j).getTextContent().trim().split("\\s+");
                        lattice[j][0] = Double.parseDouble(tokens[0]);
                        lattice[j][1] = Double.parseDouble(tokens[1]);
                        lattice[j][2] = Double.parseDouble(tokens[2]);
                    }
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
                        String[] tokens = rows.item(j).getTextContent().trim().split("\\s+");
                        double[] pos = new double[3];
                        pos[0] = Double.parseDouble(tokens[0]);
                        pos[1] = Double.parseDouble(tokens[1]);
                        pos[2] = Double.parseDouble(tokens[2]);
                        posList.add(pos);
                    }
                    break;
                }
            }

            double[][] positions = new double[posList.size()][3];
            for (int i = 0; i < posList.size(); i++) {
                positions[i] = posList.get(i);
            }

            this.results = new VasprunResults(totalEnergy, fermiEnergy, lattice, positions);

        } catch (Exception e) {
            throw new IOException("Failed to parse secure VASP vasprun.xml: " + e.getMessage(), e);
        }
    }
}
