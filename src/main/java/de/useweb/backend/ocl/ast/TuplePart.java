package de.useweb.backend.ocl.ast;

import de.useweb.backend.ocl.diagnostics.SourceRange;

public record TuplePart(String name, OclAstNode value, SourceRange nameRange, SourceRange sourceRange) {
}
