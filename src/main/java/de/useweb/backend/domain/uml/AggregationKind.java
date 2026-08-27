package de.useweb.backend.domain.uml;

public enum AggregationKind {
    NONE,
    SHARED,
    COMPOSITE;

    public static AggregationKind defaulted(AggregationKind value) {
        return value == null ? NONE : value;
    }
}
