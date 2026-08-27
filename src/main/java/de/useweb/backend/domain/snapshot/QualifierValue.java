package de.useweb.backend.domain.snapshot;

import de.useweb.backend.domain.uml.UmlQualifierId;

public record QualifierValue(UmlQualifierId qualifierId, SlotValue value) {
    public QualifierValue {
        if (qualifierId == null) throw new IllegalArgumentException("Qualifier value id must not be null");
        if (value == null) throw new IllegalArgumentException("Qualifier value must not be null");
    }
}
