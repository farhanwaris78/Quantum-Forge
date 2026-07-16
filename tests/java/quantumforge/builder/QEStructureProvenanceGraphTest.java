package quantumforge.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import quantumforge.com.math.Matrix3D;

class QEStructureProvenanceGraphTest {

    @Test
    void testProvenanceGraphRecordsAndSerializesHistory() {
        QEStructureProvenanceGraph graph = new QEStructureProvenanceGraph();

        // Step 1: Load bulk Silicon from Materials Project
        graph.addNode("LOAD", "Loaded bulk Silicon conventional cell", null, "mp-149");

        // Step 2: Build a non-diagonal supercell
        double[][] transform = {
            {2.0, 0.0, 0.0},
            {0.0, 2.0, 0.0},
            {0.0, 0.0, 2.0}
        };
        graph.addNode("SUPERCELL", "Expanded cell into 2x2x2 supercell", transform, "");

        assertEquals(2, graph.size());
        assertEquals("LOAD", graph.getNodes().get(0).getOperation());
        assertEquals("SUPERCELL", graph.getNodes().get(1).getOperation());
        assertEquals(2.0, graph.getNodes().get(1).getMatrix()[0][0], 1e-6);

        // Verify Markdown report generation
        String report = graph.generateHistoryReport();
        assertNotNull(report);
        assertTrue(report.contains("Audit Trail"));
        assertTrue(report.contains("Step 1: [LOAD]"));
        assertTrue(report.contains("Step 2: [SUPERCELL]"));
        assertTrue(report.contains("2.0000"));

        // Verify GSON Serialization / Deserialization
        String json = graph.toJson();
        assertNotNull(json);
        assertTrue(json.contains("mp-149"));

        QEStructureProvenanceGraph restored = QEStructureProvenanceGraph.fromJson(json);
        assertNotNull(restored);
        assertEquals(2, restored.size());
        assertEquals("Loaded bulk Silicon conventional cell", restored.getNodes().get(0).getDescription());
    }
}
