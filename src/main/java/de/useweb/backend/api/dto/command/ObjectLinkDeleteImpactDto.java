package de.useweb.backend.api.dto.command;

import java.util.List;

import de.useweb.backend.api.dto.snapshot.ObjectLinkDto;

public record ObjectLinkDeleteImpactDto(
        String revisionScope,
        String revision,
        CommandElementReferenceDto target,
        ObjectLinkDto currentLink,
        List<CommandElementReferenceDto> context,
        List<CommandElementReferenceDto> blockers,
        List<CommandElementReferenceDto> allowedCascades,
        List<CommandElementReferenceDto> validationTargets,
        boolean blocked) {
    public ObjectLinkDeleteImpactDto {
        context = List.copyOf(context == null ? List.of() : context);
        blockers = List.copyOf(blockers == null ? List.of() : blockers);
        allowedCascades = List.copyOf(allowedCascades == null ? List.of() : allowedCascades);
        validationTargets = List.copyOf(validationTargets == null ? List.of() : validationTargets);
    }

}
