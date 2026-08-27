package de.useweb.backend.domain.snapshot;

import java.util.List;

import de.useweb.backend.domain.uml.UmlAssociationEndId;

public record ObjectLinkEnd(UmlAssociationEndId associationEndId, ObjectInstanceId objectId,
        List<QualifierValue> qualifierValues) {

    public ObjectLinkEnd(UmlAssociationEndId associationEndId, ObjectInstanceId objectId) {
        this(associationEndId, objectId, List.of());
    }

    public ObjectLinkEnd {
        if (associationEndId == null) {
            throw new IllegalArgumentException("ObjectLinkEnd associationEndId must not be null");
        }
        if (objectId == null) {
            throw new IllegalArgumentException("ObjectLinkEnd objectId must not be null");
        }
        qualifierValues = List.copyOf(qualifierValues == null ? List.of() : qualifierValues);
        if (qualifierValues.stream().map(QualifierValue::qualifierId).distinct().count() != qualifierValues.size()) {
            throw new IllegalArgumentException("Qualifier values must be unique per qualifier id");
        }
    }
}
