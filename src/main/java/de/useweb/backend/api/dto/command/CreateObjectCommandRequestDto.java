package de.useweb.backend.api.dto.command;

import de.useweb.backend.api.dto.snapshot.ObjectInstanceDto;

public record CreateObjectCommandRequestDto(String expectedRevision, ObjectInstanceDto draft) {}
