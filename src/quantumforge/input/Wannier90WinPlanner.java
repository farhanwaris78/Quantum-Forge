/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input;

import java.util.Locale;

import quantumforge.input.card.QEKPoints;
import quantumforge.input.namelist.QEValue;
import quantumforge.operation.OperationResult;

/**
 * Wannier90 input (.win) DRAFT planner (Roadmap #69 data layer): echoes the
 * pieces of a .win that the live pw.x input honestly determines - the uniform
 * automatic K_POINTS mesh (pw2wannier90.x interfacing REQUIRES the nscf run on
 * exactly this mesh) and nbnd when set - and marks every physics choice that
 * cannot be inferred (num_wann, projections, disentanglement windows) as
 * REQUIRED-EDIT placeholders. The kpoints block is generated exactly from the
 * grid only when the count stays inside the generation bound; the decimal
 * precision of the generated block is stated. A Gamma-only or explicit-list
 * input cannot feed a Wannierization and is refused outright.
 */
public final class Wannier90WinPlanner {

    /** Explicit kpoints-block generation bound keeps the drafted .win reviewable. */
    public static final int MAX_GENERATED_KPOINTS = 4096;
    /** Decimal places used for the generated crystal coordinates (stated). */
    public static final int KPOINT_DECIMALS = 10;

    /** Immutable draft bundle. */
    public static final class WinDraft {
        private final int[] grid;
        private final int[] offset;
        private final Integer nbnd;       // echoed or null (REQUIRED-EDIT)
        private final boolean kpointsGenerated;
        private final String draft;

        WinDraft(int[] grid, int[] offset, Integer nbnd, boolean kpointsGenerated,
                String draft) {
            this.grid = grid;
            this.offset = offset;
            this.nbnd = nbnd;
            this.kpointsGenerated = kpointsGenerated;
            this.draft = draft;
        }

        public int[] getGrid() { return this.grid.clone(); }
        public int[] getOffset() { return this.offset.clone(); }
        public Integer getNbnd() { return this.nbnd; }
        public boolean isKpointsGenerated() { return this.kpointsGenerated; }
        public String getDraft() { return this.draft; }

        /** Number of REQUIRED-EDIT placeholders in the draft. */
        public int countRequiredEdits() {
            int count = 0;
            int idx = this.draft.indexOf("REQUIRED-EDIT");
            while (idx >= 0) {
                count += 1;
                idx = this.draft.indexOf("REQUIRED-EDIT", idx + 1);
            }
            return count;
        }
    }

    private Wannier90WinPlanner() { }

    /**
     * Builds the draft. Codes: W90_INPUT (no input), W90_MESH (no usable
     * uniform automatic mesh: missing card, Gamma-only, explicit list, or a
     * degenerate grid).
     */
    public static OperationResult<WinDraft> plan(QEInput input) {
        if (input == null) {
            return OperationResult.failed("W90_INPUT",
                    "The project has no resolvable current input to draft from.", null);
        }
        QEKPoints card = input.getCard(QEKPoints.class);
        if (card == null) {
            return OperationResult.failed("W90_MESH",
                    "The live input has no K_POINTS card. pw2wannier90.x interfaces a "
                            + "uniform nscf mesh - there is nothing honest to echo yet.",
                    null);
        }
        if (card.isGamma()) {
            return OperationResult.failed("W90_MESH",
                    "The live input is Gamma-only. Wannierization needs a UNIFORM k "
                            + "mesh (the same mesh pw2wannier90.x will read); a Gamma "
                            + "setup is a special case outside this bridge.",
                    null);
        }
        if (!card.isAutomatic()) {
            return OperationResult.failed("W90_MESH",
                    "The live input uses an explicit/path K_POINTS list. Wannier input "
                            + "needs a UNIFORM automatic mesh - run the nscf on one first.",
                    null);
        }
        int[] grid = card.getKGrid();
        int[] offset = card.getKOffset();
        for (int i = 0; i < 3; i++) {
            if (grid[i] < 1) {
                return OperationResult.failed("W90_MESH",
                        "The automatic mesh entries must all be >= 1.", null);
            }
            if (offset[i] != 0 && offset[i] != 1) {
                return OperationResult.failed("W90_MESH",
                        "Automatic-mesh offsets outside {0,1} are not supported by this "
                                + "draft (Monkhorst-Pack shift only).",
                        null);
            }
        }
        Integer nbnd = null;
        quantumforge.input.namelist.QENamelist system =
                input.getNamelist(QEInput.NAMELIST_SYSTEM);
        if (system != null) {
            QEValue value = system.getValue("nbnd");
            if (value != null) {
                try {
                    int n = value.getIntegerValue();
                    if (n > 0) {
                        nbnd = Integer.valueOf(n);
                    }
                } catch (RuntimeException ex) {
                    nbnd = null; // a malformed nbnd stays REQUIRED-EDIT, not guessed
                }
            }
        }
        long total = 1L;
        for (int i = 0; i < 3; i++) {
            total *= grid[i];
        }
        boolean generate = total <= MAX_GENERATED_KPOINTS;
        StringBuilder draft = new StringBuilder();
        draft.append("! Wannier90 .win DRAFT - QuantumForge Roadmap #69\n");
        draft.append("! Every REQUIRED-EDIT is a physics choice that the live pw.x input\n");
        draft.append("! does NOT determine; nothing was fabricated.\n");
        if (nbnd != null) {
            draft.append(String.format(Locale.ROOT,
                    "num_bands = %d            ! echoed from the live input's nbnd%n",
                    nbnd.intValue()));
        } else {
            draft.append("! num_bands = ...        ! REQUIRED-EDIT: set explicitly (QE\n");
            draft.append("!                        default band count is not echoed - it\n");
            draft.append("!                        varies with the setup)\n");
        }
        draft.append("! num_wann = ...         ! REQUIRED-EDIT: number of Wannier\n");
        draft.append("!                        functions - equals your projection set\n");
        draft.append(String.format(Locale.ROOT,
                "mp_grid = %d %d %d          ! echoed verbatim; the nscf for\n",
                grid[0], grid[1], grid[2]));
        draft.append("!                        pw2wannier90.x MUST use this same mesh\n");
        draft.append("! begin projections      ! REQUIRED-EDIT: one Wannier projection per\n");
        draft.append("!   target function (e.g. 'Si:sp3', 'A:l=2'); a PHYSICS choice tied\n");
        draft.append("!   to valence character - it cannot be guessed from the input\n");
        draft.append("! end projections\n");
        draft.append("! dis_win_min / dis_win_max / dis_froz_min / dis_froz_max\n");
        draft.append("!   REQUIRED-EDIT for entangled bands: energy windows come from a\n");
        draft.append("!   completed band-structure review (the BANDS_DATA kind), not from\n");
        draft.append("!   defaults. Omit only for the isolated-band case with a comment\n");
        draft.append("!   saying so.\n");
        draft.append("! num_iter 200           ! OPTIONAL: iteration budget is not\n");
        draft.append("!                        convergence - the WANNIER90_SPREAD kind\n");
        draft.append("!                        reviews the spread trajectory after the run\n");
        draft.append("! nosym=.true./noinv on the nscf: pw2wannier90.x rejects\n");
        draft.append("! symmetry-reduced sets - keep the full uniform mesh\n");
        if (generate) {
            draft.append("begin kpoints\n");
            draft.append(String.format(Locale.ROOT,
                    "! generated from the %dx%dx%d grid, %s, crystal coordinates at %d "
                            + "decimals (<= 1e-%d absolute grid error convention)%n",
                    grid[0], grid[1], grid[2],
                    "offset " + offset[0] + offset[1] + offset[2], KPOINT_DECIMALS,
                    KPOINT_DECIMALS));
            double weight = 1.0 / (double) total;
            for (int i = 0; i < grid[0]; i++) {
                for (int j = 0; j < grid[1]; j++) {
                    for (int k = 0; k < grid[2]; k++) {
                        double x = offset[0] == 1 ? (2.0 * i + 1.0) / (2.0 * grid[0])
                                : i / (double) grid[0];
                        double y = offset[1] == 1 ? (2.0 * j + 1.0) / (2.0 * grid[1])
                                : j / (double) grid[1];
                        double z = offset[2] == 1 ? (2.0 * k + 1.0) / (2.0 * grid[2])
                                : k / (double) grid[2];
                        draft.append(String.format(Locale.ROOT, "%.10f %.10f %.10f  %.10f%n",
                                x, y, z, weight));
                    }
                }
            }
            draft.append("end kpoints\n");
        } else {
            draft.append("! begin kpoints ... REQUIRED-EDIT: the explicit block is not\n");
            draft.append("! generated beyond " + MAX_GENERATED_KPOINTS + " points (count "
                    + total + "); draft the mesh separately.\n");
            draft.append("! end kpoints\n");
        }
        return OperationResult.success("W90_DRAFT_OK", "Draft built.",
                new WinDraft(grid, offset, nbnd, generate, draft.toString()));
    }
}
