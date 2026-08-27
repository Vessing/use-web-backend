package de.useweb.backend.ocl.value;

import java.util.List;

import de.useweb.backend.ocl.collection.CollectionKind;

public record BagValue(List<OclValue> values) implements CollectionValue {
    public BagValue {
        values = List.copyOf(values == null ? List.of() : values);
    }

    @Override
    public CollectionKind collectionKind() {
        return CollectionKind.BAG;
    }
}
