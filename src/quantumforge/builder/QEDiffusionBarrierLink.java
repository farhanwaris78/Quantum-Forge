/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.builder;

import quantumforge.operation.OperationResult;

/**
 * Single-barrier transition-state diffusivity estimate (Roadmap #157,
 * formulation layer).
 *
 * <p>D(T) = D0 * exp(-Ea / kB T), D0 = a^2 * nu / (2 d), where a is the hop
 * length, nu the attempt frequency, and d the hopping dimensionality. The
 * conversion 1 Ang^2 * 1 THz = 1e-4 cm^2/s is applied exactly. This is the
 * simplest uncorrelated-hopping form: it ignores correlation factors, pathway
 * networks, and concentrations, and a single NEB barrier must NOT be presented
 * as bulk ionic conductivity - the analysis layer repeats that caveat in every
 * report.</p>
 */
public final class QEDiffusionBarrierLink {

    /** Boltzmann constant in eV/K (CODATA 2018). */
    public static final double KB_EV_PER_K = 8.617333262145e-5;
    /** 1 Angstrom^2 * 1 THz expressed in cm^2/s. */
    public static final double ANG2_THZ_TO_CM2_PER_S = 1.0e-4;

    private QEDiffusionBarrierLink() {
        // Utility
    }

    /** Attempt diffusivity D0 in cm^2/s for the given hop parameters. */
    public static OperationResult<Double> preFactorCm2PerS(double hopAngstrom,
            double attemptThz, int dimension) {
        String problem = validateHop(hopAngstrom, attemptThz, dimension);
        if (problem != null) {
            return OperationResult.failed("HOP_INVALID", problem, null);
        }
        return OperationResult.success("D0_OK", "Attempt diffusivity computed.",
                hopAngstrom * hopAngstrom * attemptThz / (2.0 * dimension)
                        * ANG2_THZ_TO_CM2_PER_S);
    }

    /** Arrhenius diffusivity D(T) in cm^2/s for the stated barrier and temperature. */
    public static OperationResult<Double> estimateDiffusivityCm2PerS(double barrierEv,
            double temperatureK, double hopAngstrom, double attemptThz, int dimension) {
        if (!Double.isFinite(barrierEv) || barrierEv < 0.0) {
            return OperationResult.failed("BARRIER_INVALID",
                    "The migration barrier must be a finite value >= 0 eV (got " + barrierEv
                            + "); a negative barrier signals an unstable reference state.", null);
        }
        if (!Double.isFinite(temperatureK) || temperatureK <= 0.0) {
            return OperationResult.failed("TEMPERATURE_INVALID",
                    "The temperature must be a finite value > 0 K (got " + temperatureK + ").",
                    null);
        }
        OperationResult<Double> d0 = preFactorCm2PerS(hopAngstrom, attemptThz, dimension);
        if (!d0.isSuccess() || d0.getValue().isEmpty()) {
            return OperationResult.failed(d0.getCode(), d0.getMessage(), null);
        }
        double exponent = -barrierEv / (KB_EV_PER_K * temperatureK);
        return OperationResult.success("D_OK", "Arrhenius diffusivity computed.",
                d0.getValue().orElseThrow() * Math.exp(exponent));
    }

    private static String validateHop(double hopAngstrom, double attemptThz, int dimension) {
        if (!Double.isFinite(hopAngstrom) || hopAngstrom <= 0.0) {
            return "The hop length must be a finite value > 0 Angstrom (got "
                    + hopAngstrom + ").";
        }
        if (!Double.isFinite(attemptThz) || attemptThz <= 0.0) {
            return "The attempt frequency must be a finite value > 0 THz (got "
                    + attemptThz + ").";
        }
        if (dimension < 1 || dimension > 3) {
            return "The hopping dimensionality must be 1, 2, or 3 (got " + dimension + ").";
        }
        return null;
    }
}
