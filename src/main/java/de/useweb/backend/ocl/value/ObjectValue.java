package de.useweb.backend.ocl.value;

import de.useweb.backend.domain.snapshot.ObjectInstance;

public record ObjectValue(ObjectInstance object) implements OclValue {

    public ObjectValue {
        if (object == null) {
            throw new IllegalArgumentException("object must not be null");
        }
    }

    @Override
    public String typeName() {
        return "Object";
    }

    @Override
    public Object rawValue() {
        return object.id().value();
    }
}
