package de.useweb.backend.api.dto.command;

import java.util.List;

public record DeleteImpactDto(
        String revisionScope,
        String revision,
        CommandElementReferenceDto target,
        List<CommandElementReferenceDto> references,
        boolean blocked) {
    public DeleteImpactDto {
        references = List.copyOf(references == null ? List.of() : references);
    }
}
