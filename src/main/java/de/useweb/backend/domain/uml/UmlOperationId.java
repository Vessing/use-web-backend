package de.useweb.backend.domain.uml;

public record UmlOperationId(String value) {

    public UmlOperationId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("UmlOperationId must not be blank");
        }
    }
}
