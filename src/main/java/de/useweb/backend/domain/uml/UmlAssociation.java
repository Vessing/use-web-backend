package de.useweb.backend.domain.uml;

import java.util.List;
import java.util.Optional;

public record UmlAssociation(
        UmlAssociationId id,
        String name,
        List<UmlAssociationEnd> ends,
        UmlClassId associationClassId) {

    public UmlAssociation(UmlAssociationId id, String name, List<UmlAssociationEnd> ends) {
        this(id, name, ends, null);
    }

    public UmlAssociation {
        if (id == null) {
            throw new IllegalArgumentException("UmlAssociation id must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("UmlAssociation name must not be blank");
        }
        ends = List.copyOf(ends == null ? List.of() : ends);
        if (ends.size() < 2) {
            throw new IllegalArgumentException("Associations must have at least two ends");
        }
        if (ends.stream().filter(end -> end.aggregationKind() == AggregationKind.COMPOSITE).count() > 1) {
            throw new IllegalArgumentException("An association may have at most one composite whole end");
        }
    }

    public Optional<UmlAssociationEnd> findEnd(UmlAssociationEndId associationEndId) {
        return ends.stream()
                .filter(end -> end.id().equals(associationEndId))
                .findFirst();
    }
}
