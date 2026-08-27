package de.useweb.backend.ocl.ast;

import de.useweb.backend.ocl.diagnostics.SourceRange;

public record VariableDeclaration(
        String name,
        String declaredTypeName,
        SourceRange nameRange,
        SourceRange typeRange,
        SourceRange sourceRange) {

    public VariableDeclaration {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (nameRange == null || sourceRange == null) {
            throw new IllegalArgumentException("source ranges must not be null");
        }
        if ((declaredTypeName == null) != (typeRange == null)) {
            throw new IllegalArgumentException("declared type and type range must be present together");
        }
    }

    public boolean hasDeclaredType() {
        return declaredTypeName != null;
    }
}
