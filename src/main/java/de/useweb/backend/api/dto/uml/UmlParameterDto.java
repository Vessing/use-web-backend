package de.useweb.backend.api.dto.uml;

public record UmlParameterDto(
        String id,
        String name,
        String type,
        String direction,
        Integer position
) {
    public UmlParameterDto(String id, String name, String type) {
        this(id, name, type, "IN", 0);
    }
}
