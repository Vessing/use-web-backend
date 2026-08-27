package de.useweb.backend.ocl.ast;

import java.util.List;

import de.useweb.backend.ocl.collection.CollectionKind;
import de.useweb.backend.ocl.diagnostics.SourceRange;

public record CollectionLiteralExpression(
        CollectionKind collectionKind,
        List<CollectionLiteralPart> parts,
        SourceRange sourceRange) implements OclAstNode {

    public CollectionLiteralExpression {
        parts = List.copyOf(parts == null ? List.of() : parts);
    }
}
