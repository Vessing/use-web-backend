package de.useweb.backend.ocl.ast;

import de.useweb.backend.ocl.diagnostics.SourceRange;

public record CollectionItem(OclAstNode expression, SourceRange sourceRange) implements CollectionLiteralPart {
}
