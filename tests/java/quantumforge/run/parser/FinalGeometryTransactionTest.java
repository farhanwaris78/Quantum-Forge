/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import quantumforge.com.file.AtomicFileWriter;
import quantumforge.input.QEInput;
import quantumforge.input.QESCFInput;
import quantumforge.input.card.QEAtomicPositions;
import quantumforge.operation.OperationResult;
import quantumforge.project.Project;
import quantumforge.project.property.ProjectGeometry;
import quantumforge.project.property.ProjectGeometryList;
import quantumforge.project.property.ProjectProperty;

class FinalGeometryTransactionTest {

    @TempDir
    Path tempDir;

    /** A QEInput whose ATOMIC_POSITIONS card holds real atoms (bohr, alat sync none). */
    private static QEInput deck(String... atoms) {
        QESCFInput input = new QESCFInput();
        QEAtomicPositions positions = input.getCard(QEAtomicPositions.class);
        for (String atom : atoms) {
            String[] tokens = atom.split(" ");
            positions.addPosition(tokens[0], new double[] {
                    Double.parseDouble(tokens[1]),
                    Double.parseDouble(tokens[2]),
                    Double.parseDouble(tokens[3])},
                    new boolean[] {true, true, true});
        }
        return input;
    }

    /** A converged one-step opt trail whose coordinates are in bohr (parser provenance). */
    private static ProjectGeometryList convergedTrail(String... atoms) {
        ProjectGeometryList list = new ProjectGeometryList();
        ProjectGeometry geometry = new ProjectGeometry();
        geometry.setTime(0.0);
        geometry.setEnergy(-15.8);
        geometry.setTotalForce(1.0e-4);
        for (String atom : atoms) {
            String[] tokens = atom.split(" ");
            geometry.addAtom(tokens[0], Double.parseDouble(tokens[1]),
                    Double.parseDouble(tokens[2]), Double.parseDouble(tokens[3]));
        }
        geometry.setConverged(true);
        list.addGeometry(geometry);
        list.setConverged(true);
        return list;
    }

    /** Minimal project harness: mode decks in memory, saveQEInputs mirrors ProjectBody. */
    private static final class Harness extends Project {
        private final Map<String, QEInput> decks = new LinkedHashMap<>();
        private final ProjectGeometryList trail;
        private final ProjectProperty property;
        private boolean saveCalled;

        Harness(String dir, Map<String, QEInput> decks, ProjectGeometryList trail) {
            super(null, dir);
            this.decks.putAll(decks);
            this.trail = trail;
            this.property = new ProjectProperty(dir, "espresso") {
                @Override
                public synchronized ProjectGeometryList getOptList() {
                    return Harness.this.trail;
                }
            };
            this.saveCalled = false;
        }

        @Override public void setNetProject(Project project) { }
        @Override public boolean isValid() { return true; }
        @Override public boolean isSameAs(Project project) { return false; }
        @Override public ProjectProperty getProperty() { return this.property; }
        @Override public String getPrefixName() { return "espresso"; }
        @Override public String getInpFileName(String ext) {
            return ext == null || ext.isBlank() ? "espresso.in" : "espresso.in." + ext;
        }
        @Override public String getLogFileName(String ext) {
            return ext == null || ext.isBlank() ? "espresso.log" : "espresso.log." + ext;
        }
        @Override public String getErrFileName(String ext) {
            return ext == null || ext.isBlank() ? "espresso.err" : "espresso.err." + ext;
        }
        @Override public String getExitFileName() { return "espresso.EXIT"; }
        @Override public QEInput getQEInputGeometry() { return this.decks.get("geometry"); }
        @Override public QEInput getQEInputScf() { return this.decks.get("scf"); }
        @Override public QEInput getQEInputOptimiz() { return this.decks.get("optimiz"); }
        @Override public QEInput getQEInputMd() { return this.decks.get("md"); }
        @Override public QEInput getQEInputDos() { return this.decks.get("dos"); }
        @Override public QEInput getQEInputBand() { return this.decks.get("band"); }
        @Override public quantumforge.atoms.model.Cell getCell() { return null; }
        @Override protected void loadQEInputs() { }
        @Override public void resolveQEInputs() { }
        @Override public void markQEInputs() { }
        @Override public boolean isQEInputChanged() { return false; }
        @Override public void saveQEInputs(String directoryPath) {
            this.saveCalled = true;
            // Mirror ProjectBody semantics: one atomic write per resolved mode deck.
            for (Map.Entry<String, QEInput> entry : this.decks.entrySet()) {
                if (entry.getValue() == null) {
                    continue;
                }
                try {
                    AtomicFileWriter.writeUtf8(
                            Path.of(directoryPath, "espresso." + entry.getKey() + ".in"),
                            entry.getValue().toString());
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            }
        }
        @Override public void exportQEInputsTo(String directoryPath) { }
        @Override public Project cloneProject(String directoryPath) { return null; }
    }

    @Test
    void convergedGeometryCommitsWithFullAuditTrail() throws IOException {
        Map<String, QEInput> decks = new LinkedHashMap<>();
        decks.put("geometry", deck("Si 0.0 0.0 0.0", "Si 2.5 2.5 0.0"));
        decks.put("scf", deck("Si 0.0 0.0 0.0", "Si 2.5 2.5 0.0"));
        // optimiz/md/dos/band unresolved on purpose - they must be SKIPPED-NAMED,
        // never failed-for and never invented.
        Harness project = new Harness(this.tempDir.toString(), decks,
                convergedTrail("Si 0.1 0.2 0.3", "Si 2.6 2.4 0.1"));
        OperationResult<FinalGeometryTransaction.Plan> result =
                FinalGeometryTransaction.apply(project);
        assertTrue(result.isSuccess(), result.toString());
        FinalGeometryTransaction.Plan plan = result.getValue().orElseThrow();
        assertEquals(2, plan.getCommittedModes().size());
        assertEquals(2, plan.getAtomCount());
        assertFalse(plan.isCellWritten(), "no cell row in the trail - deck cell kept");
        assertTrue(project.saveCalled, "the only write vector is the project's own save");

        // Every committed deck carries the converged bohr coordinates, explicitly.
        for (String mode : new String[] {"geometry", "scf"}) {
            String text = Files.readString(
                    this.tempDir.resolve("espresso." + mode + ".in"), StandardCharsets.UTF_8);
            assertTrue(text.contains("{bohr}"), text);
            assertTrue(text.contains("0.100000"), text);
            assertTrue(text.contains("2.600000"), text);
            assertFalse(text.contains("2.500000"), "original coordinates are gone");
        }
        // Audit artifacts pin pre + staged content for every committed mode.
        Path audit = this.tempDir.resolve(".quantumforge");
        String pre = Files.readString(audit.resolve("geometry.pre-final-geometry"));
        String staged = Files.readString(audit.resolve("geometry.staged-geometry"));
        assertTrue(pre.contains("2.500000"), pre);
        assertTrue(staged.contains("0.100000"), staged);
        assertTrue(Files.readString(audit.resolve("final-geometry.audit.txt"))
                .contains("mode=geometry"));
        // Skipped modes are NAMED in the trail, with reasons.
        long skipped = plan.getTrail().stream()
                .filter(e -> e.getState().equals("SKIPPED")).count();
        assertEquals(4, skipped, "optimiz/md/dos/band skipped-named, never invented");
        assertTrue(plan.getTrail().stream()
                .anyMatch(e -> e.getReason().contains("left untouched")));
    }

    @Test
    void nonConvergedPreviewHasNoWritePermission() {
        ProjectGeometryList list = new ProjectGeometryList();
        ProjectGeometry geometry = new ProjectGeometry();
        geometry.addAtom("Si", 0.0, 0.0, 0.0);
        geometry.setConverged(false);
        list.addGeometry(geometry); // list NOT marked converged
        Map<String, QEInput> decks = new LinkedHashMap<>();
        decks.put("geometry", deck("Si 0.0 0.0 0.0"));
        Harness project = new Harness(this.tempDir.toString(), decks, list);
        OperationResult<FinalGeometryTransaction.Plan> result =
                FinalGeometryTransaction.apply(project);
        assertFalse(result.isSuccess(), "a preview without convergence is not a write permission");
        assertFalse(project.saveCalled, "zero live mutation on the fail path");
    }

    @Test
    void crossLabelAndCrossCountDecksAbortBeforeAnyWrite() throws IOException {
        Map<String, QEInput> decks = new LinkedHashMap<>();
        decks.put("geometry", deck("Si 0.0 0.0 0.0", "O 2.5 2.5 0.0")); // label mismatch
        decks.put("scf", deck("Si 0.0 0.0 0.0", "Si 2.5 2.5 0.0", "Si 5.0 0.0 0.0"));
        Harness project = new Harness(this.tempDir.toString(), decks,
                convergedTrail("Si 0.1 0.2 0.3", "Si 2.6 2.4 0.1"));
        OperationResult<FinalGeometryTransaction.Plan> result =
                FinalGeometryTransaction.apply(project);
        assertFalse(result.isSuccess());
        assertEquals("GEOMETRY_SHAPE", result.getCode(), "species scramble refuses first");
        assertFalse(project.saveCalled);
        assertFalse(Files.exists(this.tempDir.resolve(".quantumforge")),
                "fail-fast happens before any audit staging touches the project");
    }

    @Test
    void cellBearingGeometryWithoutCellCardRefusesPolitely() {
        ProjectGeometryList list = convergedTrail("Si 0.1 0.2 0.3");
        ProjectGeometry geometry = list.getGeometry(0);
        geometry.setCell(new double[][] {
                {5.1, 0.0, 0.0}, {0.0, 5.1, 0.0}, {0.0, 0.0, 5.1}});
        Map<String, QEInput> decks = new LinkedHashMap<>();
        decks.put("geometry", deck("Si 0.0 0.0 0.0")); // QESCFInput cardless CELL_PARAMETERS
        Harness project = new Harness(this.tempDir.toString(), decks, list);
        OperationResult<FinalGeometryTransaction.Plan> result =
                FinalGeometryTransaction.apply(project);
        assertFalse(result.isSuccess());
        assertEquals("GEOMETRY_CELL_TARGET", result.getCode(),
                "ibrav-style deck + relaxed cell = silent strain; named, never written");
        assertFalse(project.saveCalled);
    }
}
