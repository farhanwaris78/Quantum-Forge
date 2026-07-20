/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run.parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import quantumforge.operation.OperationResult;

/**
 * pw.x timing-table parser (Roadmap #43): reads the final "PWSCF : ... CPU ...
 * WALL" total (required - a log without it did not finish and reports nothing)
 * and the per-routine timing rows, with durations parsed token-wise from QE's
 * compact h/m/s notation ("1h23m45.67s", "0.22s", "2m"). Formats still vary by
 * QE version; anything outside this grammar simply does not match, and every
 * failure carries a code - values are exactly as printed.
 */
public final class QETimingParser {

    /** Parse bound identical to the other bounded log readers. */
    public static final long MAX_FILE_BYTES = 64L * 1024L * 1024L;

    /** One parsed routine row: name, CPU/WALL seconds, optional call count. */
    public static final class RoutineTime {
        private final String name;
        private final double cpuSeconds;
        private final double wallSeconds;
        private final long calls;

        RoutineTime(String name, double cpuSeconds, double wallSeconds, long calls) {
            this.name = name;
            this.cpuSeconds = cpuSeconds;
            this.wallSeconds = wallSeconds;
            this.calls = calls;
        }

        public String getName() { return this.name; }
        public double getCpuSeconds() { return this.cpuSeconds; }
        public double getWallSeconds() { return this.wallSeconds; }
        public long getCalls() { return this.calls; }
    }

    /** Parsed summary: the PWSCF totals plus every routine row found. */
    public static final class TimingSummary {
        private final double totalCpuSeconds;
        private final double totalWallSeconds;
        private final List<RoutineTime> routines;

        TimingSummary(double totalCpuSeconds, double totalWallSeconds,
                List<RoutineTime> routines) {
            this.totalCpuSeconds = totalCpuSeconds;
            this.totalWallSeconds = totalWallSeconds;
            this.routines = List.copyOf(routines);
        }

        public double getTotalCpuSeconds() { return this.totalCpuSeconds; }
        public double getTotalWallSeconds() { return this.totalWallSeconds; }
        public List<RoutineTime> getRoutines() { return this.routines; }
    }

    private static final Pattern ROUTINE = Pattern.compile(
            "^\\s*([A-Za-z0-9_]+)\\s*:\\s*([^Cc]*?)\\s+[Cc][Pp][Uu]\\s+([^Ww]*?)\\s+"
                    + "[Ww][Aa][Ll]{2}(?:\\s*\\(\\s*(\\d+)\\s+calls?\\))?\\s*$");
    private static final Pattern DURATION_TOKEN = Pattern.compile(
            "([0-9]+(?:\\.[0-9]+)?)([smhd])");

    private QETimingParser() { }

    /**
     * Parses the timing table. Codes: TIMING_IO, TIMING_TOO_LARGE,
     * TIMING_SYNTAX, TIMING_EMPTY.
     */
    public static OperationResult<TimingSummary> parse(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return OperationResult.failed("TIMING_IO", "The log file does not exist.",
                    null);
        }
        long size;
        try {
            size = Files.size(file);
        } catch (IOException ex) {
            return OperationResult.failed("TIMING_IO",
                    "Reading the log failed: " + ex.getMessage(), ex);
        }
        if (size > MAX_FILE_BYTES) {
            return OperationResult.failed("TIMING_TOO_LARGE",
                    "The log exceeds the 64 MiB parse bound.", null);
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException ex) {
            return OperationResult.failed("TIMING_IO",
                    "Reading the log failed: " + ex.getMessage(), ex);
        }
        double totalCpu = Double.NaN;
        double totalWall = Double.NaN;
        List<RoutineTime> routines = new ArrayList<>();
        for (String line : lines) {
            Matcher matcher = ROUTINE.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            double cpu = parseDuration(matcher.group(2));
            double wall = parseDuration(matcher.group(3));
            if (Double.isNaN(cpu) || Double.isNaN(wall)) {
                return OperationResult.failed("TIMING_SYNTAX",
                        "Could not parse the duration in line \"" + line.trim()
                                + "\"; the QE version may print a new format - refused "
                                + "rather than misread.", null);
            }
            long calls = matcher.group(4) == null ? -1L
                    : Long.parseLong(matcher.group(4));
            if (matcher.group(1).equalsIgnoreCase("PWSCF")) {
                totalCpu = cpu;
                totalWall = wall;
            } else {
                routines.add(new RoutineTime(matcher.group(1), cpu, wall, calls));
            }
        }
        if (Double.isNaN(totalCpu) || Double.isNaN(totalWall)) {
            return OperationResult.failed("TIMING_EMPTY",
                    "No PWSCF total-timing line was found; without it the run did not "
                            + "complete (or this is not a finished pw.x log), and "
                            + "partial routine sums are not reported as totals.", null);
        }
        return OperationResult.success("TIMING_OK",
                String.format(Locale.ROOT,
                        "pw.x total CPU %.2f s, WALL %.2f s across %d parsed routine(s).",
                        totalCpu, totalWall, routines.size()),
                new TimingSummary(totalCpu, totalWall, routines));
    }

    /** Sums QE compact duration tokens ("1h23m45.6s", "0.22s") into seconds. */
    static double parseDuration(String segment) {
        Matcher matcher = DURATION_TOKEN.matcher(segment);
        double seconds = 0.0;
        boolean any = false;
        while (matcher.find()) {
            double value;
            try {
                value = Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException ex) {
                return Double.NaN;
            }
            switch (matcher.group(2)) {
                case "d":
                    seconds += value * 86400.0;
                    break;
                case "h":
                    seconds += value * 3600.0;
                    break;
                case "m":
                    seconds += value * 60.0;
                    break;
                default:
                    seconds += value;
                    break;
            }
            any = true;
        }
        return any ? seconds : Double.NaN;
    }
}
