/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Curated, reviewed workflow templates with parameter explanations
 * (Roadmap #133): each entry states its purpose, its PREREQUISITES (the
 * convergence work that must precede it) and its known pitfalls. These are
 * starting points for a material-specific setup - explicitly NOT
 * convergence-complete defaults; ecutwfc/ecutrho/k-mesh/smearing choices must
 * come from the convergence workflows (#36/#37/#38) for YOUR system.
 */
public final class QEWorkflowTemplateLibrary {

    /** One curated template: name, calculation family, purpose, prerequisites, pitfalls. */
    public static final class WorkflowTemplate {
        private final String name;
        private final String workflow;
        private final String purpose;
        private final String prerequisites;
        private final String pitfalls;

        WorkflowTemplate(String name, String workflow, String purpose,
                String prerequisites, String pitfalls) {
            this.name = name;
            this.workflow = workflow;
            this.purpose = purpose;
            this.prerequisites = prerequisites;
            this.pitfalls = pitfalls;
        }

        public String getName() { return this.name; }
        public String getWorkflow() { return this.workflow; }
        public String getPurpose() { return this.purpose; }
        public String getPrerequisites() { return this.prerequisites; }
        public String getPitfalls() { return this.pitfalls; }
    }

    private static final List<WorkflowTemplate> TEMPLATES = new ArrayList<>();
    static {
        TEMPLATES.add(new WorkflowTemplate("scf-basic", "pw.x scf",
                "Single-point total-energy reference for later stages.",
                "Converged ecutwfc/ecutrho, a converged k mesh, and one consistent "
                        + "pseudopotential family for every element (#35/#36/#37).",
                "occupations must match the system (fixed for molecules/insulators, "
                        + "smearing for metals); conv_thr tighter than the default when "
                        + "forces/stress follow; enable tprnfor to see forces."));
        TEMPLATES.add(new WorkflowTemplate("relax-bfgs", "pw.x relax",
                "Geometry optimization at fixed cell.",
                "A working scf setup (scf-basic) plus chosen etot_conv_thr and "
                        + "forc_conv_thr thresholds.",
                "BFGS can stall near soft modes - check the energy/force convergence "
                        + "panel (#39) instead of trusting step count; restart only from "
                        + "the parsed last geometry (#40); nstep is a budget, not a "
                        + "convergence criterion."));
        TEMPLATES.add(new WorkflowTemplate("vc-relax-crystal", "pw.x vc-relax",
                "Cell + ionic optimization for periodic crystals.",
                "scf-basic settings with extra ecutrho margin for USPP/PAW stress "
                        + "accuracy and tstress enabled.",
                "After the cell moves, re-run a final fixed-cell scf/nscf before ANY "
                        + "property stage; cell_dynamics='damp' helps noisy stress; the "
                        + "final geometry update must go through the transactional path "
                        + "(#40), not a blind overwrite."));
        TEMPLATES.add(new WorkflowTemplate("bands-path", "pw.x scf -> nscf (crystal_b) "
                + "-> bands.x",
                "Band structure along a high-symmetry path.",
                "A finished scf at the production mesh; the k path from the seekpath "
                        + "service (#73), not hand-typed letters.",
                "Fermi reference comes from the scf step only; raise nbnd enough to "
                        + "cover the wanted unoccupied range; path sampling uses its own "
                        + "density, not the scf mesh."));
        TEMPLATES.add(new WorkflowTemplate("nscf-dos", "pw.x scf -> nscf (dense mesh) "
                + "-> dos.x/projwfc.x",
                "Total and projected density of states.",
                "A finished scf; the dense nscf mesh converged against the target DOS "
                        + "feature; occupations='tetrahedra' for well-sampled insulators "
                        + "or a reconverged smearing for metals.",
                "PDOS orbital sums must reproduce the total DOS within tolerance (#48 "
                        + "acceptance); check Emin/Emax/DeltaE against the band range, "
                        + "never against defaults."));
        TEMPLATES.add(new WorkflowTemplate("phonon-gamma0", "pw.x scf -> ph.x (q=Gamma) "
                + "-> dynmat.x",
                "Gamma-point vibrational frequencies, IR/Raman-activity records and "
                        + "mode eigenvectors.",
                "A tightly converged scf (conv_thr 1e-10 or tighter) - residual force "
                        + "noise turns into imaginary frequencies otherwise.",
                "eps=.true. adds dielectric/Born tensors for insulators (#61); check the "
                        + "acoustic sum rule before quoting numbers; imaginary modes are "
                        + "instabilities, not errors to hide."));
    }

    private QEWorkflowTemplateLibrary() { }

    /** All curated templates in display order. */
    public static List<WorkflowTemplate> templates() {
        return List.copyOf(TEMPLATES);
    }

    /** Case-insensitive lookup; empty outside the curated set (fail closed). */
    public static Optional<WorkflowTemplate> find(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        for (WorkflowTemplate template : TEMPLATES) {
            if (template.getName().equalsIgnoreCase(key)) {
                return Optional.of(template);
            }
        }
        return Optional.empty();
    }
}
