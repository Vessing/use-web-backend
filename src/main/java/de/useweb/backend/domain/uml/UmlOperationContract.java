package de.useweb.backend.domain.uml;

public record UmlOperationContract(
        String id,
        String name,
        Kind kind,
        String expression,
        boolean enabled) {

    public enum Kind { PRE, POST }

    public UmlOperationContract {
        if (id == null || id.isBlank() || name == null || name.isBlank()
                || kind == null || expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Operation contract metadata must be complete");
        }
        id = id.trim();
        name = name.trim();
        expression = expression.trim();
    }
}
