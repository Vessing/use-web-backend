package de.useweb.backend.api.dto.command;

import de.useweb.backend.api.dto.ocl.SourceRangeDto;

public record CommandElementReferenceDto(
        String referenceId,
        String elementType,
        String elementId,
        String elementName,
        String path,
        String relation,
        boolean cascadeAllowed,
        SourceRangeDto sourceRange) {

    public CommandElementReferenceDto(String referenceId, String elementType, String elementId, String elementName,
            String path, String relation, boolean cascadeAllowed) {
        this(referenceId, elementType, elementId, elementName, path, relation, cascadeAllowed, null);
    }
}
