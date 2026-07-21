/* Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.app.project.viewer.tensor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import quantumforge.com.math.SymmetricEigen3;
import quantumforge.com.math.TensorSurfaceSampler;

/**
 * Roadmap #125 viewer slice: the TENSOR DIRECTIONAL-SURFACE panel. Renders
 * what the batch-tested eigen layer already adjudicated - the quadratic
 * form q(n) = n^T.T.n of a symmetric rank-2 tensor - as a 2D polar slice
 * (XY/XZ/YZ convention selector) or a 3D surface (yaw/pitch, sign-colored
 * lobes). Every number drawn comes from the headless, unit-tested
 * {@link TensorSurfaceSampler}; this class owns only pixels.
 *
 * <p>Honesty rules (also written into the header): rank-2 quadratic forms
 * only (rank-4 elastic surfaces stay with the #119 ELATE work); indefinite
 * tensors keep negative lobes and are drawn red, never folded into
 * magnitudes; eigenvector sign gauge only fixes a display direction; no
 * units and no physical interpretation at this layer. The dialog renders
 * only - it writes nothing, starts nothing, and changes no project state.</p>
 */
public final class QEFXTensorSurfaceDialog extends Dialog<Void> {

    private final TextField[][] cells = new TextField[3][3];
    private final ComboBox<String> viewCombo = new ComboBox<>();
    private final ComboBox<String> planeCombo = new ComboBox<>();
    private final Slider yawSlider = new Slider(0, 360, 30);
    private final Slider pitchSlider = new Slider(-90, 90, 20);
    private final Canvas canvas = new Canvas(560, 460);
    private final Label statusLabel = new Label();
    private final Label eigenLabel = new Label();

    public QEFXTensorSurfaceDialog() {
        setTitle("Tensor directional surface viewer (rank-2, eigen basis)");
        setHeaderText("Directional surface q(n) = n^T.T.n of a symmetric 3x3 tensor."
                + " Rank-2 quadratic forms only - rank-4 elastic surfaces stay with the"
                + " ELATE draft workflow. Red lobes are NEGATIVE q(n) (sign preserved,"
                + " never folded). No units / interpretation at this layer.");
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.cells[row][col] = new TextField(row == col ? "1" : "0");
                this.cells[row][col].setPrefColumnCount(6);
            }
        }
        this.viewCombo.getItems().setAll("3D surface", "2D slice");
        this.viewCombo.setValue("3D surface");
        this.planeCombo.getItems().setAll("XY", "XZ", "YZ");
        this.planeCombo.setValue("XY");

        HBox matrixRow = new HBox(4);
        for (int row = 0; row < 3; row++) {
            VBox column = new VBox(4);
            for (int col = 0; col < 3; col++) {
                column.getChildren().add(this.cells[row][col]);
            }
            matrixRow.getChildren().add(column);
        }
        Button loadButton = new Button("Load 3x3 file ...");
        loadButton.setOnAction(event -> loadFromFile());
        Button renderButton = new Button("Render");
        renderButton.setOnAction(event -> render());

        HBox controls = new HBox(8, new Label("View:"), this.viewCombo,
                new Label("Plane:"), this.planeCombo,
                new Label("Yaw:"), this.yawSlider,
                new Label("Pitch:"), this.pitchSlider, renderButton);
        this.yawSlider.setPrefWidth(110);
        this.pitchSlider.setPrefWidth(110);
        VBox top = new VBox(8, new HBox(8, matrixRow, loadButton), controls);
        this.planeCombo.disableProperty().bind(
                this.viewCombo.valueProperty().isNotEqualTo("2D slice"));
        this.yawSlider.disableProperty().bind(
                this.viewCombo.valueProperty().isNotEqualTo("3D surface"));
        this.pitchSlider.disableProperty().bind(
                this.viewCombo.valueProperty().isNotEqualTo("3D surface"));

        BorderPane content = new BorderPane(this.canvas);
        content.setTop(top);
        VBox bottom = new VBox(4, this.eigenLabel, this.statusLabel);
        content.setBottom(bottom);
        getDialogPane().setContent(content);
        getDialogPane().setPrefSize(640, 720);

        this.viewCombo.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> render());
        this.planeCombo.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> render());
        this.yawSlider.valueProperty()
                .addListener((observable, oldValue, newValue) -> render());
        this.pitchSlider.valueProperty()
                .addListener((observable, oldValue, newValue) -> render());
        render();
    }

    /** Reads the matrix fields under the tension of the parse contract. */
    private double[][] readMatrix() {
        StringBuilder text = new StringBuilder();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                text.append(this.cells[row][col].getText()).append(' ');
            }
            text.append('\n');
        }
        return TensorSurfaceSampler.parseMatrix3x3(text.toString());
    }

    /** File load into the grid (explicit user pick; reads only). */
    private void loadFromFile() {
        FileChooser chooser = new FileChooser();
        Window owner = getDialogPane().getScene() == null ? null
                : getDialogPane().getScene().getWindow();
        File file = chooser.showOpenDialog(owner);
        if (file == null) {
            return;
        }
        final double[][] matrix;
        try {
            matrix = TensorSurfaceSampler.parseMatrix3x3(
                    Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException problem) {
            showProblem("Could not read the tensor file", problem.toString());
            return;
        } catch (IllegalArgumentException problem) {
            showProblem("Tensor file refused", problem.getMessage());
            return;
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                this.cells[row][col].setText(String.valueOf(matrix[row][col]));
            }
        }
        this.statusLabel.setText("Loaded " + file.getName()
                + " under the 3x3 contract (three finite values on each of the first"
                + " three non-blank lines).");
        render();
    }

    private void showProblem(String header, String detail) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("Tensor directional surface");
        alert.setHeaderText(header);
        alert.setContentText(detail);
        alert.showAndWait();
    }

    /** Top-level render entry: refusal paths show the typed message itself. */
    private void render() {
        final double[][] matrix;
        try {
            matrix = readMatrix();
        } catch (IllegalArgumentException problem) {
            this.eigenLabel.setText("");
            this.statusLabel.setText("Matrix refused: " + problem.getMessage());
            clearCanvas();
            return;
        }
        final SymmetricEigen3.EigenDecomposition eigen;
        try {
            eigen = SymmetricEigen3.eigenvectors(matrix);
        } catch (IllegalArgumentException problem) {
            this.eigenLabel.setText("");
            this.statusLabel.setText("Tensor refused: " + problem.getMessage()
                    + " (an asymmetric tensor has no real eigenbasis).");
            clearCanvas();
            return;
        }
        if (!eigen.isConverged()) {
            this.eigenLabel.setText("");
            this.statusLabel.setText("The Jacobi eigen decomposition did not converge;"
                    + " nothing is rendered from a suspicious eigensystem.");
            clearCanvas();
            return;
        }
        double[] values = eigen.getEigenvalues();
        double[][] vectors = eigen.getEigenvectors();
        StringBuilder lines = new StringBuilder(String.format(java.util.Locale.ROOT,
                "eigenvalues: %.6g, %.6g, %.6g   (sweeps: %d)",
                values[0], values[1], values[2], eigen.getSweeps()));
        for (int i = 0; i < 3; i++) {
            lines.append(String.format(java.util.Locale.ROOT,
                    "%n  e%d (% .4f, % .4f, % .4f)",
                    i + 1, vectors[i][0], vectors[i][1], vectors[i][2]));
        }
        this.eigenLabel.setText(lines.toString());
        if ("2D slice".equals(this.viewCombo.getValue())) {
            render2D(matrix, values);
        } else {
            render3D(matrix, values);
        }
    }

    private void clearCanvas() {
        GraphicsContext g = this.canvas.getGraphicsContext2D();
        g.setFill(Color.WHITE);
        g.fillRect(0, 0, this.canvas.getWidth(), this.canvas.getHeight());
    }

    private double canvasScale(double bound) {
        return 0.42 * Math.min(this.canvas.getWidth(), this.canvas.getHeight())
                / Math.max(bound, 1.0e-12);
    }

    /** 2D polar slice with eigen-bounds ticks and a fail-closed bounds check. */
    private void render2D(double[][] matrix, double[] values) {
        TensorSurfaceSampler.CartesianPlane plane =
                TensorSurfaceSampler.CartesianPlane.valueOf(this.planeCombo.getValue());
        double[] slice = TensorSurfaceSampler.samplePlane(matrix, plane, 360);
        double[] bounds = TensorSurfaceSampler.minMax(slice);
        assertBounds(bounds, values);
        double scale = canvasScale(Math.max(Math.abs(bounds[0]), Math.abs(bounds[1])));
        double cx = this.canvas.getWidth() / 2.0;
        double cy = this.canvas.getHeight() / 2.0;
        GraphicsContext g = this.canvas.getGraphicsContext2D();
        clearCanvas();
        g.setStroke(Color.LIGHTGRAY);
        g.strokeOval(cx - Math.abs(values[2]) * scale, cy - Math.abs(values[2]) * scale,
                2 * Math.abs(values[2]) * scale, 2 * Math.abs(values[2]) * scale);
        g.strokeOval(cx - Math.abs(values[0]) * scale, cy - Math.abs(values[0]) * scale,
                2 * Math.abs(values[0]) * scale, 2 * Math.abs(values[0]) * scale);
        g.strokeLine(cx - 0.48 * this.canvas.getWidth(), cy,
                cx + 0.48 * this.canvas.getWidth(), cy);
        g.strokeLine(cx, cy - 0.46 * this.canvas.getHeight(),
                cx, cy + 0.46 * this.canvas.getHeight());
        for (int i = 0; i < slice.length; i++) {
            double angle = Math.toRadians(i);
            double radius = Math.abs(slice[i]) * scale;
            double px = cx + radius * Math.cos(angle);
            double py = cy - radius * Math.sin(angle);
            g.setFill(slice[i] < 0 ? Color.FIREBRICK : Color.STEELBLUE);
            g.fillOval(px - 1.6, py - 1.6, 3.2, 3.2);
        }
        this.statusLabel.setText(String.format(java.util.Locale.ROOT,
                "2D slice %s: min %.6g, max %.6g within eigen bounds [%.6g, %.6g]."
                        + " Blue = positive, red = negative.",
                plane, bounds[0], bounds[1], values[0], values[2]));
    }

    /** 3D surface: sign-colored poles, wireframe, eigen-projection pins. */
    private void render3D(double[][] matrix, double[] values) {
        double[][] grid = TensorSurfaceSampler.sampleSphere(matrix, 24, 13);
        double[] bounds = TensorSurfaceSampler.minMax(grid);
        assertBounds(bounds, values);
        double scale = canvasScale(Math.max(Math.abs(bounds[0]), Math.abs(bounds[1])));
        double cx = this.canvas.getWidth() / 2.0;
        double cy = this.canvas.getHeight() / 2.0;
        double yaw = this.yawSlider.getValue();
        double pitch = this.pitchSlider.getValue();
        GraphicsContext g = this.canvas.getGraphicsContext2D();
        clearCanvas();

        List<double[]> meridians = new ArrayList<>();
        for (int a = 0; a < 24; a++) {
            double[] line = new double[13];
            for (int p = 0; p < 13; p++) {
                line[p] = grid[p][a];
            }
            meridians.add(line);
        }
        g.setLineWidth(0.6);
        for (int a = 0; a < 24; a++) {
            double previousX = 0;
            double previousY = 0;
            boolean started = false;
            for (int p = 0; p < 13; p++) {
                double az = 360.0 * a / 24.0;
                double pol = 180.0 * p / 12.0;
                double[] point = TensorSurfaceSampler.surfacePoint(matrix, az, pol);
                double[] screen = TensorSurfaceSampler.project(point[0], point[1],
                        point[2], yaw, pitch);
                double sx = cx + screen[0] * scale;
                double sy = cy - screen[1] * scale;
                if (started) {
                    g.setStroke(Color.LIGHTGRAY);
                    g.strokeLine(previousX, previousY, sx, sy);
                }
                g.setFill(grid[p][a] < 0 ? Color.FIREBRICK : Color.STEELBLUE);
                g.fillOval(sx - 2.2, sy - 2.2, 4.4, 4.4);
                previousX = sx;
                previousY = sy;
                started = true;
            }
        }
        // equator + reference poles of the grid itself (wireframe context):
        double previousX = 0;
        double previousY = 0;
        for (int a = 0; a <= 24; a++) {
            double[] point = TensorSurfaceSampler.surfacePoint(matrix, 360.0 * a / 24.0, 90);
            double[] screen = TensorSurfaceSampler.project(point[0], point[1], point[2],
                    yaw, pitch);
            double sx = cx + screen[0] * scale;
            double sy = cy - screen[1] * scale;
            if (a > 0) {
                g.setStroke(Color.LIGHTGRAY);
                g.strokeLine(previousX, previousY, sx, sy);
            }
            previousX = sx;
            previousY = sy;
        }
        // eigen-direction pins (display gauge only, sign not symmetric data):
        SymmetricEigen3.EigenDecomposition eigen = SymmetricEigen3.eigenvectors(matrix);
        double[][] vectors = eigen.getEigenvectors();
        for (int i = 0; i < 3; i++) {
            double radius = Math.abs(values[i]);
            double[] pin = TensorSurfaceSampler.project(
                    radius * vectors[i][0], radius * vectors[i][1], radius * vectors[i][2],
                    yaw, pitch);
            double sx = cx + pin[0] * scale;
            double sy = cy - pin[1] * scale;
            g.setFill(values[i] < 0 ? Color.DARKRED : Color.DARKBLUE);
            g.fillOval(sx - 4, sy - 4, 8, 8);
        }
        this.statusLabel.setText(String.format(java.util.Locale.ROOT,
                "3D surface (312 samples, |q(n)| radius): grid bounds [%.6g, %.6g] within"
                        + " eigen bounds [%.6g, %.6g]. Blue = positive, red = negative.",
                bounds[0], bounds[1], values[0], values[2]));
    }

    /** Fail-closed render guard: sampled values must stay in eigen bounds. */
    private void assertBounds(double[] bounds, double[] values) {
        if (bounds[0] < values[0] - 1.0e-9 * Math.max(1.0, Math.abs(values[2]))
                || bounds[1] > values[2] + 1.0e-9 * Math.max(1.0, Math.abs(values[2]))) {
            throw new IllegalStateException("internal sample escaped the eigen-bounds");
        }
    }
}
