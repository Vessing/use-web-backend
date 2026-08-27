package de.useweb.backend.api.dto.operation;

import java.util.List;
import de.useweb.backend.api.dto.ocl.OclDiagnosticDto;

public record OperationContractResultDto(
        String contractId,
        String contractName,
        String kind,
        String status,
        List<OclDiagnosticDto> diagnostics) {
    public OperationContractResultDto {
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }
}
