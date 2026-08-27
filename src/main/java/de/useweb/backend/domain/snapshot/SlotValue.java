package de.useweb.backend.domain.snapshot;

import de.useweb.backend.domain.uml.UmlType;

public record SlotValue(Object value, UmlType valueType) {

    public SlotValue {
        if (valueType == null) {
            throw new IllegalArgumentException("SlotValue valueType must not be null");
        }
    }

    public static SlotValue ofString(String value) {
        return new SlotValue(value, UmlType.STRING);
    }

    public static SlotValue ofInteger(int value) {
        return new SlotValue(value, UmlType.INTEGER);
    }

    public static SlotValue ofReal(double value) {
        return new SlotValue(value, UmlType.REAL);
    }

    public static SlotValue ofBoolean(boolean value) {
        return new SlotValue(value, UmlType.BOOLEAN);
    }
}
