package de.useweb.backend.api.dto.operation;

import java.util.List;

public record OperationInvocationRequestDto(
        String receiverObjectId,
        String operationId,
        List<OperationArgumentDto> arguments,
        Long expectedRevision) {}
