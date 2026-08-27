package de.useweb.backend.domain.uml;

public enum ParameterDirection {
    IN,
    OUT,
    INOUT;

    public static ParameterDirection defaulted(ParameterDirection direction) {
        return direction == null ? IN : direction;
    }
}
