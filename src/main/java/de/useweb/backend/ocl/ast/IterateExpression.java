package de.useweb.backend.ocl.ast;

import java.util.List;

import de.useweb.backend.ocl.diagnostics.SourceRange;

public record IterateExpression(
        OclAstNode source,
        List<VariableDeclaration> iterators,
        VariableDeclaration accumulator,
        OclAstNode initializer,
        OclAstNode body,
        SourceRange operationRange,
        SourceRange initializerRange,
        SourceRange bodyRange,
        SourceRange sourceRange) implements OclAstNode {

    public IterateExpression {
        iterators = List.copyOf(iterators);
        if (source == null || iterators.isEmpty() || accumulator == null || initializer == null || body == null) {
            throw new IllegalArgumentException("iterate components must not be null");
        }
        if (!accumulator.hasDeclaredType()) {
            throw new IllegalArgumentException("iterate accumulator requires a declared type");
        }
        if (operationRange == null || initializerRange == null || bodyRange == null || sourceRange == null) {
            throw new IllegalArgumentException("source ranges must not be null");
        }
    }
}
