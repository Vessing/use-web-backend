package de.useweb.backend.api.dto.command;

import java.util.List;

public record MutationResultDto(
        String command,
        String revisionScope,
        String revision,
        Object result,
        List<CommandElementReferenceDto> affectedElements) {
    public MutationResultDto {
        affectedElements = List.copyOf(affectedElements == null ? List.of() : affectedElements);
    }
}
