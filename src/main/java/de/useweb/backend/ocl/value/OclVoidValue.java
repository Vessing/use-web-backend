package de.useweb.backend.ocl.value;

public enum OclVoidValue implements OclValue {
    INSTANCE;

    @Override
    public String typeName() {
        return "OclVoid";
    }

    @Override
    public Object rawValue() {
        return null;
    }

    @Override
    public String valueKind() {
        return "NULL";
    }
}
