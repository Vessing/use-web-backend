package de.useweb.backend.ocl.lexer;

import java.util.List;

import de.useweb.backend.ocl.diagnostics.OclDiagnostic;

public record OclLexResult(List<OclToken> tokens, List<OclDiagnostic> diagnostics) {

    public OclLexResult {
        tokens = List.copyOf(tokens == null ? List.of() : tokens);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }

    public boolean success() {
        return diagnostics.isEmpty();
    }
}
