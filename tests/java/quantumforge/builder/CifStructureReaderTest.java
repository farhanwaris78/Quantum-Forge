/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.builder.CifStructureReader.CifStructure;
import quantumforge.operation.OperationResult;

class CifStructureReaderTest {

    private static final String RUTILE = """
            data_rutile_TiO2
            _cell_length_a 4.593
            _cell_length_b 4.593
            _cell_length_c 2.959
            _cell_angle_alpha 90
            _cell_angle_beta 90
            _cell_angle_gamma 90
            _chemical_formula_sum 'Ti2 O4'
            _symmetry_space_group_name_H-M 'P 42/m n m'
            # counted comment lines are skipped, nothing hidden
            loop_
            _symmetry_equiv_pos_site_id
            _symmetry_equiv_pos_as_xyz
            1 'x, y, z'
            2 '-x, -y, -z'
            loop_
            _atom_site_label
            _atom_site_type_symbol
            _atom_site_fract_x
            _atom_site_fract_y
            _atom_site_fract_z
            Ti1 Ti 0.0 0.0 0.0
            O1 O 0.305(2) 0.305(2) 0.0
            O2 O 0.5 0.0 0.5
            """;

    @Test
    void rutileBlockReviewsWithExactSubsetCounts() {
        OperationResult<CifStructure> result = CifStructureReader.parseText(RUTILE);
        assertTrue(result.isSuccess(), result.toString());
        CifStructure structure = result.getValue().orElseThrow();
        assertEquals("rutile_TiO2", structure.getBlockName());
        assertEquals("Ti2 O4", structure.getChemicalFormula(),
                "verbatim formula, quotes stripped by the tokenizer");
        assertEquals("P 42/m n m", structure.getSpaceGroupName());
        assertTrue(structure.hasCell());
        assertEquals(62.422025391, structure.cellVolume(), 1e-9);
        assertEquals(3, structure.getAtoms().size());
        assertEquals(2, structure.getSymmetryOpRows(), "ops counted but NOT applied");
        assertEquals(1, structure.getUncertaintyStripCount(),
                "0.305(2) counted as stripped, never propagated");
        assertEquals(0.305, structure.getAtoms().get(1).getFx(), 1e-12);
        assertTrue(structure.getAtoms().get(1).isUncertaintyStripped());
        assertEquals(0, structure.getAnonymousCount());

        java.util.Map<String, Integer> composition = structure.elementCounts();
        assertEquals(2, composition.size());
        assertEquals(1, composition.get("Ti").intValue());
        assertEquals(2, composition.get("O").intValue());
    }

    @Test
    void labelOnlyLoopsStayAnonymousAndDisorderIsCounted() {
        String labelOnly = """
                data_partial
                _cell_length_a 10.0
                _cell_length_b 10.0
                _cell_length_c 10.0
                _cell_angle_alpha 90
                _cell_angle_beta 90
                _cell_angle_gamma 90
                loop_
                _atom_site_label
                _atom_site_fract_x
                _atom_site_fract_y
                _atom_site_fract_z
                _atom_site_occupancy
                FE1 0.0 0.0 0.0 0.5
                C1 0.25 0.25 0.25 1.0
                M1 1.25 0.0 0.0 1.0
                """;
        CifStructure structure = CifStructureReader.parseText(labelOnly)
                .getValue().orElseThrow();
        assertEquals(3, structure.getAnonymousCount(),
                "label-only loops NEVER guess elements (FE1/C1/M1 stay anonymous)");
        assertTrue(structure.elementCounts().isEmpty());
        assertEquals(1, structure.getPartialOccupancyCount(),
                "0.5 occupancy counted as unresolved disorder");
        assertEquals(1, structure.getOutOfCellCount(), "x=1.25 outside [0,1] counted, "
                + "not wrapped");
        assertEquals(0.5, structure.getAtoms().get(0).getOccupancy(), 1e-15);
        assertNull(structure.getAtoms().get(0).getElement());
        assertEquals(1000.0, structure.cellVolume(), 1e-9);
    }

    @Test
    void triclinicVolumeUsesTheGeneralMetric() {
        String triclinic = """
                data_tri
                _cell_length_a 4.0
                _cell_length_b 5.0
                _cell_length_c 6.0
                _cell_angle_alpha 70
                _cell_angle_beta 80
                _cell_angle_gamma 100
                loop_
                _atom_site_label
                _atom_site_type_symbol
                _atom_site_fract_x
                _atom_site_fract_y
                _atom_site_fract_z
                X1 Xx 0.0 0.0 0.0
                """;
        CifStructure structure = CifStructureReader.parseText(triclinic)
                .getValue().orElseThrow();
        assertEquals(107.47127269939244, structure.cellVolume(), 1e-9);
    }

    @Test
    void failuresAreCodesNotSilence() {
        String multi = "data_one\n_cell_length_a 5\n_cell_length_b 5\n_cell_length_c 5\n"
                + "data_two\n_cell_length_a 5\n";
        assertEquals("CIF_MULTIBLOCK", CifStructureReader.parseText(multi).getCode());

        String noData = "_cell_length_a 5\n_cell_length_b 5\n_cell_length_c 5\n";
        assertEquals("CIF_SYNTAX", CifStructureReader.parseText(noData).getCode());

        String badCoord = "data_x\nloop_\n_atom_site_label\n_atom_site_fract_x\n"
                + "_atom_site_fract_y\n_atom_site_fract_z\nX1 0.1 abc 0.3\n";
        assertEquals("CIF_VALUE", CifStructureReader.parseText(badCoord).getCode());

        String shortRow = "data_x\nloop_\n_atom_site_fract_x\n_atom_site_fract_y\n"
                + "_atom_site_fract_z\n0.1 0.2\n";
        assertEquals("CIF_SYNTAX", CifStructureReader.parseText(shortRow).getCode());

        String partialCell = "data_x\n_cell_length_a 5\nloop_\n_atom_site_fract_x\n"
                + "_atom_site_fract_y\n_atom_site_fract_z\n0.0 0.0 0.0\n";
        assertEquals("CIF_CELL", CifStructureReader.parseText(partialCell).getCode());

        String noAtoms = "data_x\n_cell_length_a 5\n_cell_length_b 5\n_cell_length_c 5\n"
                + "_cell_angle_alpha 90\n_cell_angle_beta 90\n_cell_angle_gamma 90\n";
        assertEquals("CIF_EMPTY", CifStructureReader.parseText(noAtoms).getCode());

        String frameOpen = "data_x\n; text frame never closed\n";
        assertEquals("CIF_SYNTAX", CifStructureReader.parseText(frameOpen).getCode());

        assertEquals("CIF_EMPTY", CifStructureReader.parseText("  \n").getCode());
    }

    @Test
    void textFramesAndOxidationSuffixesBehave() {
        String framed = """
                data_frame
                _cell_length_a 5.0
                _cell_length_b 5.0
                _cell_length_c 5.0
                _cell_angle_alpha 90
                _cell_angle_beta 90
                _cell_angle_gamma 90
                _publ_section_abstract
                ; this is a long abstract block
                quoted lines and _cell_length_a fake tags inside are content,
                not data
                ;
                loop_
                _atom_site_label
                _atom_site_type_symbol
                _atom_site_fract_x
                _atom_site_fract_y
                _atom_site_fract_z
                O1 O2- 0.0 0.0 0.0
                Fe1 Fe3+ 0.5 0.5 0.5
                """;
        CifStructure structure = CifStructureReader.parseText(framed)
                .getValue().orElseThrow();
        assertEquals(2, structure.getAtoms().size(),
                "the text frame is skipped, not parsed");
        assertEquals("O", structure.getAtoms().get(0).getElement(),
                "oxidation suffix stripped by the type-token rule");
        assertEquals("Fe", structure.getAtoms().get(1).getElement());
        assertEquals(125.0, structure.cellVolume(), 1e-9);
    }

    @Test
    void tokenizerIsQuoteAware() {
        java.util.List<String> tokens = CifStructureReader.tokenize(
                "_symmetry_space_group_name_H-M 'P 42/m n m' # trailing comment", 1);
        assertEquals(2, tokens.size());
        assertEquals("_symmetry_space_group_name_H-M", tokens.get(0));
        assertEquals("P 42/m n m", tokens.get(1));
    }
}
