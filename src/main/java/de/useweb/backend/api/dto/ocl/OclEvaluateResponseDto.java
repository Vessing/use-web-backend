package de.useweb.backend.api.dto.ocl;

import java.util.List;

public record OclEvaluateResponseDto(
        boolean success,
        String resultType,
        Object value,
        String valueKind,
        String collectionKind,
        String elementType,
        List<OclDiagnosticDto> diagnostics) {

    public OclEvaluateResponseDto(boolean success, String resultType, Object value, List<OclDiagnosticDto> diagnostics) {
        this(success, resultType, value, value == null ? null : "DEFINED", null, null, diagnostics);
    }

    public OclEvaluateResponseDto(boolean success, String resultType, Object value, String valueKind, List<OclDiagnosticDto> diagnostics) {
        this(success, resultType, value, valueKind, null, null, diagnostics);
    }
}
