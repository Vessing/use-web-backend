package de.useweb.backend.ocl.value;

import java.util.Map;

import de.useweb.backend.ocl.typecheck.OclType;

public record ClassifierValue(String classifierId, String qualifiedName,
        OclType representedType) implements OclValue {
    public ClassifierValue {
        if (classifierId == null || classifierId.isBlank() || qualifiedName == null || qualifiedName.isBlank()
                || representedType == null) {
            throw new IllegalArgumentException("Classifier value metadata must not be blank");
        }
    }

    @Override public String typeName() { return "OclType"; }

    @Override public Object rawValue() {
        return Map.of("classifierId", classifierId, "qualifiedName", qualifiedName,
                "representedType", representedType.displayName());
    }
}
