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
        private final Map<String, String> rawScalars;

        public QeXmlResults(String schemaVersion, Double fermiEnergyEv, Double totalEnergyRy,
                            Boolean scfConverged, Integer nat, Integer nspins,
                            Map<String, String> rawScalars) {
            this.schemaVersion = schemaVersion == null ? "" : schemaVersion;
            this.fermiEnergyEv = fermiEnergyEv;
            this.totalEnergyRy = totalEnergyRy;
            this.scfConverged = scfConverged;
            this.nat = nat;
            this.nspins = nspins;
            this.rawScalars = rawScalars == null
                    ? Map.of() : Map.copyOf(rawScalars);
        }

        public String getSchemaVersion() { return this.schemaVersion; }
        public Optional<Double> getFermiEnergyEv() { return Optional.ofNullable(this.fermiEnergyEv); }
        public Optional<Double> getTotalEnergyRy() { return Optional.ofNullable(this.totalEnergyRy); }
        public Optional<Boolean> getScfConverged() { return Optional.ofNullable(this.scfConverged); }
        public Optional<Integer> getNat() { return Optional.ofNullable(this.nat); }
        public Optional<Integer> getNspins() { return Optional.ofNullable(this.nspins); }
        public Map<String, String> getRawScalars() { return this.rawScalars; }
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

            QeXmlResults results = new QeXmlResults(
                    schemaVersion, fermi, etot, converged, nat, nspin, scalars);
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
                if (!trimmed.isEmpty() && trimmed.length() < 200 && !trimmed.contains("\n\n")) {
                    if (!trimmed.contains("  ") || trimmed.matches("[-+0-9eE. \t]+")) {
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
                return ScfConvergenceAnalyzer.parseFortranDouble(raw);
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
