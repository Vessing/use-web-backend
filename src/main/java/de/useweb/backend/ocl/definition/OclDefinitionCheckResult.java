package de.useweb.backend.ocl.definition;

import java.util.List;

import de.useweb.backend.ocl.diagnostics.OclDiagnostic;
import de.useweb.backend.ocl.typecheck.OclType;

public record OclDefinitionCheckResult(boolean success, OclType resultType, List<OclDiagnostic> diagnostics) {
    public OclDefinitionCheckResult {
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
        success = success && diagnostics.isEmpty();
    }
}
