package de.useweb.backend.ocl.ast;

import de.useweb.backend.ocl.diagnostics.SourceRange;

public record SelfExpression(SourceRange sourceRange) implements OclAstNode {

    public SelfExpression {
        if (sourceRange == null) {
            throw new IllegalArgumentException("sourceRange must not be null");
        }
    }
}
