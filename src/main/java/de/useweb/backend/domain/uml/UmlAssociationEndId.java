package de.useweb.backend.domain.uml;

public record UmlAssociationEndId(String value) {

    public UmlAssociationEndId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("UmlAssociationEndId must not be blank");
        }
    }
}
