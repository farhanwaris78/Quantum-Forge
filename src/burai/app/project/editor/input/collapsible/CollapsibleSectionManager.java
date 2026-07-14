/*
 * Copyright (C) 2025 QuantumForge Team
 */
package burai.app.project.editor.input.collapsible;

import java.util.ArrayList;
import java.util.List;

/**
 * Collapsible sections and default value buttons for Input Editor.
 * 
 * NanoLabo's input editor provides:
 * - Collapsible sections (expandable/collapsible panels)
 * - Default value buttons (reset to recommended values)
 * - Visual hierarchy for complex settings
 * - Advanced Settings dialogs (gear icons)
 */
public class CollapsibleSectionManager {

    public static class Section {
        public final String id;
        public final String label;
        public final String parentId;
        public final boolean expandedByDefault;
        public final List<Section> children;
        public boolean expanded;

        public Section(String id, String label, String parent, boolean expanded) {
            this.id = id;
            this.label = label;
            this.parentId = parent;
            this.expandedByDefault = expanded;
            this.expanded = expanded;
            this.children = new ArrayList<>();
        }

        public void addChild(Section child) { if (child != null) this.children.add(child); }
        public void toggle() { this.expanded = !this.expanded; }
    }

    private List<Section> rootSections;

    public CollapsibleSectionManager() {
        this.rootSections = new ArrayList<>();
        this.initDefaultSections();
    }

    private void initDefaultSections() {
        // SCF section hierarchy
        Section scf = new Section("scf", "SCF Calculation", null, true);
        Section system = new Section("scf_system", "System", "scf", true);
        system.addChild(new Section("scf_cutoff", "Cutoff Energies", "scf_system", true));
        system.addChild(new Section("scf_smearing", "Smearing", "scf_system", true));
        system.addChild(new Section("scf_spin", "Spin Polarization", "scf_system", true));
        system.addChild(new Section("scf_hubbard", "DFT+U (Hubbard)", "scf_system", false));
        system.addChild(new Section("scf_vdw", "van der Waals", "scf_system", false));
        system.addChild(new Section("scf_hybrid", "Hybrid Functionals", "scf_system", false));
        system.addChild(new Section("scf_esm", "ESM Method", "scf_system", false));
        scf.addChild(system);

        Section electrons = new Section("scf_electrons", "Electrons", "scf", true);
        electrons.addChild(new Section("scf_conv", "Convergence", "scf_electrons", true));
        electrons.addChild(new Section("scf_mixing", "Mixing", "scf_electrons", true));
        electrons.addChild(new Section("scf_diag", "Diagonalization", "scf_electrons", false));
        scf.addChild(electrons);

        Section kpoints = new Section("scf_kpoints", "k-Points", "scf", true);
        kpoints.addChild(new Section("scf_kgrid", "k-Grid", "scf_kpoints", true));
        kpoints.addChild(new Section("scf_ktrick", "Γ-Trick", "scf_kpoints", false));
        scf.addChild(kpoints);

        rootSections.add(scf);

        // Geometry section
        Section geom = new Section("geom", "Geometry", null, true);
        geom.addChild(new Section("geom_cell", "Cell Parameters", "geom", true));
        geom.addChild(new Section("geom_atoms", "Atomic Positions", "geom", true));
        geom.addChild(new Section("geom_species", "Atomic Species", "geom", true));
        geom.addChild(new Section("geom_fix", "Fixed Atoms", "geom", false));
        rootSections.add(geom);

        // Advanced settings
        Section adv = new Section("advanced", "Advanced Settings", null, false);
        adv.addChild(new Section("adv_control", "CONTROL Namelist", "advanced", false));
        adv.addChild(new Section("adv_system", "SYSTEM Namelist", "advanced", false));
        rootSections.add(adv);
    }

    public List<Section> getRootSections() { return this.rootSections; }
}
