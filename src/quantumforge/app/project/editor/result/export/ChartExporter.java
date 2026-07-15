/*
 * Copyright (C) 2025 QuantumForge Team
 */
package quantumforge.app.project.editor.result.export;

/**
 * Chart export utilities for screenshots and CSV data.
 * 
 * NanoLabo provides:
 * - Screenshot export of plots (PNG)
 * - CSV data export of plotted data
 * - Plot appearance customization
 * 
 * This enables users to save results for publications.
 */
public class ChartExporter {

    public static final int FORMAT_PNG = 0;
    public static final int FORMAT_SVG = 1;
    public static final int FORMAT_CSV = 2;
    public static final int FORMAT_PDF = 3;

    private String filePath;
    private int format;

    public ChartExporter(String filePath, int format) {
        this.filePath = filePath;
        this.format = format;
    }

    public void setFilePath(String path) { this.filePath = path; }
    public String getFilePath() { return this.filePath; }
    public void setFormat(int fmt) { this.format = fmt; }
    public int getFormat() { return this.format; }

    /**
     * Export data to CSV format
     */
    public static String toCSV(double[] xData, double[][] yData, String[] seriesNames) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("x");
        if (seriesNames != null) {
            for (String name : seriesNames) {
                sb.append(",").append(name != null ? name : "series");
            }
        }
        sb.append("\n");

        // Data rows
        int nRows = xData != null ? xData.length : 0;
        for (int i = 0; i < nRows; i++) {
            sb.append(xData[i]);
            if (yData != null) {
                for (double[] series : yData) {
                    if (series != null && i < series.length) {
                        sb.append(",").append(series[i]);
                    }
                }
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
