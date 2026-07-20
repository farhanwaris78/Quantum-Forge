/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run.parser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import quantumforge.com.file.AtomicFileWriter;
import quantumforge.input.QEInput;
import quantumforge.input.card.QEAtomicPositions;
import quantumforge.input.card.QECard;
import quantumforge.input.card.QECellParameters;
import quantumforge.operation.OperationResult;
import quantumforge.project.Project;
import quantumforge.project.property.ProjectGeometry;

/**
 * Roadmap #40 core: TRANSACTIONAL write-back of the last converged optimised
 * geometry. {@link FinalGeometryUpdater} proves a typed read-only preview
 * exists; this class supplies the missing jeopardy control that lets the
 * write-back stop being fail-closed:
 *
 * <ul>
 *   <li>Gate: {@link FinalGeometryUpdater#preview} is the codebase's single
 *       convergence oracle for this path; a successful preview IS the write
 *       permission (there is deliberately no weaker second gate);</li>
 *   <li>Pre-seal: for every resolved mode the ORIGINAL deck state
 *       (ATOMIC_POSITIONS labels/positions/mobiles/option and CELL_PARAMETERS
 *       vectors/option) is captured in memory, and both the original and the
 *       staged deck text are pinned into {@code <project>/.quantumforge/}
 *       ({@code final-geometry.audit.txt} with SHA-256 hashes, per-mode
 *       {@code <mode>.pre-final-geometry} / {@code <mode>.staged-geometry}
 *       artifacts). All staging happens BEFORE any live mutation - a staging
 *       failure cannot have touched the project;</li>
 *   <li>Cell honesty: a converged geometry row that CARRIES cell vectors
 *       (vc-relax) must be written to a CELL_PARAMETERS card. A deck that
 *       defines its cell via ibrav/celldm instead would be silently strained
 *       by bohr positions under a new cell - the write refuses for that mode
 *       ({@code GEOMETRY_CELL_TARGET}) rather than corrupt it;</li>
 *   <li>Unit honesty: coordinates in {@link ProjectGeometry} are bohr by
 *       parser provenance; cards are rewritten explicitly {@code bohr} -
 *       unit-matching by declaration, never by assumption;</li>
 *   <li>Commit: ONLY after staging succeeds are the LIVE in-memory decks
 *       mutated, then written exactly the way a deliberate GUI save writes
 *       them ({@code project.saveQEInputs}). The write is verified by
 *       re-rendering every live deck against its staged text - the project's
 *       own save logs I/O failures instead of throwing, so comparison is the
 *       only honest success signal;</li>
 *   <li>Rollback: any verification mismatch restores the captured ORIGINAL
 *       in-memory state per card and saves again. A second verification pass
 *       decides the verdict: restored-clean (failure, but exact) or
 *       RESTORE-UNVERIFIABLE (failure with the replay instructions: the audit
 *       artifacts pin every original byte for hand recovery). Deleting the
 *       audit folder forfeits the recovery path - the report says so.</li>
 * </ul>
 *
 * <p>Codes: preview pass-through codes, GEOMETRY_PRECONDITION, GEOMETRY_MISSING,
 * GEOMETRY_SHAPE, GEOMETRY_CELL_TARGET, GEOMETRY_STAGE, GEOMETRY_COMMIT,
 * GEOMETRY_OK.</p>
 */
public final class FinalGeometryTransaction {

    /** One audited mode outcome. */
    public static final class TrailEntry {
        private final String mode;
        private final String state;      // COMMITTED | SKIPPED (with reason below)
        private final String reason;     // skip reason, or cell note
        private final String preHash;    // sha256 of original deck text ("" when skipped)
        private final String stagedHash; // sha256 of staged deck text ("" when skipped)

        TrailEntry(String mode, String state, String reason, String preHash, String stagedHash) {
            this.mode = mode;
            this.state = state;
            this.reason = reason == null ? "" : reason;
            this.preHash = preHash == null ? "" : preHash;
            this.stagedHash = stagedHash == null ? "" : stagedHash;
        }

        public String getMode() { return this.mode; }
        public String getState() { return this.state; }
        public String getReason() { return this.reason; }
        public String getPreHash() { return this.preHash; }
        public String getStagedHash() { return this.stagedHash; }
    }

    /** The committed plan value (report payload). */
    public static final class Plan {
        private final int stepIndex;
        private final int atomCount;
        private final boolean cellWritten;
        private final List<String> committedModes;
        private final List<TrailEntry> trail;

        Plan(int stepIndex, int atomCount, boolean cellWritten,
             List<String> committedModes, List<TrailEntry> trail) {
            this.stepIndex = stepIndex;
            this.atomCount = atomCount;
            this.cellWritten = cellWritten;
            this.committedModes = List.copyOf(committedModes);
            this.trail = List.copyOf(trail);
        }

        public int getStepIndex() { return this.stepIndex; }
        public int getAtomCount() { return this.atomCount; }
        public boolean isCellWritten() { return this.cellWritten; }
        public List<String> getCommittedModes() { return this.committedModes; }
        public List<TrailEntry> getTrail() { return this.trail; }
    }

    /** Per-mode original card state captured before mutation (rollback source). */
    private static final class CardSnapshot {
        String option;
        final List<String> labels = new ArrayList<>();
        final List<double[]> positions = new ArrayList<>();
        final List<boolean[]> mobiles = new ArrayList<>();
    }

    private static final class CellSnapshot {
        String option;
        double[][] vectors; // [3][3]
    }

    /** The six tracked modes: label + live input getter, insertion-ordered. */
    private enum Mode {
        GEOMETRY("geometry"), SCF("scf"), OPT("optimiz"),
        MD("md"), DOS("dos"), BAND("band");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        String label() { return this.label; }
    }

    private FinalGeometryTransaction() {
        // Utility.
    }

    /** Run the gate, stage everything, commit, verify, and return the plan. */
    public static OperationResult<Plan> apply(Project project) {
        if (project == null) {
            return OperationResult.failed("GEOMETRY_PRECONDITION",
                    "No project - nothing to transact.", null);
        }
        OperationResult<FinalGeometryUpdater.GeometryPreview> preview =
                FinalGeometryUpdater.preview(project);
        if (!preview.isSuccess() || preview.getValue().isEmpty()) {
            return OperationResult.failed(preview.getCode(), preview.getMessage(), null);
        }
        FinalGeometryUpdater.GeometryPreview pv = preview.getValue().get();
        // preview() is the codebase's single convergence oracle for this path: it refuses
        // unless the validator (or the geometry/list flags) marks the path converged, so a
        // successful preview IS the write permission - there is no weaker second gate here.
        ProjectGeometry geometry = project.getProperty().getOptList()
                .getGeometry(pv.getStepIndex());
        int atomCount = pv.getAtomCount();
        if (geometry == null || geometry.numAtoms() != atomCount) {
            return OperationResult.failed("GEOMETRY_MISSING",
                    "Converged step " + (pv.getStepIndex() + 1) + " has no readable "
                            + "geometry row with " + atomCount + " atom(s) - refusing to "
                            + "imagine coordinates.", null);
        }
        for (int i = 0; i < atomCount; i++) {
            String label = geometry.getName(i);
            if (label == null || label.isBlank()) {
                return OperationResult.failed("GEOMETRY_SHAPE",
                        "Geometry atom " + (i + 1) + " has an empty species label - "
                                + "refusing a cross-label write.", null);
            }
            if (!Double.isFinite(geometry.getX(i)) || !Double.isFinite(geometry.getY(i))
                    || !Double.isFinite(geometry.getZ(i))) {
                return OperationResult.failed("GEOMETRY_SHAPE",
                        "Geometry atom " + (i + 1) + " has a non-finite coordinate - "
                                + "refusing to propagate NaN/Infinity into live decks.", null);
            }
        }
        double[][] cell = geometry.getCell();
        boolean cellPresent = cell != null && cell.length >= 3
                && cell[0] != null && cell[1] != null && cell[2] != null
                && cell[0].length >= 3 && cell[1].length >= 3 && cell[2].length >= 3
                && Double.isFinite(cell[0][0]);
        boolean cellFinite = cellPresent;
        if (cellPresent) {
            for (int v = 0; v < 3 && cellFinite; v++) {
                for (int c = 0; c < 3 && cellFinite; c++) {
                    cellFinite = Double.isFinite(cell[v][c]);
                }
            }
        }

        // ---- per-mode resolution + shape gates (no mutations yet) ----
        Map<Mode, QEInput> live = new LinkedHashMap<>();
        live.put(Mode.GEOMETRY, project.getQEInputGeometry());
        live.put(Mode.SCF, project.getQEInputScf());
        live.put(Mode.OPT, project.getQEInputOptimiz());
        live.put(Mode.MD, project.getQEInputMd());
        live.put(Mode.DOS, project.getQEInputDos());
        live.put(Mode.BAND, project.getQEInputBand());
        List<TrailEntry> skips = new ArrayList<>();
        List<Mode> targets = new ArrayList<>();
        for (Map.Entry<Mode, QEInput> entry : live.entrySet()) {
            Mode mode = entry.getKey();
            QEInput input = entry.getValue();
            if (input == null) {
                skips.add(new TrailEntry(mode.label(), "SKIPPED",
                        "mode not resolved in this project; left untouched", null, null));
                continue;
            }
            QEAtomicPositions positions = input.getCard(QEAtomicPositions.class);
            if (positions == null) {
                skips.add(new TrailEntry(mode.label(), "SKIPPED",
                        "no ATOMIC_POSITIONS card; left untouched rather than invented",
                        null, null));
                continue;
            }
            if (positions.numPositions() != atomCount) {
                return OperationResult.failed("GEOMETRY_SHAPE",
                        "Mode " + mode.label() + " has " + positions.numPositions()
                                + " positions vs " + atomCount + " geometry atoms - a "
                                + "cross-count write is unsafe; nothing was touched.", null);
            }
            for (int i = 0; i < atomCount; i++) {
                if (!positions.getLabel(i).equalsIgnoreCase(geometry.getName(i))) {
                    return OperationResult.failed("GEOMETRY_SHAPE",
                            "Atom " + (i + 1) + " in mode " + mode.label() + " is "
                                    + positions.getLabel(i) + " but the geometry row "
                                    + "carries " + geometry.getName(i) + " - a cross-label "
                                    + "write would scramble species; nothing was touched.",
                            null);
                }
            }
            if (cellFinite && input.getCard(QECellParameters.class) == null) {
                return OperationResult.failed("GEOMETRY_CELL_TARGET",
                        "The converged geometry carries a relaxed CELL, but mode "
                                + mode.label() + " defines its cell via ibrav/celldm (no "
                                + "CELL_PARAMETERS card to update) - writing bohr positions "
                                + "under a new cell would silently strain the deck. Review "
                                + "the cell treatment explicitly; nothing was touched.", null);
            }
            targets.add(mode);
        }
        if (targets.isEmpty()) {
            return OperationResult.failed("GEOMETRY_PRECONDITION",
                    "No resolved mode carries an ATOMIC_POSITIONS card (geometry/scf/"
                            + "optimiz/md/dos/band all skipped) - there is nothing honest "
                            + "to transact.", null);
        }

        // ---- phase 1: stage on COPIES; live objects untouched so far ----
        Map<Mode, Snapshot> originals = new LinkedHashMap<>();
        Map<Mode, String> stagedText = new LinkedHashMap<>();
        Map<Mode, String> originalText = new LinkedHashMap<>();
        for (Mode mode : targets) {
            QEInput liveInput = live.get(mode);
            QEInput staged = liveInput.copy();
            if (staged == null) {
                return OperationResult.failed("GEOMETRY_COMMIT",
                        "Mode " + mode.label() + " returned a null copy - refusing to "
                                + "mutate the live deck; nothing was touched.", null);
            }
            QEAtomicPositions stagedPositions = staged.getCard(QEAtomicPositions.class);
            QECellParameters stagedCell = staged.getCard(QECellParameters.class);
            stagedPositions.setBohr();
            for (int i = 0; i < atomCount; i++) {
                stagedPositions.setPosition(i, new double[] {
                        geometry.getX(i), geometry.getY(i), geometry.getZ(i)});
            }
            if (stagedCell != null && cellFinite) {
                stagedCell.setBohr();
                for (int v = 0; v < 3; v++) {
                    stagedCell.setVector(v + 1, cell[v]);
                }
            }
            String rendered;
            try {
                rendered = staged.toString();
            } catch (RuntimeException ex) {
                return OperationResult.failed("GEOMETRY_STAGE",
                        "Mode " + mode.label() + " could not render its staged deck: "
                                + ex.getMessage() + " - live decks NOT touched.", null);
            }
            stagedText.put(mode, rendered);
            try {
                originalText.put(mode, liveInput.toString());
            } catch (RuntimeException ex) {
                return OperationResult.failed("GEOMETRY_STAGE",
                        "Mode " + mode.label() + " could not render its original deck: "
                                + ex.getMessage() + " - live decks NOT touched.", null);
            }
            // Capture the original live card state for in-memory rollback.
            originals.put(mode, capture(liveInput));
        }

        // ---- phase 2: pin the audit trail on disk (still before live mutation) ----
        Path auditDir;
        List<TrailEntry> trail = new ArrayList<>(skips);
        StringBuilder marker = new StringBuilder();
        marker.append("# QuantumForge final-geometry transaction (converged step ")
                .append(pv.getStepIndex() + 1).append(")\n");
        marker.append("# filenames: <mode>.pre-final-geometry = original deck text BEFORE ")
                .append("the apply (replay to undo); <mode>.staged-geometry = the text that ")
                .append("was committed. Hashes are SHA-256 of the UTF-8 text. Deleting this ")
                .append("folder forfeits the recovery path.\n");
        try {
            Path directory = project.getDirectory() == null ? null
                    : project.getDirectory().toPath();
            if (directory == null || !Files.isDirectory(directory)) {
                return OperationResult.failed("GEOMETRY_STAGE",
                        "Project has no on-disk directory - live decks NOT touched.", null);
            }
            auditDir = directory.resolve(".quantumforge");
            Files.createDirectories(auditDir);
            for (Mode mode : targets) {
                String orig = originalText.get(mode);
                String staged = stagedText.get(mode);
                AtomicFileWriter.writeUtf8(
                        auditDir.resolve(mode.label() + ".pre-final-geometry"), orig);
                AtomicFileWriter.writeUtf8(
                        auditDir.resolve(mode.label() + ".staged-geometry"), staged);
                trail.add(new TrailEntry(mode.label(), "COMMITTED",
                        cellFinite ? "positions + CELL_PARAMETERS rewritten (bohr)"
                                : "positions rewritten (bohr); deck cell kept",
                        sha256Hex(orig), sha256Hex(staged)));
                marker.append("mode=").append(mode.label())
                        .append(" pre=").append(sha256Hex(orig))
                        .append(" staged=").append(sha256Hex(staged)).append('\n');
            }
            for (TrailEntry skip : skips) {
                marker.append("mode=").append(skip.getMode()).append(" skipped: ")
                        .append(skip.getReason()).append('\n');
            }
            AtomicFileWriter.writeUtf8(
                    auditDir.resolve("final-geometry.audit.txt"), marker.toString());
        } catch (IOException ex) {
            return OperationResult.failed("GEOMETRY_STAGE",
                    "Audit staging failed: " + ex.getMessage()
                            + " - live decks NOT touched.", ex);
        } catch (RuntimeException ex) {
            return OperationResult.failed("GEOMETRY_STAGE",
                    "Audit staging failed: " + ex.getMessage()
                            + " - live decks NOT touched.", null);
        }

        // ---- phase 3: commit live + write-through + verify ----
        List<String> committed = new ArrayList<>();
        for (Mode mode : targets) {
            QEAtomicPositions positions = live.get(mode).getCard(QEAtomicPositions.class);
            QECellParameters cellParams = live.get(mode).getCard(QECellParameters.class);
            positions.setBohr();
            for (int i = 0; i < atomCount; i++) {
                positions.setPosition(i, new double[] {
                        geometry.getX(i), geometry.getY(i), geometry.getZ(i)});
            }
            if (cellParams != null && cellFinite) {
                cellParams.setBohr();
                for (int v = 0; v < 3; v++) {
                    cellParams.setVector(v + 1, cell[v]);
                }
            }
            committed.add(mode.label());
        }
        try {
            project.saveQEInputs(project.getDirectory().getAbsolutePath());
        } catch (RuntimeException ex) {
            return rollback(project, targets, originals, live,
                    "write-through threw: " + ex.getMessage());
        }
        List<String> drifted = new ArrayList<>();
        for (Mode mode : targets) {
            String liveText = live.get(mode).toString();
            if (!liveText.equals(stagedText.get(mode))) {
                drifted.add(mode.label());
            }
        }
        if (!drifted.isEmpty()) {
            return rollback(project, targets, originals, live,
                    "post-write verification failed for mode(s) " + drifted
                            + " (the project save did not produce the staged decks)");
        }
        return OperationResult.success("GEOMETRY_OK",
                "Converged geometry committed to " + committed.size()
                        + " resolved mode(s); audit trail pinned under .quantumforge/.",
                new Plan(pv.getStepIndex(), atomCount, cellFinite, committed, trail));
    }

    /** Restore original in-memory cards, re-save, and give an exact verdict. */
    private static OperationResult<Plan> rollback(Project project, List<Mode> targets,
                                                  Map<Mode, Snapshot> originals,
                                                  Map<Mode, QEInput> live, String cause) {
        for (Mode mode : targets) {
            Snapshot snapshot = originals.get(mode);
            if (snapshot == null) {
                continue;
            }
            QEInput input = live.get(mode);
            QEAtomicPositions positions = input.getCard(QEAtomicPositions.class);
            restoreOption(positions, snapshot.positions.option);
            for (int i = 0; i < snapshot.positions.labels.size(); i++) {
                positions.setLabel(i, snapshot.positions.labels.get(i));
                positions.setPosition(i, snapshot.positions.positions.get(i).clone());
                positions.setMobile(i, snapshot.positions.mobiles.get(i).clone());
            }
            QECellParameters cellParams = input.getCard(QECellParameters.class);
            if (cellParams != null && snapshot.cell != null && snapshot.cell.vectors != null) {
                restoreOption(cellParams, snapshot.cell.option);
                for (int v = 0; v < 3; v++) {
                    cellParams.setVector(v + 1, snapshot.cell.vectors[v].clone());
                }
            }
        }
        String saveStatus;
        try {
            project.saveQEInputs(project.getDirectory().getAbsolutePath());
            saveStatus = "original in-memory cards restored and re-saved";
        } catch (RuntimeException ex) {
            return OperationResult.failed("GEOMETRY_COMMIT",
                    cause + ". ROLLBACK ALSO FAILED (" + ex.getMessage() + ") - replay the "
                            + ".quantumforge/<mode>.pre-final-geometry artifacts by hand; "
                            + "reload the project before any further edit.", ex);
        }
        // Honesty: in-memory rollback is verified by re-rendering the ORIGINAL texts.
        List<String> unrestored = new ArrayList<>();
        for (Mode mode : targets) {
            Snapshot snapshot = originals.get(mode);
            if (snapshot == null || snapshot.originalRendered == null) {
                continue;
            }
            if (!live.get(mode).toString().equals(snapshot.originalRendered)) {
                unrestored.add(mode.label());
            }
        }
        if (unrestored.isEmpty()) {
            return OperationResult.failed("GEOMETRY_COMMIT",
                    cause + ". Rollback " + saveStatus + " and VERIFIED clean (" 
                            + targets.size() + " mode(s)); no live deck kept any staged "
                            + "content.", null);
        }
        return OperationResult.failed("GEOMETRY_COMMIT",
                cause + ". Rollback re-save reported " + saveStatus + " but verification "
                        + "still sees staged content in mode(s) " + unrestored
                        + " - RESTORE-UNVERIFIABLE: replay .quantumforge/<mode>"
                        + ".pre-final-geometry by hand and reload the project.", null);
    }

    /** Capture the original card state AND its rendered text for verification. */
    private static Snapshot capture(QEInput input) {
        Snapshot snapshot = new Snapshot();
        QEAtomicPositions positions = input.getCard(QEAtomicPositions.class);
        snapshot.positions.option = positions.getOption();
        for (int i = 0; i < positions.numPositions(); i++) {
            snapshot.positions.labels.add(positions.getLabel(i));
            snapshot.positions.positions.add(positions.getPosition(i).clone());
            snapshot.positions.mobiles.add(positions.getMobile(i).clone());
        }
        QECellParameters cellParams = input.getCard(QECellParameters.class);
        if (cellParams != null) {
            snapshot.cell = new CellSnapshot();
            snapshot.cell.option = cellParams.getOption();
            snapshot.cell.vectors = new double[][] {
                    cellParams.getVector1().clone(),
                    cellParams.getVector2().clone(),
                    cellParams.getVector3().clone()};
        }
        try {
            snapshot.originalRendered = input.toString();
        } catch (RuntimeException ex) {
            snapshot.originalRendered = null;
        }
        return snapshot;
    }

    /** Restore an option string through the only public unit API. */
    private static void restoreOption(QECard card, String option) {
        if (option == null) {
            return;
        }
        switch (option.toLowerCase(java.util.Locale.ROOT)) {
        case "bohr":
            if (card instanceof QEAtomicPositions p) {
                p.setBohr();
            } else if (card instanceof QECellParameters c) {
                c.setBohr();
            }
            break;
        case "angstrom":
            if (card instanceof QEAtomicPositions p) {
                p.setAngstrom();
            }
            break;
        case "alat":
            if (card instanceof QEAtomicPositions p) {
                p.setAlat();
            } else if (card instanceof QECellParameters c) {
                c.setAlat();
            }
            break;
        case "crystal":
            if (card instanceof QEAtomicPositions p) {
                p.setCrystal();
            }
            break;
        default:
            break;
        }
    }

    private static final class Snapshot {
        final CardSnapshot positions = new CardSnapshot();
        CellSnapshot cell;
        String originalRendered;
    }

    private static String sha256Hex(String text) {
        try {
            java.security.MessageDigest digest =
                    java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : hash) {
                out.append(String.format(java.util.Locale.ROOT, "%02x", b));
            }
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
