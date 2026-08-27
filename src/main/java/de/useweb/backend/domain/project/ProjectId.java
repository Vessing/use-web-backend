package de.useweb.backend.domain.project;

public record ProjectId(String value) {

    public ProjectId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ProjectId must not be blank");
        }
    }
}
