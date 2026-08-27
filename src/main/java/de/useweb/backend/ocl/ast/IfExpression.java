package de.useweb.backend.ocl.ast;

import de.useweb.backend.ocl.diagnostics.SourceRange;

public record IfExpression(
        OclAstNode condition,
        OclAstNode thenExpression,
        OclAstNode elseExpression,
        SourceRange conditionRange,
        SourceRange thenRange,
        SourceRange elseRange,
        SourceRange sourceRange) implements OclAstNode {

    public IfExpression {
        if (condition == null || thenExpression == null || elseExpression == null) {
            throw new IllegalArgumentException("if expression parts must not be null");
        }
        if (conditionRange == null || thenRange == null || elseRange == null || sourceRange == null) {
            throw new IllegalArgumentException("source ranges must not be null");
        }
    }
}
