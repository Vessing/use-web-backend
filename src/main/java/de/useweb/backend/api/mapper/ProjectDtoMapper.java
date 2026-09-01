package de.useweb.backend.api.mapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.useweb.backend.api.dto.common.PointDto;
import de.useweb.backend.api.dto.layout.DiagramLayoutDto;
import de.useweb.backend.api.dto.layout.EdgeLayoutDto;
import de.useweb.backend.api.dto.layout.LayoutDto;
import de.useweb.backend.api.dto.layout.NodeLayoutDto;
import de.useweb.backend.api.dto.layout.ViewportDto;
import de.useweb.backend.api.dto.modeltext.ModelTextDto;
import de.useweb.backend.api.dto.modeltext.ModelTextSourceProvenanceDto;
import de.useweb.backend.api.dto.ocl.OclExpressionDto;
import de.useweb.backend.api.dto.ocl.SourceRangeDto;
import de.useweb.backend.api.dto.ocl.OclDefinitionElementDto;
import de.useweb.backend.api.dto.project.ProjectDto;
import de.useweb.backend.api.dto.project.ProjectMetadataDto;
import de.useweb.backend.api.dto.project.ProjectSummaryDto;
import de.useweb.backend.api.dto.snapshot.ObjectInstanceDto;
import de.useweb.backend.api.dto.snapshot.ObjectLinkDto;
import de.useweb.backend.api.dto.snapshot.ObjectLinkEndValueDto;
import de.useweb.backend.api.dto.snapshot.ObjectModelDto;
import de.useweb.backend.api.dto.snapshot.SlotDto;
import de.useweb.backend.api.dto.snapshot.SlotValueDto;
import de.useweb.backend.api.dto.snapshot.QualifierValueDto;
import de.useweb.backend.api.dto.uml.MultiplicityDto;
import de.useweb.backend.api.dto.uml.UmlAssociationDto;
import de.useweb.backend.api.dto.uml.UmlAssociationEndDto;
import de.useweb.backend.api.dto.uml.UmlAttributeDto;
import de.useweb.backend.api.dto.uml.UmlClassDto;
import de.useweb.backend.api.dto.uml.UmlInvariantDto;
import de.useweb.backend.api.dto.uml.UmlEnumerationDto;
import de.useweb.backend.api.dto.uml.UmlEnumerationLiteralDto;
import de.useweb.backend.api.dto.uml.UmlDataTypeDto;
import de.useweb.backend.api.dto.uml.UmlDataTypePropertyDto;
import de.useweb.backend.api.dto.uml.UmlModelDto;
import de.useweb.backend.api.dto.uml.UmlOperationDto;
import de.useweb.backend.api.dto.uml.UmlOperationContractDto;
import de.useweb.backend.api.dto.uml.UmlPackageDto;
import de.useweb.backend.api.dto.uml.UmlModelImportDto;
import de.useweb.backend.api.dto.uml.UmlParameterDto;
import de.useweb.backend.api.dto.uml.UmlQualifierDefinitionDto;
import de.useweb.backend.api.dto.validation.ElementTargetDto;
import de.useweb.backend.api.dto.validation.ValidationErrorDto;
import de.useweb.backend.api.dto.validation.ValidationResultDto;
import de.useweb.backend.api.dto.validation.ValidationSummaryDto;
import de.useweb.backend.domain.layout.DiagramLayout;
import de.useweb.backend.domain.layout.EdgeLayout;
import de.useweb.backend.domain.layout.LayoutInformation;
import de.useweb.backend.domain.layout.NodeLayout;
import de.useweb.backend.domain.layout.Point;
import de.useweb.backend.domain.layout.Viewport;
import de.useweb.backend.domain.modeltext.ModelText;
import de.useweb.backend.domain.modeltext.ModelTextSourceProvenance;
import de.useweb.backend.domain.ocl.OclExpression;
import de.useweb.backend.domain.ocl.OclExpressionId;
import de.useweb.backend.domain.ocl.OclDefinitionElement;
import de.useweb.backend.domain.ocl.OclDefinitionElementId;
import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.project.ProjectId;
import de.useweb.backend.domain.project.ProjectMetadata;
import de.useweb.backend.domain.snapshot.ObjectInstance;
import de.useweb.backend.domain.snapshot.ObjectInstanceId;
import de.useweb.backend.domain.snapshot.ObjectLink;
import de.useweb.backend.domain.snapshot.ObjectLinkEnd;
import de.useweb.backend.domain.snapshot.QualifierValue;
import de.useweb.backend.domain.snapshot.ObjectLinkId;
import de.useweb.backend.domain.snapshot.ObjectModel;
import de.useweb.backend.domain.snapshot.ObjectModelId;
import de.useweb.backend.domain.snapshot.Slot;
import de.useweb.backend.domain.snapshot.SlotId;
import de.useweb.backend.domain.snapshot.SlotValue;
import de.useweb.backend.domain.uml.Multiplicity;
import de.useweb.backend.domain.uml.AggregationKind;
import de.useweb.backend.domain.uml.PrimitiveType;
import de.useweb.backend.domain.uml.UmlAssociation;
import de.useweb.backend.domain.uml.UmlAssociationEnd;
import de.useweb.backend.domain.uml.UmlAssociationEndId;
import de.useweb.backend.domain.uml.UmlQualifierDefinition;
import de.useweb.backend.domain.uml.UmlQualifierId;
import de.useweb.backend.domain.uml.UmlAssociationId;
import de.useweb.backend.domain.uml.UmlAttribute;
import de.useweb.backend.domain.uml.UmlAttributeId;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlClassifierValue;
import de.useweb.backend.domain.uml.UmlInvariant;
import de.useweb.backend.domain.uml.UmlEnumeration;
import de.useweb.backend.domain.uml.UmlEnumerationId;
import de.useweb.backend.domain.uml.UmlEnumerationLiteral;
import de.useweb.backend.domain.uml.UmlEnumerationLiteralId;
import de.useweb.backend.domain.uml.UmlDataType;
import de.useweb.backend.domain.uml.UmlDataTypeId;
import de.useweb.backend.domain.uml.UmlDataTypeProperty;
import de.useweb.backend.domain.uml.UmlInvariantId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlModelId;
import de.useweb.backend.domain.uml.UmlOperation;
import de.useweb.backend.domain.uml.UmlOperationContract;
import de.useweb.backend.domain.uml.UmlPackage;
import de.useweb.backend.domain.uml.UmlPackageId;
import de.useweb.backend.domain.uml.UmlModelImport;
import de.useweb.backend.domain.uml.UmlModelImportId;
import de.useweb.backend.domain.uml.UmlVisibility;
import de.useweb.backend.domain.uml.UmlOperationId;
import de.useweb.backend.domain.uml.UmlParameter;
import de.useweb.backend.domain.uml.UmlParameterId;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.domain.validation.ElementTarget;
import de.useweb.backend.domain.validation.ElementType;
import de.useweb.backend.domain.validation.ValidationError;
import de.useweb.backend.domain.validation.ValidationErrorCode;
import de.useweb.backend.domain.validation.ValidationErrorId;
import de.useweb.backend.domain.validation.ValidationResult;
import de.useweb.backend.domain.validation.ValidationResultId;
import de.useweb.backend.domain.validation.ValidationSeverity;
import de.useweb.backend.domain.validation.ValidationStatus;
import de.useweb.backend.domain.validation.ValidationSummary;

public final class ProjectDtoMapper {

    private ProjectDtoMapper() {
    }

    public static ProjectDto toDto(Project project) {
        return new ProjectDto(
                project.metadata().formatVersion(),
                toDto(project.id(), project.metadata()),
                toDto(project.id(), project.modelText()),
                toDto(project.umlModel()),
                toDto(project.objectModel()),
                toDto(project.layout()),
                null,
                Map.of(),
                project.definitions().stream().map(definition -> toDto(definition, project.umlModel())).toList());
    }

    public static Project toDomain(ProjectDto dto) {
        return new Project(
                new ProjectId(dto.project().id()),
                new ProjectMetadata(
                        dto.project().name(),
                        dto.project().description(),
                        dto.formatVersion(),
                        dto.project().createdAt(),
                        dto.project().updatedAt()),
                toDomain(dto.modelText()),
                toDomain(dto.umlModel()),
                toDomain(dto.objectModel()),
                dto.layout() == null ? LayoutInformation.empty() : toDomain(dto.layout()),
                safe(dto.definitions()).stream().map(ProjectDtoMapper::toDomain).toList());
    }

    public static OclDefinitionElementDto toDto(OclDefinitionElement definition, UmlModel model) {
        String ownerName = definition.ownerKind() == OclDefinitionElement.OwnerKind.CLASS
                ? model.findClass(new UmlClassId(definition.ownerId())).map(type -> type.qualifiedName(model)).orElse(definition.ownerId())
                : model.findPackage(new UmlPackageId(definition.ownerId())).map(UmlPackage::qualifiedName).orElse(definition.ownerId());
        var parsed = new de.useweb.backend.ocl.parser.OclParser().parse(definition.expression());
        var range = definition.sourceRange() != null ? definition.sourceRange()
                : parsed.ast() == null ? null : parsed.ast().sourceRange();
        SourceRangeDto sourceRange = range == null ? null : new SourceRangeDto(range.start().line(), range.start().column(),
                range.start().offset(), range.end().line(), range.end().column(), range.end().offset());
        return new OclDefinitionElementDto(definition.id().value(), definition.kind().name(),
                definition.ownerKind().name(), definition.ownerId(), ownerName, definition.name(),
                ownerName + "::" + definition.name(), definition.resultType().name(),
                definition.parameters().stream().map(ProjectDtoMapper::toDto).toList(),
                definition.expression(), sourceRange);
    }

    public static OclDefinitionElement toDomain(OclDefinitionElementDto dto) {
        de.useweb.backend.ocl.diagnostics.SourceRange range = dto.sourceRange() == null ? null
                : new de.useweb.backend.ocl.diagnostics.SourceRange(
                        new de.useweb.backend.ocl.diagnostics.SourcePosition(dto.sourceRange().startLine(),
                                dto.sourceRange().startColumn(), dto.sourceRange().startOffset()),
                        new de.useweb.backend.ocl.diagnostics.SourcePosition(dto.sourceRange().endLine(),
                                dto.sourceRange().endColumn(), dto.sourceRange().endOffset()));
        OclDefinitionElement value = new OclDefinitionElement(new OclDefinitionElementId(dto.id()),
                OclDefinitionElement.Kind.valueOf(dto.kind()),
                OclDefinitionElement.OwnerKind.valueOf(dto.ownerKind()), dto.ownerId(), dto.name(),
                new UmlType(dto.resultType()), safe(dto.parameters()).stream().map(ProjectDtoMapper::toDomain).toList(),
                dto.expression(), range);
        if (value.sourceRange() != null) return value;
        var parsed = new de.useweb.backend.ocl.parser.OclParser().parse(value.expression());
        return new OclDefinitionElement(value.id(), value.kind(), value.ownerKind(), value.ownerId(), value.name(),
                value.resultType(), value.parameters(), value.expression(),
                parsed.ast() == null ? null : parsed.ast().sourceRange());
    }

    public static ProjectSummaryDto toSummaryDto(Project project) {
        return new ProjectSummaryDto(
                project.id().value(),
                project.metadata().name(),
                project.metadata().description(),
                project.metadata().updatedAt(),
                sourceFormat(project));
    }

    private static String sourceFormat(Project project) {
        return project.modelText() != null && Objects.equals("example-project", project.modelText().sourceOrigin())
                ? "example"
                : "json";
    }

    public static ValidationResultDto toDto(ValidationResult result) {
        return new ValidationResultDto(
                result.id().value(),
                result.projectId().value(),
                result.objectModelId().value(),
                result.status().name(),
                result.checkedAt(),
                result.findings().stream().map(ProjectDtoMapper::toDto).toList(),
                toDto(result.summary()));
    }

    public static ValidationResult toDomain(ValidationResultDto dto) {
        return new ValidationResult(
                new ValidationResultId(dto.id()),
                new ProjectId(dto.projectId()),
                new ObjectModelId(dto.objectModelId()),
                ValidationStatus.valueOf(dto.status()),
                dto.checkedAt(),
                dto.findings().stream().map(ProjectDtoMapper::toDomain).toList(),
                toDomain(dto.summary()));
    }

    private static ProjectMetadataDto toDto(ProjectId id, ProjectMetadata metadata) {
        return new ProjectMetadataDto(
                id.value(),
                metadata.name(),
                metadata.description(),
                metadata.createdAt(),
                metadata.updatedAt());
    }

    public static ModelTextDto toDto(ProjectId projectId, ModelText modelText) {
        if (modelText == null) {
            return null;
        }
        return new ModelTextDto(
                projectId.value(),
                modelText.text(),
                modelText.language(),
                modelText.languageVersion(),
                modelText.sourceName(),
                modelText.sourceOrigin(),
                "LF",
                modelText.updatedAt(),
                modelText.sources().stream().map(source -> new ModelTextSourceProvenanceDto(
                        source.sourcePath(), source.importedBy(), source.selectedNames(), source.depth(), source.sha256()))
                        .toList());
    }

    public static ModelText toDomain(ModelTextDto dto) {
        if (dto == null) {
            return null;
        }
        return new ModelText(
                dto.modelText(),
                dto.format(),
                dto.version(),
                dto.updatedAt(),
                dto.sourceName(),
                dto.sourceOrigin(),
                safe(dto.sources()).stream().map(source -> new ModelTextSourceProvenance(
                        source.sourcePath(), source.importedBy(), source.selectedNames(), source.depth(), source.sha256()))
                        .toList());
    }

    public static UmlModelDto toDto(UmlModel model) {
        return new UmlModelDto(
                model.id().value(),
                model.name(),
                List.of("String", "Integer", "Real", "Boolean", "UnlimitedNatural"),
                model.classes().stream().map(umlClass -> toDto(umlClass, model)).toList(),
                model.associations().stream().map(ProjectDtoMapper::toDto).toList(),
                model.invariants().stream().map(ProjectDtoMapper::toDto).toList(),
                model.enumerations().stream().map(enumeration -> toDto(enumeration, model)).toList(),
                model.packages().stream().map(ProjectDtoMapper::toDto).toList(),
                model.imports().stream().map(ProjectDtoMapper::toDto).toList(),
                model.dataTypes().stream().map(dataType -> toDto(dataType, model)).toList());
    }

    public static UmlModel toDomain(UmlModelDto dto) {
        return new UmlModel(
                new UmlModelId(dto.id()),
                dto.name(),
                dto.classes().stream().map(ProjectDtoMapper::toDomain).toList(),
                dto.associations().stream().map(ProjectDtoMapper::toDomain).toList(),
                dto.invariants().stream().map(ProjectDtoMapper::toDomain).toList(),
                safe(dto.enumerations()).stream().map(ProjectDtoMapper::toDomain).toList(),
                safe(dto.packages()).stream().map(ProjectDtoMapper::toDomain).toList(),
                safe(dto.imports()).stream().map(ProjectDtoMapper::toDomain).toList(),
                safe(dto.dataTypes()).stream().map(ProjectDtoMapper::toDomain).toList());
    }

    public static UmlClassDto toDto(UmlClass umlClass) {
        return toDto(umlClass, null);
    }

    private static UmlClassDto toDto(UmlClass umlClass, UmlModel model) {
        return new UmlClassDto(
                umlClass.id().value(),
                umlClass.name(),
                umlClass.attributes().stream().map(ProjectDtoMapper::toDto).toList(),
                umlClass.operations().stream().map(ProjectDtoMapper::toDto).toList(),
                umlClass.abstractClass(),
                umlClass.superClassIds().stream().map(UmlClassId::value).toList(),
                umlClass.visibility().name(),
                umlClass.packageId() == null ? null : umlClass.packageId().value(),
                model == null ? umlClass.name() : umlClass.qualifiedName(model));
    }

    public static UmlClass toDomain(UmlClassDto dto) {
        return new UmlClass(
                new UmlClassId(dto.id()),
                dto.name(),
                dto.attributes().stream().map(ProjectDtoMapper::toDomain).toList(),
                dto.operations().stream().map(ProjectDtoMapper::toDomain).toList(),
                dto.abstractClass(),
                safe(dto.superClassIds()).stream().map(UmlClassId::new).toList(),
                visibility(dto.visibility()),
                dto.packageId() == null || dto.packageId().isBlank() ? null : new UmlPackageId(dto.packageId()));
    }

    public static UmlEnumerationDto toDto(UmlEnumeration enumeration) {
        return toDto(enumeration, null);
    }

    private static UmlEnumerationDto toDto(UmlEnumeration enumeration, UmlModel model) {
        return new UmlEnumerationDto(enumeration.id().value(), enumeration.name(), enumeration.literals(),
                enumeration.packageId() == null ? null : enumeration.packageId().value(),
                model == null ? enumeration.name() : enumeration.qualifiedName(model), enumeration.visibility().name(),
                enumeration.literalDefinitions().stream().map(literal ->
                        new UmlEnumerationLiteralDto(literal.id().value(), literal.name())).toList());
    }

    public static UmlEnumeration toDomain(UmlEnumerationDto dto) {
        UmlEnumerationId enumerationId = new UmlEnumerationId(dto.id());
        List<UmlEnumerationLiteral> definitions = safe(dto.literalDefinitions()).isEmpty()
                ? legacyEnumerationLiterals(enumerationId, safe(dto.literals()))
                : dto.literalDefinitions().stream().map(literal -> new UmlEnumerationLiteral(
                        new UmlEnumerationLiteralId(literal.id()), literal.name())).toList();
        return new UmlEnumeration(enumerationId, dto.name(), definitions,
                dto.packageId() == null ? null : new UmlPackageId(dto.packageId()), visibility(dto.visibility()));
    }

    private static List<UmlEnumerationLiteral> legacyEnumerationLiterals(UmlEnumerationId enumerationId,
            List<String> literals) {
        int[] index = {0};
        return literals.stream().map(name -> new UmlEnumerationLiteral(
                new UmlEnumerationLiteralId(enumerationId.value() + ":literal:" + index[0]++), name)).toList();
    }

    public static UmlDataTypeDto toDto(UmlDataType dataType, UmlModel model) {
        return new UmlDataTypeDto(dataType.id().value(), dataType.name(), dataType.properties().stream()
                .map(property -> new UmlDataTypePropertyDto(property.id(), property.name(), property.type().name()))
                .toList(), dataType.packageId() == null ? null : dataType.packageId().value(), dataType.qualifiedName(model),
                dataType.operations().stream().map(ProjectDtoMapper::toDto).toList());
    }

    public static UmlDataType toDomain(UmlDataTypeDto dto) {
        return new UmlDataType(new UmlDataTypeId(dto.id()), dto.name(), safe(dto.properties()).stream()
                .map(property -> new UmlDataTypeProperty(property.id(), property.name(), new UmlType(property.type())))
                .toList(), dto.packageId() == null ? null : new UmlPackageId(dto.packageId()),
                safe(dto.operations()).stream().map(ProjectDtoMapper::toDomain).toList());
    }

    public static UmlPackageDto toDto(UmlPackage umlPackage) {
        return new UmlPackageDto(umlPackage.id().value(), umlPackage.qualifiedName());
    }

    public static UmlPackage toDomain(UmlPackageDto dto) {
        return new UmlPackage(new UmlPackageId(dto.id()), dto.qualifiedName());
    }

    public static UmlModelImportDto toDto(UmlModelImport modelImport) {
        return new UmlModelImportDto(modelImport.id().value(), modelImport.importingPackageId().value(),
                modelImport.importedPackageId().value(), modelImport.alias(), modelImport.source(),
                modelImport.provenance());
    }

    public static UmlModelImport toDomain(UmlModelImportDto dto) {
        return new UmlModelImport(new UmlModelImportId(dto.id()), new UmlPackageId(dto.importingPackageId()),
                new UmlPackageId(dto.importedPackageId()), dto.alias(), dto.source(), dto.provenance());
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    public static UmlAttributeDto toDto(UmlAttribute attribute) {
        return new UmlAttributeDto(attribute.id().value(), attribute.name(), attribute.type().name(),
                attribute.derived(), attribute.deriveExpression(), attribute.initExpression(), attribute.visibility().name(),
                attribute.redefinedAttributeIds().stream().map(UmlAttributeId::value).toList(),
                attribute.staticAttribute(), attribute.classifierValue() == null ? null
                        : new SlotValueDto(attribute.classifierValue().valueType().name(),
                                attribute.classifierValue().value()));
    }

    public static UmlAttribute toDomain(UmlAttributeDto dto) {
        return new UmlAttribute(new UmlAttributeId(dto.id()), dto.name(), typeOf(dto.type()),
                Boolean.TRUE.equals(dto.derived()), dto.deriveExpression(), dto.initExpression(), visibility(dto.visibility()),
                safe(dto.redefinedAttributeIds()).stream().map(UmlAttributeId::new).toList(),
                Boolean.TRUE.equals(dto.staticAttribute()), dto.classifierValue() == null ? null
                        : new UmlClassifierValue(typeOf(dto.classifierValue().type()), dto.classifierValue().value()));
    }

    public static UmlOperationDto toDto(UmlOperation operation) {
        return new UmlOperationDto(
                operation.id().value(),
                operation.name(),
                operation.returnType().name(),
                operation.parameters().stream().map(ProjectDtoMapper::toDto).toList(),
                operation.bodyExpression(), operation.visibility().name(),
                operation.abstractOperation(), operation.query(), operation.staticOperation(), operation.contracts().stream()
                        .map(contract -> new UmlOperationContractDto(contract.id(), contract.name(),
                                contract.kind().name(), contract.expression(), contract.enabled()))
                        .toList(), safe(operation.redefinedOperationIds()).stream().map(UmlOperationId::value).toList());
    }

    public static UmlOperation toDomain(UmlOperationDto dto) {
        return new UmlOperation(
                new UmlOperationId(dto.id()),
                dto.name(),
                typeOf(dto.returnType()),
                dto.parameters().stream().map(ProjectDtoMapper::toDomain).toList(),
                dto.bodyExpression(), visibility(dto.visibility()),
                Boolean.TRUE.equals(dto.abstractOperation()), Boolean.TRUE.equals(dto.query()),
                Boolean.TRUE.equals(dto.staticOperation()), safe(dto.contracts()).stream()
                        .map(contract -> new UmlOperationContract(contract.id(), contract.name(),
                                UmlOperationContract.Kind.valueOf(contract.kind().toUpperCase()),
                                contract.expression(), !Boolean.FALSE.equals(contract.enabled())))
                        .toList(), safe(dto.redefinedOperationIds()).stream().map(UmlOperationId::new).toList());
    }

    public static UmlParameterDto toDto(UmlParameter parameter) {
        return new UmlParameterDto(parameter.id().value(), parameter.name(), parameter.type().name(),
                parameter.direction().name(), parameter.position());
    }

    public static UmlParameter toDomain(UmlParameterDto dto) {
        return new UmlParameter(new UmlParameterId(dto.id()), dto.name(), typeOf(dto.type()),
                dto.direction() == null ? de.useweb.backend.domain.uml.ParameterDirection.IN
                        : de.useweb.backend.domain.uml.ParameterDirection.valueOf(dto.direction().toUpperCase()),
                dto.position() == null ? 0 : dto.position());
    }

    public static UmlAssociationDto toDto(UmlAssociation association) {
        return new UmlAssociationDto(
                association.id().value(),
                association.name(),
                association.ends().stream().map(ProjectDtoMapper::toDto).toList(),
                association.associationClassId() == null ? null : association.associationClassId().value());
    }

    public static UmlAssociation toDomain(UmlAssociationDto dto) {
        return new UmlAssociation(
                new UmlAssociationId(dto.id()),
                dto.name(),
                dto.ends().stream().map(ProjectDtoMapper::toDomain).toList(),
                dto.associationClassId() == null ? null : new UmlClassId(dto.associationClassId()));
    }

    public static UmlAssociationEndDto toDto(UmlAssociationEnd associationEnd) {
        return new UmlAssociationEndDto(
                associationEnd.id().value(),
                associationEnd.classId().value(),
                associationEnd.roleName(),
                toDto(associationEnd.multiplicity()),
                associationEnd.navigable(), associationEnd.ordered(), associationEnd.unique(),
                associationEnd.derived(), associationEnd.union(),
                associationEnd.subsettedEndIds().stream().map(UmlAssociationEndId::value).toList(),
                associationEnd.redefinedEndIds().stream().map(UmlAssociationEndId::value).toList(),
                navigationType(associationEnd), associationEnd.qualifiers().stream()
                        .map(qualifier -> new UmlQualifierDefinitionDto(qualifier.id().value(), qualifier.name(),
                                qualifier.type().name(), qualifier.order()))
                        .toList(), associationEnd.aggregationKind().name(), associationEnd.deriveExpression());
    }

    public static UmlAssociationEnd toDomain(UmlAssociationEndDto dto) {
        return new UmlAssociationEnd(
                new UmlAssociationEndId(dto.id()),
                new UmlClassId(dto.classId()),
                dto.roleName(),
                toDomain(dto.multiplicity()),
                dto.navigable(), Boolean.TRUE.equals(dto.ordered()), !Boolean.FALSE.equals(dto.unique()),
                Boolean.TRUE.equals(dto.derived()), Boolean.TRUE.equals(dto.union()),
                (dto.subsettedEndIds() == null ? List.<String>of() : dto.subsettedEndIds()).stream()
                        .map(UmlAssociationEndId::new).toList(),
                (dto.redefinedEndIds() == null ? List.<String>of() : dto.redefinedEndIds()).stream()
                        .map(UmlAssociationEndId::new).toList(),
                (dto.qualifiers() == null ? List.<UmlQualifierDefinitionDto>of() : dto.qualifiers()).stream()
                        .map(qualifier -> new UmlQualifierDefinition(new UmlQualifierId(qualifier.id()),
                                qualifier.name(), typeOf(qualifier.type()), qualifier.order() == null ? 0 : qualifier.order()))
                        .toList(), dto.aggregationKind() == null ? AggregationKind.NONE
                                : AggregationKind.valueOf(dto.aggregationKind().toUpperCase()), dto.deriveExpression());
    }

    private static String navigationType(UmlAssociationEnd end) {
        if (!end.multiplicity().unbounded() && end.multiplicity().upper() != null
                && end.multiplicity().upper() <= 1) {
            return "SINGLE";
        }
        if (end.ordered()) return end.unique() ? "ORDERED_SET" : "SEQUENCE";
        return end.unique() ? "SET" : "BAG";
    }

    public static MultiplicityDto toDto(Multiplicity multiplicity) {
        return new MultiplicityDto(
                multiplicity.lower(),
                multiplicity.upper(),
                multiplicity.unbounded(),
                multiplicity.raw());
    }

    public static Multiplicity toDomain(MultiplicityDto dto) {
        return new Multiplicity(dto.lower(), dto.upper(), dto.unbounded(), dto.raw());
    }

    public static UmlInvariantDto toDto(UmlInvariant invariant) {
        return new UmlInvariantDto(
                invariant.id().value(),
                invariant.name(),
                invariant.contextClassId().value(),
                toDto(invariant.expression()),
                invariant.enabled(), invariant.contextVariableNames(), invariant.existential());
    }

    public static UmlInvariant toDomain(UmlInvariantDto dto) {
        return new UmlInvariant(
                new UmlInvariantId(dto.id()),
                dto.name(),
                new UmlClassId(dto.contextClassId()),
                toDomain(dto.expression()),
                dto.enabled(), safe(dto.contextVariableNames()), Boolean.TRUE.equals(dto.existential()));
    }

    public static OclExpressionDto toDto(OclExpression expression) {
        return new OclExpressionDto(
                expression.id().value(),
                expression.text(),
                "OCL",
                expression.languageVersion());
    }

    public static OclExpression toDomain(OclExpressionDto dto) {
        return new OclExpression(new OclExpressionId(dto.id()), dto.text(), dto.languageVersion());
    }

    public static ObjectModelDto toDto(ObjectModel objectModel) {
        return new ObjectModelDto(
                objectModel.id().value(),
                objectModel.name(),
                objectModel.objects().stream().map(ProjectDtoMapper::toDto).toList(),
                objectModel.links().stream().map(ProjectDtoMapper::toDto).toList());
    }

    public static ObjectModel toDomain(ObjectModelDto dto) {
        return new ObjectModel(
                new ObjectModelId(dto.id()),
                dto.name(),
                dto.objects().stream().map(ProjectDtoMapper::toDomain).toList(),
                dto.links().stream().map(ProjectDtoMapper::toDomain).toList());
    }

    public static ObjectInstanceDto toDto(ObjectInstance objectInstance) {
        return new ObjectInstanceDto(
                objectInstance.id().value(),
                objectInstance.name(),
                objectInstance.classId().value(),
                objectInstance.slots().stream().map(ProjectDtoMapper::toDto).toList());
    }

    public static ObjectInstance toDomain(ObjectInstanceDto dto) {
        return new ObjectInstance(
                new ObjectInstanceId(dto.id()),
                dto.name(),
                new UmlClassId(dto.classId()),
                dto.slots().stream().map(ProjectDtoMapper::toDomain).toList());
    }

    public static SlotDto toDto(Slot slot) {
        return new SlotDto(
                slot.id().value(),
                slot.attributeId().value(),
                new SlotValueDto(slot.value().valueType().name(), slot.value().value()),
                false);
    }

    public static Slot toDomain(SlotDto dto) {
        return new Slot(
                new SlotId(dto.id()),
                new UmlAttributeId(dto.attributeId()),
                new SlotValue(dto.value().value(), typeOf(dto.value().type())));
    }

    public static ObjectLinkDto toDto(ObjectLink link) {
        return new ObjectLinkDto(
                link.id().value(),
                link.associationId().value(),
                link.ends().stream().map(ProjectDtoMapper::toDto).toList(),
                link.associationClassObjectId() == null ? null : link.associationClassObjectId().value());
    }

    public static ObjectLink toDomain(ObjectLinkDto dto) {
        return new ObjectLink(
                new ObjectLinkId(dto.id()),
                new UmlAssociationId(dto.associationId()),
                dto.endValues().stream().map(ProjectDtoMapper::toDomain).toList(),
                dto.associationClassObjectId() == null ? null : new ObjectInstanceId(dto.associationClassObjectId()));
    }

    public static ObjectLinkEndValueDto toDto(ObjectLinkEnd end) {
        return new ObjectLinkEndValueDto(end.associationEndId().value(), end.objectId().value(),
                end.qualifierValues().stream().map(value -> new QualifierValueDto(value.qualifierId().value(),
                        new SlotValueDto(value.value().valueType().name(), value.value().value()))).toList());
    }

    public static ObjectLinkEnd toDomain(ObjectLinkEndValueDto dto) {
        return new ObjectLinkEnd(new UmlAssociationEndId(dto.associationEndId()), new ObjectInstanceId(dto.objectId()),
                (dto.qualifierValues() == null ? List.<QualifierValueDto>of() : dto.qualifierValues()).stream()
                        .map(value -> new QualifierValue(new UmlQualifierId(value.qualifierId()),
                                new SlotValue(value.value().value(), typeOf(value.value().type())))).toList());
    }

    private static LayoutDto toDto(LayoutInformation layout) {
        return new LayoutDto(toDto(layout.classDiagram()), toDto(layout.objectDiagram()));
    }

    private static LayoutInformation toDomain(LayoutDto dto) {
        return new LayoutInformation(toDomain(dto.classDiagram()), toDomain(dto.objectDiagram()));
    }

    private static DiagramLayoutDto toDto(DiagramLayout layout) {
        return new DiagramLayoutDto(
                layout.nodes().stream().map(ProjectDtoMapper::toDto).toList(),
                layout.edges().stream().map(ProjectDtoMapper::toDto).toList(),
                layout.viewport() == null ? null : toDto(layout.viewport()));
    }

    private static DiagramLayout toDomain(DiagramLayoutDto dto) {
        return new DiagramLayout(
                dto.nodes() == null ? List.of() : dto.nodes().stream().map(ProjectDtoMapper::toDomain).toList(),
                dto.edges() == null ? List.of() : dto.edges().stream().map(ProjectDtoMapper::toDomain).toList(),
                dto.viewport() == null ? null : toDomain(dto.viewport()));
    }

    private static NodeLayoutDto toDto(NodeLayout layout) {
        return new NodeLayoutDto(layout.elementId(), layout.x(), layout.y(), layout.width(), layout.height());
    }

    private static NodeLayout toDomain(NodeLayoutDto dto) {
        return new NodeLayout(dto.elementId(), dto.x(), dto.y(), dto.width(), dto.height());
    }

    private static EdgeLayoutDto toDto(EdgeLayout layout) {
        return new EdgeLayoutDto(
                layout.elementId(),
                layout.bendPoints().stream().map(ProjectDtoMapper::toDto).toList(),
                layout.labelPosition() == null ? null : toDto(layout.labelPosition()));
    }

    private static EdgeLayout toDomain(EdgeLayoutDto dto) {
        return new EdgeLayout(
                dto.elementId(),
                dto.bendPoints() == null ? List.of() : dto.bendPoints().stream().map(ProjectDtoMapper::toDomain).toList(),
                dto.labelPosition() == null ? null : toDomain(dto.labelPosition()));
    }

    private static PointDto toDto(Point point) {
        return new PointDto(point.x(), point.y());
    }

    private static Point toDomain(PointDto dto) {
        return new Point(dto.x(), dto.y());
    }

    private static ViewportDto toDto(Viewport viewport) {
        return new ViewportDto(viewport.x(), viewport.y(), viewport.zoom());
    }

    private static Viewport toDomain(ViewportDto dto) {
        return new Viewport(dto.x(), dto.y(), dto.zoom());
    }

    private static ValidationErrorDto toDto(ValidationError error) {
        List<ElementTargetDto> targets = error.targets().stream().map(ProjectDtoMapper::toDto).toList();
        ElementTargetDto primaryTarget = targets.isEmpty() ? null : targets.getFirst();
        return new ValidationErrorDto(
                error.id().value(),
                "VALIDATION_ERROR",
                error.code().name(),
                error.severity().name(),
                error.message(),
                userMessage(error),
                error.message(),
                primaryTarget == null ? null : primaryTarget.elementType(),
                primaryTarget == null ? null : primaryTarget.elementId(),
                targets.stream().map(ElementTargetDto::elementId).distinct().toList(),
                stringDetail(error.details(), "contextClassId"),
                stringDetail(error.details(), "contextObjectId"),
                stringDetail(error.details(), "invariantId"),
                stringDetail(error.details(), "expression"),
                sourceRange(error.details()),
                targets,
                error.details(),
                suggestedFix(error));
    }

    private static ValidationError toDomain(ValidationErrorDto dto) {
        return new ValidationError(
                new ValidationErrorId(dto.id()),
                ValidationErrorCode.valueOf(dto.code()),
                ValidationSeverity.valueOf(dto.severity()),
                dto.message(),
                dto.targets().stream().map(ProjectDtoMapper::toDomain).toList(),
                dto.details());
    }

    private static ElementTargetDto toDto(ElementTarget target) {
        return new ElementTargetDto(target.elementType().name(), target.elementId(), target.path());
    }

    private static ElementTarget toDomain(ElementTargetDto dto) {
        return new ElementTarget(ElementType.valueOf(dto.elementType()), dto.elementId(), dto.path());
    }

    private static ValidationSummaryDto toDto(ValidationSummary summary) {
        return new ValidationSummaryDto(summary.errorCount(), summary.warningCount(), summary.infoCount());
    }

    private static ValidationSummary toDomain(ValidationSummaryDto dto) {
        return new ValidationSummary(dto.errorCount(), dto.warningCount(), dto.infoCount());
    }

    private static String userMessage(ValidationError error) {
        Object explicitUserMessage = error.details().get("userMessage");
        if (explicitUserMessage instanceof String value && !value.isBlank()) {
            return value;
        }
        return switch (error.code()) {
            case SYNTAX_ERROR -> "Der OCL-Ausdruck ist syntaktisch ungueltig.";
            case TYPE_ERROR -> "Der OCL-Ausdruck oder ein Modellwert verletzt Typregeln.";
            case UNKNOWN_CLASS -> "Eine referenzierte Klasse existiert nicht.";
            case UNKNOWN_ATTRIBUTE -> "Ein referenziertes Attribut oder eine Property existiert nicht.";
            case INVALID_SLOT_VALUE -> "Ein Objektwert passt nicht zum erwarteten Attributtyp.";
            case INVALID_LINK -> "Ein Objektlink passt nicht zur definierten Association.";
            case ASSOCIATION_CLASS_IDENTITY_VIOLATION -> "Link und Linkobjekt besitzen keine eindeutige gemeinsame Identitaet.";
            case COMPOSITE_OWNERSHIP_VIOLATION -> "Ein Teilobjekt besitzt mehr als ein Composite-Ganzes.";
            case COMPOSITION_CYCLE -> "Composition-Beziehungen bilden einen unzulaessigen Zyklus.";
            case MULTIPLICITY_VIOLATION -> "Eine Multiplizitaet ist verletzt.";
            case INVARIANT_VIOLATION -> invariantViolationUserMessage(error);
            case EVALUATION_ERROR -> "Ein OCL-Ausdruck konnte fuer den aktuellen Snapshot nicht ausgewertet werden.";
        };
    }

    private static String invariantViolationUserMessage(ValidationError error) {
        String contextObjectName = stringDetail(error.details(), "contextObjectName");
        String invariantName = stringDetail(error.details(), "invariantName");
        if (contextObjectName != null && invariantName != null) {
            return "Das Objekt '" + contextObjectName + "' verletzt die Invariante '" + invariantName + "'.";
        }
        return "Eine OCL-Invariante ist verletzt.";
    }

    private static String suggestedFix(ValidationError error) {
        return switch (error.code()) {
            case SYNTAX_ERROR -> "Korrigiere die OCL-Syntax an der markierten Stelle.";
            case TYPE_ERROR -> "Pruefe die Typen im OCL-Ausdruck und im UML-Modell.";
            case UNKNOWN_CLASS -> "Waehle eine vorhandene Klasse oder korrigiere die Referenz.";
            case UNKNOWN_ATTRIBUTE -> "Waehle ein vorhandenes Attribut, eine Rolle oder korrigiere die OCL-Navigation.";
            case INVALID_SLOT_VALUE -> "Setze einen Wert, der zum Typ des Attributs passt.";
            case INVALID_LINK -> "Erstelle den Link mit Objekten der passenden Klassen.";
            case ASSOCIATION_CLASS_IDENTITY_VIOLATION -> "Verbinde genau ein passendes Linkobjekt mit genau einem Link.";
            case COMPOSITE_OWNERSHIP_VIOLATION -> "Entferne die zusaetzliche Composite-Ownership des Teilobjekts.";
            case COMPOSITION_CYCLE -> "Entferne mindestens eine Composition-Beziehung aus dem Zyklus.";
            case MULTIPLICITY_VIOLATION -> "Passe Objektlinks oder Multiplizitaet an.";
            case INVARIANT_VIOLATION -> "Korrigiere den Objektzustand oder passe die Invariante an.";
            case EVALUATION_ERROR -> "Pruefe fehlende Slot-Werte, Links und OCL-Navigation.";
        };
    }

    private static SourceRangeDto sourceRange(Map<String, Object> details) {
        Integer startLine = integerDetail(details, "startLine");
        Integer startColumn = integerDetail(details, "startColumn");
        Integer endLine = integerDetail(details, "endLine");
        Integer endColumn = integerDetail(details, "endColumn");
        if (startLine == null || startColumn == null || endLine == null || endColumn == null) {
            return null;
        }
        return new SourceRangeDto(startLine, startColumn, -1, endLine, endColumn, -1);
    }

    private static String stringDetail(Map<String, Object> details, String key) {
        Object value = details.get(key);
        return value instanceof String stringValue && !stringValue.isBlank() ? stringValue : null;
    }

    private static Integer integerDetail(Map<String, Object> details, String key) {
        Object value = details.get(key);
        if (value instanceof Integer integerValue) {
            return integerValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        return null;
    }

    private static UmlType typeOf(String typeName) {
        for (PrimitiveType primitiveType : PrimitiveType.values()) {
            if (primitiveType.displayName().equals(typeName)) {
                return switch (primitiveType) {
                    case STRING -> UmlType.STRING;
                    case INTEGER -> UmlType.INTEGER;
                    case REAL -> UmlType.REAL;
                    case BOOLEAN -> UmlType.BOOLEAN;
                };
            }
        }
        if (Objects.equals("Void", typeName)) {
            return UmlType.VOID;
        }
        return UmlType.classType(typeName);
    }

    private static UmlVisibility visibility(String value) {
        return value == null || value.isBlank() ? UmlVisibility.PUBLIC : UmlVisibility.valueOf(value.toUpperCase());
    }
}
