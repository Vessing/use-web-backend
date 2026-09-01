package de.useweb.backend.domain.uml;

import java.util.List;

import de.useweb.backend.domain.ocl.OclExpression;

public record UmlInvariant(
        UmlInvariantId id,
        String name,
        UmlClassId contextClassId,
        OclExpression expression,
        boolean enabled,
        List<String> contextVariableNames,
        boolean existential) {

    public UmlInvariant(UmlInvariantId id, String name, UmlClassId contextClassId,
            OclExpression expression, boolean enabled) {
        this(id, name, contextClassId, expression, enabled, List.of(), false);
    }

    public UmlInvariant {
        if (id == null) {
            throw new IllegalArgumentException("UmlInvariant id must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("UmlInvariant name must not be blank");
        }
        if (contextClassId == null) {
            throw new IllegalArgumentException("UmlInvariant contextClassId must not be null");
        }
        if (expression == null) {
            throw new IllegalArgumentException("UmlInvariant expression must not be null");
        }
        contextVariableNames = List.copyOf(contextVariableNames == null ? List.of() : contextVariableNames);
        if (contextVariableNames.stream().anyMatch(value -> value == null || value.isBlank())
                || contextVariableNames.stream().distinct().count() != contextVariableNames.size()) {
            throw new IllegalArgumentException("Invariant context variable names must be non-blank and unique");
        }
    }
}
