package de.useweb.backend.ocl.value;

public sealed interface OclValue permits BooleanValue, ClassifierValue, CollectionValue, DataTypeValue, EnumValue,
        IntegerValue, ObjectValue, OclInvalidValue, OclVoidValue, RealValue, StringValue, TupleValue,
        UnlimitedNaturalValue {

    String typeName();

    Object rawValue();

    default String valueKind() {
        return "DEFINED";
    }
}
