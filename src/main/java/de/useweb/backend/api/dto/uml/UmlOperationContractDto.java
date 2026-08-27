package de.useweb.backend.api.dto.uml;

public record UmlOperationContractDto(
        String id,
        String name,
        String kind,
        String expression,
        Boolean enabled) {}
