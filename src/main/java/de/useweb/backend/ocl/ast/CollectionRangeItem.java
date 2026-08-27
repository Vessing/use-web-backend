package de.useweb.backend.ocl.ast;

import de.useweb.backend.ocl.diagnostics.SourceRange;

public record CollectionRangeItem(OclAstNode first, OclAstNode last, SourceRange sourceRange) implements CollectionLiteralPart {
}
