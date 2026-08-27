package de.useweb.backend.domain.snapshot;

public record ObjectLinkId(String value) {

    public ObjectLinkId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ObjectLinkId must not be blank");
        }
    }
}
