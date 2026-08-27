package de.useweb.backend.ocl.value;

import java.util.List;

import de.useweb.backend.ocl.collection.CollectionKind;

public sealed interface CollectionValue extends OclValue permits SetValue, BagValue, SequenceValue, OrderedSetValue {

    CollectionKind collectionKind();

    List<OclValue> values();

    @Override
    default String typeName() {
        return collectionKind().oclName();
    }

    @Override
    default Object rawValue() {
        return values().stream().map(OclValue::rawValue).toList();
    }
}
