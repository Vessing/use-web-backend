package de.useweb.backend.ocl.parser;

import java.util.List;

import de.useweb.backend.ocl.ast.OclAstNode;
import de.useweb.backend.ocl.diagnostics.OclDiagnostic;

public record OclParseResult(boolean success, OclAstNode ast, List<OclDiagnostic> diagnostics) {

    public OclParseResult {
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }

    public static OclParseResult ok(OclAstNode ast) {
        return new OclParseResult(true, ast, List.of());
    }

    public static OclParseResult failure(List<OclDiagnostic> diagnostics) {
        return new OclParseResult(false, null, diagnostics);
    }
}
