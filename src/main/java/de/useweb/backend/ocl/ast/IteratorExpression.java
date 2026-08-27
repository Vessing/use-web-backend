package de.useweb.backend.ocl.ast;

import java.util.List;

import de.useweb.backend.ocl.diagnostics.SourceRange;

public record IteratorExpression(
        OclAstNode source,
        IteratorKind kind,
        List<VariableDeclaration> variables,
        OclAstNode body,
        SourceRange operationRange,
        SourceRange bodyRange,
        SourceRange sourceRange) implements OclAstNode {

    public IteratorExpression {
        if (source == null || kind == null || body == null) {
            throw new IllegalArgumentException("source, kind and body must not be null");
        }
        variables = List.copyOf(variables == null ? List.of() : variables);
        if (operationRange == null || bodyRange == null || sourceRange == null) {
            throw new IllegalArgumentException("source ranges must not be null");
        }
    }
}
