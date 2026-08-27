package de.useweb.backend.ocl.value;

import java.util.List;

import de.useweb.backend.ocl.collection.CollectionKind;

public record SetValue(List<OclValue> values) implements CollectionValue {
    public SetValue {
        values = CollectionValues.distinct(values);
    }

    @Override
    public CollectionKind collectionKind() {
        return CollectionKind.SET;
    }
}
