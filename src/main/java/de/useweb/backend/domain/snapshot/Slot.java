package de.useweb.backend.domain.snapshot;

import de.useweb.backend.domain.uml.UmlAttributeId;

public record Slot(SlotId id, UmlAttributeId attributeId, SlotValue value) {

    public Slot {
        if (id == null) {
            throw new IllegalArgumentException("Slot id must not be null");
        }
        if (attributeId == null) {
            throw new IllegalArgumentException("Slot attributeId must not be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("Slot value must not be null");
        }
    }
}
