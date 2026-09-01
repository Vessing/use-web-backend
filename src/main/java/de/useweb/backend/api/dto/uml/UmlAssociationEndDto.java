package de.useweb.backend.api.dto.uml;

import java.util.List;

public record UmlAssociationEndDto(
        String id,
        String classId,
        String roleName,
        MultiplicityDto multiplicity,
        boolean navigable,
        Boolean ordered,
        Boolean unique,
        Boolean derived,
        Boolean union,
        List<String> subsettedEndIds,
        List<String> redefinedEndIds,
        String navigationType,
        List<UmlQualifierDefinitionDto> qualifiers,
        String aggregationKind,
        String deriveExpression
) {
    public UmlAssociationEndDto(String id, String classId, String roleName,
            MultiplicityDto multiplicity, boolean navigable) {
        this(id, classId, roleName, multiplicity, navigable, false, true, false, false,
                List.of(), List.of(), null, List.of(), "NONE", null);
    }

    public UmlAssociationEndDto(String id, String classId, String roleName, MultiplicityDto multiplicity,
            boolean navigable, Boolean ordered, Boolean unique, Boolean derived, Boolean union,
            List<String> subsettedEndIds, List<String> redefinedEndIds, String navigationType) {
        this(id, classId, roleName, multiplicity, navigable, ordered, unique, derived, union,
                subsettedEndIds, redefinedEndIds, navigationType, List.of(), "NONE", null);
    }

    public UmlAssociationEndDto(String id, String classId, String roleName, MultiplicityDto multiplicity,
            boolean navigable, Boolean ordered, Boolean unique, Boolean derived, Boolean union,
            List<String> subsettedEndIds, List<String> redefinedEndIds, String navigationType,
            List<UmlQualifierDefinitionDto> qualifiers) {
        this(id, classId, roleName, multiplicity, navigable, ordered, unique, derived, union,
                subsettedEndIds, redefinedEndIds, navigationType, qualifiers, "NONE", null);
    }

    public UmlAssociationEndDto(String id, String classId, String roleName, MultiplicityDto multiplicity,
            boolean navigable, Boolean ordered, Boolean unique, Boolean derived, Boolean union,
            List<String> subsettedEndIds, List<String> redefinedEndIds, String navigationType,
            List<UmlQualifierDefinitionDto> qualifiers, String aggregationKind) {
        this(id, classId, roleName, multiplicity, navigable, ordered, unique, derived, union,
                subsettedEndIds, redefinedEndIds, navigationType, qualifiers, aggregationKind, null);
    }
}
