/* Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.viewer.auxdeck;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import quantumforge.input.QEAuxDeckPlanner;
import quantumforge.input.schema.QEAuxSchema;
import quantumforge.input.schema.QENamelistSchema;
import quantumforge.input.validation.ValidationIssue;
import quantumforge.operation.OperationResult;

/**
 * Batch-160 (QE roadmap R7 frontend slice): the auxiliary input DECK BUILDER
 * dialog for the 24 mined auxiliary QE programs. Every prompt row, typed
 * refusal, render and live verdict comes from {@link QEAuxDeckPlanner} - the
 * dialog holds NO grammar of its own, so the preview it shows is adjudicated
 * by exactly the same mined QEAuxDeckAudit the results surface runs.
 *
 * <p>Consent doctrine: the dialog never writes files, never runs jobs and
 * never touches the project input; it only RETURNS the rendered deck text
 * when the user explicitly presses \"Use deck text\". The caller
 * (ViewerActions) separately asks whether to copy or save; cancelling
 * anywhere produces nothing.</p>
 */
public final class QEFXAuxDeckDialog extends Dialog<String> {

    private final ComboBox<String> programCombo = new ComboBox<>();
    private final ComboBox<String> versionCombo = new ComboBox<>();
    private final Label docLabel = new Label();
    private final ListView<String> keywordList = new ListView<>();
    private final TextField valueField = new TextField();
    private final Button setButton = new Button("Set assignment");
    private final Button clearButton = new Button("Clear assignment");
    private final ListView<String> assignmentList = new ListView<>();
    private final TextArea previewArea = new TextArea();
    private final TextArea auditArea = new TextArea();
    private final Map<String, String> fieldValues =
            new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    private QEAuxDeckPlanner planner;
    private List<QEAuxDeckPlanner.FieldRow> currentFields = new ArrayList<>();

    public QEFXAuxDeckDialog() {
        setTitle("Auxiliary QE input deck builder (mined grammar, 24 programs)");
        setHeaderText("Draft an auxiliary-program input deck against the machine-mined"
                + " keyword grammar; the live verdict below is the mined deck audit,"
                + " never a simulation readiness claim.");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button okButton = (Button) getDialogPane().lookupButton(ButtonType.OK);
        okButton.setText("Use deck text");

        String newest = QENamelistSchema.VERSIONS.get(QENamelistSchema.VERSIONS.size() - 1);
        this.programCombo.getItems().setAll(QEAuxSchema.programs());
        this.programCombo.setValue(QEAuxSchema.programs().get(0));
        this.versionCombo.getItems().setAll(QENamelistSchema.VERSIONS);
        this.versionCombo.setValue(newest);

        GridPane top = new GridPane();
        top.setHgap(8);
        top.setVgap(6);
        top.addRow(0, new Label("Program:"), this.programCombo,
                new Label("QE version:"), this.versionCombo);
        top.add(this.docLabel, 0, 1, 4, 1);
        this.docLabel.setWrapText(true);

        HBox editorRow = new HBox(8, new Label("Value:"), this.valueField,
                this.setButton, this.clearButton);
        HBox.setHgrow(this.valueField, Priority.ALWAYS);

        this.keywordList.setPrefSize(520, 260);
        this.assignmentList.setPrefSize(520, 120);
        this.previewArea.setEditable(false);
        this.previewArea.setPrefRowCount(10);
        this.auditArea.setEditable(false);
        this.auditArea.setPrefRowCount(7);

        VBox center = new VBox(6,
                new Label("Keyword window (only keywords the pinned binary reads):"),
                this.keywordList, editorRow,
                new Label("Current assignments:"), this.assignmentList,
                new Label("Rendered deck preview:"), this.previewArea,
                new Label("Live mined-grammar audit:"), this.auditArea);
        center.setPadding(new Insets(10));
        center.setMaxWidth(Double.MAX_VALUE);
        BorderPane content = new BorderPane(center);
        content.setTop(top);
        BorderPane.setMargin(top, new Insets(10, 10, 0, 10));
        getDialogPane().setContent(content);
        getDialogPane().setPrefSize(760, 780);

        this.programCombo.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> rebuildPlanner());
        this.versionCombo.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> rebuildPlanner());
        this.keywordList.getSelectionModel().selectedIndexProperty()
                .addListener((observable, oldValue, newValue) -> loadSelectedValue());
        this.setButton.setOnAction(event -> applyAssignment(true));
        this.clearButton.setOnAction(event -> applyAssignment(false));

        setResultConverter(button -> {
            if (button == ButtonType.OK && this.planner != null) {
                OperationResult<String> render = this.planner.renderDraft(this.fieldValues);
                return render.isSuccess() ? render.getValue().orElse("") : null;
            }
            return null; // cancel anywhere -> nothing (consent)
        });
        rebuildPlanner();
    }

    /** The program the user currently has selected (for the caller's header). */
    public String getSelectedProgram() {
        return this.programCombo.getValue();
    }

    /**
     * Rebuilds the planner for the pinned program+version; every refusal is
     * shown as the typed message itself (an EMPTY keyword list would look
     * like \"nothing is promptable\", which is never the truth).
     */
    private void rebuildPlanner() {
        String program = this.programCombo.getValue();
        String version = this.versionCombo.getValue();
        OperationResult<QEAuxDeckPlanner> built =
                QEAuxDeckPlanner.forProgram(program, version);
        if (built.isSuccess() && built.getValue().isPresent()) {
            this.planner = built.getValue().get();
            this.currentFields = new ArrayList<>(this.planner.fields());
            List<String> rows = new ArrayList<>();
            for (QEAuxDeckPlanner.FieldRow field : this.currentFields) {
                rows.add(field.promptLabel());
            }
            this.keywordList.getItems().setAll(rows);
            this.docLabel.setText("Doc page of record: " + this.planner.docPage()
                    + "   [grammar window: QE " + this.planner.getVersion()
                    + ", " + this.currentFields.size() + " keyword(s)]");
        } else {
            this.planner = null;
            this.currentFields = new ArrayList<>();
            this.keywordList.getItems().setAll("grammar window unavailable: "
                    + built.getCode() + " - " + built.getMessage());
            this.docLabel.setText("");
        }
        refreshDraft();
    }

    /** Loads the selected field's current value into the value box. */
    private void loadSelectedValue() {
        int index = this.keywordList.getSelectionModel().getSelectedIndex();
        if (this.planner == null || index < 0 || index >= this.currentFields.size()) {
            return;
        }
        String existing =
                this.fieldValues.getOrDefault(this.currentFields.get(index).getName(), "");
        this.valueField.setText(existing);
    }

    /** Sets or clears the selected field's assignment, then re-renders. */
    private void applyAssignment(boolean assign) {
        int index = this.keywordList.getSelectionModel().getSelectedIndex();
        if (this.planner == null || index < 0 || index >= this.currentFields.size()) {
            return;
        }
        String keyword = this.currentFields.get(index).getName();
        if (assign) {
            this.fieldValues.put(keyword, this.valueField.getText());
        } else {
            this.fieldValues.remove(keyword);
            this.valueField.clear();
        }
        refreshDraft();
    }

    /**
     * Re-renders the preview and re-runs the mined audit on the current
     * assignments. The render and the verdict both come from the planner's
     * single grammar implementation; the OK button is armed only when a
     * non-empty deck actually rendered.
     */
    private void refreshDraft() {
        Button okButton = (Button) getDialogPane().lookupButton(ButtonType.OK);
        if (this.planner == null) {
            this.previewArea.setText("(no grammar window - see the keyword list above)");
            this.auditArea.setText("");
            this.assignmentList.getItems().clear();
            okButton.setDisable(true);
            return;
        }
        List<String> assignmentRows = new ArrayList<>();
        for (Map.Entry<String, String> entry : this.fieldValues.entrySet()) {
            assignmentRows.add(entry.getKey() + " = " + entry.getValue());
        }
        this.assignmentList.getItems().setAll(assignmentRows);
        OperationResult<String> render = this.planner.renderDraft(this.fieldValues);
        String preview = render.isSuccess() ? render.getValue().orElse("") : "";
        this.previewArea.setText(preview.isEmpty()
                ? "(" + render.getCode() + ": " + render.getMessage() + ")" : preview);
        OperationResult<List<ValidationIssue>> audit =
                this.planner.auditDraft(this.fieldValues);
        StringBuilder auditText = new StringBuilder();
        if (!audit.isSuccess()) {
            auditText.append(audit.getCode()).append(": ").append(audit.getMessage());
        } else {
            for (ValidationIssue issue : audit.getValue().orElse(List.of())) {
                auditText.append(issue.getSeverity()).append(" [")
                        .append(issue.getCode()).append("] ")
                        .append(issue.getMessage()).append('\n');
            }
            if (auditText.length() == 0) {
                auditText.append(render.isSuccess() && !preview.isEmpty()
                        ? "No grammar findings at QE " + this.planner.getVersion()
                        : "(nothing to audit yet - no assignments)");
            }
        }
        this.auditArea.setText(auditText.toString());
        okButton.setDisable(!(render.isSuccess() && !preview.isEmpty()));
    }
}
