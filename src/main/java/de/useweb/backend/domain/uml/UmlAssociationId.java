package de.useweb.backend.domain.uml;

public record UmlAssociationId(String value) {

    public UmlAssociationId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("UmlAssociationId must not be blank");
        }
    }
}
