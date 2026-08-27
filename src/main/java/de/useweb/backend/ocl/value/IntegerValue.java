package de.useweb.backend.ocl.value;

public record IntegerValue(int value) implements OclValue {

    @Override
    public String typeName() {
        return "Integer";
    }

    @Override
    public Object rawValue() {
        return value;
    }
}
