package de.useweb.backend.api.dto.command;

import java.util.List;

public record DeleteCommandRequestDto(String expectedRevision, List<String> cascadeReferenceIds, String enumerationId) {
    public DeleteCommandRequestDto(String expectedRevision, List<String> cascadeReferenceIds) {
        this(expectedRevision, cascadeReferenceIds, null);
    }
    public DeleteCommandRequestDto {
        cascadeReferenceIds = List.copyOf(cascadeReferenceIds == null ? List.of() : cascadeReferenceIds);
    }
}
