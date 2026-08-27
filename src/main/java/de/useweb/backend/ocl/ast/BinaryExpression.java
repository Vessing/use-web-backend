package de.useweb.backend.ocl.ast;

import de.useweb.backend.ocl.diagnostics.SourceRange;

public record BinaryExpression(OclAstNode left, BinaryOperator operator, OclAstNode right, SourceRange operatorRange, SourceRange sourceRange) implements OclAstNode {

    public BinaryExpression(OclAstNode left, BinaryOperator operator, OclAstNode right, SourceRange sourceRange) {
        this(left, operator, right, sourceRange, sourceRange);
    }

    public BinaryExpression {
        if (left == null) {
            throw new IllegalArgumentException("left must not be null");
        }
        if (operator == null) {
            throw new IllegalArgumentException("operator must not be null");
        }
        if (right == null) {
            throw new IllegalArgumentException("right must not be null");
        }
        if (sourceRange == null) {
            throw new IllegalArgumentException("sourceRange must not be null");
        }
        if (operatorRange == null) {
            throw new IllegalArgumentException("operatorRange must not be null");
        }
    }
}
