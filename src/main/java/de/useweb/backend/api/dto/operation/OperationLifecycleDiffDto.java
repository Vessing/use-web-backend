package de.useweb.backend.api.dto.operation;

import java.util.List;

public record OperationLifecycleDiffDto(
        List<NamedElementReferenceDto> createdObjects,
        List<NamedElementReferenceDto> changedObjects,
        List<NamedElementReferenceDto> deletedObjects) {}
