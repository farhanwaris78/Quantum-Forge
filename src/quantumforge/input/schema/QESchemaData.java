/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
// GENERATED FILE - DO NOT EDIT BY HAND.
// Generated 2026-07-21 by scripts/qe_schema_miner.py from Quantum
// ESPRESSO ground-truth grammar files (tags qe-7.2 .. qe-7.6). Regenerate
// with: python3 scripts/qe_schema_miner.py --qe-src <dir-of-tagged-grammar>
// The ground-truth files are QA inputs only and are NOT vendored into
// this repository (Quantum ESPRESSO is GPL): only metadata FACTS are
// extracted (keyword names, namelist membership, declared types,
// declared defaults, REQUIRED flags, documented option literals, the
// exact literals each program's own Fortran SELECT CASE validation
// accepts - mined per version because the accepted set drifts - and
// per-version presence). No documentation prose is copied; descriptions
// stay at QE's own online docs, which QENamelistSchema links per program.
// Source fingerprints (sha256 over the exact bytes mined):
//   qe-7.2  INPUT_PW.def           1d2d91921a16f382621db8b5f1e64fb2964a75f91b9da11a31c3950f3578d465
//   qe-7.2  input_parameters.f90   31f78ffb68f6f9c637e2bf2fc0bb95e712a14e73e3e6a16e38768082944ef30c
//   qe-7.2  pw_input.f90           0d466916dc490d1af28c368c73d9ccf96fe32e41f5843fcdb86e40094d4dfe1c
//   qe-7.2  set_occupations.f90    0e7784e2faa16d39820e2760bb28a56174b75c6f3e58f6f3ee01493290ffa9cc
//   qe-7.2  INPUT_PH.def           580e41eb7bd73ab3a7497d8d0973976d1963cbad48ac83d1c2461a3eb657cd05
//   qe-7.2  phq_readin.f90         5f2f0a7b1920fc9f7b5806f0f9cf3eb33fd80d48175fd074c915665b345761c6
//   qe-7.2  INPUT_HP.def           21923d5758f39f65eb34a5800c78f1521b02c2bd3e44b664bbc0e2cfff616a73
//   qe-7.2  hp_readin.f90          c46a0f3d7c873cd46eab427ebb1f24ab9880bc407ddef9de828ee2767007fc19
//   qe-7.3  INPUT_PW.def           0298b07e3e4be88ad95943e0ecba374e0e86eff25829644f384f8f9c2960d9f5
//   qe-7.3  input_parameters.f90   23f029eaddf354760e73eac6464f906da489605b6e54902786b24c71504e879a
//   qe-7.3  pw_input.f90           a43687d532ad5f724cf574e7225a01befb4366e684ac1c60da0eeef2da6971f4
//   qe-7.3  set_occupations.f90    feb6505fd556b89d64f103b7709de83136d4c3277e594480a9790283235ce985
//   qe-7.3  INPUT_PH.def           9a9af9f4e50487aca8c89a6c888cca5ad4def1d93a364cbe254ebb1bd749f33e
//   qe-7.3  phq_readin.f90         90e41aad70e45d5b219d5b3a09afb1e40ef54afdd004643245ceed85e130c78b
//   qe-7.3  INPUT_HP.def           21923d5758f39f65eb34a5800c78f1521b02c2bd3e44b664bbc0e2cfff616a73
//   qe-7.3  hp_readin.f90          6d8e1fec3bf4422537f29c279bd8ed45a23cd915e90d1bbfd6474be82fd196ce
//   qe-7.4  INPUT_PW.def           b4b994fbfd262841a39bc5af2569238dfd4444fb9c0f50c783ef966ede8f24e0
//   qe-7.4  input_parameters.f90   84993fa1afa7b7ec4c761005a4ee1ccdd02f1a20232551ee9c097ccfdbe822cc
//   qe-7.4  pw_input.f90           31fd9a935a27fb833dd80f0a276589355690fe8b1800245a55eaee07ad7f0494
//   qe-7.4  set_occupations.f90    feb6505fd556b89d64f103b7709de83136d4c3277e594480a9790283235ce985
//   qe-7.4  INPUT_PH.def           9a9af9f4e50487aca8c89a6c888cca5ad4def1d93a364cbe254ebb1bd749f33e
//   qe-7.4  phq_readin.f90         9c128f2068fce76b0b992094122db0bd3ae60f63d6cf01af631ad683694da13f
//   qe-7.4  INPUT_HP.def           21923d5758f39f65eb34a5800c78f1521b02c2bd3e44b664bbc0e2cfff616a73
//   qe-7.4  hp_readin.f90          6d8e1fec3bf4422537f29c279bd8ed45a23cd915e90d1bbfd6474be82fd196ce
//   qe-7.5  INPUT_PW.def           3652c7782541781b436d77a14ff6626dca97d64c0381d75e792cd677abf8a1b7
//   qe-7.5  input_parameters.f90   0a871234d0983b2203c1e3550a1711cdf43cbc2145ce3d4e6f3285f814336f56
//   qe-7.5  pw_input.f90           bbfe23ac73ccd363812dfd1fb2d6b1784db7e2d430d7a18df177108d3ea4d85a
//   qe-7.5  set_occupations.f90    feb6505fd556b89d64f103b7709de83136d4c3277e594480a9790283235ce985
//   qe-7.5  INPUT_PH.def           347037924168d8015366b2daef7ee68f0223d6929252b2cd51cfa62deb5be7ed
//   qe-7.5  phq_readin.f90         97da3763391063708a37e31857d8b2b1ca1aaeeca4a048cb65849c8c5dc145e2
//   qe-7.5  INPUT_HP.def           3dbe914a8dc69dd8a8d0b3481f168ecaf11dae3deadf918b2b25f6c2cb3fc7e7
//   qe-7.5  hp_readin.f90          5cf684636273d8f7b9b26b910ded70f695a5eb02e88547c7661a29442bc9547a
//   qe-7.6  INPUT_PW.def           2c8416bc529df0110e6dd134ca9f0ee1c1ced93dadab76fd2cbf6fda492859b9
//   qe-7.6  input_parameters.f90   5def120503a296463c1ea5889a6802bd88a6ea29a4ea7b0f1d7bdb130552d543
//   qe-7.6  pw_input.f90           b42a145c2e0c4d5877093ae5f4191a3d765cc4755771e1229bf470c4f803a7d5
//   qe-7.6  set_occupations.f90    feb6505fd556b89d64f103b7709de83136d4c3277e594480a9790283235ce985
//   qe-7.6  INPUT_PH.def           4ef5e4bb4bb3fda616cc5ffc07629a2c5e6aff20311ad93c6949b2de60c752a4
//   qe-7.6  phq_readin.f90         d54dc14d273f77622d1ea33444557819dba1178e58747f035d15df4cf867bf88
//   qe-7.6  INPUT_HP.def           1f9a9d9756626204f477c8b5aecaf25f0a437e3f4b10be777169c8d408c495aa
//   qe-7.6  hp_readin.f90          5cf684636273d8f7b9b26b910ded70f695a5eb02e88547c7661a29442bc9547a
package quantumforge.input.schema;

import java.util.ArrayList;
import java.util.List;

/** Union metadata table mined from QE grammar: see QENamelistSchema. */
final class QESchemaData {

    private QESchemaData() {
    }

    static List<QENamelistSchema.Entry> buildEntries() {
        List<QENamelistSchema.Entry> entries = new ArrayList<>();
        entries.add(QENamelistSchema.entry("pw", "CELL", "cell_damping", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CELL", "cell_dofree", QENamelistSchema.Type.CHARACTER, null, false, "'all'", 0x1F, "all,ibrav,a,b,c,fixa,fixb,fixc,x,y,z,xy,xz,yz,xyz,shape,volume,2Dxy,2Dshape,epitaxial_ab,epitaxial_ac,epitaxial_bc", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CELL", "cell_dynamics", QENamelistSchema.Type.CHARACTER, null, false, "[7.2-7.5: (none); 7.6: 'none']", 0x1F, "none,sd,damp-pr,damp-w,bfgs,pr,w", "none,damp-pr,damp-w,bfgs,ipi,pr,w", ""));
        entries.add(QENamelistSchema.entry("pw", "CELL", "cell_factor", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 2.0 for variable-cell calculations, 1.0 otherwise; 7.6: case -test {variable-cell @ref calculation} { 2.0 } case { 1.0 }]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CELL", "cell_nstepe", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CELL", "cell_parameters", QENamelistSchema.Type.CHARACTER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CELL", "cell_temperature", QENamelistSchema.Type.CHARACTER, null, false, null, 0x1F, "", "nose~0x18,not_controlled~0x18,not-controlled~0x18,not controlled~0x18", ""));
        entries.add(QENamelistSchema.entry("pw", "CELL", "cell_velocities", QENamelistSchema.Type.CHARACTER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CELL", "fnoseh", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CELL", "greash", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CELL", "press", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0.D0; 7.6: 0.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CELL", "press_conv_thr", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0.5D0 Kbar; 7.6: 0.5]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CELL", "temph", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CELL", "treinit_gvecs", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CELL", "wmass", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0.75*Tot_Mass/pi**2 for Parrinello-Rahman MD; 0.75*Tot_Mass/pi**2/Omega**(2/3) for Wentzcovitch MD; 7.6: internal]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "calculation", QENamelistSchema.Type.CHARACTER, null, false, "'scf'", 0x1F, "scf,nscf,bands,relax,md,vc-relax,vc-md", "scf,ensemble,nscf,bands,relax,md,vc-relax,vc-md", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "dipfield", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "disk_io", QENamelistSchema.Type.CHARACTER, null, false, "[7.2-7.5: see below; 7.6: 'default']", 0x1F, "high,medium,low,nowf,none", "", "high,medium,low,nowf,none,minimal~0x1E"));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "dt", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 20.D0; 7.6: 20.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "ekin_conv_thr", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "etot_conv_thr", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 1.0D-4; 7.6: 0.0001]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "forc_conv_thr", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 1.0D-3; 7.6: 0.001]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "gate", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "gdir", QENamelistSchema.Type.INTEGER, null, false, "[7.2-7.5: (none); 7.6: 0]", 0x1F, "0,1,2,3", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "input_xml_schema_file", QENamelistSchema.Type.CHARACTER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "iprint", QENamelistSchema.Type.INTEGER, null, false, "[7.2-7.5: write only at convergence; 7.6: 100000]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "isave", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "lberry", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "lecrpa", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "lelfield", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "lfcp", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "lkpoint_dir", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: (none); 7.6: .true.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "lorbm", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "max_seconds", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 1.D+7, or 150 days, i.e. no time limit; 7.6: 10000000.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "memory", QENamelistSchema.Type.CHARACTER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "nberrycyc", QENamelistSchema.Type.INTEGER, null, false, "1", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "ndr", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "ndw", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "nppstr", QENamelistSchema.Type.INTEGER, null, false, "[7.2-7.5: (none); 7.6: 0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "nstep", QENamelistSchema.Type.INTEGER, null, false, "[7.2-7.5: 1 if @ref calculation == 'scf', 'nscf', 'bands'; 50 for the other cases; 7.6: case -test {@ref calculation=='scf' || @ref calculation=='nscf' || @ref calculation=='bands'} { 1 } case { 50 }]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "outdir", QENamelistSchema.Type.CHARACTER, null, false, "[7.2-7.5: value of the ESPRESSO_TMPDIR environment variable if set; current directory ('./') otherwise; 7.6: case -test {ESPRESSO_TMPDIR is set} { from_environment } case { ./ }]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "point_label_type", QENamelistSchema.Type.CHARACTER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "prefix", QENamelistSchema.Type.CHARACTER, null, false, "'pwscf'", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "pseudo_dir", QENamelistSchema.Type.CHARACTER, null, false, "[7.2-7.5: value of the $ESPRESSO_PSEUDO environment variable if set; '$HOME/espresso/pseudo/' otherwise; 7.6: case -test {ESPRESSO_PSEUDO is set} { from_environment } case { $HOME/espresso/pseudo/ }]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "refg", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "restart_mode", QENamelistSchema.Type.CHARACTER, null, false, "'from_scratch'", 0x1F, "from_scratch,restart", "from_scratch,restart", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "saverho", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "symmetry_with_labels", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1C, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "tabps", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "tefield", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "tefield2", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "title", QENamelistSchema.Type.CHARACTER, null, false, "' '", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "tprnfor", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: (none); 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "tqmmm", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "trism", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "tstress", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "twochem", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "use_spinflip", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1C, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "use_wannier", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "vdw_table_name", QENamelistSchema.Type.CHARACTER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "verbosity", QENamelistSchema.Type.CHARACTER, null, false, "[7.2-7.5: 'low'; 7.6: 'default']", 0x1F, "high,low", "", "debug,high,medium,low,default,minimal"));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "wf_collect", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: (none); 7.6: .true.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "CONTROL", "wfcdir", QENamelistSchema.Type.CHARACTER, null, false, "[7.2-7.5: same as @ref outdir; 7.6: see @ref outdir]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "adaptive_thr", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "ampre", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "conv_thr", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 1.D-6; 7.6: 1e-06]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "conv_thr_init", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 1.D-3; 7.6: 0.001]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "conv_thr_multi", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 1.D-1; 7.6: 0.1]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "diago_cg_maxiter", QENamelistSchema.Type.INTEGER, null, false, "[7.2-7.5: (none); 7.6: 20]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "diago_david_ndim", QENamelistSchema.Type.INTEGER, null, false, "2", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "diago_full_acc", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "diago_gs_nblock", QENamelistSchema.Type.INTEGER, null, false, "16", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "diago_ppcg_maxiter", QENamelistSchema.Type.INTEGER, null, false, null, 0x07, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "diago_rmm_conv", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "diago_rmm_ndim", QENamelistSchema.Type.INTEGER, null, false, "4", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "diago_thr_init", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: (none); 7.6: 0.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "diagonalization", QENamelistSchema.Type.CHARACTER, null, false, "'david'", 0x1F, "david,cg", "david,davidson,cg,ppcg,paro,rmm,rmm-diis,rmm-davidson,rmm-paro,PPCG~0x18,ParO~0x18,direct~0x10", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "diis_achmix", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "diis_chguess", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "diis_delt", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "diis_ethr", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "diis_fthr", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "diis_g0chmix", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "diis_g1chmix", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "diis_hcut", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "diis_maxstep", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "diis_nchmix", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "diis_nreset", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "diis_nrot", QENamelistSchema.Type.INTEGER, "3", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "diis_rot", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "diis_rothr", QENamelistSchema.Type.REAL, "3", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "diis_size", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "diis_temp", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "diis_wthr", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "dt_emin", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "efield", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0.D0; 7.6: 0.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "efield2", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "efield_cart", QENamelistSchema.Type.REAL, "3", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "efield_phase", QENamelistSchema.Type.CHARACTER, null, false, "'none'", 0x1F, "read,write,none", "none,write,read", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "ekincw", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "electron_damping", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "electron_damping_emin", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "electron_dynamics", QENamelistSchema.Type.CHARACTER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "electron_maxstep", QENamelistSchema.Type.INTEGER, null, false, "100", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "electron_temperature", QENamelistSchema.Type.CHARACTER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "electron_velocities", QENamelistSchema.Type.CHARACTER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "emass", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "emass_cutoff", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "emass_cutoff_emin", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "emass_emin", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "epol", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "epol2", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "etresh", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "exx_maxstep", QENamelistSchema.Type.INTEGER, null, false, "100", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "fermi_energy", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "fnosee", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "grease", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "lambda_cold", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "maxiter", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "mixing_beta", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0.7D0; 7.6: 0.7]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "mixing_fixed_ns", QENamelistSchema.Type.INTEGER, null, false, "0", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "mixing_mode", QENamelistSchema.Type.CHARACTER, null, false, "'plain'", 0x1F, "plain,TF,local-TF", "plain,TF,local-TF,potential", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "mixing_ndim", QENamelistSchema.Type.INTEGER, null, false, "8", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "n_inner", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "niter_cg_restart", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "niter_cold_restart", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "occmass", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "occupation_constraints", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "occupation_damping", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "occupation_dynamics", QENamelistSchema.Type.CHARACTER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "ortho_eps", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "ortho_max", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "orthogonalization", QENamelistSchema.Type.CHARACTER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "passop", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "pre_state", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "real_space", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "rotation_damping", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "rotation_dynamics", QENamelistSchema.Type.CHARACTER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "rotmass", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "scf_must_converge", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .TRUE.; 7.6: .true.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "startingpot", QENamelistSchema.Type.CHARACTER, null, false, "[7.2-7.5: (none); 7.6: 'atomic']", 0x1F, "atomic,file", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "startingwfc", QENamelistSchema.Type.CHARACTER, null, false, "'atomic+random'", 0x1F, "atomic,atomic+random,random,file", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "tbeta_smoothing", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "tcg", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "tcpbo", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "tq_smoothing", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "ELECTRONS", "tqr", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "FCP", "fcp_conv_thr", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 1.D-2; 7.6: 0.01]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "FCP", "fcp_delta_t", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: @ref delta_t; 7.6: see @ref delta_t]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "FCP", "fcp_dynamics", QENamelistSchema.Type.CHARACTER, null, false, "[7.2-7.5: (none); 7.6: 'none']", 0x1F, "bfgs,newton,damp,lm,velocity-verlet,verlet", "", ""));
        entries.add(QENamelistSchema.entry("pw", "FCP", "fcp_mass", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 5.D+6 / (xy area) for ESM only; 5.D+4 / (xy area) for ESM-RISM; 7.6: case -test {@ref esm_bc=='bc2' || @ref esm_bc=='bc3'} { 5.D+6 / xy_area } case { 5.D+4 / xy_area }]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "FCP", "fcp_mu", QENamelistSchema.Type.REAL, null, true, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "FCP", "fcp_ndiis", QENamelistSchema.Type.INTEGER, null, false, "4", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "FCP", "fcp_nraise", QENamelistSchema.Type.INTEGER, null, false, "[7.2-7.5: @ref nraise; 7.6: see @ref nraise]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "FCP", "fcp_rdiis", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "FCP", "fcp_temperature", QENamelistSchema.Type.CHARACTER, null, false, "[7.2-7.5: @ref ion_temperature; 7.6: see @ref ion_temperature]", 0x1F, "rescaling,rescale-v,rescale-T,reduce-T,berendsen,andersen,initial,not_controlled", "", ""));
        entries.add(QENamelistSchema.entry("pw", "FCP", "fcp_tempw", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: @ref tempw; 7.6: see @ref tempw]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "FCP", "fcp_tolp", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: @ref tolp; 7.6: see @ref tolp]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "FCP", "fcp_velocity", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: determined by @ref fcp_temperature; 7.6: internal]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "FCP", "freeze_all_atoms", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "amprp", QENamelistSchema.Type.REAL, "nsx", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "bfgs_ndim", QENamelistSchema.Type.INTEGER, null, false, "1", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "delta_t", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 1.D0; 7.6: 1.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "fire_alpha_init", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0.2D0; 7.6: 0.2]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "fire_dtmax", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 10.D0; 7.6: 10.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "fire_f_dec", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0.5D0; 7.6: 0.5]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "fire_f_inc", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 1.1D0; 7.6: 1.1]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "fire_falpha", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0.99D0; 7.6: 0.99]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "fire_nmin", QENamelistSchema.Type.INTEGER, null, false, "5", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "fnhscl", QENamelistSchema.Type.REAL, "nsx", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "fnosep", QENamelistSchema.Type.REAL, "nhclm", false, "[7.2-7.4: (none); 7.5: 1.D0; 7.6: 1.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "greasp", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "iesr", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "ion_damping", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "ion_dynamics", QENamelistSchema.Type.CHARACTER, null, false, "[7.2-7.5: (none); 7.6: 'none']", 0x1F, "bfgs,damp,fire,verlet,langevin,langevin-smc,beeman", "bfgs,damp,fire,ipi,verlet,langevin,langevin-smc,langevin+smc,velocity-verlet~0x18", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "ion_maxstep", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "ion_nstepe", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "ion_positions", QENamelistSchema.Type.CHARACTER, null, false, "'default'", 0x1F, "default,from_input", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "ion_radius", QENamelistSchema.Type.REAL, "nsx", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "ion_temperature", QENamelistSchema.Type.CHARACTER, null, false, "'not_controlled'", 0x1F, "rescaling,rescale-v,rescale-T,reduce-T,berendsen,andersen,svr,initial,not_controlled", "not_controlled,not-controlled,not controlled,initial,rescaling,rescale-v,rescale-V,rescale_v,rescale_V,reduce-T,reduce-t,reduce_T,reduce_t,rescale-T,rescale-t,rescale_T,rescale_t,berendsen, Berendsen,svr,Svr,SVR,andersen,Andersen,nose~0x18", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "ion_velocities", QENamelistSchema.Type.CHARACTER, null, false, "'default'", 0x1F, "default,from_input", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "ndega", QENamelistSchema.Type.INTEGER, null, false, "[7.2-7.4: (none); 7.5-7.6: 0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "nhgrp", QENamelistSchema.Type.INTEGER, "nsx", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "nhpcl", QENamelistSchema.Type.INTEGER, null, false, "[7.2-7.4: (none); 7.5: 1; 7.6: 0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "nhptyp", QENamelistSchema.Type.INTEGER, null, false, "[7.2-7.4: (none); 7.5-7.6: 0]", 0x1F, "0,1,2,3", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "nraise", QENamelistSchema.Type.INTEGER, null, false, "1", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "pot_extrapolation", QENamelistSchema.Type.CHARACTER, null, false, "'atomic'", 0x1F, "none,atomic,first_order,second_order", "", "from_wfcs,from-wfcs,none,first_order,first-order,first order,second_order,second-order,second order"));
        entries.add(QENamelistSchema.entry("pw", "IONS", "refold_pos", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "remove_rigid_rot", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "tempw", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 300.D0; 7.6: 300.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "tgdiis_step", QENamelistSchema.Type.LOGICAL, null, false, ".true.", 0x18, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "tolp", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 100.D0; 7.6: 100.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "tranp", QENamelistSchema.Type.LOGICAL, "nsx", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "trust_radius_ini", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0.5D0; 7.6: 0.5]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "trust_radius_max", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0.8D0; 7.6: 0.8]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "trust_radius_min", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 1.D-3; 7.6: 0.0001]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "upscale", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 100.D0; 7.6: 100.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "w_1", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0.01D0; 7.6: 0.01]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "w_2", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0.5D0; 7.6: 0.5]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "IONS", "wfc_extrapolation", QENamelistSchema.Type.CHARACTER, null, false, "'none'", 0x1F, "none,first_order,second_order", "", "first_order,first-order,first order,second_order,second-order,second order"));
        entries.add(QENamelistSchema.entry("pw", "PRESS_AI", "abisur", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "PRESS_AI", "abivol", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "PRESS_AI", "axis", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "PRESS_AI", "cntr", QENamelistSchema.Type.LOGICAL, "nsx", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "PRESS_AI", "delta_eps", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "PRESS_AI", "delta_sigma", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "PRESS_AI", "dthr", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "PRESS_AI", "h_j", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "PRESS_AI", "jellium", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "PRESS_AI", "n_cntr", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "PRESS_AI", "p_ext", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "PRESS_AI", "p_fin", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "PRESS_AI", "p_in", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "PRESS_AI", "pvar", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "PRESS_AI", "r_j", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "PRESS_AI", "rho_thr", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "PRESS_AI", "scale_at", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "PRESS_AI", "t_gauss", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "closure", QENamelistSchema.Type.CHARACTER, null, false, "'kh'", 0x1F, "kh,hnc", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "ecutsolv", QENamelistSchema.Type.REAL, null, false, "4 * @ref ecutwfc", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "laue_both_hands", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "laue_buffer_left", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 8.0 if @ref laue_expand_left > 0.0; -1.0 if @ref laue_expand_left <= 0.0; 7.6: case -test {@ref laue_expand_left > 0.0} { 8.0 } case { -1.0 }]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "laue_buffer_left_solu", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "laue_buffer_left_solv", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "laue_buffer_right", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 8.0 if @ref laue_expand_right > 0.0; -1.0 if @ref laue_expand_right <= 0.0; 7.6: case -test {@ref laue_expand_right > 0.0} { 8.0 } case { -1.0 }]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "laue_buffer_right_solu", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "laue_buffer_right_solv", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "laue_expand_left", QENamelistSchema.Type.REAL, null, false, "-1.0", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "laue_expand_right", QENamelistSchema.Type.REAL, null, false, "-1.0", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "laue_nfit", QENamelistSchema.Type.INTEGER, null, false, "4", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "laue_reference", QENamelistSchema.Type.CHARACTER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "laue_starting_left", QENamelistSchema.Type.REAL, null, false, "0.0", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "laue_starting_right", QENamelistSchema.Type.REAL, null, false, "0.0", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "laue_wall", QENamelistSchema.Type.CHARACTER, null, false, "'auto'", 0x1F, "none,auto,manual", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "laue_wall_epsilon", QENamelistSchema.Type.REAL, null, false, "0.1", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "laue_wall_lj6", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "laue_wall_rho", QENamelistSchema.Type.REAL, null, false, "0.01", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "laue_wall_sigma", QENamelistSchema.Type.REAL, null, false, "4.0", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "laue_wall_z", QENamelistSchema.Type.REAL, null, false, "0.0", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "mdiis1d_size", QENamelistSchema.Type.INTEGER, null, false, "20", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "mdiis1d_step", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0.5D0; 7.6: 0.5]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "mdiis3d_size", QENamelistSchema.Type.INTEGER, null, false, "10", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "mdiis3d_step", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0.8D0; 7.6: 0.8]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "nsolv", QENamelistSchema.Type.INTEGER, null, true, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "rism1d_bond_width", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: (none); 7.6: 0.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "rism1d_conv_thr", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 1.D-8; 7.6: 1e-08]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "rism1d_dielectric", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: -1.0D0; 7.6: -1.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "rism1d_maxstep", QENamelistSchema.Type.INTEGER, null, false, "50000", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "rism1d_molesize", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 2.0D0; 7.6: 2.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "rism1d_nproc", QENamelistSchema.Type.INTEGER, null, false, "128", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "rism1d_nproc_switch", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "rism3d_conv_level", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0.1 if @ref laue_both_hands == .FALSE. .AND. @ref lgcscf == .FALSE.; 0.3 if @ref laue_both_hands == .FALSE. .AND. @ref lgcscf == .TRUE.; 0.5 if @ref laue_both_hands == .TRUE.; 7.6: case -test {@ref laue_both_hands==.TRUE.} { 0.5 } case -test {@ref lgcscf==.TRUE.} { 0.3 } case { 0.1 }]", 0x1F, "0.0,0.0<x<1.0,1.0", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "rism3d_conv_thr", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 1.D-5 if @ref lgcscf == .FALSE.; 5.D-6 if @ref lgcscf == .TRUE.; 7.6: case -test {@ref lgcscf==.FALSE.} { 1e-05 } case { 5e-06 }]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "rism3d_maxstep", QENamelistSchema.Type.INTEGER, null, false, "5000", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "rism3d_planar_average", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: (none); 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "rmax1d", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "rmax_lj", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "smear1d", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 2.D0; 7.6: 2.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "smear3d", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 2.D0; 7.6: 2.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "solute_epsilon", QENamelistSchema.Type.REAL, "nsx", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "solute_lj", QENamelistSchema.Type.CHARACTER, "nsx", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "solute_sigma", QENamelistSchema.Type.REAL, "nsx", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "starting1d", QENamelistSchema.Type.CHARACTER, null, false, "[7.2-7.5: (none); 7.6: 'zero']", 0x1F, "zero,file,fix", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "starting3d", QENamelistSchema.Type.CHARACTER, null, false, "[7.2-7.5: (none); 7.6: 'zero']", 0x1F, "zero,file", "", ""));
        entries.add(QENamelistSchema.entry("pw", "RISM", "tempv", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 300.D0; 7.6: 300.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "A", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "a_pen", QENamelistSchema.Type.REAL, "10,nspinx", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "ace", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: true; 7.6: .true.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "alpha_pen", QENamelistSchema.Type.REAL, "10", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "angle1", QENamelistSchema.Type.REAL, "nsx", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "angle2", QENamelistSchema.Type.REAL, "nsx", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "assume_isolated", QENamelistSchema.Type.CHARACTER, null, false, "'none'", 0x1F, "none,esm,2D", "makov-payne,m-p,mp,martyna-tuckerman,m-t,mt,esm,2D,none", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "B", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "b_field", QENamelistSchema.Type.REAL, "3", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "block", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "block_1", QENamelistSchema.Type.REAL, null, false, "0.45", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "block_2", QENamelistSchema.Type.REAL, null, false, "0.55", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "block_height", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0.1; 7.6: 0.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "C", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "celldm", QENamelistSchema.Type.REAL, "6", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "constrained_magnetization", QENamelistSchema.Type.CHARACTER, null, false, "'none'", 0x1F, "none,total,atomic", "none,atomic,atomic direction,total,total direction", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "cosAB", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "cosAC", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "cosBC", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "degauss", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0.D0 Ry; 7.6: 0.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "degauss_cond", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0.D0 Ry; 7.6: 0.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "dftd3_threebody", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: TRUE; 7.6: .true.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "dftd3_version", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "dmft", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "dmft_prefix", QENamelistSchema.Type.CHARACTER, null, false, "[7.2-7.5: @ref prefix; 7.6: see @ref prefix]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "eamp", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0.001 a.u.; 7.6: 0.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "ecfixed", QENamelistSchema.Type.REAL, null, false, "0.0", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "ecutfock", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: ecutrho; 7.6: see @ref ecutrho]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "ecutrho", QENamelistSchema.Type.REAL, null, false, "4 * @ref ecutwfc", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "ecutvcut", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0.0 Ry; 7.6: 0.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "ecutwfc", QENamelistSchema.Type.REAL, null, true, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "edir", QENamelistSchema.Type.INTEGER, null, false, "[7.2-7.5: (none); 7.6: 1]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "emaxpos", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0.5D0; 7.6: 0.5]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "ensemble_energies", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "eopreg", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0.1D0; 7.6: 0.1]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "esm_a", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "esm_bc", QENamelistSchema.Type.CHARACTER, null, false, "'pbc'", 0x1F, "pbc,bc1,bc2,bc3", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "esm_debug", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "esm_debug_gpmax", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "esm_efield", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0.d0; 7.6: 0.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "esm_nfit", QENamelistSchema.Type.INTEGER, null, false, "4", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "esm_w", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0.d0; 7.6: 0.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "esm_zb", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "exx_fraction", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: it depends on the specified functional; 7.6: case { internal }]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "exx_type", QENamelistSchema.Type.CHARACTER, null, false, "'band_pairs'", 0x10, "bands,band_pairs", "bands~0x10,band_pairs~0x10", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "exxdiv_treatment", QENamelistSchema.Type.CHARACTER, null, false, "'gygi-baldereschi'", 0x1F, "gygi-baldereschi,vcut_spherical,vcut_ws,none", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "fixed_magnetization", QENamelistSchema.Type.REAL, "3", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "force_pairing", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "force_symmorphic", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "gcscf_beta", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0.05D0; 7.6: 0.05]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "gcscf_conv_thr", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 1.D-2; 7.6: 0.01]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "gcscf_gh", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "gcscf_gk", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "gcscf_ignore_mun", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "gcscf_mu", QENamelistSchema.Type.REAL, null, true, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "hub_pot_fix", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "hubbard_alpha", QENamelistSchema.Type.REAL, "nsx", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "hubbard_alpha_back", QENamelistSchema.Type.REAL, "nsx", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "hubbard_beta", QENamelistSchema.Type.REAL, "nsx", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "hubbard_j", QENamelistSchema.Type.REAL, "3,nsx", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "hubbard_j0", QENamelistSchema.Type.REAL, "nsx", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "hubbard_occ", QENamelistSchema.Type.REAL, "nsx,3", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "hubbard_parameters", QENamelistSchema.Type.CHARACTER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "hubbard_u", QENamelistSchema.Type.REAL, "nsx", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "hubbard_u_back", QENamelistSchema.Type.REAL, "nsx", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "hubbard_v", QENamelistSchema.Type.UNKNOWN, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "ibrav", QENamelistSchema.Type.INTEGER, null, true, null, 0x1F, "0,1,2,3,-3,4,5,-5,6,7,8,9,-9,91,10,11,12,-12,13,-13,14", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "input_dft", QENamelistSchema.Type.CHARACTER, null, false, "[7.2-7.5: read from pseudopotential files; 7.6: case { from_pseudopotential }]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "la2f", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "lambda", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 1.d0; 7.6: 1.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "lda_plus_u", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "lda_plus_u_kind", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "lforcet", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: (none); 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "lgcscf", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "localization_thr", QENamelistSchema.Type.REAL, null, false, "0.0", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "london", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "london_c6", QENamelistSchema.Type.REAL, "nsx", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "london_rcut", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 200; 7.6: 200.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "london_rvdw", QENamelistSchema.Type.REAL, "nsx", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "london_s6", QENamelistSchema.Type.REAL, null, false, "0.75", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "lspinorb", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: (none); 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "mbd_vdw", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "n_proj", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "nat", QENamelistSchema.Type.INTEGER, null, true, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "nbnd", QENamelistSchema.Type.INTEGER, null, false, "[7.2-7.5: for an insulator, @ref nbnd = number of valence bands (@ref nbnd = # of electrons /2); @br for a metal, 20% more (minimum 4 more); 7.6: internal]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "nbnd_cond", QENamelistSchema.Type.INTEGER, null, false, "[7.2-7.5: nbnd_cond = @ref nbnd - # of electrons / 2 in the collinear case; nbnd_cond = @ref nbnd - # of electrons in the noncollinear case.; 7.6: internal]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "nelec_cond", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0.D0; 7.6: 0.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "nextffield", QENamelistSchema.Type.INTEGER, null, false, "0", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "no_t_rev", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "noinv", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "noncolin", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "nosym", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "nosym_evc", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "nqx1", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "nqx2", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "nqx3", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "nr1", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "nr1b", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "nr1s", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "nr2", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "nr2b", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "nr2s", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "nr3", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "nr3b", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "nr3s", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "nscdm", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "nspin", QENamelistSchema.Type.INTEGER, null, false, "1", 0x1F, "1,2,4", "1,2,4", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "ntyp", QENamelistSchema.Type.INTEGER, null, true, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "occupations", QENamelistSchema.Type.CHARACTER, null, false, "[7.2-7.5: (none); 7.6: 'fixed']", 0x1F, "smearing,tetrahedra,tetrahedra_lin,tetrahedra_opt,fixed,from_input", "fixed,smearing,tetrahedra,tetrahedra_lin,tetrahedra-lin,tetrahedra_opt,tetrahedra-opt,from_input", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "one_atom_occupations", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "orbital_resolved", QENamelistSchema.Type.LOGICAL, null, false, null, 0x18, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "origin_choice", QENamelistSchema.Type.INTEGER, null, false, "1", 0x1F, "1,2", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "pol_type", QENamelistSchema.Type.CHARACTER, null, false, "[7.2-7.5: (none); 7.6: 'none']", 0x1F, "e,h", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "q2sigma", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0.1; 7.6: 0.01]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "qcutz", QENamelistSchema.Type.REAL, null, false, "0.0", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "ref_alat", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "relaxz", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "report", QENamelistSchema.Type.INTEGER, null, false, "-1", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "reserv", QENamelistSchema.Type.LOGICAL, "nsx", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "reserv_back", QENamelistSchema.Type.LOGICAL, "nsx", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "rhombohedral", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .TRUE.; 7.6: .true.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "scdm", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "scdmden", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "scdmgrd", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "sci_cb", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0; 7.6: 0.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "sci_vb", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0; 7.6: 0.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "screening_parameter", QENamelistSchema.Type.REAL, null, false, "0.106", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "sic", QENamelistSchema.Type.CHARACTER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "sic_alpha", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "sic_energy", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "sic_epsilon", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "sic_gamma", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0; 7.6: 0.0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "sigma_pen", QENamelistSchema.Type.REAL, "10", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "smearing", QENamelistSchema.Type.CHARACTER, null, false, "'gaussian'", 0x1F, "gaussian,methfessel-paxton,marzari-vanderbilt,fermi-dirac", "gaussian,gauss,Gaussian,Gauss,methfessel-paxton,m-p,mp,Methfessel-Paxton,M-P,MP,marzari-vanderbilt,cold,m-v,mv,Marzari-Vanderbilt,M-V,MV,fermi-dirac,f-d,fd,Fermi-Dirac,F-D,FD", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "space_group", QENamelistSchema.Type.INTEGER, null, false, "0", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "starting_charge", QENamelistSchema.Type.REAL, "nsx", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "starting_magnetization", QENamelistSchema.Type.REAL, "nsx", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "starting_ns_eigenvalue", QENamelistSchema.Type.REAL, "lqmax,nspinx,nsx", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "starting_spin_angle", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "step_pen", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "tot_charge", QENamelistSchema.Type.REAL, null, false, "0.0", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "tot_magnetization", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: -10000 [unspecified]; 7.6: -10000]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "ts_vdw", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "ts_vdw_econv_thr", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 1.D-6; 7.6: 1e-06]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "ts_vdw_isolated", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "u_projection_type", QENamelistSchema.Type.CHARACTER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "uniqueb", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "use_all_frac", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "vdw_corr", QENamelistSchema.Type.CHARACTER, null, false, "'none'", 0x1F, "none,grimme-d2,grimme-d3,tkatchenko-scheffler,many-body-dispersion,XDM", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "x_gamma_extrapolation", QENamelistSchema.Type.LOGICAL, null, false, ".true.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "xdm", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: .FALSE.; 7.6: .false.]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "xdm_a1", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 0.6836; 7.6: internal]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "xdm_a2", QENamelistSchema.Type.REAL, null, false, "[7.2-7.5: 1.5045; 7.6: internal]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "yukawa", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "SYSTEM", "zgate", QENamelistSchema.Type.REAL, null, false, "0.5", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "adapt", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "calwf", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "efx0", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "efx1", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "efy0", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "efy1", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "efz0", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "efz1", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "exx_dis_cutoff", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "exx_me_rcut_pair", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "exx_me_rcut_self", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "exx_neigh", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "exx_poisson_eps", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "exx_ps_rcut_pair", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "exx_ps_rcut_self", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "exx_use_cube_domain", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "maxwfdt", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "nit", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "nsd", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "nsteps", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "nwf", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "sw_len", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "tolw", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "vnbsp", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "wf_efield", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "wf_friction", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "wf_q", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "wf_switch", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "wfdt", QENamelistSchema.Type.REAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "wffort", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "wfsd", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER", "writev", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER_AC", "constrain_pot", QENamelistSchema.Type.REAL, "nwanx,2", false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER_AC", "nwan", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER_AC", "plot_wan_num", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER_AC", "plot_wan_spin", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER_AC", "plot_wannier", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER_AC", "print_wannier_coeff", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("pw", "WANNIER_AC", "use_energy_int", QENamelistSchema.Type.LOGICAL, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "ahc_dir", QENamelistSchema.Type.CHARACTER, null, false, "[7.2-7.5: outdir // 'ahc_dir/'; 7.6: @ref outdir // 'ahc_dir/']", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "ahc_nbnd", QENamelistSchema.Type.INTEGER, null, true, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "ahc_nbndskip", QENamelistSchema.Type.INTEGER, null, false, "0", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "alpha_mix", QENamelistSchema.Type.UNKNOWN, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "amass", QENamelistSchema.Type.UNKNOWN, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "asr", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "d2ns_type", QENamelistSchema.Type.UNKNOWN, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "dek", QENamelistSchema.Type.REAL, null, false, "1.0e-3", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "dftd3_hess", QENamelistSchema.Type.CHARACTER, null, false, "[7.2-7.5: 'prefix.hess'; 7.6: @ref prefix // '.hess']", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "diagonalization", QENamelistSchema.Type.CHARACTER, null, false, "'david'", 0x1F, "david,cg", "david,davidson,cg,direct~0x10", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "do_charge_neutral", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "do_long_range", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "drho_star", QENamelistSchema.Type.STRUCTURE, null, false, "[7.2-7.5: disabled; 7.6: internal]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "dvscf_star", QENamelistSchema.Type.STRUCTURE, null, false, "[7.2-7.5: disabled; 7.6: internal]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "el_ph_ngauss", QENamelistSchema.Type.UNKNOWN, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "el_ph_nsigma", QENamelistSchema.Type.INTEGER, null, false, "10", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "el_ph_sigma", QENamelistSchema.Type.REAL, null, false, "0.02", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "electron_phonon", QENamelistSchema.Type.CHARACTER, null, false, "' '", 0x1F, "simple,interpolated,lambda_tetra,gamma_tetra,epa,ahc", "", "simple,epa,Wannier,interpolated,yambo,dvscf,lambda_tetra,gamma_tetra,scdft_input,ahc,prt~0x1E"));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "elop", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "elph_nbnd_max", QENamelistSchema.Type.UNKNOWN, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "elph_nbnd_min", QENamelistSchema.Type.UNKNOWN, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "epsil", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "eth_ns", QENamelistSchema.Type.REAL, null, false, "1.0e-12", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "eth_rps", QENamelistSchema.Type.REAL, null, false, "1.0d-9", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "fildrho", QENamelistSchema.Type.CHARACTER, null, false, "[7.2-7.5: ' '; 7.6: case -test {@ref lraman .or. @ref elop .or. @ref drho_star%open .or. @ref lmultipole} { 'drho' } case { ' ' }]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "fildvscf", QENamelistSchema.Type.CHARACTER, null, false, "[7.2-7.5: ' '; 7.6: case -test {elph_mat .or. @ref dvscf_star%open .or. @ref lmultipole} { 'dvscf' } case { ' ' }]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "fildyn", QENamelistSchema.Type.CHARACTER, null, false, "'matdyn'", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "fpol", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "iverbosity", QENamelistSchema.Type.UNKNOWN, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "k1", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "k2", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "k3", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "kx", QENamelistSchema.Type.UNKNOWN, null, false, null, 0x1E, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "ky", QENamelistSchema.Type.UNKNOWN, null, false, null, 0x1E, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "kz", QENamelistSchema.Type.UNKNOWN, null, false, null, 0x1E, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "last_irr", QENamelistSchema.Type.INTEGER, null, false, "[7.2-7.5: 3*nat; 7.6: case -test {not specified} { 3*nat } case { -1000 }]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "last_q", QENamelistSchema.Type.INTEGER, null, false, "[7.2-7.5: number of q points; 7.6: case -test {not specified} { number of q points (nqs) } case { -1000 }]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "ldiag", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "ldisp", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "ldvscf_interpolate", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "lmultipole", QENamelistSchema.Type.LOGICAL, null, false, "[7.5: (none); 7.6: .false.]", 0x18, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "lnoloc", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "low_directory_check", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "lqdir", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "lraman", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "lrpa", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "lshift_q", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "max_seconds", QENamelistSchema.Type.REAL, null, false, "1.d7", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "modenum", QENamelistSchema.Type.INTEGER, null, false, "0", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "nat_todo", QENamelistSchema.Type.INTEGER, null, false, "[7.2-7.5: 0, i.e. displace all atoms; 7.6: 0]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "niter_ph", QENamelistSchema.Type.INTEGER, null, false, "[7.2-7.5: maxter=100; 7.6: maxter]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "nk1", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "nk2", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "nk3", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "nmix_ph", QENamelistSchema.Type.INTEGER, null, false, "4", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "nogg", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "nq1", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "nq2", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "nq3", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "only_init", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "only_wfc", QENamelistSchema.Type.UNKNOWN, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "outdir", QENamelistSchema.Type.CHARACTER, null, false, "[7.2-7.5: value of the @tt ESPRESSO_TMPDIR environment variable if set; @br current directory ('./') otherwise; 7.6: case -test {ESPRESSO_TMPDIR is set} { from_environment } case { './' }]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "prefix", QENamelistSchema.Type.CHARACTER, null, false, "'pwscf'", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "q2d", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "q_in_band_form", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "qplot", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "read_dns_bare", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "recover", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "reduce_io", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "search_sym", QENamelistSchema.Type.LOGICAL, null, false, ".true.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "skip_upper", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x18, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "skip_upperfan", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x07, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "start_irr", QENamelistSchema.Type.INTEGER, null, false, "1", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "start_q", QENamelistSchema.Type.INTEGER, null, false, "1", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "tr2_ph", QENamelistSchema.Type.REAL, null, false, "1e-12", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "trans", QENamelistSchema.Type.LOGICAL, null, false, ".true.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "verbosity", QENamelistSchema.Type.CHARACTER, null, false, "'default'", 0x1F, "high,low", "", "debug,high,medium,low,default,minimal"));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "wpot_dir", QENamelistSchema.Type.CHARACTER, null, false, "[7.2-7.5: outdir // 'w_pot/'; 7.6: @ref outdir // 'w_pot/']", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "zeu", QENamelistSchema.Type.LOGICAL, null, false, "[7.2-7.5: zeu=@ref epsil; 7.6: epsil]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("ph", "INPUTPH", "zue", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "alpha_mix", QENamelistSchema.Type.UNKNOWN, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "background", QENamelistSchema.Type.UNKNOWN, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "compute_hp", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "conv_thr_chi", QENamelistSchema.Type.REAL, null, false, "1.D-5", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "determine_num_pert_only", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "determine_q_mesh_only", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "disable_type_analysis", QENamelistSchema.Type.UNKNOWN, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "dist_thr", QENamelistSchema.Type.REAL, null, false, "6.D-4", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "docc_thr", QENamelistSchema.Type.REAL, null, false, "5.D-5", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "equiv_type", QENamelistSchema.Type.UNKNOWN, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "ethr_nscf", QENamelistSchema.Type.REAL, null, false, "1.D-11", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "find_atpert", QENamelistSchema.Type.INTEGER, null, false, "1", 0x1F, "1,2,3,4", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "iverbosity", QENamelistSchema.Type.INTEGER, null, false, "1", 0x1F, "1,2,3,4", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "last_q", QENamelistSchema.Type.INTEGER, null, false, "[7.2-7.5: number of q points; 7.6: internal]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "lmin", QENamelistSchema.Type.INTEGER, null, false, "2", 0x1F, "0,1,2,3", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "max_seconds", QENamelistSchema.Type.REAL, null, false, "1.d7", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "niter_max", QENamelistSchema.Type.INTEGER, null, false, "100", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "nmix", QENamelistSchema.Type.INTEGER, null, false, "4", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "no_metq0", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x18, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "nq1", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "nq2", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "nq3", QENamelistSchema.Type.INTEGER, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "num_neigh", QENamelistSchema.Type.INTEGER, null, false, "6", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "outdir", QENamelistSchema.Type.CHARACTER, null, false, "[7.2-7.5: value of the @tt ESPRESSO_TMPDIR environment variable if set; @br current directory ('./') otherwise; 7.6: case -test {ESPRESSO_TMPDIR is set} { from_environment } case { './' }]", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "perturb_only_atom", QENamelistSchema.Type.UNKNOWN, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "prefix", QENamelistSchema.Type.CHARACTER, null, false, "'pwscf'", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "rmax", QENamelistSchema.Type.REAL, null, false, "100.D0", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "skip_atom", QENamelistSchema.Type.UNKNOWN, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "skip_equivalence_q", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "skip_type", QENamelistSchema.Type.UNKNOWN, null, false, null, 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "start_q", QENamelistSchema.Type.INTEGER, null, false, "1", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "sum_pertq", QENamelistSchema.Type.LOGICAL, null, false, ".false.", 0x1F, "", "", ""));
        entries.add(QENamelistSchema.entry("hp", "INPUTHP", "thresh_init", QENamelistSchema.Type.REAL, null, false, "1.D-14", 0x1F, "", "", ""));
        return entries;
    }

    /** Mined entry count, pinned by QENamelistSchemaTest. */
    static final int ENTRY_COUNT = 572;
}
