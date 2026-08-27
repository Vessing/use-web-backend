package de.useweb.backend.api.dto.ocl;

import java.util.List;

public record OclTypecheckResponseDto(
        boolean success,
        String resultType,
        List<OclDiagnosticDto> diagnostics) {
}
