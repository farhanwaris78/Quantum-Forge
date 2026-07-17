package quantumforge.input.namelist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SMILESParserTest {

    @Test
    void testParserProcessesStandardAromaticBenzeneSMILES() {
        // Benzene has 6 aromatic Carbon atoms (lowercase 'c' in SMILES)
        SMILESParser parser = new SMILESParser("c1ccccc1");
        assertTrue(parser.isValid());

        // Aromatic Carbon must be detected and converted to capital C.
        // It must estimate implicit hydrogens (each Carbon has 3 bonds locally inside aromatic rings, so 1 implicit H each).
        // Total formula = C6H6
        String formula = parser.getChemicalFormula();
        assertEquals("C6H6", formula);
        assertTrue(parser.isOrganic(), "Benzene must be classified as an organic molecule");
    }

    @Test
    void testParserProcessesEthanolAndCarbonylSMILES() {
        // Ethanol "CCO" -> C2 H(4+3+1 = 8) O
        SMILESParser parser1 = new SMILESParser("CCO");
        assertEquals("C2H6O", parser1.getChemicalFormula()); // C=2, H=6 (first C has 3 H, second C has 2 H, O has 1 H)

        // Acetic Acid "CC(=O)O"
        SMILESParser parser2 = new SMILESParser("CC(=O)O");
        assertTrue(parser2.isOrganic());
        assertEquals("C2H4O2", parser2.getChemicalFormula());
    }
}
