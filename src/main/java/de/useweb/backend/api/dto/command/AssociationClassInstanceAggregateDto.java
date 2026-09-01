package de.useweb.backend.api.dto.command;

import de.useweb.backend.api.dto.snapshot.ObjectInstanceDto;
import de.useweb.backend.api.dto.snapshot.ObjectLinkDto;

public record AssociationClassInstanceAggregateDto(
        ObjectLinkDto link,
        ObjectInstanceDto associationClassObject) {
}
