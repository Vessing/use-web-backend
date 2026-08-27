package de.useweb.backend.ocl.ast;

import de.useweb.backend.ocl.diagnostics.SourceRange;

public record PropertyAccessExpression(OclAstNode receiver, String propertyName, SourceRange propertyRange, SourceRange sourceRange) implements OclAstNode {

    public PropertyAccessExpression(OclAstNode receiver, String propertyName, SourceRange sourceRange) {
        this(receiver, propertyName, sourceRange, sourceRange);
    }

    public PropertyAccessExpression {
        if (receiver == null) {
            throw new IllegalArgumentException("receiver must not be null");
        }
        if (propertyName == null || propertyName.isBlank()) {
            throw new IllegalArgumentException("propertyName must not be blank");
        }
        if (sourceRange == null) {
            throw new IllegalArgumentException("sourceRange must not be null");
        }
        if (propertyRange == null) {
            throw new IllegalArgumentException("propertyRange must not be null");
        }
    }
}
