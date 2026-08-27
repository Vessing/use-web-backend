package de.useweb.backend.ocl.ast;

import java.util.List;

import de.useweb.backend.ocl.diagnostics.SourceRange;

public record TupleExpression(List<TuplePart> parts, SourceRange sourceRange) implements OclAstNode {
    public TupleExpression {
        parts = List.copyOf(parts);
    }
}
