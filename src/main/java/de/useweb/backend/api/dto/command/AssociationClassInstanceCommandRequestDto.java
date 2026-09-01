package de.useweb.backend.api.dto.command;

public record AssociationClassInstanceCommandRequestDto(
        String expectedRevision,
        AssociationClassInstanceDraftDto draft) {
}
