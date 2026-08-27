package de.useweb.backend.ocl.value;

public record RealValue(double value) implements OclValue {

    @Override
    public String typeName() {
        return "Real";
    }

    @Override
    public Object rawValue() {
        return value;
    }
}
