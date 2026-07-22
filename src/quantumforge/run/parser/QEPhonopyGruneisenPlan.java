/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import java.util.ArrayList;
import java.util.List;

import quantumforge.operation.OperationResult;

/**
 * Builder for {@code phonopy-gruneisen} command lines - the three-volume
 * mode-Grüneisen workflow. Pinned read-only against upstream
 * (github.com/phonopy/phonopy commit
 * 3a3e0f099da5de2556e75d72ea89b3bb22c8e97e):
 *
 * <ul>
 *   <li>doc/gruneisen.md "How to run": THREE phonon calculations - one at
 *       the equilibrium volume, one slightly LARGER, one slightly SMALLER,
 *       with the unit cells "fully relaxed under the constraint of each
 *       volume" (verbatim doc requirement); files POSCAR-unitcell (or the
 *       calculator's own cell file under an interface option) + FORCE_SETS
 *       (or FORCE_CONSTANTS with {@code --readfc}) + optionally BORN, stored
 *       in three directories like {@code equiv / plus / minus};</li>
 *   <li>doc/gruneisen.md command shape:
 *       {@code phonopy-gruneisen orig plus minus --dim="2 2 2"
 *       --pa="..." --band="..." -p -c POSCAR-unitcell} (band mode) and the
 *       {@code --mesh="20 20 20"} (mesh mode) + {@code --color="RB"} form;
 *       <b>{@code --factor} was deprecated at v2.44</b> (doc verbatim) and
 *       {@code --band="auto"} does NOT work (doc verbatim);</li>
 *   <li>the CLI surface of phonopy_gruneisen_script.py get_options:
 *       positional {@code dir_orig dir_plus dir_minus}, {@code --band},
 *       {@code --mp/--mesh}, {@code --dim}, {@code --pa/--primitive_axis},
 *       {@code --band_points}, {@code --readfc}, {@code --nac}, {@code --color},
 *       {@code --cutoff}, {@code -c/--cell}, {@code -p}, {@code -s},
 *       {@code --nomeshsym}, calculator flags via add_arguments_of_calculators
 *       (so {@code --qe} exists for the Quantum ESPRESSO interface);</li>
 *   <li>the physics statement (doc verbatim math, in words):
 *       gamma(q,nu) = -V/(2*omega^2) * &lt;e|dD/dV|e&gt; approximated by the
 *       finite difference Delta D / Delta V; gamma may diverge around Gamma
 *       (the script itself skips the exact Gamma value on plots).</li>
 * </ul>
 *
 * <p>What is NOT here, deliberately: QuantumForge does not run phonopy and
 * never claims to - this is a PREVIEW of the exact command lines, mirroring
 * the QEPhonopyPlan doctrine. For QE users the per-volume FORCE_SETS come
 * from the batch-168 flow per volume directory ({@code phonopy-init --qe
 * ...} + pw.x + {@code phonopy-init -f}); the three-volume relaxation is
 * performed by the user in QE (vc-relax under fixed volume or manual volume
 * series) - that requirement is doc-verbatim and stated in every plan.</p>
 */
public final class QEPhonopyGruneisenPlan {

    private QEPhonopyGruneisenPlan() {
        // Utility
    }

    /** Three-directory + mode options request. */
    public static final class Request {
        private String origDir = "equiv";
        private String plusDir = "plus";
        private String minusDir = "minus";
        private String cellFilename = "pw.in";
        private final List<int[]> dim = new ArrayList<>(1);
        private String primitiveAxis;
        private boolean qeInterface = true; // the mandate is QE-first
        private boolean readfc;
        private boolean nac;
        private boolean bandMode = true;
        private final List<String[]> bandVertices = new ArrayList<>();
        private int bandPoints = 51;
        private int[] mesh = {20, 20, 20};
        private String colorScheme = "RB";

        /** Directory holding the EQUILIBRIUM volume run (doc: 'orig'/'equiv'). */
        public Request origDir(String dir) { this.origDir = dir; return this; }

        /** Directory holding the slightly LARGER volume run. */
        public Request plusDir(String dir) { this.plusDir = dir; return this; }

        /** Directory holding the slightly SMALLER volume run. */
        public Request minusDir(String dir) { this.minusDir = dir; return this; }

        /** Cell file name INSIDE each directory (doc: POSCAR-unitcell; pw.in under --qe). */
        public Request cellFilename(String name) { this.cellFilename = name; return this; }

        /** Supercell dimension (3 or 9 ints), same shape as QEPhonopyPlan. */
        public Request supercellDim(int... values) {
            this.dim.clear();
            if (values != null) {
                this.dim.add(values.clone());
            }
            return this;
        }

        /** Primitive axis string for --pa (optional, e.g. '0 1/2 1/2 ...'). */
        public Request primitiveAxis(String pa) { this.primitiveAxis = pa; return this; }

        /** Emit --qe (calculator interface option, doc's own Abinit form mirrored). */
        public Request qeInterface(boolean qe) { this.qeInterface = qe; return this; }

        /** Consume FORCE_CONSTANTS instead of FORCE_SETS (--readfc). */
        public Request readfc(boolean readfc) { this.readfc = readfc; return this; }

        /** NAC via per-directory BORN files (--nac exists on this script). */
        public Request nac(boolean enable) { this.nac = enable; return this; }

        /** Band-path mode with the SAME vertex grammar as QEPhonopyPlan. */
        public Request bandVertex(String x, String y, String z) {
            this.bandVertices.add(new String[] {x, y, z});
            return this;
        }

        public Request bandPoints(int points) { this.bandPoints = points; return this; }

        /** Mesh mode (overrides band mode when called). */
        public Request mesh(int mx, int my, int mz) {
            this.bandMode = false;
            this.mesh = new int[] {mx, my, mz};
            return this;
        }

        /** --color scheme for the mesh/band plot ('RB', 'RG', 'RGB'). */
        public Request colorScheme(String scheme) { this.colorScheme = scheme; return this; }
    }

    /** The preview product: command lines + warnings + doctrine notes. */
    public static final class Plan {
        private final List<String> perVolumeCommands;
        private final List<String> gruneisenCommands;
        private final List<String> warnings;
        private final List<String> notes;

        private Plan(List<String> perVolumeCommands, List<String> gruneisenCommands,
                List<String> warnings, List<String> notes) {
            this.perVolumeCommands = perVolumeCommands;
            this.gruneisenCommands = gruneisenCommands;
            this.warnings = warnings;
            this.notes = notes;
        }

        /** Per-volume prep commands (relax requirement + batch-168 flow per dir). */
        public List<String> getPerVolumeCommands() { return this.perVolumeCommands; }

        /** The final phonopy-gruneisen command line(s). */
        public List<String> getGruneisenCommands() { return this.gruneisenCommands; }

        public List<String> getWarnings() { return this.warnings; }

        public List<String> getNotes() { return this.notes; }
    }

    /** Builds the command preview, or an enumerated INPUT refusal. */
    public static OperationResult<Plan> build(Request request) {
        if (request == null) {
            return OperationResult.failed("PHONOPY_GRUNEISEN_PLAN_INPUT",
                    "No gruneisen plan request.", null);
        }
        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        // ---- directories ----
        String[] dirs = {request.origDir, request.plusDir, request.minusDir};
        for (int i = 0; i < 3; i++) {
            String d = dirs[i] == null ? "" : dirs[i].trim();
            if (d.isEmpty() || d.contains("\"") || d.contains(" ")) {
                issues.add("directory " + (i == 0 ? "orig" : i == 1 ? "plus" : "minus")
                        + " must be one shell-token without quotes/spaces (given: '"
                        + d + "')");
            }
            dirs[i] = d;
        }
        if (dirs[0].equals(dirs[1]) || dirs[0].equals(dirs[2])
                || dirs[1].equals(dirs[2])) {
            issues.add("the three directories must differ - gruneisen needs THREE"
                    + " distinct volumes (equilibrium / larger / smaller), not one"
                    + " reused run");
        }

        // ---- cell file ----
        String cell = request.cellFilename == null ? "" : request.cellFilename.trim();
        if (cell.isEmpty() || cell.contains("\"") || cell.contains(" ")) {
            issues.add("cell filename must be one shell-token without quotes/spaces"
                    + " (given: '" + cell + "')");
        }

        // ---- DIM ----
        int[] dimValues = request.dim.isEmpty() ? null : request.dim.get(0);
        String dimText = null;
        if (dimValues == null) {
            issues.add("DIM missing: reuse the 3-int or 9-int shape used for the"
                    + " per-volume displacement runs");
        } else if (dimValues.length == 3 || dimValues.length == 9) {
            if (dimValues.length == 3) {
                for (int d : dimValues) {
                    if (d < 1) {
                        issues.add("DIM diagonal entries must be >= 1 (found " + d
                                + ")");
                    }
                }
            }
            StringBuilder flat = new StringBuilder();
            for (int i = 0; i < dimValues.length; i++) {
                if (i > 0) {
                    flat.append(' ');
                }
                flat.append(dimValues[i]);
            }
            dimText = flat.toString();
        } else {
            issues.add("DIM accepts 3 or 9 integers (found " + dimValues.length + ")");
        }

        // ---- band vs mesh ----
        List<String[]> vertices = new ArrayList<>();
        if (request.bandMode) {
            if (request.bandVertices.size() < 2) {
                issues.add("band mode needs at least TWO vertices; for a mesh plot"
                        + " call mesh(mx,my,mz) instead (doc: '--band=\"auto\" doesn't'"
                        + " work, so a path must be given explicitly)");
            }
            for (int v = 0; v < request.bandVertices.size(); v++) {
                String[] vertex = request.bandVertices.get(v);
                for (int c = 0; c < 3; c++) {
                    String token = vertex[c] == null ? "" : vertex[c].trim();
                    if (!QEPhonopyPlan.validQToken(token)) {
                        issues.add("vertex " + (v + 1) + " coordinate " + (c + 1)
                                + " ('" + token + "') is not an int, a decimal, or a"
                                + " fraction a/b");
                    }
                }
                vertices.add(new String[] {vertex[0].trim(), vertex[1].trim(),
                        vertex[2].trim()});
            }
            if (request.bandPoints <= 1) {
                issues.add("--band_points must be > 1 (upstream script's own"
                        + " constraint shape; default 51 on that script)");
            }
        } else {
            for (int m : request.mesh) {
                if (m < 1) {
                    issues.add("mesh entries must be >= 1 (found " + m + ")");
                }
            }
        }
        if (request.colorScheme != null) {
            String cs = request.colorScheme.trim();
            if (!cs.matches("R|G|B|RB|RG|GB|RGB")) {
                warnings.add("--color '" + cs + "' is not one of the doc-documented"
                        + " schemes (RB / RG / RGB); emitted as given");
            }
        }

        if (!issues.isEmpty()) {
            return OperationResult.failed("PHONOPY_GRUNEISEN_PLAN_INPUT",
                    "gruneisen plan refused (" + issues.size() + " issue(s)): "
                            + String.join(" | ", issues), null);
        }

        // ---- per-volume commands (doc doctrine + batch-168 QE flow) ----
        List<String> perVolume = new ArrayList<>();
        perVolume.add("# doc/gruneisen.md verbatim requirement: THREE phonon"
                + " calculations at equilibrium / slightly larger / slightly"
                + " smaller volume, each cell fully relaxed UNDER THE CONSTRAINT of"
                + " its volume (a vc-relax at fixed volume or a manual a0 series in"
                + " QE - QuantumForge previews, it does not relax)");
        String[] tags = {"equilibrium", "larger", "smaller"};
        for (int i = 0; i < 3; i++) {
            perVolume.add("# [" + dirs[i] + "] " + tags[i] + " volume: run the"
                    + " batch-168 QE flow INSIDE this directory:");
            perVolume.add("cd " + dirs[i] + " && phonopy-init --qe -d --dim=\""
                    + dimText + "\" -c " + cell + "    # supercells");
            perVolume.add("cd " + dirs[i] + " && pw.x -i disp-001.in |& tee"
                    + " disp-001.out   # ... for EVERY supercell");
            perVolume.add("cd " + dirs[i] + " && phonopy-init -f disp-*.out"
                    + "    # FORCE_SETS for the " + tags[i] + " volume"
                    + (request.readfc ? " (or phonopy --writefc for FORCE_CONSTANTS"
                            + " consumed with --readfc)" : ""));
        }

        // ---- final command ----
        List<String> commands = new ArrayList<>();
        StringBuilder cmd = new StringBuilder("phonopy-gruneisen ")
                .append(dirs[0]).append(' ').append(dirs[1]).append(' ').append(dirs[2]);
        cmd.append(" --dim=\"").append(dimText).append('\"');
        if (request.primitiveAxis != null && !request.primitiveAxis.trim().isEmpty()) {
            cmd.append(" --pa=\"").append(request.primitiveAxis.trim()).append('\"');
            notes.add("--pa emitted verbatim: '" + request.primitiveAxis.trim()
                    + "' (doc: '--pa=\"auto\"' works, explicit string always accepted)");
        }
        if (request.bandMode) {
            StringBuilder flat = new StringBuilder();
            for (String[] vertex : vertices) {
                if (flat.length() > 0) {
                    flat.append("  ");
                }
                flat.append(vertex[0]).append(' ').append(vertex[1]).append(' ')
                        .append(vertex[2]);
            }
            cmd.append(" --band=\"").append(flat).append('\"');
            cmd.append(" --band_points ").append(request.bandPoints);
            notes.add("band mode: the script connects neighboring q-points in each"
                    + " segment considering phonon symmetry (band crossings handled),"
                    + " so frequencies in gruneisen.yaml may NOT be ordered - verbatim"
                    + " doc statement");
        } else {
            cmd.append(" --mesh=\"").append(request.mesh[0]).append(' ')
                    .append(request.mesh[1]).append(' ').append(request.mesh[2])
                    .append('\"');
        }
        if (request.qeInterface) {
            cmd.append(" --qe");
            notes.add("--qe interfaces the run to Quantum ESPRESSO (the doc's"
                    + " interface-option family: '--abinit, ...' - unit conversion"
                    + " factor is then chosen appropriately BY phonopy)");
        }
        cmd.append(" -c ").append(cell);
        if (request.readfc) {
            cmd.append(" --readfc");
        }
        if (request.nac) {
            cmd.append(" --nac");
            notes.add("--nac on phonopy-gruneisen reads EACH directory's BORN file"
                    + " (the NaCl-gruneisen example ships BORN in all three dirs)");
        }
        cmd.append(" --color=\"").append(request.colorScheme == null
                ? "RB" : request.colorScheme.trim()).append('\"');
        cmd.append(" -p -s");
        commands.add(cmd.toString() + "    # writes "
                + (request.bandMode ? "gruneisen.yaml" : "gruneisen_mesh.yaml")
                + " (-s) and plots (-p)");
        commands.add("cat " + (request.bandMode ? "gruneisen.yaml" : "gruneisen_mesh.yaml")
                + " | head -40    # then open it in QuantumForge's phonopy studio"
                + " (WATCH tab reloads it live while the script runs)");

        notes.add("gamma(q,nu) = -V/(2*omega^2) * <e|dD/dV|e>, approximated by the"
                + " finite difference Delta D / Delta V over the three volumes"
                + " (doc/gruneisen.md verbatim definition); gamma may DIVERGE around"
                + " Gamma - the upstream script itself skips the exact Gamma value"
                + " on plots and uses the neighboring q-points");
        notes.add("the '--factor' option was deprecated at v2.44 (doc verbatim) -"
                + " this builder never emits it; unit conversion belongs to phonopy");
        notes.add("everything above is a PREVIEW: QuantumForge does not bundle"
                + " phonopy-gruneisen and never claims it ran - paste the lines or"
                + " hand them to your own job file");

        return OperationResult.success("PHONOPY_GRUNEISEN_PLAN_OK",
                "per-volume prep (" + perVolume.size() + " lines) + "
                        + commands.size() + " gruneisen command line(s)"
                        + (warnings.isEmpty() ? "" : "; " + warnings.size()
                                + " warning(s) stated"),
                new Plan(List.copyOf(perVolume), List.copyOf(commands),
                        List.copyOf(warnings), List.copyOf(notes)));
    }

    /** The Si-shaped doc example preset mirrored to QE defaults. */
    public static Request siBandPreset() {
        return new Request()
                .origDir("orig").plusDir("plus").minusDir("minus")
                .supercellDim(2, 2, 2)
                .primitiveAxis("0 1/2 1/2 1/2 0 1/2 1/2 1/2 0")
                .bandVertex("1/2", "1/4", "3/4")
                .bandVertex("0", "0", "0")
                .bandVertex("1/2", "1/2", "1/2")
                .bandVertex("1/2", "0.0", "1/2")
                .bandPoints(51);
    }
}
