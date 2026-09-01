package de.useweb.backend.api.dto.command;

import de.useweb.backend.api.dto.uml.UmlAssociationDto;
import de.useweb.backend.api.dto.uml.UmlClassDto;

public record AssociationClassAggregateDto(
        UmlAssociationDto association,
        UmlClassDto associationClass) {
}
