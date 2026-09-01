package de.useweb.backend.api.dto.projection;

import java.util.List;
import java.util.Map;

import de.useweb.backend.api.dto.ocl.SourceRangeDto;
import de.useweb.backend.api.dto.validation.ValidationErrorDto;

/** Additive API-v1 projection for semantic, read-only frontend views. */
public record ProjectReadModelDto(
        String projectId,
        String modelId,
        String snapshotId,
        String readVersion,
        Map<String, Boolean> capabilities,
        List<ExplorerElementDto> explorer,
        List<ClassProjectionDto> classes,
        List<EnumerationProjectionDto> enumerations,
        List<DefinitionProjectionDto> definitions,
        List<ObjectProjectionDto> objects,
        List<ObjectAssociationProjectionDto> objectAssociations,
        List<ValidationErrorDto> diagnostics) {

    public record NamedElementDto(String id, String name, String qualifiedName, String kind) {}

    public record ExplorerElementDto(
            String nodeId, String elementId, String parentNodeId, String name, String qualifiedName, String kind,
            boolean imported, boolean readOnly, String importId, String provenance) {}

    public record ClassProjectionDto(
            String id, String name, String qualifiedName, boolean abstractClass,
            List<NamedElementDto> directSuperClasses,
            List<NamedElementDto> generalizationOrder,
            List<FeatureProjectionDto> attributes,
            List<FeatureProjectionDto> operations) {}

    public record EnumerationProjectionDto(
            String id, String name, String qualifiedName, String packageId, String visibility,
            List<EnumerationLiteralProjectionDto> literals) {}

    public record EnumerationLiteralProjectionDto(String id, String name, int order) {}

    public record FeatureProjectionDto(
            String id, String name, String qualifiedName, String kind, String type,
            NamedElementDto definingClassifier, boolean inherited, boolean derived,
            boolean readOnly, boolean staticFeature, String expression,
            List<NamedElementDto> redefinedFeatures, ValueProjectionDto classifierValue) {}

    public record DefinitionProjectionDto(
            String id, String kind, String name, String qualifiedName,
            NamedElementDto owner, String resultType, List<NamedElementDto> parameters,
            String expression, SourceRangeDto sourceRange, boolean readOnly) {}

    public record ObjectProjectionDto(
            String id, String name, NamedElementDto classifier, List<SlotProjectionDto> slots) {}

    public record SlotProjectionDto(
            String id, String attributeId, String attributeName, String type,
            NamedElementDto definingClassifier, boolean inherited, boolean derived,
            boolean readOnly, String valueStatus, ValueProjectionDto value,
            List<ValidationErrorDto> diagnostics) {}

    public record ValueProjectionDto(
            String status, String type, String kind, Object scalar,
            List<ValueProjectionDto> elements, Map<String, ValueProjectionDto> fields) {}

    public record ObjectAssociationProjectionDto(
            String objectId, String objectName, List<RelatedLinkDto> relatedLinks,
            List<ValidationErrorDto> diagnostics) {}

    public record RelatedLinkDto(
            String id, String name, NamedElementDto association, String projectionKind,
            String associationClassObjectId, List<LinkEndProjectionDto> ends,
            List<ValidationErrorDto> diagnostics) {}

    public record LinkEndProjectionDto(
            String associationEndId, String roleName, NamedElementDto classifier,
            String objectId, String objectName, boolean ordered, boolean unique,
            Integer position, List<QualifierProjectionDto> qualifiers) {}

    public record QualifierProjectionDto(
            String qualifierId, String name, String type, ValueProjectionDto value) {}
}
