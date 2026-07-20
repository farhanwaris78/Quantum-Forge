/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.export;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import quantumforge.operation.OperationResult;

/**
 * Minimal, dependency-free SVG line-plot renderer for two-column CSV series
 * (Roadmap #122, partial). The renderer is deterministic: the same CSV rows
 * always produce byte-identical SVG text, axis labels come only from the CSV
 * header row, every embedded value is XML-escaped, and the original series is
 * hashed (SHA-256) into a provenance comment so an exported plot can be traced
 * back to its source rows. This is a single-series helper, not a charting
 * engine; multi-axis or styled publication layouts remain future work.
 */
public final class SvgSeriesPlotter {

    /** Pixel geometry of every generated figure. */
    public static final int WIDTH = 840;
    public static final int HEIGHT = 560;
    public static final int MARGIN_LEFT = 90;
    public static final int MARGIN_RIGHT = 30;
    public static final int MARGIN_TOP = 50;
    public static final int MARGIN_BOTTOM = 70;
    /** Minimum number of numeric data rows for a meaningful plot. */
    public static final int MIN_POINTS = 3;

    private SvgSeriesPlotter() {
        // Utility
    }

    /**
     * Renders the first two numeric columns of the CSV lines to SVG text.
     * A first line whose first two cells are not both numeric is treated as the
     * header and supplies the axis labels; later unparsable rows are counted as
     * rejected (never silently dropped). Fortran D exponents are tolerated.
     */
    public static OperationResult<String> plot(List<String> csvLines, String title) {
        if (csvLines == null || csvLines.isEmpty()) {
            return OperationResult.failed("PLOT_EMPTY", "The report has no CSV rows to plot.",
                    null);
        }
        String xLabel = "x";
        String yLabel = "y";
        List<double[]> points = new ArrayList<>();
        int rejected = 0;
        boolean first = true;
        for (String line : csvLines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] cells = trimmed.split(",");
            if (cells.length < 2) {
                rejected++;
                continue;
            }
            double x = parseDouble(cells[0]);
            double y = parseDouble(cells[1]);
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                if (first && points.isEmpty()) {
                    xLabel = cells[0].trim().isEmpty() ? "x" : cells[0].trim();
                    yLabel = cells[1].trim().isEmpty() ? "y" : cells[1].trim();
                } else {
                    rejected++;
                }
                first = false;
                continue;
            }
            first = false;
            points.add(new double[] {x, y});
        }
        if (points.size() < MIN_POINTS) {
            return OperationResult.failed("PLOT_NOT_SERIES",
                    "Only " + points.size() + " numeric data row(s) were parsed (need >= "
                            + MIN_POINTS + "); this CSV shape is not a plottable series.",
                    null);
        }
        double xmin = Double.POSITIVE_INFINITY;
        double xmax = Double.NEGATIVE_INFINITY;
        double ymin = Double.POSITIVE_INFINITY;
        double ymax = Double.NEGATIVE_INFINITY;
        for (double[] point : points) {
            xmin = Math.min(xmin, point[0]);
            xmax = Math.max(xmax, point[0]);
            ymin = Math.min(ymin, point[1]);
            ymax = Math.max(ymax, point[1]);
        }
        double xRange = xmax - xmin;
        double yRange = ymax - ymin;
        if (!(xRange > 0.0)) {
            xmin -= 0.5;
            xmax += 0.5;
            xRange = 1.0;
        }
        if (!(yRange > 0.0)) {
            ymin -= 0.5;
            ymax += 0.5;
            yRange = 1.0;
        }

        StringBuilder svg = new StringBuilder();
        svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        svg.append("<!-- QuantumForge SvgSeriesPlotter: sha256=").append(hash(csvLines))
                .append(" points=").append(points.size()).append(" rejected=").append(rejected)
                .append(" -->\n");
        svg.append(String.format(Locale.ROOT,
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" "
                        + "viewBox=\"0 0 %d %d\">\n", WIDTH, HEIGHT, WIDTH, HEIGHT));
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"white\"/>\n");
        svg.append(String.format(Locale.ROOT,
                "<text x=\"%d\" y=\"28\" text-anchor=\"middle\" font-family=\"sans-serif\" "
                        + "font-size=\"18\">%s</text>\n", WIDTH / 2,
                escape(title == null || title.isBlank() ? "Series plot" : title)));

        // Axes.
        int plotRight = WIDTH - MARGIN_RIGHT;
        int plotBottom = HEIGHT - MARGIN_BOTTOM;
        svg.append(String.format(Locale.ROOT,
                "<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"black\" "
                        + "stroke-width=\"1.5\"/>\n",
                MARGIN_LEFT, MARGIN_TOP, MARGIN_LEFT, plotBottom));
        svg.append(String.format(Locale.ROOT,
                "<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"black\" "
                        + "stroke-width=\"1.5\"/>\n",
                MARGIN_LEFT, plotBottom, plotRight, plotBottom));

        // Ticks: 5 linear ticks per axis.
        for (int i = 0; i < 5; i++) {
            double fraction = i / 4.0;
            double xValue = xmin + fraction * xRange;
            int xPixel = MARGIN_LEFT + (int) Math.round(fraction * (plotRight - MARGIN_LEFT));
            svg.append(String.format(Locale.ROOT,
                    "<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"black\"/>\n"
                            + "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" "
                            + "font-family=\"sans-serif\" font-size=\"11\">%s</text>\n",
                    xPixel, plotBottom, xPixel, plotBottom + 5, xPixel, plotBottom + 18,
                    escape(String.format(Locale.ROOT, "%.4g", xValue))));
            double yValue = ymin + fraction * yRange;
            int yPixel = plotBottom - (int) Math.round(fraction * (plotBottom - MARGIN_TOP));
            svg.append(String.format(Locale.ROOT,
                    "<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"black\"/>\n"
                            + "<text x=\"%d\" y=\"%d\" text-anchor=\"end\" "
                            + "font-family=\"sans-serif\" font-size=\"11\">%s</text>\n",
                    MARGIN_LEFT - 5, yPixel, MARGIN_LEFT, yPixel, MARGIN_LEFT - 8, yPixel + 4,
                    escape(String.format(Locale.ROOT, "%.4g", yValue))));
        }
        // Axis labels.
        svg.append(String.format(Locale.ROOT,
                "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" font-family=\"sans-serif\" "
                        + "font-size=\"14\">%s</text>\n",
                (MARGIN_LEFT + plotRight) / 2, HEIGHT - 20, escape(xLabel)));
        svg.append(String.format(Locale.ROOT,
                "<text x=\"24\" y=\"%d\" text-anchor=\"middle\" font-family=\"sans-serif\" "
                        + "font-size=\"14\" transform=\"rotate(-90 24 %d)\">%s</text>\n",
                (MARGIN_TOP + plotBottom) / 2, (MARGIN_TOP + plotBottom) / 2, escape(yLabel)));

        // Data polyline.
        StringBuilder polyline = new StringBuilder();
        for (double[] point : points) {
            double xPixel = MARGIN_LEFT + (point[0] - xmin) / xRange * (plotRight - MARGIN_LEFT);
            double yPixel = plotBottom - (point[1] - ymin) / yRange * (plotBottom - MARGIN_TOP);
            if (!polyline.isEmpty()) {
                polyline.append(' ');
            }
            polyline.append(String.format(Locale.ROOT, "%.2f,%.2f", xPixel, yPixel));
        }
        svg.append(String.format(Locale.ROOT,
                "<polyline fill=\"none\" stroke=\"#1f4e9c\" stroke-width=\"1.8\" "
                        + "points=\"%s\"/>\n", polyline));
        svg.append("</svg>\n");
        return OperationResult.success("PLOT_OK",
                "SVG plot rendered (" + points.size() + " points, " + rejected
                        + " rejected row(s)).", svg.toString());
    }

    private static double parseDouble(String cell) {
        try {
            return Double.parseDouble(cell.trim().replace('D', 'E').replace('d', 'E'));
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }

    /** XML-escapes the five predefined entities so labels cannot break the document. */
    static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String hash(List<String> lines) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(
                    String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hashed) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            return "unavailable";
        }
    }
}
