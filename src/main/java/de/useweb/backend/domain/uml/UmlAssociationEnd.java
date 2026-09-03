package de.useweb.backend.domain.uml;

import java.util.List;

public record UmlAssociationEnd(
        UmlAssociationEndId id,
        UmlClassId classId,
        String roleName,
        Multiplicity multiplicity,
        boolean navigable,
        boolean ordered,
        boolean unique,
        boolean derived,
        boolean union,
        List<UmlAssociationEndId> subsettedEndIds,
        List<UmlAssociationEndId> redefinedEndIds,
        List<UmlQualifierDefinition> qualifiers,
        AggregationKind aggregationKind,
        String deriveExpression) {

    public UmlAssociationEnd(UmlAssociationEndId id, UmlClassId classId, String roleName,
            Multiplicity multiplicity, boolean navigable) {
        this(id, classId, roleName, multiplicity, navigable, false, true, false, false, List.of(), List.of(), List.of(), AggregationKind.NONE, null);
    }

    public UmlAssociationEnd(UmlAssociationEndId id, UmlClassId classId, String roleName,
            Multiplicity multiplicity, boolean navigable, boolean ordered, boolean unique,
            boolean derived, boolean union, List<UmlAssociationEndId> subsettedEndIds,
            List<UmlAssociationEndId> redefinedEndIds) {
        this(id, classId, roleName, multiplicity, navigable, ordered, unique, derived, union,
                subsettedEndIds, redefinedEndIds, List.of(), AggregationKind.NONE, null);
    }

    public UmlAssociationEnd(UmlAssociationEndId id, UmlClassId classId, String roleName,
            Multiplicity multiplicity, boolean navigable, boolean ordered, boolean unique,
            boolean derived, boolean union, List<UmlAssociationEndId> subsettedEndIds,
            List<UmlAssociationEndId> redefinedEndIds, List<UmlQualifierDefinition> qualifiers) {
        this(id, classId, roleName, multiplicity, navigable, ordered, unique, derived, union,
                subsettedEndIds, redefinedEndIds, qualifiers, AggregationKind.NONE, null);
    }

    public UmlAssociationEnd(UmlAssociationEndId id, UmlClassId classId, String roleName,
            Multiplicity multiplicity, boolean navigable, boolean ordered, boolean unique,
            boolean derived, boolean union, List<UmlAssociationEndId> subsettedEndIds,
            List<UmlAssociationEndId> redefinedEndIds, List<UmlQualifierDefinition> qualifiers,
            AggregationKind aggregationKind) {
        this(id, classId, roleName, multiplicity, navigable, ordered, unique, derived, union,
                subsettedEndIds, redefinedEndIds, qualifiers, aggregationKind, null);
    }

    public UmlAssociationEnd {
        if (id == null) {
            throw new IllegalArgumentException("UmlAssociationEnd id must not be null");
        }
        if (classId == null) {
            throw new IllegalArgumentException("UmlAssociationEnd classId must not be null");
        }
        // UML Properties used as association ends may be unnamed. The stable end id,
        // rather than a display role, identifies an end throughout the model.
        roleName = roleName == null || roleName.isBlank() ? null : roleName.trim();
        if (multiplicity == null) {
            throw new IllegalArgumentException("UmlAssociationEnd multiplicity must not be null");
        }
        subsettedEndIds = List.copyOf(subsettedEndIds == null ? List.of() : subsettedEndIds);
        redefinedEndIds = List.copyOf(redefinedEndIds == null ? List.of() : redefinedEndIds);
        qualifiers = List.copyOf(qualifiers == null ? List.of() : qualifiers);
        aggregationKind = AggregationKind.defaulted(aggregationKind);
        deriveExpression = deriveExpression == null || deriveExpression.isBlank() ? null : deriveExpression.trim();
        if (union && !derived) {
            throw new IllegalArgumentException("A union association end must also be derived");
        }
        if (deriveExpression != null && !derived) {
            throw new IllegalArgumentException("A derive expression requires a derived association end");
        }
        if (subsettedEndIds.contains(id) || redefinedEndIds.contains(id)) {
            throw new IllegalArgumentException("An association end cannot subset or redefine itself");
        }
        if (qualifiers.stream().map(UmlQualifierDefinition::id).distinct().count() != qualifiers.size()
                || qualifiers.stream().map(UmlQualifierDefinition::name).distinct().count() != qualifiers.size()
                || qualifiers.stream().map(UmlQualifierDefinition::order).distinct().count() != qualifiers.size()) {
            throw new IllegalArgumentException("Qualifier ids, names and order values must be unique per association end");
        }
    }
}
