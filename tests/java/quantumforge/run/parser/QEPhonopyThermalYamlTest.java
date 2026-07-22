/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.run.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;
import quantumforge.run.parser.QEPhonopyThermalYaml.ThermalRow;
import quantumforge.run.parser.QEPhonopyThermalYaml.ThermalYaml;

/**
 * Pins for {@link QEPhonopyThermalYaml}: the grammar is the upstream writer
 * {@code ThermalProperties._get_tp_yaml_lines} (phonopy commit 3a3e0f09),
 * including the in-file {@code unit:} block that the writer ALWAYS emits -
 * unit labels are read verbatim from the file, never hardcoded. The
 * 'energy = free_energy + entropy * T / 1000' identity is the writer's own
 * last-column formula and is pinned on the constructed rows.
 */
class QEPhonopyThermalYamlTest {

    private static final String HEADER =
            "# Thermal properties / unit cell (natom)\n"
            + "\n"
            + "unit:\n"
            + "  temperature:   K\n"
            + "  free_energy:   kJ/mol\n"
            + "  entropy:       J/K/mol\n"
            + "  heat_capacity: J/K/mol\n"
            + "\n"
            + "natom:   2    \n"
            + "cutoff_frequency: 0.00000\n"
            + "num_modes: 48002\n"
            + "num_integrated_modes: 48002\n"
            + "\n"
            + "zero_point_energy:      6.3456789\n"
            + "\n"
            + "thermal_properties:\n";

    private static final String ROW0 =
            "- temperature:         0.0000000\n"
            + "  free_energy:         6.3456789\n"
            + "  entropy:             0.0000000\n"
            + "  heat_capacity:       0.0000000\n"
            + "  energy:              6.3456789\n";

    private static final String ROW100 =
            "- temperature:       100.0000000\n"
            + "  free_energy:         5.9000000\n"
            + "  entropy:            12.3000000\n"
            + "  heat_capacity:      45.1000000\n"
            + "  energy:              7.1300000\n";

    @Test
    void testFullDocumentWithVerbatimUnits() {
        OperationResult<ThermalYaml> result = QEPhonopyThermalYaml.parseText(
                HEADER + ROW0 + ROW100, "thermal_properties.yaml");
        assertTrue(result.isSuccess(), () -> result.getMessage());
        assertEquals("PHONOPY_TPROP_OK", result.getCode());
        ThermalYaml yaml = result.getValue().orElseThrow();
        assertEquals(4, yaml.getUnitLabels().size());
        assertEquals("K", yaml.unitOf("temperature"));
        assertEquals("kJ/mol", yaml.unitOf("free_energy"));
        assertEquals("J/K/mol", yaml.unitOf("entropy"));
        assertEquals("J/K/mol", yaml.unitOf("heat_capacity"));
        assertNull(yaml.unitOf("energy"),
                "the writer gives no unit row for 'energy' - absent, not invented");
        assertEquals(Integer.valueOf(2), yaml.getNatom());
        assertEquals(Double.valueOf(0.0), yaml.getCutoffFrequency());
        assertEquals(Integer.valueOf(48002), yaml.getNumModes());
        assertEquals(Integer.valueOf(48002), yaml.getNumIntegratedModes());
        assertEquals(6.3456789, yaml.getZeroPointEnergy(), 1e-9);
        assertEquals(2, yaml.getRows().size());
        ThermalRow row = yaml.getRows().get(1);
        assertEquals(100.0, row.getTemperature(), 1e-9);
        assertEquals(5.9, row.getFreeEnergy(), 1e-9);
        assertEquals(12.3, row.getEntropy(), 1e-9);
        assertEquals(45.1, row.getHeatCapacity(), 1e-9);
        assertEquals(7.13, row.getEnergy(), 1e-9);
        assertEquals(row.getFreeEnergy() + row.getEntropy() * row.getTemperature()
                / 1000.0, row.getEnergy(), 1e-9,
                "the writer's own last-column identity, pinned");
        assertEquals(0, yaml.getPartialRowsHeld());
        assertTrue(yaml.getMolNote().contains("primitive cell"),
                "the doc's mol-convention caveat is carried, not hidden");
        assertTrue(QEPhonopyThermalYaml.chartSeries(yaml)[1][1].startsWith("12.30"));
    }

    @Test
    void testLivePartialEntryHeldBack() {
        // last entry has temperature + free_energy only: a live append
        String partial = HEADER + ROW0
                + "- temperature:       200.0000000\n"
                + "  free_energy:         5.1000000\n";
        OperationResult<ThermalYaml> result = QEPhonopyThermalYaml.parseText(partial,
                "thermal_properties.yaml");
        assertTrue(result.isSuccess(), () -> result.getMessage());
        ThermalYaml yaml = result.getValue().orElseThrow();
        assertEquals(1, yaml.getPartialRowsHeld());
        assertEquals(1, yaml.getRows().size());
    }

    @Test
    void testHeaderRefusals() {
        OperationResult<ThermalYaml> noProperties = QEPhonopyThermalYaml.parseText(
                "nqpoint: 101\nnpath: 3\n", "band.yaml");
        assertFalse(noProperties.isSuccess());
        assertEquals("PHONOPY_TPROP_HEADER", noProperties.getCode());

        // unit block absent: the writer ALWAYS emits one - a hand-made file here
        OperationResult<ThermalYaml> noUnit = QEPhonopyThermalYaml.parseText(
                "natom: 1\nzero_point_energy: 1.0\nthermal_properties:\n" + ROW0,
                "thermal_properties.yaml");
        assertFalse(noUnit.isSuccess());
        assertEquals("PHONOPY_TPROP_HEADER", noUnit.getCode(),
                "no unit: block -> unit labels are never invented");

        OperationResult<ThermalYaml> headerOnly = QEPhonopyThermalYaml.parseText(
                HEADER, "thermal_properties.yaml");
        assertFalse(headerOnly.isSuccess());
        assertEquals("PHONOPY_TPROP_PARTIAL", headerOnly.getCode());

        assertEquals("PHONOPY_TPROP_INPUT",
                QEPhonopyThermalYaml.parseText(null, "x").getCode());
    }

    @Test
    void testMidFileShortEntryIsShapeNeverGuessed() {
        String bad = HEADER + ROW0
                + "- temperature:       100.0000000\n"
                + "  free_energy:         5.9000000\n"
                + "  entropy:            12.3000000\n"
                + ROW100; // a second entry whose first block pretends to be the same
        OperationResult<ThermalYaml> result = QEPhonopyThermalYaml.parseText(bad,
                "thermal_properties.yaml");
        assertFalse(result.isSuccess());
        assertEquals("PHONOPY_TPROP_SHAPE", result.getCode(),
                "a mid-file short entry is a grammar break, not completed by re-reading"
                        + " the following entry's fields");

        String nanRow = HEADER + ROW0 + ROW100.replace(
                "  entropy:            12.3000000\n", "  entropy: nan\n");
        OperationResult<ThermalYaml> nanResult = QEPhonopyThermalYaml.parseText(nanRow,
                "thermal_properties.yaml");
        assertFalse(nanResult.isSuccess());
        assertEquals("PHONOPY_TPROP_SHAPE", nanResult.getCode());
    }
}
