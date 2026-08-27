package de.useweb.backend.ocl.value;

import java.util.List;

import de.useweb.backend.ocl.collection.CollectionKind;

public record OrderedSetValue(List<OclValue> values) implements CollectionValue {
    public OrderedSetValue {
        values = CollectionValues.distinct(values);
    }

    @Override
    public CollectionKind collectionKind() {
        return CollectionKind.ORDERED_SET;
    }
}
