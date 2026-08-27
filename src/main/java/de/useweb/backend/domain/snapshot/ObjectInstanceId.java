package de.useweb.backend.domain.snapshot;

public record ObjectInstanceId(String value) {

    public ObjectInstanceId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ObjectInstanceId must not be blank");
        }
    }
}
