/*
 * Copyright (C) 2025-2026 QuantumForge Team
 */

package quantumforge.builder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Automatically compiles standard BibTeX academic citations for utilized engines,
 * pseudopotential families, and mathematical plugins, ensuring rigorous attribution
 * in materials publications (Roadmap #134).
 */
public final class QECitationManager {

    private static final Map<String, String> BIBTEX_DATABASE = new LinkedHashMap<>();
    static {
        BIBTEX_DATABASE.put("QUANTUM_ESPRESSO",
            "@article{giannozzi2009quantum,\n" +
            "  title={QUANTUM ESPRESSO: a modular and open-source software project for quantum simulations of materials},\n" +
            "  author={Giannozzi, Paolo and Baroni, Stefano and Bonini, Nicola and Calandra, Matteo and Car, Roberto and Cavazzoni, Carlo and Ceresoli, Davide and Chiarotti, Guido L and Cococcioni, Matteo and Dabo, Ismaila and others},\n" +
            "  journal={Journal of Physics: Condensed Matter},\n" +
            "  volume={21},\n" +
            "  number={39},\n" +
            "  pages={395502},\n" +
            "  year={2009},\n" +
            "  publisher={IOP Publishing}\n" +
            "}"
        );
        BIBTEX_DATABASE.put("QUANTUM_ESPRESSO_2017",
            "@article{giannozzi2017advanced,\n" +
            "  title={Advanced capabilities for materials modelling with Quantum ESPRESSO},\n" +
            "  author={Giannozzi, Paolo and Andreussi, Oliviero and Brumme, T and Bunau, O and Nardelli, M Buongiorno and Calandra, M and Car, R and Cavazzoni, C and Ceresoli, D and Cococcioni, M and others},\n" +
            "  journal={Journal of Physics: Condensed Matter},\n" +
            "  volume={29},\n" +
            "  number={46},\n" +
            "  pages={465901},\n" +
            "  year={2017},\n" +
            "  publisher={IOP Publishing}\n" +
            "}"
        );
        BIBTEX_DATABASE.put("THERMO_PW",
            "@article{dalcorso2016elastic,\n" +
            "  title={Elastic constants and thermodynamic properties from first principles: the thermo_pw density functional perturbation theory package},\n" +
            "  author={Dal Corso, Andrea},\n" +
            "  journal={Computer Physics Communications},\n" +
            "  volume={207},\n" +
            "  pages={128--135},\n" +
            "  year={2016},\n" +
            "  publisher={Elsevier}\n" +
            "}"
        );
        BIBTEX_DATABASE.put("PHONOPY",
            "@article{togo2015first,\n" +
            "  title={First principles phonon calculations in materials science},\n" +
            "  author={Togo, Atsushi and Tanaka, Isao},\n" +
            "  journal={Scripta Materialia},\n" +
            "  volume={108},\n" +
            "  pages={1--5},\n" +
            "  year={2015},\n" +
            "  publisher={Elsevier}\n" +
            "}"
        );
        BIBTEX_DATABASE.put("SPGLIB",
            "@misc{togo2018spglib,\n" +
            "  title={Spglib: a software library for crystal symmetry search},\n" +
            "  author={Togo, Atsushi},\n" +
            "  year={2018},\n" +
            "  note={https://github.com/spglib/spglib}\n" +
            "}"
        );
        BIBTEX_DATABASE.put("WANNIER90",
            "@article{pizzi2020wannier90,\n" +
            "  title={Wannier90 as a community code: new features and applications},\n" +
            "  author={Pizzi, Giovanni and Vitale, Valerio and Redies, Thibault and Behrends, Jan and Gerasimov, Edward and Geranton, Guillaume and Spillane, Marc and Lin, Lin and Mauri, Francesco and Pasquarello, Alfredo and others},\n" +
            "  journal={Journal of Physics: Condensed Matter},\n" +
            "  volume={32},\n" +
            "  number={16},\n" +
            "  pages={165902},\n" +
            "  year={2020},\n" +
            "  publisher={IOP Publishing}\n" +
            "}"
        );
        BIBTEX_DATABASE.put("SSSP",
            "@article{prandini2018precision,\n" +
            "  title={Precision and efficiency in solid-state pseudopotential calculations},\n" +
            "  author={Prandini, Gianluca and Marrazzo, Antimo and Castelli, Ivano E and Mounet, Nicolas and Marzari, Nicola},\n" +
            "  journal={npj Computational Materials},\n" +
            "  volume={4},\n" +
            "  number={1},\n" +
            "  pages={72},\n" +
            "  year={2018},\n" +
            "  publisher={Nature Publishing Group}\n" +
            "}"
        );
    }

    private final List<String> activeCitationKeys = new ArrayList<>();

    public QECitationManager() {
        // Default citations for core DFT calculations
        this.activeCitationKeys.add("QUANTUM_ESPRESSO");
        this.activeCitationKeys.add("QUANTUM_ESPRESSO_2017");
    }

    public List<String> getActiveCitationKeys() { return List.copyOf(this.activeCitationKeys); }

    /**
     * Automatically registers auxiliary tool citations based on active calculation styles.
     */
    public void registerFeatureCitations(boolean usesPhonons, boolean usesThermo, boolean usesWannier, boolean usesSSSP) {
        if (usesPhonons) {
            this.activeCitationKeys.add("PHONOPY");
            this.activeCitationKeys.add("SPGLIB");
        }
        if (usesThermo) {
            this.activeCitationKeys.add("THERMO_PW");
        }
        if (usesWannier) {
            this.activeCitationKeys.add("WANNIER90");
        }
        if (usesSSSP) {
            this.activeCitationKeys.add("SSSP");
        }
    }

    /**
     * Compiles and formats all active citations into a unified standard BibTeX (.bib) file structure.
     */
    public String compileBibTex() {
        StringBuilder sb = new StringBuilder();
        for (String key : this.activeCitationKeys) {
            String bib = BIBTEX_DATABASE.get(key);
            if (bib != null) {
                sb.append(bib).append("\n\n");
            }
        }
        return sb.toString();
    }

    /**
     * Writes the compiled BibTeX citations to disk.
     */
    public void writeBibTexFile(File file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("file is null");
        }

        String content = compileBibTex();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(content);
        }
    }
}
