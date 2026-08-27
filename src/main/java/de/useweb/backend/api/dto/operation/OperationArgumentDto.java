package de.useweb.backend.api.dto.operation;

import de.useweb.backend.api.dto.snapshot.SlotValueDto;

public record OperationArgumentDto(String parameterId, SlotValueDto value) {}
