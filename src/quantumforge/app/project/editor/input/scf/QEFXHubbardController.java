/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.project.editor.input.scf;

import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import quantumforge.app.QEFXMainController;
import quantumforge.app.project.editor.input.QEFXInputController;
import quantumforge.app.project.editor.input.items.QEFXToggleBoolean;
import quantumforge.atoms.element.ElementUtil;
import quantumforge.input.QEInput;
import quantumforge.input.card.QEAtomicSpecies;
import quantumforge.input.card.QECard;
import quantumforge.input.namelist.QENamelist;
import quantumforge.input.namelist.QEValueBuffer;

public class QEFXHubbardController extends QEFXInputController {

    /*
     * apply GGA+U
     */
    @FXML
    private Label applyLabel;

    @FXML
    private ToggleButton applyToggle;

    @FXML
    private Button applyButton;

    /*
     * Hubbard parameters
     */
    private ElementAnsatzBinder elementBinder;

    @FXML
    private TableView<ElementAnsatz> elementTable;

    @FXML
    private TableColumn<ElementAnsatz, Integer> indexColumn;

    @FXML
    private TableColumn<ElementAnsatz, String> nameColumn;

    @FXML
    private TableColumn<ElementAnsatz, String> hubbardColumn;

    @FXML
    private Button linearResponseButton;

    public QEFXHubbardController(QEFXMainController mainController, QEInput input) {
        super(mainController, input);
        this.elementBinder = null;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        QENamelist nmlSystem = this.input.getNamelist(QEInput.NAMELIST_SYSTEM);

        if (nmlSystem != null) {
            this.setupApplyGGApUItem(nmlSystem);
            this.initializeElementBinder(nmlSystem);
            this.setupElementCondition(nmlSystem);
        }

        this.setupIndexColumn();
        this.setupNameColumn();
        this.setupHubbardColumn();
        this.setupElementTable();
        this.setupLinearResponseButton();
    }

    private void setupLinearResponseButton() {
        if (this.linearResponseButton == null) return;
        this.linearResponseButton.setOnAction(event -> {
            // HP.x (Linear response) logic placeholder
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle("Linear Response U");
            alert.setHeaderText("Hubbard Parameter Calculation");
            alert.setContentText("Triggering hp.x to calculate self-consistent Hubbard U parameters for active elements...");
            alert.showAndWait();
        });
    }

    private void setupApplyGGApUItem(QENamelist nmlSystem) {
        if (this.applyToggle == null) {
            return;
        }

        QEFXToggleBoolean item = new QEFXToggleBoolean(nmlSystem.getValueBuffer("lda_plus_u"), this.applyToggle, false);

        if (this.applyLabel != null) {
            item.setLabel(this.applyLabel);
        }

        if (this.applyButton != null) {
            item.setDefault(false, this.applyButton);
        }
    }

    private void initializeElementBinder(QENamelist nmlSystem) {
        if (this.elementTable == null) {
            return;
        }

        QECard card = this.input.getCard(QEAtomicSpecies.CARD_NAME);
        if (card == null || !(card instanceof QEAtomicSpecies)) {
            return;
        }

        QEAtomicSpecies atomicSpecies = (QEAtomicSpecies) card;

        this.elementBinder = new ElementAnsatzBinder(this.elementTable, atomicSpecies, nmlSystem);
    }

    private void setupElementTable() {
        if (this.elementTable == null) {
            return;
        }

        if (this.elementBinder != null) {
            this.elementBinder.bindTable();
        }
    }

    private void setupIndexColumn() {
        if (this.indexColumn == null) {
            return;
        }

        this.indexColumn.setCellValueFactory(new PropertyValueFactory<ElementAnsatz, Integer>("index1"));
    }

    private void setupNameColumn() {
        if (this.nameColumn == null) {
            return;
        }

        this.nameColumn.setCellValueFactory(new PropertyValueFactory<ElementAnsatz, String>("name"));
        this.nameColumn.setComparator((String name1, String name2) -> {
            int atomNum1 = ElementUtil.getAtomicNumber(name1);
            int atomNum2 = ElementUtil.getAtomicNumber(name2);
            if (atomNum1 < atomNum2) {
                return -1;
            } else if (atomNum1 > atomNum2) {
                return 1;
            }
            return 0;
        });
    }

    private void setupHubbardColumn() {
        if (this.hubbardColumn == null) {
            return;
        }

        this.hubbardColumn.setCellFactory(TextFieldTableCell.<ElementAnsatz> forTableColumn());
        this.hubbardColumn.setCellValueFactory(new PropertyValueFactory<ElementAnsatz, String>("hubbard"));
    }

    private void setupElementCondition(QENamelist nmlSystem) {
        QEValueBuffer ldaUValue = nmlSystem.getValueBuffer("lda_plus_u");
        if (ldaUValue != null && ldaUValue.hasValue()) {
            this.updateElementCondition(ldaUValue.getLogicalValue());
        } else {
            this.updateElementCondition(false);
        }

        ldaUValue.addListener(value -> {
            if (value != null) {
                this.updateElementCondition(value.getLogicalValue());
            } else {
                this.updateElementCondition(false);
            }
        });
    }

    private void updateElementCondition(boolean apply) {
        if (apply) {
            if (this.elementTable != null) {
                this.elementTable.setDisable(false);
            }

        } else {
            if (this.elementTable != null) {
                this.elementTable.setDisable(true);
            }
        }
    }
}
