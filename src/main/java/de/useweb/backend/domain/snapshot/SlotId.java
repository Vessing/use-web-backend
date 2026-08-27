package de.useweb.backend.domain.snapshot;

public record SlotId(String value) {

    public SlotId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SlotId must not be blank");
        }
    }
}
