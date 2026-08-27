package de.useweb.backend.ocl.value;

public record StringValue(String value) implements OclValue {

    @Override
    public String typeName() {
        return "String";
    }

    @Override
    public Object rawValue() {
        return value;
    }
}
