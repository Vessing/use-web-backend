package de.useweb.backend.ocl.contract;

import java.util.List;
import java.util.Optional;

import de.useweb.backend.ocl.diagnostics.OclDiagnostic;

public record OperationContractParseResult(OperationContract contract, List<OclDiagnostic> diagnostics) {
    public OperationContractParseResult {
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }

    public boolean success() {
        return contract != null && diagnostics.isEmpty();
    }

    public Optional<OperationContract> optionalContract() {
        return Optional.ofNullable(contract);
    }
}
