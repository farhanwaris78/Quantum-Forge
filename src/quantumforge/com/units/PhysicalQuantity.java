/*
 * Copyright (C) 2025-2026 QuantumForge Development Team.
 */
package quantumforge.com.units;

import java.util.Locale;
import java.util.Objects;

/**
 * Immutable scientific quantity with an explicit unit.
 *
 * <p>Public scientific APIs should prefer this type over bare doubles so unit
 * mistakes become compile-time / conversion-time failures rather than silent
 * wrong plots.</p>
 */
public final class PhysicalQuantity {

    private final double value;
    private final Unit unit;

    public PhysicalQuantity(double value, Unit unit) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("quantity value is not finite: " + value);
        }
        this.value = value;
        this.unit = Objects.requireNonNull(unit, "unit");
    }

    public static PhysicalQuantity of(double value, Unit unit) {
        return new PhysicalQuantity(value, unit);
    }

    public double getValue() {
        return this.value;
    }

    public Unit getUnit() {
        return this.unit;
    }

    public PhysicalQuantity to(Unit target) {
        Objects.requireNonNull(target, "target");
        if (this.unit == target) {
            return this;
        }
        if (this.unit.getDimension() != target.getDimension()) {
            throw new IllegalArgumentException("Cannot convert " + this.unit + " to " + target
                    + " (different physical dimensions)");
        }
        double si = this.unit.toSi(this.value);
        return new PhysicalQuantity(target.fromSi(si), target);
    }

    public double valueIn(Unit target) {
        return to(target).getValue();
    }

    public PhysicalQuantity plus(PhysicalQuantity other) {
        Objects.requireNonNull(other, "other");
        PhysicalQuantity rhs = other.to(this.unit);
        return new PhysicalQuantity(this.value + rhs.value, this.unit);
    }

    public PhysicalQuantity minus(PhysicalQuantity other) {
        Objects.requireNonNull(other, "other");
        PhysicalQuantity rhs = other.to(this.unit);
        return new PhysicalQuantity(this.value - rhs.value, this.unit);
    }

    public PhysicalQuantity times(double scalar) {
        if (!Double.isFinite(scalar)) {
            throw new IllegalArgumentException("scalar is not finite");
        }
        return new PhysicalQuantity(this.value * scalar, this.unit);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PhysicalQuantity)) {
            return false;
        }
        PhysicalQuantity other = (PhysicalQuantity) obj;
        return this.unit == other.unit && Double.compare(this.value, other.value) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.value, this.unit);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%.12g %s", this.value, this.unit.getSymbol());
    }
}
