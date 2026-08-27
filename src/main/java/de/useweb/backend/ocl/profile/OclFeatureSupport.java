package de.useweb.backend.ocl.profile;

import java.util.Objects;

public record OclFeatureSupport(
        String id,
        String group,
        OclFeatureStatus status,
        String standardBasis,
        String notes) {

    public OclFeatureSupport {
        id = requireText(id, "id");
        group = requireText(group, "group");
        status = Objects.requireNonNull(status, "status");
        standardBasis = requireText(standardBasis, "standardBasis");
        notes = requireText(notes, "notes");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
