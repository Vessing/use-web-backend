package de.useweb.backend.ocl.ast;

import de.useweb.backend.ocl.diagnostics.SourceRange;

public record ParenthesizedExpression(OclAstNode expression, SourceRange sourceRange) implements OclAstNode {

    public ParenthesizedExpression {
        if (expression == null) {
            throw new IllegalArgumentException("expression must not be null");
        }
        if (sourceRange == null) {
            throw new IllegalArgumentException("sourceRange must not be null");
        }
    }
}
