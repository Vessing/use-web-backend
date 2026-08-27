package de.useweb.backend.api.dto.operation;

import java.util.List;
import de.useweb.backend.api.dto.snapshot.SlotValueDto;

public record OperationInvocationResultDto(
        String invocationId,
        String status,
        NamedElementReferenceDto receiver,
        String requestedOperationId,
        String resolvedOperationId,
        String resolvedOperationName,
        String resolvedOwnerClassId,
        SlotValueDto result,
        List<NamedOperationValueDto> outValues,
        OperationLifecycleDiffDto lifecycle,
        String beforeSnapshotId,
        String afterSnapshotId,
        String candidateAfterSnapshotId,
        long revision,
        List<String> diagnostics,
        List<OperationContractResultDto> contractResults) {
    public OperationInvocationResultDto {
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
        contractResults = List.copyOf(contractResults == null ? List.of() : contractResults);
    }
}
