/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import quantumforge.operation.OperationResult;

/**
 * XML-first parser for Quantum ESPRESSO {@code data-file-schema.xml}.
 *
 * <p>When XML is present it is preferred over prose log parsing. Malformed or
 * unsupported schema versions fail closed with diagnostics rather than inventing
 * values.</p>
 */
public final class QeXmlResultParser {

    public static final class QeXmlResults {
        private final String schemaVersion;
        private final Double fermiEnergyEv;
        private final Double totalEnergyRy;
        private final Boolean scfConverged;
        private final Integer nat;
        private final Integer nspins;
        private final Double totalForce;
        private final double[][] stressRyBohr3;
        private final double[][] atomicForces;
        private final Map<String, String> rawScalars;

        public QeXmlResults(String schemaVersion, Double fermiEnergyEv, Double totalEnergyRy,
                            Boolean scfConverged, Integer nat, Integer nspins,
                            Double totalForce, double[][] stressRyBohr3,
                            double[][] atomicForces,
                            Map<String, String> rawScalars) {
            this.schemaVersion = schemaVersion == null ? "" : schemaVersion;
            this.fermiEnergyEv = fermiEnergyEv;
            this.totalEnergyRy = totalEnergyRy;
            this.scfConverged = scfConverged;
            this.nat = nat;
            this.nspins = nspins;
            this.totalForce = totalForce;
            this.stressRyBohr3 = stressRyBohr3 == null ? null : copy3(stressRyBohr3);
            this.atomicForces = atomicForces == null ? null : copyNx3(atomicForces);
            this.rawScalars = rawScalars == null
                    ? Map.of() : Map.copyOf(rawScalars);
        }

        public String getSchemaVersion() { return this.schemaVersion; }
        public Optional<Double> getFermiEnergyEv() { return Optional.ofNullable(this.fermiEnergyEv); }
        public Optional<Double> getTotalEnergyRy() { return Optional.ofNullable(this.totalEnergyRy); }
        public Optional<Boolean> getScfConverged() { return Optional.ofNullable(this.scfConverged); }
        public Optional<Integer> getNat() { return Optional.ofNullable(this.nat); }
        public Optional<Integer> getNspins() { return Optional.ofNullable(this.nspins); }
        public Optional<Double> getTotalForce() { return Optional.ofNullable(this.totalForce); }
        public Optional<double[][]> getStressRyBohr3() {
            return this.stressRyBohr3 == null ? Optional.empty() : Optional.of(copy3(this.stressRyBohr3));
        }
        public Optional<double[][]> getAtomicForces() {
            return this.atomicForces == null ? Optional.empty() : Optional.of(copyNx3(this.atomicForces));
        }
        public Map<String, String> getRawScalars() { return this.rawScalars; }

        private static double[][] copy3(double[][] src) {
            double[][] out = new double[3][3];
            for (int i = 0; i < 3; i++) {
                System.arraycopy(src[i], 0, out[i], 0, 3);
            }
            return out;
        }

        private static double[][] copyNx3(double[][] src) {
            double[][] out = new double[src.length][3];
            for (int i = 0; i < src.length; i++) {
                if (src[i] != null) {
                    System.arraycopy(src[i], 0, out[i], 0, Math.min(3, src[i].length));
                }
            }
            return out;
        }
    }

    private QeXmlResultParser() {
        // Utility.
    }

    public static OperationResult<QeXmlResults> parseFile(Path xmlFile) {
        if (xmlFile == null || !Files.isRegularFile(xmlFile)) {
            return OperationResult.failed("QE_XML_MISSING",
                    "data-file-schema.xml not found: " + xmlFile, null);
        }
        try (InputStream in = Files.newInputStream(xmlFile)) {
            return parseStream(in, xmlFile.toString());
        } catch (IOException ex) {
            return OperationResult.failed("QE_XML_IO",
                    "Could not read QE XML: " + ex.getMessage(), ex);
        }
    }

    public static OperationResult<QeXmlResults> parseStream(InputStream in, String sourceLabel) {
        Objects.requireNonNull(in, "in");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // XXE hardening
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(false);

            Document doc = factory.newDocumentBuilder().parse(in);
            Element root = doc.getDocumentElement();
            if (root == null) {
                return OperationResult.failed("QE_XML_EMPTY",
                        "Empty QE XML document: " + sourceLabel, null);
            }

            Map<String, String> scalars = new LinkedHashMap<>();
            collectScalars(root, "", scalars);

            String schemaVersion = firstNonBlank(
                    attr(root, "version"),
                    textOfFirst(root, "format"),
                    scalars.get("general_info/xml_format/VERSION"),
                    scalars.get("general_info/created/VERSION"));

            Double fermi = firstDouble(
                    scalars,
                    "output/band_structure/fermi_energy",
                    "output/band_structure/highestOccupiedLevel",
                    "band_structure/fermi_energy",
                    "fermi_energy");
            // QE XML often stores Fermi in Hartree; convert to eV when magnitude looks atomic.
            if (fermi != null && Math.abs(fermi) < 5.0) {
                fermi = fermi * 27.211386245988; // Ha -> eV
                scalars.put("_fermi_unit_converted", "Ha_to_eV");
            }

            Double etot = firstDouble(
                    scalars,
                    "output/total_energy/etot",
                    "total_energy/etot",
                    "etot");
            // etot is typically in Ry already for QE XML; leave as Ry.

            Boolean converged = firstBoolean(
                    scalars,
                    "output/convergence_info/scf_conv/convergence_achieved",
                    "convergence_info/scf_conv/convergence_achieved",
                    "scf_conv/convergence_achieved");

            Integer nat = firstInt(scalars, "output/atomic_structure/nat", "atomic_structure/nat", "nat");
            Integer nspin = firstInt(scalars, "output/band_structure/nspin", "band_structure/lsda", "nspin");
            if (nspin == null && scalars.containsKey("output/band_structure/lsda")) {
                String lsda = scalars.get("output/band_structure/lsda");
                if ("true".equalsIgnoreCase(lsda)) {
                    nspin = 2;
                }
            }

            Double totalForce = firstDouble(
                    scalars,
                    "output/forces/total_force",
                    "forces/total_force",
                    "total_force");
            double[][] stress = extractStress(scalars);

            double[][] atomicForces = extractAtomicForces(root, nat);
            QeXmlResults results = new QeXmlResults(
                    schemaVersion, fermi, etot, converged, nat, nspin, totalForce, stress, atomicForces, scalars);
            return OperationResult.success("QE_XML_OK",
                    "Parsed QE XML from " + sourceLabel, results);
        } catch (Exception ex) {
            return OperationResult.failed("QE_XML_PARSE",
                    "Failed to parse QE XML (" + sourceLabel + "): " + ex.getMessage(), ex);
        }
    }

    /**
     * Prefer XML under {@code prefix.save/data-file-schema.xml}; fall back to text logs.
     */
    public static OperationResult<QeXmlResults> parseProjectSave(Path projectDir, String prefix) {
        if (projectDir == null) {
            return OperationResult.failed("PROJECT_DIR_NULL", "Project directory is null.", null);
        }
        String p = prefix == null || prefix.isBlank() ? "espresso" : prefix.trim();
        Path xml = projectDir.resolve(p + ".save").resolve("data-file-schema.xml");
        if (!Files.isRegularFile(xml)) {
            xml = projectDir.resolve(p + ".save").resolve("data-file.xml");
        }
        return parseFile(xml);
    }

    private static void collectScalars(Node node, String path, Map<String, String> out) {
        if (node == null) {
            return;
        }
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element el = (Element) node;
            String name = el.getTagName();
            String next = path == null || path.isEmpty() ? name : path + "/" + name;
            String text = el.getTextContent();
            if (text != null) {
                String trimmed = text.trim();
                // Keep only short scalar-like values, not huge matrices.
                if (!trimmed.isEmpty() && trimmed.length() < 400 && !trimmed.contains("\n\n")) {
                    if (trimmed.matches("[-+0-9eE. \t]+")) {
                        // Keep full numeric vectors/matrices (forces/stress), not only first token.
                        out.putIfAbsent(next, trimmed.replaceAll("\\s+", " ").trim());
                    } else if (!trimmed.contains("  ")) {
                        out.putIfAbsent(next, trimmed.split("\\s+")[0]);
                    }
                }
            }
            if (el.hasAttributes()) {
                for (int i = 0; i < el.getAttributes().getLength(); i++) {
                    Node attr = el.getAttributes().item(i);
                    out.putIfAbsent(next + "/@" + attr.getNodeName(), attr.getNodeValue());
                }
            }
            NodeList children = el.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                collectScalars(children.item(i), next, out);
            }
        }
    }

    private static String textOfFirst(Element root, String tag) {
        NodeList list = root.getElementsByTagName(tag);
        if (list.getLength() == 0) {
            return null;
        }
        String text = list.item(0).getTextContent();
        return text == null ? null : text.trim();
    }

    private static String attr(Element el, String name) {
        return el != null && el.hasAttribute(name) ? el.getAttribute(name) : null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static Double firstDouble(Map<String, String> scalars, String... keys) {
        for (String key : keys) {
            String raw = findIgnoreCase(scalars, key);
            if (raw == null) {
                continue;
            }
            try {
                String token = raw.trim().split("\\s+")[0];
                return ScfConvergenceAnalyzer.parseFortranDouble(token);
            } catch (NumberFormatException ignored) {
                // try next
            }
        }
        return null;
    }

    private static Integer firstInt(Map<String, String> scalars, String... keys) {
        for (String key : keys) {
            String raw = findIgnoreCase(scalars, key);
            if (raw == null) {
                continue;
            }
            try {
                return Integer.parseInt(raw.trim().split("\\s+")[0]);
            } catch (NumberFormatException ignored) {
                // try next
            }
        }
        return null;
    }

    private static Boolean firstBoolean(Map<String, String> scalars, String... keys) {
        for (String key : keys) {
            String raw = findIgnoreCase(scalars, key);
            if (raw == null) {
                continue;
            }
            String v = raw.trim().toLowerCase(Locale.ROOT);
            if (v.equals("true") || v.equals("t") || v.equals(".true.")) {
                return true;
            }
            if (v.equals("false") || v.equals("f") || v.equals(".false.")) {
                return false;
            }
        }
        return null;
    }



    private static double[][] extractAtomicForces(Element root, Integer nat) {
        if (root == null) {
            return null;
        }
        NodeList forceNodes = root.getElementsByTagName("force");
        java.util.List<double[]> list = new java.util.ArrayList<>();
        for (int i = 0; i < forceNodes.getLength(); i++) {
            Node node = forceNodes.item(i);
            if (node == null || node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String text = node.getTextContent();
            if (text == null) {
                continue;
            }
            String[] parts = text.trim().split("\\s+");
            if (parts.length < 3) {
                continue;
            }
            try {
                list.add(new double[] {
                        Double.parseDouble(parts[0]),
                        Double.parseDouble(parts[1]),
                        Double.parseDouble(parts[2])
                });
            } catch (NumberFormatException ignored) {
            }
        }
        if (list.isEmpty()) {
            return null;
        }
        return list.toArray(new double[0][]);
    }

    private static double[][] extractStress(Map<String, String> scalars) {
        double[][] m = new double[3][3];
        boolean any = false;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                String[] candidates = {
                        "output/stress/" + (i + 1) + (j + 1),
                        "output/stress/sigma_" + (i + 1) + (j + 1),
                        "stress/" + (i + 1) + "," + (j + 1),
                        "stress_" + i + "_" + j
                };
                Double v = firstDouble(scalars, candidates);
                if (v != null) {
                    m[i][j] = v;
                    any = true;
                }
            }
        }
        if (!any) {
            String packed = findIgnoreCase(scalars, "output/stress/sigma");
            if (packed == null) {
                packed = findIgnoreCase(scalars, "stress/sigma");
            }
            if (packed == null) {
                packed = findIgnoreCase(scalars, "sigma");
            }
            if (packed != null) {
                String[] parts = packed.trim().split("\\s+");
                try {
                    if (parts.length >= 9) {
                        int idx = 0;
                        for (int i = 0; i < 3; i++) {
                            for (int j = 0; j < 3; j++) {
                                m[i][j] = Double.parseDouble(parts[idx++]);
                            }
                        }
                        any = true;
                    } else if (parts.length >= 6) {
                        m[0][0] = Double.parseDouble(parts[0]);
                        m[1][1] = Double.parseDouble(parts[1]);
                        m[2][2] = Double.parseDouble(parts[2]);
                        m[0][1] = m[1][0] = Double.parseDouble(parts[3]);
                        m[0][2] = m[2][0] = Double.parseDouble(parts[4]);
                        m[1][2] = m[2][1] = Double.parseDouble(parts[5]);
                        any = true;
                    }
                } catch (NumberFormatException ignored) {
                    any = false;
                }
            }
        }
        return any ? m : null;
    }

    private static String findIgnoreCase(Map<String, String> scalars, String key) {
        if (scalars.containsKey(key)) {
            return scalars.get(key);
        }
        String lower = key.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : scalars.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).endsWith(lower)
                    || entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
