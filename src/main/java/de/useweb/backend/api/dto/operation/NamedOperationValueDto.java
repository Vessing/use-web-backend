package de.useweb.backend.api.dto.operation;

import de.useweb.backend.api.dto.snapshot.SlotValueDto;

public record NamedOperationValueDto(String parameterId, String parameterName, SlotValueDto value) {}
