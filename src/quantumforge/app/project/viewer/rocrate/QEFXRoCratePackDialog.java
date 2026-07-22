/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.app.project.viewer.rocrate;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javafx.beans.binding.BooleanBinding;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import quantumforge.export.RoCrateExporter;
import quantumforge.export.RoCratePacker;

/**
 * Roadmap #135 pack dialog: the consent surface for materializing a reviewed
 * RO-Crate metadata draft into a real crate folder.
 *
 * <p>The dialog holds no copy/verify logic of its own - every number it shows
 * comes from the {@link RoCrateExporter.CrateDraft} the caller built, and every
 * byte moves through {@link RoCratePacker} after an explicit "Pack crate".</p>
 *
 * <p>Consent doctrine: the dialog only RENDERS the draft census and RETURNS a
 * {@link PackRequest}; it writes nothing itself. The caller (ViewerActions)
 * executes the pack strictly after OK, and the packer itself refuses existing
 * targets, drifted sources, and empty drafts with typed verdicts.</p>
 */
public final class QEFXRoCratePackDialog extends Dialog<QEFXRoCratePackDialog.PackRequest> {

    /** What the user explicitly approved: target folder plus verbatim metadata. */
    public static final class PackRequest {
        private final Path targetDir;
        private final String licence;
        private final List<RoCrateExporter.CrateAuthor> authors;

        private PackRequest(Path targetDir, String licence,
                            List<RoCrateExporter.CrateAuthor> authors) {
            this.targetDir = targetDir;
            this.licence = licence;
            this.authors = authors;
        }

        public Path getTargetDir() { return this.targetDir; }
        public String getLicence() { return this.licence; }
        public List<RoCrateExporter.CrateAuthor> getAuthors() { return this.authors; }
    }

    private final RoCrateExporter.CrateDraft draft;
    private final TextField licenceField = new TextField();
    private final TextField authorsField = new TextField();
    private final TextField parentField = new TextField();
    private final TextField nameField = new TextField();

    public QEFXRoCratePackDialog(RoCrateExporter.CrateDraft draft, String defaultFolderName) {
        this.draft = draft;
        setTitle("Pack RO-Crate folder (Roadmap #135)");
        setHeaderText("Materialize the reviewed metadata draft into a real crate folder."
                + " Every pinned file is copied and re-hashed AFTER the copy against the"
                + " draft's SHA-256 - any drift aborts before anything is activated."
                + " Licence and author records are optional and used verbatim; none are"
                + " invented or scraped from anywhere.");
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button okButton = (Button) getDialogPane().lookupButton(ButtonType.OK);
        okButton.setText("Pack crate");

        TextArea census = new TextArea(buildCensusText(draft));
        census.setEditable(false);
        census.setWrapText(false);
        census.setPrefRowCount(10);
        census.setStyle("-fx-font-family: monospace;");

        this.licenceField.setPromptText("Licence (optional - e.g. CC-BY-4.0; verbatim, never guessed)");
        this.authorsField.setPromptText("Authors (optional, separated by ; - e.g. Doe, Jane; Roe, John)");
        this.parentField.setPromptText("Parent folder (an existing directory; chosen explicitly)");
        this.parentField.setEditable(false);
        this.nameField.setText(defaultFolderName == null || defaultFolderName.isBlank()
                ? "quantumforge-ro-crate" : defaultFolderName);

        Button chooseButton = new Button("Choose parent folder ...");
        chooseButton.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Choose the parent folder for the new crate directory");
            Window owner = getDialogPane().getScene() == null ? null
                    : getDialogPane().getScene().getWindow();
            File chosen = chooser.showDialog(owner);
            if (chosen != null) {
                this.parentField.setText(chosen.getAbsolutePath());
            }
        });

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.addRow(0, new Label("Licence:"), this.licenceField);
        form.addRow(1, new Label("Authors:"), this.authorsField);
        form.addRow(2, new Label("Parent:"), this.parentField, chooseButton);
        form.addRow(3, new Label("Crate folder name:"), this.nameField);
        GridPane.setHgrow(this.licenceField, Priority.ALWAYS);
        GridPane.setHgrow(this.authorsField, Priority.ALWAYS);
        GridPane.setHgrow(this.parentField, Priority.ALWAYS);
        GridPane.setHgrow(this.nameField, Priority.ALWAYS);

        Label honesty = new Label("The crate folder must NOT exist yet - the packer creates it"
                + " in one rename and refuses to merge into existing content. A zero-artifact"
                + " draft is never packed.");
        honesty.setWrapText(true);

        VBox content = new VBox(10, new Label("Draft census (what will be pinned):"), census,
                form, honesty);
        content.setPadding(new Insets(10));
        VBox.setVgrow(census, Priority.ALWAYS);
        getDialogPane().setContent(content);
        getDialogPane().setPrefWidth(760);

        BooleanBinding notReady = this.parentField.textProperty().isEmpty()
                .or(this.nameField.textProperty().isEmpty());
        okButton.disableProperty().bind(notReady);
        okButton.setDisable(draft.getEntries().isEmpty() || notReady.get());
        if (draft.getEntries().isEmpty()) {
            notReady.addListener((obs, oldV, newV) -> okButton.setDisable(true));
        }

        setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            Path parent = Path.of(this.parentField.getText().trim());
            String folder = this.nameField.getText().trim();
            return new PackRequest(parent.resolve(folder),
                    this.licenceField.getText().trim().isEmpty() ? null
                            : this.licenceField.getText().trim(),
                    parseAuthors(this.authorsField.getText()));
        });
    }

    /** Semicolon-separated authors; blank segments are skipped, never erroring the flow. */
    private static List<RoCrateExporter.CrateAuthor> parseAuthors(String text) {
        List<RoCrateExporter.CrateAuthor> authors = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return authors;
        }
        for (String segment : text.split(";")) {
            String name = segment.trim();
            if (!name.isEmpty()) {
                authors.add(new RoCrateExporter.CrateAuthor(name, null));
            }
        }
        return authors;
    }

    private static String buildCensusText(RoCrateExporter.CrateDraft draft) {
        StringBuilder text = new StringBuilder();
        long total = 0L;
        for (RoCrateExporter.CrateEntry entry : draft.getEntries()) {
            text.append(String.format(java.util.Locale.ROOT, "  %-42s %10d bytes%n",
                    entry.getRelativePath(), entry.getBytes()));
            total += entry.getBytes();
        }
        for (String skipped : draft.getSkipped()) {
            text.append("  SKIPPED: ").append(skipped).append('\n');
        }
        text.append(String.format(java.util.Locale.ROOT,
                "%n%d file(s), %d byte(s); %d skip reason(s) preserved in the result.",
                draft.getEntries().size(), total, draft.getSkipped().size()));
        if (draft.getEntries().isEmpty()) {
            text.append("\n\nNothing is includable - the Pack button stays disabled.");
        }
        return text.toString();
    }
}
