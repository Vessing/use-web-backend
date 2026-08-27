package de.useweb.backend.ocl.ast;

import de.useweb.backend.ocl.diagnostics.SourceRange;

public record UnaryExpression(UnaryOperator operator, OclAstNode expression, SourceRange sourceRange) implements OclAstNode {

    public UnaryExpression {
        if (operator == null) {
            throw new IllegalArgumentException("operator must not be null");
        }
        if (expression == null) {
            throw new IllegalArgumentException("expression must not be null");
        }
        if (sourceRange == null) {
            throw new IllegalArgumentException("sourceRange must not be null");
        }
    }
}
