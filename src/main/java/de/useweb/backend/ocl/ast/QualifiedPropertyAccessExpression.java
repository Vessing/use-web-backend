package de.useweb.backend.ocl.ast;

import de.useweb.backend.ocl.diagnostics.SourceRange;

import java.util.List;
import java.util.Objects;

public record QualifiedPropertyAccessExpression(
        OclAstNode receiver,
        String propertyName,
        List<OclAstNode> qualifierArguments,
        SourceRange propertyRange,
        SourceRange sourceRange) implements OclAstNode {

    public QualifiedPropertyAccessExpression {
        Objects.requireNonNull(receiver, "receiver");
        Objects.requireNonNull(propertyName, "propertyName");
        qualifierArguments = List.copyOf(Objects.requireNonNull(qualifierArguments, "qualifierArguments"));
        Objects.requireNonNull(propertyRange, "propertyRange");
        Objects.requireNonNull(sourceRange, "sourceRange");
        if (qualifierArguments.isEmpty()) {
            throw new IllegalArgumentException("Qualified navigation requires at least one qualifier argument.");
        }
    }
}
