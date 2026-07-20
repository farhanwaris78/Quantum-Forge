/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.run.parser;

import java.util.Locale;

import quantumforge.operation.OperationResult;

/**
 * Animation-frame synthesis for one validated vibrational mode (Roadmap #52
 * data layer): displaced positions base + amplitude * Re(e_i) * sin(2 pi f / N)
 * rendered as a multi-frame XYZ document. The dynmat eigenvectors are
 * mass-weighted and orthonormal, so the fixed Angstrom amplitude is a VISUAL
 * scaling, not a physical displacement field; the report states this, drops
 * imaginary components explicitly, and never invents supercell replicas.
 */
public final class PhononFrameSynthesis {

    /** Amplitude guard so a silly value cannot produce misleading graphics. */
    public static final double MAX_AMPLITUDE_ANG = 5.0;
    public static final int MIN_FRAMES = 3;
    public static final int MAX_FRAMES = 240;

    /**
     * Builds the phase-sampled frame document. All shapes must agree:
     * realParts[i], basePositions[i] and elements[i] per atom.
     */
    public static OperationResult<String> frames(double[][] realParts,
            double[][] basePositions, String[] elements,
            double amplitudeAngstrom, int frameCount,
            double frequencyCm1, int modeIndex) {
        if (realParts == null || basePositions == null || elements == null
                || realParts.length == 0
                || basePositions.length != realParts.length
                || elements.length != realParts.length) {
            return OperationResult.failed("FRAMES_SHAPE",
                    "Mode rows, base positions and element names must be non-empty arrays "
                            + "of the same length.", null);
        }
        if (!Double.isFinite(amplitudeAngstrom) || amplitudeAngstrom <= 0.0
                || amplitudeAngstrom > MAX_AMPLITUDE_ANG) {
            return OperationResult.failed("FRAMES_AMPLITUDE",
                    "The visual amplitude must be a finite value within (0, "
                            + MAX_AMPLITUDE_ANG + "] Angstrom.", null);
        }
        if (frameCount < MIN_FRAMES || frameCount > MAX_FRAMES) {
            return OperationResult.failed("FRAMES_COUNT",
                    "The frame count must be within " + MIN_FRAMES + ".." + MAX_FRAMES + ".",
                    null);
        }
        if (!Double.isFinite(frequencyCm1)) {
            return OperationResult.failed("FRAMES_FREQUENCY",
                    "The mode frequency must be finite.", null);
        }
        int natoms = realParts.length;
        StringBuilder document = new StringBuilder();
        for (int frame = 0; frame < frameCount; frame++) {
            double phase = Math.sin(2.0 * Math.PI * frame / frameCount);
            document.append(natoms).append('\n');
            document.append(String.format(Locale.ROOT,
                    "frame %d/%d phase=sin(2*pi*%d/%d)=%+.6f mode=%d omega=%.6f cm-1 "
                            + "amplitude=%.6f A (dynmat mass-weighted mode, real part)",
                    frame + 1, frameCount, frame, frameCount, phase, modeIndex,
                    frequencyCm1, amplitudeAngstrom));
            document.append('\n');
            for (int atom = 0; atom < natoms; atom++) {
                double[] base = basePositions[atom];
                double[] real = realParts[atom];
                if (base == null || real == null || base.length < 3 || real.length < 3) {
                    return OperationResult.failed("FRAMES_SHAPE",
                            "Atom " + (atom + 1) + " lacks the required 3 components.", null);
                }
                for (int axis = 0; axis < 3; axis++) {
                    if (!Double.isFinite(base[axis]) || !Double.isFinite(real[axis])) {
                        return OperationResult.failed("FRAMES_VALUE",
                                "A non-finite value was found at atom " + (atom + 1)
                                        + ", axis " + (axis + 1) + ".", null);
                    }
                }
                String element = elements[atom] == null || elements[atom].isBlank()
                        ? "X" : elements[atom].trim();
                document.append(String.format(Locale.ROOT,
                        "%-3s %14.8f %14.8f %14.8f%n", element,
                        base[0] + amplitudeAngstrom * real[0] * phase,
                        base[1] + amplitudeAngstrom * real[1] * phase,
                        base[2] + amplitudeAngstrom * real[2] * phase));
            }
        }
        return OperationResult.success("FRAMES_OK",
                "Synthesized " + frameCount + " phase-sampled frame(s) for mode "
                        + modeIndex + ".",
                document.toString());
    }
}
