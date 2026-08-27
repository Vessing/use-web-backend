package de.useweb.backend.ocl.ast;

import de.useweb.backend.ocl.diagnostics.SourceRange;

public record LiteralExpression(LiteralType literalType, Object value, SourceRange sourceRange) implements OclAstNode {

    public LiteralExpression {
        if (literalType == null) {
            throw new IllegalArgumentException("literalType must not be null");
        }
        if (sourceRange == null) {
            throw new IllegalArgumentException("sourceRange must not be null");
        }
    }
}
