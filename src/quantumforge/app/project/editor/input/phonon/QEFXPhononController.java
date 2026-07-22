/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */

package quantumforge.app.project.editor.input.phonon;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.ArrayList;
import java.util.List;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import quantumforge.app.QEFXMainController;
import quantumforge.app.project.editor.QEFXEditorController;
import quantumforge.input.QEDeckKeywordCatalog;
import quantumforge.input.schema.QENamelistSchema;
import quantumforge.operation.OperationResult;

public class QEFXPhononController extends QEFXEditorController {

    @FXML
    private TextField nq1Field, nq2Field, nq3Field;
    @FXML
    private TextField tr2phField;
    @FXML
    private CheckBox epsilCheck;
    @FXML
    private ComboBox<String> asrCombo;
    @FXML
    private ComboBox<String> qeVersionCombo;
    @FXML
    private ListView<String> keywordWindowList;

    public QEFXPhononController(QEFXMainController mainController) {
        super(mainController);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (asrCombo != null) {
            asrCombo.getItems().addAll("no", "simple", "crystal", "one-dim", "poly-eth");
            asrCombo.setValue("crystal");
        }
        // Batch-154 (QE-integration roadmap R3): the ph.x prompt surface is
        // typed against the mined QE grammar window. The version picker
        // defaults to the newest mined window and every keyword row shown
        // below comes from QEDeckKeywordCatalog.forVersion(PH, version) -
        // version-gated keywords (lmultipole 7.5+, skip_upperfan 7.2-7.4)
        // appear ONLY in the windows where the binary knows them, so a prompt
        // can never hand ph.x a keyword its namelist reader will abort on.
        if (this.qeVersionCombo != null) {
            this.qeVersionCombo.getItems().setAll(QENamelistSchema.VERSIONS);
            this.qeVersionCombo.setValue(
                    QENamelistSchema.VERSIONS.get(QENamelistSchema.VERSIONS.size() - 1));
            this.qeVersionCombo.getSelectionModel().selectedItemProperty()
                    .addListener((observable, oldValue, newValue) -> refreshKeywordWindow());
        }
        refreshKeywordWindow();
    }

    /**
     * Repopulates the version-windowed keyword browser. On any catalog
     * refusal the row list is replaced by the typed refusal message itself -
     * an EMPTY list here would look like "nothing is promptable", which is
     * never the truth.
     */
    private void refreshKeywordWindow() {
        if (this.keywordWindowList == null || this.qeVersionCombo == null) {
            return;
        }
        String version = this.qeVersionCombo.getValue();
        OperationResult<QEDeckKeywordCatalog> catalog =
                QEDeckKeywordCatalog.forVersion(QENamelistSchema.Kind.PH, version);
        List<String> rows = new ArrayList<>();
        if (catalog.isSuccess() && catalog.getValue().isPresent()) {
            QEDeckKeywordCatalog typed = catalog.getValue().get();
            for (QEDeckKeywordCatalog.KeywordRow row : typed.rows()) {
                rows.add(row.promptLabel());
            }
        } else {
            rows.add("grammar window unavailable: " + catalog.getMessage());
        }
        this.keywordWindowList.getItems().setAll(rows);
    }
}
