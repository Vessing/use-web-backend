package de.useweb.backend.api.dto.validation;

public record ValidationRequestDto(String mode, boolean includeWarnings) {
}
