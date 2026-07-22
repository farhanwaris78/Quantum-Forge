/* Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.input.schema;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Batch-173 pinned INCAR grammar DATA for {@link VaspIncarSchema}.
 *
 * <p>Every TIER1 row is transcribed from the vasp.at wiki raw wikitext
 * (MediaWiki API, 2026-07-22), one row per tag page:
 * {@code {{TAGDEF}}} supplies the type/options, {@code {{DEF}}} the default
 * text (conditional defaults rendered as 'X if COND | Y else'), and the
 * lead Description sentence the doc line. Empty default text means the wiki
 * pins NO default (e.g. IDIPOL, EFIELD) - nothing was invented. TIER2_NAMES
 * is the verbatim name list of the wiki's 'Category:INCAR tag' index, first
 * page (Alphabet A..ELPH_USEBLAS, i.e. the first 200 of 614 listed tags);
 * the index pages 2..4 exist (the audit states this window and does not
 * pretend the unlisted tail is unknown to VASP).</p>
 */
final class VaspIncarSchemaData {

    private VaspIncarSchemaData() { }

    /**
     * Columns: name, TagType, options '|'-separated (empty = unconstrained),
     * default text (verbatim/wikitext-shaped; empty = wiki pins none),
     * doc line, named unit (empty = none stated), family.
     */
    static final String[][] TIER1_ROWS = {
        // ----------------------------- electronic minimization
        {"ENCUT", "REAL", "", "largest ENMAX in the POTCAR file",
                "energy cutoff for the plane-wave basis set", "eV", "SCF"},
        {"EDIFF", "REAL", "", "10^{-4}",
                "global break condition for the electronic SC-loop", "eV", "SCF"},
        {"NELM", "INTEGER", "", "60",
                "maximum number of electronic self-consistency steps", "", "SCF"},
        {"NELMIN", "INTEGER", "", "2",
                "minimum number of electronic self-consistency steps", "", "SCF"},
        {"ISMEAR", "INTEGER", "-15|-14|-5|-4|-3|-2|-1|0|>0", "1",
                "how partial occupancies are set (>0 = Methfessel-Paxton order)",
                "", "OCCUPANCY"},
        {"SIGMA", "REAL", "", "0.2", "width of the smearing", "eV", "OCCUPANCY"},
        {"ALGO", "STRING",
                "Normal|Fast|VeryFast|Exact|Subrot|All|Conjugate|Damped|Chi"
                + "|Eigenval|None|Nothing", "Normal",
                "electronic-minimization algorithm and/or many-body method",
                "", "SCF"},
        {"PREC", "FLAGS", "Normal|Single|SingleN|Accurate|Low|Medium|High",
                "Medium for VASP.4.X | Normal since VASP.5.X",
                "precision mode (defaults for ENCUT, FFT grids, ROPT)", "", "SCF"},
        {"LREAL", "FLAGS", ".FALSE.|Auto|A|On|O|.TRUE.", ".FALSE.",
                "projection operators evaluated in real space vs reciprocal",
                "", "SCF"},
        {"ADDGRID", "LOGICAL", "", ".FALSE.",
                "additional (8x-denser) support grid for augmentation charges",
                "", "SCF"},
        {"LASPH", "LOGICAL", "", ".FALSE.",
                "non-spherical gradient contributions inside the PAW spheres",
                "", "SCF"},
        {"AMIX", "REAL", "",
                "0.8 if ISPIN=1 and US-PPs | 0.4 if ISPIN=2 and US-PPs"
                + " | 0.4 if PAW datasets",
                "linear mixing parameter", "", "MIXING"},
        {"BMIX", "REAL", "", "1.0",
                "cutoff wave vector for the Kerker mixing scheme", "", "MIXING"},
        // ----------------------------- restart / start density
        {"ISTART", "INTEGER", "0|1|2|3", "1 if a WAVECAR file exists | 0 else",
                "whether or not to read the WAVECAR file", "", "RESTART"},
        {"ICHARG", "INTEGER", "0|1|2|4|5|10|11|12",
                "2 if ISTART=0 | 0 else",
                "how VASP constructs the initial charge density"
                + " (+10 keeps it constant)", "", "RESTART"},
        {"ISYM", "INTEGER", "-1|0|1|2|3",
                "1 if VASP runs with USPPs | 3 if LHFCALC=.TRUE. | 2 else",
                "the way VASP treats symmetry", "", "SYMMETRY"},
        {"SYSTEM", "STRING", "", "unknown system",
                "title string, echoed to the OUTCAR file", "", "META"},
        {"NWRITE", "INTEGER", "0|1|2|3|4", "2",
                "how much will be written to the OUTCAR file (verbosity)", "",
                "META"},
        // ----------------------------- magnetism
        {"ISPIN", "INTEGER", "1|2", "1",
                "with or without spin polarization", "", "MAGNETISM"},
        {"MAGMOM", "REAL_ARRAY", "",
                "NIONS * 1.0 for ISPIN=2 | 3 * NIONS * 1.0 for noncollinear"
                + " (LNONCOLLINEAR=.TRUE.)",
                "initial on-site magnetic moment for each atom"
                + " ('N*x' repetition allowed)", "", "MAGNETISM"},
        {"NUPDOWN", "REAL", "", "not set (full relaxation; -1 is equivalent)",
                "fixed difference between up- and down-spin electrons", "",
                "MAGNETISM"},
        // ----------------------------- functionals
        {"GGA", "STRING", "",
                "the functional specified by LEXCH in the POTCAR"
                + " if METAGGA and XC are also not specified",
                "LDA or GGA exchange-correlation functional", "", "XC"},
        {"METAGGA", "STRING", "",
                "the functional specified by LEXCH in the POTCAR"
                + " if GGA and XC are also not specified",
                "meta-GGA exchange-correlation functional", "", "XC"},
        {"IVDW", "INTEGER", "", "0 (no correction)",
                "vdW dispersion term, atom-pairwise or many-body type", "", "XC"},
        // ----------------------------- DOS / projection / output files
        {"EMIN", "REAL", "", "lowest KS eigenvalue - Delta",
                "lower boundary of the DOS energy range", "eV", "DOS"},
        {"EMAX", "REAL", "", "highest KS eigenvalue + Delta",
                "upper boundary of the DOS energy range", "eV", "DOS"},
        {"NEDOS", "INTEGER", "", "301",
                "grid points for the electronic DOS and dielectric function",
                "", "DOS"},
        {"LORBIT", "INTEGER", "0|1|2|5|10|11|12", "0",
                "projection onto local quantum numbers (writes PROCAR/PROOUT)",
                "", "DOS"},
        {"LCHARG", "LOGICAL", "", ".True.",
                "whether the charge density (CHGCAR/CHG) is written", "",
                "OUTPUT"},
        {"LWAVE", "LOGICAL", "", ".TRUE.",
                "whether the wavefunctions (WAVECAR) are written", "", "OUTPUT"},
        // ----------------------------- ionic motion
        {"IBRION", "INTEGER", "-1|0|1|2|3|5|6|7|8|11|12|40|44",
                "-1 for NSW=-1 or 0 | 0 else",
                "how the crystal structure changes"
                + " (none/MD/relax/phonons/IRC/dimer/interactive)", "", "IONS"},
        {"ISIF", "INTEGER", "0|1|2|3|4|5|6|7|8",
                "0 for IBRION=0 (MD) or LHFCALC=.TRUE. (hybrids) | 2 else",
                "stress-tensor calculation and ionic degrees of freedom", "",
                "IONS"},
        {"NSW", "INTEGER", "", "0", "maximum number of ionic steps", "", "IONS"},
        {"EDIFFG", "REAL", "", "EDIFF x 10",
                "break condition for the ionic relaxation loop"
                + " (>0 energy change eV, <0 |force| eV/A, 0 stop after NSW)",
                "eV (or eV/A)", "IONS"},
        {"POTIM", "REAL", "",
                "none - MUST be set if IBRION=0 (MD) | 0.5 for IBRION=1,2,3"
                + " (and 5 up to VASP.4.6) | 0.015 for IBRION=5,6 (VASP.5.1+)",
                "time step in MD or step-width scaling in relaxation",
                "fs (MD)", "IONS"},
        // ----------------------------- DFT+U and hybrids
        {"LDAU", "LOGICAL", "", ".FALSE.", "switch on DFT+U", "", "DFT+U"},
        {"LDAUTYPE", "INTEGER", "1|2|4", "2",
                "the DFT+U variant (the page text also documents 3 ="
                + " Cococcioni linear response)", "", "DFT+U"},
        {"LDAUL", "INTEGER_ARRAY", "", "NTYP*2",
                "l-quantum number the on-site interaction is added to"
                + " (-1 = none), one per species", "", "DFT+U"},
        {"LDAUU", "REAL_ARRAY", "", "NTYP*0.0",
                "effective on-site Coulomb interaction, one per species", "eV",
                "DFT+U"},
        {"LDAUJ", "REAL_ARRAY", "", "NTYP*0.0",
                "effective on-site exchange interaction, one per species", "eV",
                "DFT+U"},
        {"LDAUPRINT", "INTEGER", "0|1", "0",
                "verbosity of a DFT+U calculation (onsite occupancy matrices)",
                "", "DFT+U"},
        {"LMAXMIX", "INTEGER", "", "2",
                "one-center PAW charge l-cutoff through the mixer and CHGCAR",
                "", "MIXING"},
        {"LHFCALC", "LOGICAL", "", ".FALSE.",
                "Hartree-Fock/DFT hybrid-functional-type calculation"
                + " (defaults to the PBE0 family)", "", "HYBRID"},
        {"HFSCREEN", "REAL", "", "0 (none)",
                "range-separation parameter (HSE03 = 0.3, HSE06 = 0.2)", "A^-1",
                "HYBRID"},
        {"AEXX", "REAL", "",
                "0.25 if LHFCALC=.TRUE. and LRHFCALC=.FALSE."
                + " | 1 if LRHFCALC=.TRUE. | 0 if LHFCALC=.FALSE.",
                "fraction of exact exchange in a hybrid calculation", "",
                "HYBRID"},
        // ----------------------------- parallelization
        {"KSPACING", "REAL", "", "0.5",
                "smallest k-point spacing for the auto mesh when KPOINTS"
                + " is absent (KGAMMA=T default)", "A^-1", "KPOINTS"},
        {"KPAR", "INTEGER", "", "1",
                "k-points treated in parallel (integer divisor of cores"
                + " recommended)", "", "PARALLEL"},
        {"NCORE", "INTEGER", "", "1",
                "MPI ranks collaborating on a single band"
                + " (modern; NPAR takes precedence if both set)", "", "PARALLEL"},
        {"NPAR", "INTEGER", "", "available ranks",
                "bands treated in parallel (legacy tag; use NCORE)", "",
                "PARALLEL"},
        // ----------------------------- electrostatics
        {"LDIPOL", "LOGICAL", "", ".FALSE.",
                "dipole corrections to the potential and forces"
                + " (charged cells / net-dipole slabs; IDIPOL required)",
                "", "DIPOLE"},
        {"IDIPOL", "INTEGER", "1|2|3|4", "",
                "direction (1-3) or all directions (4) for the"
                + " monopole/dipole/quadrupole energy corrections", "", "DIPOLE"},
        {"DIPOL", "REAL_ARRAY", "",
                "(unset: VASP determines the center from"
                + " the charge-density minimum)",
                "cell center (direct coordinates) for the dipole moment", "",
                "DIPOLE"},
        {"EFIELD", "REAL", "", "",
                "applied electric force field (electrons move ALONG it;"
                + " definition is opposite to the common one)", "eV/A", "DIPOLE"},
    };

    /**
     * Wiki 'Category:INCAR tag' index page-1 names (200 of 614), transcribed
     * verbatim (underscores stand for the wiki page-name spaces). Tier-1
     * names are a subset; the audit reports tier-2 as
     * 'recognized on the pinned window, value grammar not audited'.
     */
    static final Set<String> TIER2_NAMES = new LinkedHashSet<>(List.of(
            "ADDGRID", "AEXX", "AGGAC", "AGGAX", "ALDAC", "ALDAX", "ALGO",
            "ALPHA_VDW", "AMGGAC", "AMGGAX", "AMIN", "AMIX", "AMIX_MAG",
            "ANDERSEN_PROB", "ANTIRES", "APACO",
            "BANDGAP", "BEXT", "BEXX", "BMIX", "BMIX_MAG", "BPARAM",
            "BSEELECTRON", "BSEHOLE", "BSEPREC",
            "CH_AMPLIFICATION", "CH_LSPEC", "CH_NEDOS", "CH_SIGMA",
            "CHECKPOINT_FD", "CLL", "CLN", "CLNT", "CLZ", "CMBJ", "CMBJA",
            "CMBJB", "CMBJE", "CPARAM", "CSHIFT", "CUTOFF_MU", "CUTOFF_SIGMA",
            "CUTOFF_TYPE",
            "DEG_THRESHOLD", "DEPER", "DFTD4_MODEL", "DFTD4_XC", "DIMER_DIST",
            "DIPOL", "DQ",
            "EBREAK", "EDIFF", "EDIFFG", "EFERMI", "EFERMI_NEDOS", "EFIELD",
            "EFIELD_PEAD", "EFOR", "EINT",
            "ELPH_DECOMPOSE", "ELPH_DRIVER", "ELPH_FERMI_NEDOS",
            "ELPH_IGNORE_IMAG_PHONONS", "ELPH_ISMEAR", "ELPH_KSPACING",
            "ELPH_LR", "ELPH_MODE", "ELPH_NBANDS", "ELPH_NBANDS_SUM",
            "ELPH_POT_FFT_MESH", "ELPH_POT_GENERATE", "ELPH_POT_LATTICE",
            "ELPH_PREPARE", "ELPH_RUN", "ELPH_SCATTERING_APPROX",
            "ELPH_SELFEN_BAND_START", "ELPH_SELFEN_BAND_START_KP",
            "ELPH_SELFEN_BAND_STOP", "ELPH_SELFEN_BAND_STOP_KP",
            "ELPH_SELFEN_BROAD_TOL", "ELPH_SELFEN_CARRIER_DEN",
            "ELPH_SELFEN_CARRIER_DEN_RANGE", "ELPH_SELFEN_CARRIER_PER_CELL",
            "ELPH_SELFEN_DELTA", "ELPH_SELFEN_DW",
            "ELPH_SELFEN_ENERGY_WINDOW", "ELPH_SELFEN_FAN",
            "ELPH_SELFEN_G_SKIP", "ELPH_SELFEN_GAPS", "ELPH_SELFEN_IKPT",
            "ELPH_SELFEN_IMAG_SKIP", "ELPH_SELFEN_KPTS", "ELPH_SELFEN_MU",
            "ELPH_SELFEN_MU_RANGE", "ELPH_SELFEN_NW",
            "ELPH_SELFEN_STATIC", "ELPH_SELFEN_TEMPS",
            "ELPH_SELFEN_TEMPS_RANGE", "ELPH_SELFEN_WRANGE", "ELPH_TRANSPORT",
            "ELPH_TRANSPORT_DFERMI_TOL", "ELPH_TRANSPORT_DRIVER",
            "ELPH_TRANSPORT_EMAX", "ELPH_TRANSPORT_EMAX_PLOT",
            "ELPH_TRANSPORT_EMIN", "ELPH_TRANSPORT_EMIN_PLOT",
            "ELPH_TRANSPORT_NEDOS_PLOT", "ELPH_USEBLAS"));
}
