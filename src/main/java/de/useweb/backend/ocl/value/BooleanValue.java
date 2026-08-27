package de.useweb.backend.ocl.value;

public record BooleanValue(boolean value) implements OclValue {

    @Override
    public String typeName() {
        return "Boolean";
    }

    @Override
    public Object rawValue() {
        return value;
    }
}
