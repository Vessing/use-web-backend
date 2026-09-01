package de.useweb.backend.api.dto.command;

import de.useweb.backend.api.dto.snapshot.ObjectLinkDto;

public record CreateObjectLinkCommandRequestDto(String expectedRevision, ObjectLinkDto draft) {}
