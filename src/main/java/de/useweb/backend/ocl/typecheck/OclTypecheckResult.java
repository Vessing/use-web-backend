package de.useweb.backend.ocl.typecheck;

import java.util.List;

import de.useweb.backend.ocl.diagnostics.OclDiagnostic;

public record OclTypecheckResult(boolean success, OclType resultType, List<OclDiagnostic> diagnostics) {

    public OclTypecheckResult {
        if (resultType == null) {
            throw new IllegalArgumentException("resultType must not be null");
        }
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
        success = success && diagnostics.isEmpty();
    }

    public static OclTypecheckResult ok(OclType resultType) {
        return new OclTypecheckResult(true, resultType, List.of());
    }

    public static OclTypecheckResult failure(OclType resultType, List<OclDiagnostic> diagnostics) {
        return new OclTypecheckResult(false, resultType, diagnostics);
    }
}
