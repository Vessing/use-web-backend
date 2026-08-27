package de.useweb.backend.ocl.ast;

import de.useweb.backend.ocl.diagnostics.SourceRange;

public record AtPreExpression(OclAstNode expression, SourceRange atPreRange, SourceRange sourceRange)
        implements OclAstNode {
}
