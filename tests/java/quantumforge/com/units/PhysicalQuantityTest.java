package quantumforge.com.units;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PhysicalQuantityTest {

    private static final double TOL = 1.0e-12;

    @Test
    void energyRoundTripsThroughSi() {
        PhysicalQuantity ry = PhysicalQuantity.of(1.0, Unit.RYDBERG);
        PhysicalQuantity ev = ry.to(Unit.ELECTRONVOLT);
        PhysicalQuantity ha = ry.to(Unit.HARTREE);
        assertEquals(0.5, ha.getValue(), TOL);
        assertEquals(1.0, ev.to(Unit.RYDBERG).getValue(), 1.0e-10);
        assertTrue(ev.getValue() > 13.0 && ev.getValue() < 14.0);
    }

    @Test
    void lengthBohrAngstromRoundTrip() {
        PhysicalQuantity bohr = PhysicalQuantity.of(1.0, Unit.BOHR);
        PhysicalQuantity ang = bohr.to(Unit.ANGSTROM);
        assertEquals(0.52917720859, ang.getValue(), 1.0e-10);
        assertEquals(1.0, ang.to(Unit.BOHR).getValue(), 1.0e-10);
    }

    @Test
    void pressureKbarGpa() {
        PhysicalQuantity kbar = PhysicalQuantity.of(10.0, Unit.KBAR);
        assertEquals(1.0, kbar.valueIn(Unit.GPA), TOL);
    }

    @Test
    void rejectsCrossDimensionConversion() {
        assertThrows(IllegalArgumentException.class,
                () -> PhysicalQuantity.of(1.0, Unit.RYDBERG).to(Unit.ANGSTROM));
    }

    @Test
    void rejectsNonFinite() {
        assertThrows(IllegalArgumentException.class,
                () -> PhysicalQuantity.of(Double.NaN, Unit.RYDBERG));
    }
}
