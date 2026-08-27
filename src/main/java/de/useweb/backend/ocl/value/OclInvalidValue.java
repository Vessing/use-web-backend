package de.useweb.backend.ocl.value;

public enum OclInvalidValue implements OclValue {
    INSTANCE;

    @Override
    public String typeName() {
        return "OclInvalid";
    }

    @Override
    public Object rawValue() {
        return null;
    }

    @Override
    public String valueKind() {
        return "INVALID";
    }
}
