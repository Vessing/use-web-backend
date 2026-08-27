package de.useweb.backend.domain.uml;

public enum UmlVisibility {
    PUBLIC,
    PRIVATE,
    PROTECTED,
    PACKAGE;

    public static UmlVisibility defaulted(UmlVisibility value) {
        return value == null ? PUBLIC : value;
    }
}
