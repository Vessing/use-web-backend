package de.useweb.backend.ocl.evaluation;

import java.util.List;

import de.useweb.backend.ocl.diagnostics.OclDiagnostic;
import de.useweb.backend.ocl.value.OclValue;

public record OclEvaluationResult(boolean success, OclValue value, List<OclDiagnostic> diagnostics) {

    public OclEvaluationResult {
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
        success = success && diagnostics.isEmpty();
    }

    public static OclEvaluationResult ok(OclValue value) {
        return new OclEvaluationResult(true, value, List.of());
    }

    public static OclEvaluationResult failure(List<OclDiagnostic> diagnostics) {
        return new OclEvaluationResult(false, null, diagnostics);
    }
}
