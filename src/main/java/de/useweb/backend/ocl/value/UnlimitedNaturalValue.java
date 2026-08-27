package de.useweb.backend.ocl.value;

public enum UnlimitedNaturalValue implements OclValue {
    UNLIMITED;

    @Override
    public String typeName() {
        return "UnlimitedNatural";
    }

    @Override
    public Object rawValue() {
        return "*";
    }
}
