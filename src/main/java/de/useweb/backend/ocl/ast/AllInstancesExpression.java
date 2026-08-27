package de.useweb.backend.ocl.ast;

import de.useweb.backend.ocl.diagnostics.SourceRange;

public record AllInstancesExpression(
        String typeName,
        SourceRange typeRange,
        SourceRange operationRange,
        SourceRange sourceRange) implements OclAstNode {

    public AllInstancesExpression {
        if (typeName == null || typeName.isBlank()) {
            throw new IllegalArgumentException("typeName must not be blank");
        }
        if (typeRange == null || operationRange == null || sourceRange == null) {
            throw new IllegalArgumentException("source ranges must not be null");
        }
    }
}
