package de.useweb.backend.ocl.contract;

import java.util.Optional;

import de.useweb.backend.ocl.value.OclValue;

public record OperationResultSlot(boolean available, OclValue value) {
    public OperationResultSlot {
        if (available && value == null) {
            throw new IllegalArgumentException("Available operation result must have a value");
        }
        if (!available && value != null) {
            throw new IllegalArgumentException("Unavailable operation result must not have a value");
        }
    }

    public static OperationResultSlot unavailable() {
        return new OperationResultSlot(false, null);
    }

    public static OperationResultSlot of(OclValue value) {
        return new OperationResultSlot(true, value);
    }

    public Optional<OclValue> optionalValue() {
        return Optional.ofNullable(value);
    }
}
