/* Copyright (C) 2025-2026 QuantumForge Development Team. */
package quantumforge.input.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import quantumforge.operation.OperationResult;

/**
 * Batch-173 pins for {@link VaspKpointsDeck}: all four documented KPOINTS
 * modes, the wiki-verbatim notes they carry, every refusal shape, and the
 * factory/writer round-trips. Provenance: vasp.at/wiki/index.php/KPOINTS
 * (fetched 2026-07-22).
 */
class VaspKpointsDeckTest {

    private static VaspKpointsDeck ok(String text) {
        OperationResult<VaspKpointsDeck> parsed = VaspKpointsDeck.parse(text);
        assertTrue(parsed.isSuccess(), parsed::getMessage);
        return parsed.getValue().orElseThrow();
    }

    @Test
    void gammaAutomaticMeshReadsWithOptionalShift() {
        VaspKpointsDeck deck = ok(
                "Regular 4 x 4 x 4 mesh centered at Gamma\n"
                + "0\nGamma\n4 4 4\n0 0 0\n");
        assertEquals(VaspKpointsDeck.Mode.AUTO_GAMMA, deck.getMode());
        assertEquals(3, deck.getSubdivisions().length);
        assertEquals(4, deck.getSubdivisions()[0]);
        assertEquals(0.0, deck.getShift()[2]);
        assertEquals("Regular 4 x 4 x 4 mesh centered at Gamma",
                deck.getComment());
        VaspKpointsDeck plain = ok("mesh\n0\ng\n8 8 8\n");
        assertEquals(0, plain.getShift().length,
                "the shift line is optional per the wiki grammar");
    }

    @Test
    void monkhorstPackCarriesTheEvenShiftNote() {
        VaspKpointsDeck deck = ok("mp mesh\n0\nMonkhorst-Pack\n4 4 4\n");
        assertEquals(VaspKpointsDeck.Mode.AUTO_MP, deck.getMode());
        assertTrue(deck.getNotes().stream().anyMatch(note -> note.contains(
                        "shifted by 1/2")),
                "even subdivisions shift MP by 1/2 vs Gamma (wiki formulas): "
                        + deck.getNotes());
        assertTrue(deck.getNotes().stream().anyMatch(note -> note.contains(
                        "ONLY Gamma-centered")),
                "fcc/hexagonal/fcc-orthorhombic pin travels verbatim: "
                        + deck.getNotes());
        OperationResult<VaspKpointsDeck> zero = VaspKpointsDeck.parse(
                "bad\n0\nGamma\n0 4 4\n");
        assertEquals("VASP_KPOINTS_SHAPE", zero.getCode(),
                "subdivisions must be >= 1");
    }

    @Test
    void generalizedMeshNeedsSevenLinesAndQuotesCommensurability() {
        VaspKpointsDeck deck = ok(
                "Automatic generation\n"
                + "0\nCartesian\n"
                + "0.25 0.00 0.00\n"
                + "0.00 0.25 0.00\n"
                + "0.00 0.00 0.25\n"
                + "0.50 0.50 0.50\n");
        assertEquals(VaspKpointsDeck.Mode.AUTO_GENERALIZED, deck.getMode());
        assertTrue(deck.isCartesian());
        assertEquals(0.25, deck.getGenerators()[1][1]);
        assertEquals(0.5, deck.getShift()[0]);
        assertTrue(deck.getNotes().stream().anyMatch(note -> note.contains(
                        "commensurate with the reciprocal lattice")),
                "the wiki Important block is quoted verbatim: "
                        + deck.getNotes());
        assertTrue(deck.getNotes().stream().anyMatch(note -> note.contains(
                        "integer entries")));
        OperationResult<VaspKpointsDeck> shortFile = VaspKpointsDeck.parse(
                "c\n0\nReciprocal\n0.25 0 0\n0 0.25 0\n");
        assertEquals("VASP_KPOINTS_SHAPE", shortFile.getCode());
    }

    @Test
    void unknownSelectorsFallBackToReciprocalWithTheWikisCaution() {
        VaspKpointsDeck deck = ok(
                "odd selector\n0\nZ\n"
                + "0.25 0.00 0.00\n"
                + "0.00 0.25 0.00\n"
                + "0.00 0.00 0.25\n"
                + "0.00 0.00 0.00\n");
        assertEquals(VaspKpointsDeck.Mode.AUTO_GENERALIZED, deck.getMode());
        assertFalse(deck.isCartesian(),
                "RECIPROCAL is the documented default fallback");
        assertTrue(deck.getNotes().stream().anyMatch(note -> note.contains(
                        "we recommend not relying on this behavior")),
                "the wiki caution is quoted verbatim: " + deck.getNotes());
        assertTrue(deck.getNotes().stream().anyMatch(note -> note.contains(
                        "future versions of VASP")),
                "the second wiki sentence (further automatic modes) travels");
    }

    @Test
    void lineModeReadsTheWikiTemplateAndCarriesBothWarnings() {
        VaspKpointsDeck deck = ok(
                "k points along high symmetry lines\n"
                + " 40 ! number of points per line\n"
                + "line mode\n"
                + "fractional\n"
                + "  0    0    0    G\n"
                + "  0.5  0.5  0    X\n"
                + "\n"
                + "  0.5  0.5  0    X\n"
                + "  0.5  0.75 0.25 W\n"
                + "\n"
                + "  0.5  0.75 0.25 W\n"
                + "  0    0    0    G\n");
        assertEquals(VaspKpointsDeck.Mode.LINE_MODE, deck.getMode());
        assertEquals(40, deck.getPointsPerSegment());
        assertFalse(deck.isCartesian());
        assertEquals(6, deck.getVertices().size(),
                "3 segments = 6 vertex lines (blank separators tolerated)");
        assertEquals("X", deck.getVertices().get(2).label(),
                "the second segment starts at X (wiki template)");
        assertEquals(0.25, deck.getVertices().get(3).z(),
                "the W vertex of the wiki template parses exactly");
        assertTrue(deck.getNotes().stream().anyMatch(note -> note.contains(
                        "Please set ICHARG = 11")),
                "wiki verbatim warning 1: " + deck.getNotes());
        assertTrue(deck.getNotes().stream().anyMatch(note -> note.contains(
                        "For meta-GGA and hybrid functionals, a regular mesh"
                                + " must always be provided")),
                "wiki verbatim warning 2");
        assertTrue(deck.getNotes().stream().anyMatch(note -> note.contains(
                        "two X and W points")),
                "endpoints-included remark travels verbatim");
        // odd vertex census is a shape error (pairs define segments)
        OperationResult<VaspKpointsDeck> odd = VaspKpointsDeck.parse(
                "t\n10\nline mode\nfractional\n0 0 0 G\n0.5 0.5 0 X\n0 0 0 G\n");
        assertEquals("VASP_KPOINTS_SHAPE", odd.getCode());
        // cartesian variant + too few points per segment
        VaspKpointsDeck cart = ok("t\n10\nline mode\nCartesian\n"
                + "0 0 0 G\n0.5 0.5 0 X\n");
        assertTrue(cart.isCartesian());
        assertEquals("VASP_KPOINTS_SHAPE", VaspKpointsDeck.parse(
                "t\n1\nline mode\nfractional\n0 0 0 G\n0.5 0.5 0 X\n")
                .getCode());
    }

    @Test
    void explicitListsAreCountExactAndTetrahedraAreValidated() {
        VaspKpointsDeck deck = ok(
                "Explicit k-point list\n"
                + "4\nCartesian\n"
                + "0.0  0.0  0.0   1\n"
                + "0.0  0.0  0.5   1\n"
                + "0.0  0.5  0.5   2\n"
                + "0.5  0.5  0.5   4\n"
                + "Tetrahedra\n"
                + "1  0.183333333333333\n"
                + "6    1 2 3 4\n");
        assertEquals(VaspKpointsDeck.Mode.EXPLICIT, deck.getMode());
        assertTrue(deck.isCartesian());
        assertEquals(4, deck.getPoints().size());
        assertEquals(4.0, deck.getPoints().get(3).weight());
        assertEquals(1, deck.getTetras().size());
        assertEquals(6, deck.getTetras().get(0).degeneracy());
        assertTrue(deck.getNotes().stream().anyMatch(note -> note.contains(
                        "weights are properly normalized")),
                "wiki verbatim weights note: " + deck.getNotes());
        assertTrue(deck.getNotes().stream().anyMatch(note -> note.contains(
                        "does not renormalize the weights of the tetrahedra")),
                "wiki verbatim tetrahedra warning: " + deck.getNotes());
        // count exactness: a short list is an error, never padded
        assertEquals("VASP_KPOINTS_SHAPE", VaspKpointsDeck.parse(
                "t\n4\nReciprocal\n0 0 0 1\n0 0 0.5 1\n").getCode());
        // corner indices are 1-based and must stay inside the list
        OperationResult<VaspKpointsDeck> out = VaspKpointsDeck.parse(
                "t\n1\nReciprocal\n0 0 0 1\nT\n1 0.5\n6 1 1 1 2\n");
        assertEquals("VASP_KPOINTS_SHAPE", out.getCode());
        assertTrue(out.getMessage().contains("outside 1..1"),
                out.getMessage());
        // marker is the first CHARACTER, per the wiki ('must start with
        // T or t'): a single-letter T works too
        VaspKpointsDeck single = ok(
                "t\n1\nReciprocal\n0 0 0 1\nT\n1 0.5\n6 1 1 1 1\n");
        assertEquals(1, single.getTetras().size());
    }

    @Test
    void boundsAndShapeRefusalsStayTyped() {
        assertEquals("VASP_KPOINTS_INPUT", VaspKpointsDeck.parse(null)
                .getCode());
        assertEquals("VASP_KPOINTS_EMPTY", VaspKpointsDeck.parse("  \n")
                .getCode());
        assertEquals("VASP_KPOINTS_SHAPE", VaspKpointsDeck.parse(
                "only a comment\n").getCode());
        assertEquals("VASP_KPOINTS_SHAPE", VaspKpointsDeck.parse(
                "c\n-3\nGamma\n4 4 4\n").getCode());
        assertEquals("VASP_KPOINTS_SHAPE", VaspKpointsDeck.parse(
                "c\nnope\nGamma\n4 4 4\n").getCode());
    }

    @Test
    void gammaMeshFactoryRoundTrips() {
        VaspKpointsDeck factory = VaspKpointsDeck.gammaMesh(
                "gamma 4x4x4", 4, 4, 4, 0.0, 0.0, 0.0);
        String text = factory.toKpointsText();
        assertTrue(text.contains("Gamma"), text);
        VaspKpointsDeck back = ok(text);
        assertEquals(factory.getMode(), back.getMode());
        assertEquals(4, back.getSubdivisions()[2]);
        assertEquals(3, back.getShift().length);
        assertEquals("gamma 4x4x4", back.getComment());
    }

    @Test
    void factoriesOfEveryModeRoundTrip() {
        VaspKpointsDeck mp = VaspKpointsDeck.mpMesh("mp", 6, 6, 6);
        VaspKpointsDeck mpBack = ok(mp.toKpointsText());
        assertEquals(VaspKpointsDeck.Mode.AUTO_MP, mpBack.getMode());
        assertEquals(6, mpBack.getSubdivisions()[0]);

        VaspKpointsDeck path = VaspKpointsDeck.bandPath("bands", 40, false,
                List.of(new VaspKpointsDeck.Vertex(0, 0, 0, "G"),
                        new VaspKpointsDeck.Vertex(0.5, 0.5, 0, "X"),
                        new VaspKpointsDeck.Vertex(0.5, 0.5, 0, "X"),
                        new VaspKpointsDeck.Vertex(0.5, 0.75, 0.25, "W")));
        String pathText = path.toKpointsText();
        assertTrue(pathText.contains("\n\n"),
                "wiki template uses blank lines between segments");
        VaspKpointsDeck pathBack = ok(pathText);
        assertEquals(VaspKpointsDeck.Mode.LINE_MODE, pathBack.getMode());
        assertEquals(40, pathBack.getPointsPerSegment());
        assertEquals(4, pathBack.getVertices().size());
        assertEquals("W", pathBack.getVertices().get(3).label());

        VaspKpointsDeck explicit = VaspKpointsDeck.explicit("e", false,
                List.of(new VaspKpointsDeck.ExplicitPoint(0, 0, 0, 1),
                        new VaspKpointsDeck.ExplicitPoint(0, 0, 0.5, 2)),
                List.of(new VaspKpointsDeck.Tetra(6, 1, 1, 2, 2)));
        VaspKpointsDeck explicitBack = ok(explicit.toKpointsText());
        assertEquals(VaspKpointsDeck.Mode.EXPLICIT, explicitBack.getMode());
        assertEquals(2, explicitBack.getPoints().size());
        assertEquals(2.0, explicitBack.getPoints().get(1).weight());
        assertEquals(1, explicitBack.getTetras().size(),
                "the Tetrahedra block itself round-trips");
        assertEquals(6, explicitBack.getTetras().get(0).degeneracy());

        assertEquals("0.5", VaspKpointsDeck.fmt(0.5));
        assertEquals("4", VaspKpointsDeck.fmt(4.0),
                "integers stay integers in KPOINTS text");
    }
}
