package de.useweb.backend.api.dto.command;

import de.useweb.backend.api.dto.snapshot.SlotDto;

public record UpdateSlotCommandRequestDto(String expectedRevision, SlotDto draft) {}
