/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.app.extension.vasp;

import java.util.List;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.layout.Priority; // disambiguate quantumforge.hpc.Priority
import javafx.scene.text.Font;
import quantumforge.app.extension.SoftwareExtension;
import quantumforge.input.VaspIncarPresets;
import quantumforge.input.validation.VaspIncarDeck;
import quantumforge.input.validation.VaspIncarDeckAudit;
import quantumforge.input.validation.ValidationIssue;
import quantumforge.operation.OperationResult;

/**
 * VASP extension workbench (batch 173, roadmap #111 - input side).
 *
 * <p>The editor pane is a REVIEW workbench built on the batch-173
 * wiki-pinned grammar ({@code VaspIncarSchema}/{@code VaspIncarDeckAudit},
 * vasp.at 6.x window of 2026-07-22): a preset builds an INCAR review copy
 * into an editable preview, the audit button runs the real grammar audit on
 * whatever the preview currently holds (presets or pasted decks alike), and
 * the KPOINTS companion pane shows the preset's companion meshes rendered
 * by the canonical writer. The workbench WRITES NOTHING: copy buttons move
 * text to the clipboard, the operator owns every file. quantumforge never
 * executes VASP and never touches a POTCAR (VASP's licensed file; the
 * schema references only the wiki-stated ENMAX/LEXCH facts).</p>
 *
 * <p>{@link #isAvailable()} stays {@code false}: this pane is validated
 * grammar tooling, not an end-to-end VASP workflow, and the extension
 * manager lists only validated capabilities.</p>
 */
public class VASPExtension implements SoftwareExtension {

    @Override
    public String getName() {
        return "VASP";
    }

    @Override
    public String getVersion() {
        return "6.4";
    }

    @Override
    public Node getEditorGUI() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));

        Label title = new Label("VASP INCAR workbench (wiki-pinned 6.x grammar)");
        title.setFont(new Font("System Bold", 18));

        Label honesty = new Label(
                "Review workbench: text is generated INTO the preview for your judgment"
                        + " - nothing is written, executed or validated against VASP."
                        + " ENCUT is always your pin (default = largest POTCAR ENMAX,"
                        + " licensed file). POTCAR content is never read or generated.");
        honesty.setWrapText(true);
        honesty.setStyle("-fx-text-fill: #7a4a00;");

        ComboBox<String> preset = new ComboBox<>();
        for (String key : VaspIncarPresets.KEYS) {
            preset.getItems().add(key + " - " + VaspIncarPresets.labelOf(key));
        }
        preset.getSelectionModel().selectFirst();
        preset.setMaxWidth(Double.MAX_VALUE);

        TextArea incarPreview = new TextArea();
        incarPreview.setFont(Font.font("Monospaced", 12));
        incarPreview.setPrefRowCount(24);
        incarPreview.setPromptText("Generate a preset review copy, or paste your own INCAR here.");

        TextArea companion = new TextArea();
        companion.setFont(Font.font("Monospaced", 11));
        companion.setPrefRowCount(10);
        companion.setEditable(false);
        TitledPane companionPane = new TitledPane("KPOINTS companion (canonical writer output)", companion);
        companionPane.setExpanded(false);

        ListView<String> findings = new ListView<>();
        findings.setPrefHeight(150);

        Label census = new Label("no audit run yet");

        Button generateBtn = new Button("Generate preset review copy");
        generateBtn.setMaxWidth(Double.MAX_VALUE);
        generateBtn.setOnAction(event -> {
            String key = VaspIncarPresets.KEYS.get(preset.getSelectionModel()
                    .getSelectedIndex());
            incarPreview.setText(VaspIncarPresets.buildIncar(key));
            companion.setText(VaspIncarPresets.companionText(key));
            findings.getItems().setAll("preset '" + key + "' generated - run"
                    + " the audit to see the grammar findings");
        });

        Button auditBtn = new Button("Audit the preview text (INCAR grammar)");
        auditBtn.setMaxWidth(Double.MAX_VALUE);
        auditBtn.setOnAction(event -> {
            String text = incarPreview.getText();
            OperationResult<VaspIncarDeck> deck = VaspIncarDeck.parse(text);
            List<ValidationIssue> issues = VaspIncarDeckAudit.auditDeckText(text);
            findings.getItems().clear();
            long errors = issues.stream().filter(issue -> issue.getSeverity()
                    == quantumforge.input.validation.ValidationSeverity.ERROR).count();
            long warnings = issues.stream().filter(issue -> issue.getSeverity()
                    == quantumforge.input.validation.ValidationSeverity.WARNING).count();
            if (issues.isEmpty()) {
                findings.getItems().add("no grammar findings (pinned tier-1"
                        + " window holds, no consistency rule trips)");
            }
            for (ValidationIssue issue : issues) {
                findings.getItems().add(issue.getSeverity() + " ["
                        + issue.getCode() + "] " + issue.getMessage()
                        + (issue.getDocumentationUrl().isEmpty() ? ""
                                : "\n    docs: " + issue.getDocumentationUrl()));
            }
            census.setText(deck.isSuccess() && deck.getValue().isPresent()
                    ? "deck: " + deck.getValue().get().getStatements().size()
                            + " statement(s), "
                            + deck.getValue().get().distinctTags().size()
                            + " distinct tag(s) | findings: " + errors
                            + " ERROR, " + warnings + " WARNING, "
                            + (issues.size() - errors - warnings) + " INFO"
                    : "deck refused: " + deck.getCode());
        });

        Button copyIncar = new Button("Copy INCAR text");
        copyIncar.setOnAction(event -> copyToClipboard(incarPreview.getText()));
        Button copyKpoints = new Button("Copy KPOINTS companion");
        copyKpoints.setOnAction(event -> copyToClipboard(companion.getText()));
        HBox copyRow = new HBox(10, copyIncar, copyKpoints);
        copyRow.setHgrow(copyIncar, Priority.ALWAYS);
        copyRow.setHgrow(copyKpoints, Priority.ALWAYS);
        copyIncar.setMaxWidth(Double.MAX_VALUE);
        copyKpoints.setMaxWidth(Double.MAX_VALUE);

        vbox.getChildren().addAll(title, new Separator(), honesty, preset,
                generateBtn, incarPreview, copyRow, new Separator(),
                auditBtn, census, new Label("Findings (severity [code] message + wiki doc):"),
                findings, companionPane);
        return vbox;
    }

    private static void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text == null ? "" : text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    @Override
    public Node getResultGUI() {
        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));

        Label rtitle = new Label("VASP result inspection");
        rtitle.setFont(new Font("System Bold", 14));

        Label status = new Label(
                "vasprun.xml files route through the production Result Analysis lane:\n"
                        + "open the file in the project browser and choose 'vasprun.xml\n"
                        + "inspection (parser only)' - energies, Fermi level, lattice and\n"
                        + "fractional positions, bounded reads, parser only.\n"
                        + "INCAR/KPOINTS grammar audits auto-route in the same lane.\n"
                        + "This pane runs nothing itself.");
        status.setWrapText(true);

        vbox.getChildren().addAll(rtitle, new Separator(), status);
        return vbox;
    }

    @Override
    public boolean isAvailable() {
        // The workbench above is validated GRAMMAR tooling (batch 173), not a
        // validated end-to-end VASP workflow: no job submission, no POTCAR
        // handling (licensed), no production GUI listing until the full
        // workflow is independently validated. Same bar as every other
        // extension in the manager.
        return false;
    }
}
