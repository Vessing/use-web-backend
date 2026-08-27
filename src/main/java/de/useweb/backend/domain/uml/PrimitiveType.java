package de.useweb.backend.domain.uml;

public enum PrimitiveType {
    STRING("String"),
    INTEGER("Integer"),
    REAL("Real"),
    BOOLEAN("Boolean");

    private final String displayName;

    PrimitiveType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
