package de.useweb.backend.api.dto.ocl;

import java.util.List;
import java.util.Map;

public record OclParseResponseDto(
        boolean success,
        List<OclDiagnosticDto> diagnostics,
        List<OclTokenDto> tokens,
        Map<String, Object> ast
) {
}
