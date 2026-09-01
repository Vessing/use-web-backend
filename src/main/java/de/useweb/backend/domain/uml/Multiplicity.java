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
        if (count < 0) {
            return false;
        }
        String normalized = raw == null ? "" : raw.replaceAll("\\s+", "");
        if (!normalized.contains(",")) {
            return count >= lower && (unbounded || count <= upper);
        }
        for (String range : normalized.split(",")) {
            if ("*".equals(range)) {
                return true;
            }
            if (range.contains("..")) {
                String[] bounds = range.split("\\.\\.", 2);
                int rangeLower = Integer.parseInt(bounds[0]);
                if (count >= rangeLower && ("*".equals(bounds[1]) || count <= Integer.parseInt(bounds[1]))) {
                    return true;
                }
            } else if (count == Integer.parseInt(range)) {
                return true;
            }
        }
        return false;
    }
}
