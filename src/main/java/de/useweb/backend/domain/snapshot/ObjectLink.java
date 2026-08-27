package de.useweb.backend.domain.snapshot;

import java.util.List;

import de.useweb.backend.domain.uml.UmlAssociationId;

public record ObjectLink(
        ObjectLinkId id,
        UmlAssociationId associationId,
        List<ObjectLinkEnd> ends,
        ObjectInstanceId associationClassObjectId) {

    public ObjectLink(ObjectLinkId id, UmlAssociationId associationId, List<ObjectLinkEnd> ends) {
        this(id, associationId, ends, null);
    }

    public ObjectLink {
        if (id == null) {
            throw new IllegalArgumentException("ObjectLink id must not be null");
        }
        if (associationId == null) {
            throw new IllegalArgumentException("ObjectLink associationId must not be null");
        }
        ends = List.copyOf(ends == null ? List.of() : ends);
        if (ends.size() < 2) {
            throw new IllegalArgumentException("Object links must have at least two ends");
        }
    }
}
