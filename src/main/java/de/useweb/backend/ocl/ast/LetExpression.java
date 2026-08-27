package de.useweb.backend.ocl.ast;

import de.useweb.backend.ocl.diagnostics.SourceRange;

public record LetExpression(
        VariableDeclaration variable,
        OclAstNode initializer,
        OclAstNode body,
        SourceRange initializerRange,
        SourceRange bodyRange,
        SourceRange sourceRange) implements OclAstNode {

    public LetExpression {
        if (variable == null || initializer == null || body == null) {
            throw new IllegalArgumentException("let expression parts must not be null");
        }
        if (initializerRange == null || bodyRange == null || sourceRange == null) {
            throw new IllegalArgumentException("source ranges must not be null");
        }
    }
}
