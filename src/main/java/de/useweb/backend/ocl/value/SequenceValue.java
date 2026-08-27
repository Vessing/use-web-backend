package de.useweb.backend.ocl.value;

import java.util.List;

import de.useweb.backend.ocl.collection.CollectionKind;

public record SequenceValue(List<OclValue> values) implements CollectionValue {
    public SequenceValue {
        values = List.copyOf(values == null ? List.of() : values);
    }

    @Override
    public CollectionKind collectionKind() {
        return CollectionKind.SEQUENCE;
    }
}
