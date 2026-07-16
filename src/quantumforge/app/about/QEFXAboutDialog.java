/*
 * Copyright (C) 2025 QuantumForge Team
 *
 * Proprietary and Confidential - All Rights Reserved (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 */

package quantumforge.app.about;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import quantumforge.app.QEFXMain;
import quantumforge.com.env.Environments;
import quantumforge.ver.Version;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class QEFXAboutDialog extends Dialog<ButtonType> implements Initializable {

    @FXML
    private ImageView imageView;

    @FXML
    private TextArea textArea;

    public QEFXAboutDialog() {
        super();

        DialogPane dialogPane = this.getDialogPane();
        QEFXMain.initializeStyleSheets(dialogPane.getStylesheets());
        QEFXMain.initializeDialogOwner(this);

        this.setResizable(false);
        this.setTitle("About QUANTUMFORGE");
        dialogPane.getButtonTypes().clear();
        dialogPane.getButtonTypes().add(ButtonType.CLOSE);

        Node node = null;
        try {
            node = this.createContent();
        } catch (Exception e) {
            node = new Label("ERROR: cannot show QEFXAboutDialog.");
            e.printStackTrace();
        }

        dialogPane.setContent(node);

        this.setResultConverter(buttonType -> {
            return null;
        });
    }

    private Node createContent() throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(this.getClass().getResource("QEFXAboutDialog.fxml"));
        fxmlLoader.setController(this);
        return fxmlLoader.load();
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.setupImageView();
        this.setupTextArea();
    }

    private void setupImageView() {
        if (this.imageView == null) {
            return;
        }

        URL url = QEFXMain.class.getResource("resource/image/icon_128.png");
        String imageName = url == null ? null : url.toExternalForm();

        if (imageName != null && (!imageName.isEmpty())) {
            Image image = null;
            try {
                image = new Image(imageName);
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (image != null) {
                this.imageView.setFitWidth(image.getWidth());
                this.imageView.setFitHeight(image.getHeight());
                this.imageView.setImage(image);
            }
        }
    }

    private void setupTextArea() {
        if (this.textArea == null) {
            return;
        }

        String ls = System.lineSeparator();
        String quantumforge = "QUANTUMFORGE" + Version.VERSION;
        String qeWeb = Environments.getEspressoWebsite();

        String message = "";
        message = message + quantumforge + " is a GUI system of Quantum ESPRESSO <" + qeWeb + ">." + ls;
        message = message + ls;
        message = message + "This JavaFX application requires a 64-bit Java 17 or later runtime." + ls;
        message = message + ls;
        message = message + "Key external libraries: OpenJFX, exp4j, Gson, JCodec, and the maintained JSch fork." + ls;
        message = message + "Exact versions and component licenses are in the release CycloneDX SBOM and" + ls;
        message = message + "THIRD_PARTY_NOTICES.md." + ls;
        message = message + ls;
        message = message + "----------------------------------------------------------------------------" + ls;
        message = message + "LICENSE AND SCIENTIFIC STATUS:" + ls;
        message = message + "----------------------------------------------------------------------------" + ls;
        message = message + "  Repository-level terms are proprietary; see LICENSE." + ls;
        message = message + "  Some inherited portions and dependencies have separate open-source terms." + ls;
        message = message + "  Several advanced modules are experimental/unvalidated; see CODE_AUDIT.md." + ls;
        message = message + "  This software is provided without warranty; independently validate results." + ls;
        message = message + "----------------------------------------------------------------------------" + ls;

        this.textArea.setText(message);
    }
}
