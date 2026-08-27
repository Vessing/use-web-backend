package de.useweb.backend.ocl.value;

import de.useweb.backend.domain.uml.UmlEnumerationId;

public record EnumValue(UmlEnumerationId enumerationId, String enumerationName, String literal) implements OclValue {
    public EnumValue {
        if (enumerationId == null || enumerationName == null || enumerationName.isBlank()
                || literal == null || literal.isBlank()) {
            throw new IllegalArgumentException("Enum value metadata must not be blank");
        }
    }

    @Override
    public String typeName() {
        return enumerationName;
    }

    @Override
    public Object rawValue() {
        return enumerationName + "::" + literal;
    }
}
