package de.useweb.backend.api.dto.command;

import com.fasterxml.jackson.databind.JsonNode;

public record MutationCommandRequestDto(String expectedRevision, JsonNode draft) {}
