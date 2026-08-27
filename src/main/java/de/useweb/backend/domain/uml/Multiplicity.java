package de.useweb.backend.domain.uml;

public record Multiplicity(int lower, Integer upper, boolean unbounded, String raw) {

    public Multiplicity {
        if (lower < 0) {
            throw new IllegalArgumentException("Multiplicity lower bound must not be negative");
        }
        if (!unbounded && upper == null) {
            throw new IllegalArgumentException("Bounded multiplicity requires an upper bound");
        }
        if (upper != null && upper < lower) {
            throw new IllegalArgumentException("Multiplicity upper bound must be >= lower bound");
        }
    }

    public static Multiplicity exactlyOne() {
        return new Multiplicity(1, 1, false, "1");
    }

    public static Multiplicity zeroToMany() {
        return new Multiplicity(0, null, true, "0..*");
    }

    public boolean contains(int count) {
        return count >= lower && (unbounded || count <= upper);
    }
}
