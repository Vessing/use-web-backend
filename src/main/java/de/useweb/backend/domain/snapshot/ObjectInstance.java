package de.useweb.backend.domain.snapshot;

import java.util.List;
import java.util.Optional;

import de.useweb.backend.domain.uml.UmlAttributeId;
import de.useweb.backend.domain.uml.UmlClassId;

public record ObjectInstance(
        ObjectInstanceId id,
        String name,
        UmlClassId classId,
        List<Slot> slots) {

    public ObjectInstance {
        if (id == null) {
            throw new IllegalArgumentException("ObjectInstance id must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ObjectInstance name must not be blank");
        }
        if (classId == null) {
            throw new IllegalArgumentException("ObjectInstance classId must not be null");
        }
        slots = List.copyOf(slots == null ? List.of() : slots);
    }

    public Optional<Slot> findSlot(UmlAttributeId attributeId) {
        return slots.stream()
                .filter(slot -> slot.attributeId().equals(attributeId))
                .findFirst();
    }
}
