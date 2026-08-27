package de.useweb.backend.domain.uml;

import de.useweb.backend.domain.ocl.OclExpression;

public record UmlInvariant(
        UmlInvariantId id,
        String name,
        UmlClassId contextClassId,
        OclExpression expression,
        boolean enabled) {

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
    }
}
