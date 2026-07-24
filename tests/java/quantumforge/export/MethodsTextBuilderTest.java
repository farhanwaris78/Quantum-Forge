package quantumforge.export;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import quantumforge.atoms.model.Atom;
import quantumforge.atoms.model.Cell;
import quantumforge.com.math.Matrix3D;
import quantumforge.export.MethodsTextBuilder.MethodsDraft;
import quantumforge.input.QESCFInput;
import quantumforge.input.QEInput;
import quantumforge.input.card.QEAtomicSpecies;
import quantumforge.input.card.QEKPoints;
import quantumforge.input.namelist.QEValueBase;

/** Batch-49 coverage for the methods-section draft generator (Roadmap #123). */
class MethodsTextBuilderTest {

    @Test
    void testFullInputIsTranscribed() throws Exception {
        QESCFInput input = new QESCFInput();
        input.getNamelist(QEInput.NAMELIST_CONTROL).setValue(
                QEValueBase.getInstance("calculation", "vc-relax"));
        input.getNamelist(QEInput.NAMELIST_SYSTEM).setValue(
                QEValueBase.getInstance("ecutwfc", "40.0"));
        input.getNamelist(QEInput.NAMELIST_SYSTEM).setValue(
                QEValueBase.getInstance("ecutrho", "320.0"));
        input.getNamelist(QEInput.NAMELIST_SYSTEM).setValue(
                QEValueBase.getInstance("occupations", "smearing"));
        input.getNamelist(QEInput.NAMELIST_SYSTEM).setValue(
                QEValueBase.getInstance("smearing", "mp"));
        input.getNamelist(QEInput.NAMELIST_SYSTEM).setValue(
                QEValueBase.getInstance("degauss", "0.01"));
        QEAtomicSpecies species = input.getCard(QEAtomicSpecies.class);
        species.addSpecies("Si", 28.086, "Si.pz-vbc.UPF");
        QEKPoints kpoints = input.getCard(QEKPoints.class);
        kpoints.setAutomatic();
        kpoints.setKGrid(new int[] {4, 4, 4});
        kpoints.setKOffset(new int[] {0, 0, 0});
        Cell cell = new Cell(Matrix3D.unit(10.0));
        cell.addAtom(new quantumforge.atoms.model.Atom("Si", 0.0, 0.0, 0.0));
        cell.addAtom(new quantumforge.atoms.model.Atom("Si", 0.25, 0.25, 0.25));

        MethodsDraft draft = MethodsTextBuilder.build(input, cell, List.of("qe2009"),
                "@article{qe2009}");
        String text = draft.getText();
        assertTrue(text.contains("`vc-relax`"), text);
        assertTrue(text.contains("`ecutwfc` = 40.00 Ry"), text);
        assertTrue(text.contains("`ecutrho` = 320.00 Ry, ratio 8.000"), text);
        assertTrue(text.contains("Monkhorst-Pack mesh"), text);
        assertTrue(text.contains("Si <- Si.pz-vbc.UPF"), text);
        assertTrue(text.contains("Occupations: `smearing`"), text);
        assertTrue(text.contains("smearing `mp`"), text);
        assertTrue(text.contains("degauss 0.01000 Ry"), text);
        assertTrue(text.contains("Si"), text);
        assertTrue(text.contains("qe2009"), text);
        assertTrue(text.contains("```bibtex"), text);
        assertTrue(draft.getMissing().size() <= 2,
                "A complete transcription has at most 2 missing items: " + draft.getMissing());
        assertFalse(text.contains("PBE"), "A functional name is never fabricated");
    }

    @Test
    void testMissingItemsAreListedNotInvented() {
        MethodsDraft draft = MethodsTextBuilder.build(null, null, List.of(), null);
        String text = draft.getText();
        assertTrue(text.contains("### Not recorded"), text);
        assertTrue(draft.getMissing().size() >= 4,
                "No input and no cell must list every field as missing: " + draft.getMissing());
        long inputItems = draft.getMissing().stream()
                .filter(item -> item.contains("cutoff") || item.contains("pseudopot")
                        || item.contains("sampling") || item.contains("occupation")
                        || item.contains("calculation")).count();
        assertTrue(inputItems >= 4, draft.getMissing().toString());
        assertTrue(text.contains("references") || !text.contains("```bibtex"),
                "No BibTeX section without content");
        assertFalse(text.contains("`scf`"), "No calculation type may be asserted when absent");
    }

    @Test
    void testPartialInputMixedTranscription() throws Exception {
        QESCFInput input = new QESCFInput();
        input.getNamelist(QEInput.NAMELIST_CONTROL).setValue(
                QEValueBase.getInstance("calculation", "scf"));
        MethodsDraft draft = MethodsTextBuilder.build(input, null, List.of(), null);
        assertTrue(draft.getText().contains("`scf`"), draft.getText());
        assertTrue(draft.getMissing().stream().anyMatch(item -> item.contains("cutoff")),
                draft.getMissing().toString());
        assertTrue(draft.getMissing().stream().anyMatch(item -> item.contains("composition")),
                draft.getMissing().toString());
        assertTrue(draft.getText().contains("single \u0393 point")
                        || draft.getText().contains("K_POINTS"),
                "Explicit or absent K_POINTS list is handled honestly: " + draft.getText());
    }
}
