package de.useweb.backend.application.projection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import de.useweb.backend.api.dto.ocl.SourceRangeDto;
import de.useweb.backend.api.dto.projection.ProjectReadModelDto;
import de.useweb.backend.api.dto.projection.ProjectReadModelDto.ClassProjectionDto;
import de.useweb.backend.api.dto.projection.ProjectReadModelDto.DefinitionProjectionDto;
import de.useweb.backend.api.dto.projection.ProjectReadModelDto.ExplorerElementDto;
import de.useweb.backend.api.dto.projection.ProjectReadModelDto.EnumerationLiteralProjectionDto;
import de.useweb.backend.api.dto.projection.ProjectReadModelDto.EnumerationProjectionDto;
import de.useweb.backend.api.dto.projection.ProjectReadModelDto.FeatureProjectionDto;
import de.useweb.backend.api.dto.projection.ProjectReadModelDto.LinkEndProjectionDto;
import de.useweb.backend.api.dto.projection.ProjectReadModelDto.NamedElementDto;
import de.useweb.backend.api.dto.projection.ProjectReadModelDto.ObjectAssociationProjectionDto;
import de.useweb.backend.api.dto.projection.ProjectReadModelDto.ObjectProjectionDto;
import de.useweb.backend.api.dto.projection.ProjectReadModelDto.QualifierProjectionDto;
import de.useweb.backend.api.dto.projection.ProjectReadModelDto.RelatedLinkDto;
import de.useweb.backend.api.dto.projection.ProjectReadModelDto.SlotProjectionDto;
import de.useweb.backend.api.dto.projection.ProjectReadModelDto.ValueProjectionDto;
import de.useweb.backend.api.dto.validation.ValidationErrorDto;
import de.useweb.backend.api.mapper.ProjectDtoMapper;
import de.useweb.backend.application.project.ProjectService;
import de.useweb.backend.application.ocl.OclDefinitionApplicationService;
import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.project.ProjectId;
import de.useweb.backend.domain.snapshot.ObjectInstance;
import de.useweb.backend.domain.snapshot.ObjectInstanceId;
import de.useweb.backend.domain.snapshot.ObjectLink;
import de.useweb.backend.domain.snapshot.Slot;
import de.useweb.backend.domain.snapshot.SlotValue;
import de.useweb.backend.domain.uml.UmlAssociation;
import de.useweb.backend.domain.uml.UmlAssociationEnd;
import de.useweb.backend.domain.uml.UmlAttribute;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlOperation;
import de.useweb.backend.modeltext.importer.ModelTextImportResolver;
import de.useweb.backend.ocl.definition.OclDefinition;
import de.useweb.backend.ocl.definition.OclDefinitionService;
import de.useweb.backend.ocl.definition.OclModelDefinitionFactory;
import de.useweb.backend.ocl.value.CollectionValue;
import de.useweb.backend.ocl.value.DataTypeValue;
import de.useweb.backend.ocl.value.OclInvalidValue;
import de.useweb.backend.ocl.value.OclValue;
import de.useweb.backend.ocl.value.OclVoidValue;
import de.useweb.backend.ocl.value.TupleValue;
import de.useweb.backend.validation.service.ValidationService;

@Service
public class ProjectReadModelService {
    private final ProjectService projectService;
    private final ValidationService validationService;
    private final OclDefinitionApplicationService definitionService;
    private final ModelTextImportResolver importResolver;

    public ProjectReadModelService(ProjectService projectService, ValidationService validationService,
            OclDefinitionApplicationService definitionService, ModelTextImportResolver importResolver) {
        this.projectService = projectService;
        this.validationService = validationService;
        this.definitionService = definitionService;
        this.importResolver = importResolver;
    }

    public ProjectReadModelDto get(ProjectId projectId) {
        Project project = projectService.loadProject(projectId);
        List<ValidationErrorDto> diagnostics = ProjectDtoMapper.toDto(validationService.validate(project)).findings();
        List<OclDefinition> definitions = definitions(project);
        return new ProjectReadModelDto(
                project.id().value(), project.umlModel().id().value(), project.objectModel().id().value(),
                project.metadata().updatedAt().toString(),
                Map.of("staticFeatures", true, "featureRedefinition", true,
                        "typedValues", true, "naryLinks", true, "associationClasses", true),
                explorer(project), classes(project, definitions), enumerations(project.umlModel()),
                definitionViews(project, definitions),
                objects(project, definitions, diagnostics), objectAssociations(project, diagnostics), diagnostics);
    }

    private List<EnumerationProjectionDto> enumerations(UmlModel model) {
        return model.enumerations().stream().map(enumeration -> {
            int[] order = {0};
            return new EnumerationProjectionDto(enumeration.id().value(), enumeration.name(),
                    enumeration.qualifiedName(model),
                    enumeration.packageId() == null ? null : enumeration.packageId().value(),
                    enumeration.visibility().name(), enumeration.literalDefinitions().stream()
                            .map(literal -> new EnumerationLiteralProjectionDto(literal.id().value(), literal.name(),
                                    order[0]++))
                            .toList());
        }).toList();
    }

    private List<OclDefinition> definitions(Project project) {
        try {
            return definitionService.runtimeDefinitions(project);
        } catch (IllegalArgumentException ignored) {
            return List.of();
        }
    }

    private List<ClassProjectionDto> classes(Project project, List<OclDefinition> definitions) {
        UmlModel model = project.umlModel();
        OclDefinitionService runtime = new OclDefinitionService(model, definitions);
        return model.classes().stream().map(type -> new ClassProjectionDto(
                type.id().value(), type.name(), type.qualifiedName(model), type.abstractClass(),
                type.superClassIds().stream().map(id -> namedClass(model, id)).toList(),
                model.typeConformanceOrder(type.id()).stream().map(id -> namedClass(model, id)).toList(),
                effectiveAttributes(project, runtime, type), effectiveOperations(model, type))).toList();
    }

    private List<FeatureProjectionDto> effectiveAttributes(Project project, OclDefinitionService runtime,
            UmlClass selected) {
        UmlModel model = project.umlModel();
        List<FeatureProjectionDto> result = new ArrayList<>();
        for (UmlClassId ownerId : model.typeConformanceOrder(selected.id())) {
            UmlClass owner = model.findClass(ownerId).orElseThrow();
            owner.attributes().forEach(attribute -> result.add(
                    attributeView(project, runtime, selected, owner, attribute)));
        }
        return List.copyOf(result);
    }

    private FeatureProjectionDto attributeView(Project project, OclDefinitionService runtime,
            UmlClass selected, UmlClass owner,
            UmlAttribute attribute) {
        UmlModel model = project.umlModel();
        ValueProjectionDto classifierValue = null;
        if (attribute.staticAttribute()) {
            if (attribute.derived()) {
                try {
                    ObjectInstance staticContext = new ObjectInstance(
                            new ObjectInstanceId("classifier-context-" + owner.id().value()),
                            owner.name(), owner.id(), List.of());
                    OclValue evaluated = runtime.property(new de.useweb.backend.ocl.value.ObjectValue(staticContext),
                            attribute.name(), new de.useweb.backend.ocl.evaluation.EvaluationContext(
                                    model, project.objectModel(), staticContext)).orElse(OclInvalidValue.INSTANCE);
                    classifierValue = value(evaluated);
                } catch (RuntimeException exception) {
                    classifierValue = new ValueProjectionDto("INVALID", attribute.type().name(), "INVALID", null,
                            List.of(), Map.of());
                }
            } else if (attribute.classifierValue() != null) {
                classifierValue = value(new SlotValue(attribute.classifierValue().value(),
                        attribute.classifierValue().valueType()));
            }
        }
        return new FeatureProjectionDto(attribute.id().value(), attribute.name(),
                owner.qualifiedName(model) + "::" + attribute.name(), "ATTRIBUTE", attribute.type().name(),
                namedClass(model, owner.id()), !owner.id().equals(selected.id()), attribute.derived(),
                attribute.derived(), attribute.staticAttribute(),
                attribute.derived() ? attribute.deriveExpression() : attribute.initExpression(),
                attribute.redefinedAttributeIds().stream().map(id -> namedAttribute(model, id)).toList(),
                classifierValue);
    }

    private List<FeatureProjectionDto> effectiveOperations(UmlModel model, UmlClass selected) {
        List<FeatureProjectionDto> result = new ArrayList<>();
        for (UmlClassId ownerId : model.typeConformanceOrder(selected.id())) {
            UmlClass owner = model.findClass(ownerId).orElseThrow();
            owner.operations().forEach(operation -> result.add(new FeatureProjectionDto(
                    operation.id().value(), operation.name(), owner.qualifiedName(model) + "::" + operation.name(),
                    "OPERATION", operation.returnType().name(), namedClass(model, owner.id()),
                    !owner.id().equals(selected.id()), false, false, operation.staticOperation(), operation.bodyExpression(),
                    redefinedOperations(model, selected, owner, operation), null)));
        }
        return List.copyOf(result);
    }

    private List<NamedElementDto> redefinedOperations(UmlModel model, UmlClass selected, UmlClass owner,
            UmlOperation operation) {
        LinkedHashSet<String> targetIds = new LinkedHashSet<>(operation.redefinedOperationIds().stream()
                .map(id -> id.value()).toList());
        // USE operation declarations express overriding by an equal signature in a subtype.
        // Preserve that intent in the read model even though the source has no separate UML
        // 'redefines' token.
        if (owner.id().equals(selected.id())) {
            model.superClassesOf(selected.id()).stream().map(model::findClass).flatMap(java.util.Optional::stream)
                    .flatMap(supertype -> supertype.operations().stream())
                    .filter(candidate -> sameSignature(operation, candidate))
                    .forEach(candidate -> targetIds.add(candidate.id().value()));
        }
        return targetIds.stream().map(id -> namedOperation(model,
                new de.useweb.backend.domain.uml.UmlOperationId(id))).toList();
    }

    private boolean sameSignature(UmlOperation left, UmlOperation right) {
        return left.name().equals(right.name())
                && left.returnType().equals(right.returnType())
                && left.parameters().stream().map(parameter -> parameter.type()).toList()
                        .equals(right.parameters().stream().map(parameter -> parameter.type()).toList());
    }

    private NamedElementDto namedAttribute(UmlModel model, de.useweb.backend.domain.uml.UmlAttributeId id) {
        for (UmlClass owner : model.classes()) {
            var feature = owner.findAttribute(id);
            if (feature.isPresent()) return new NamedElementDto(id.value(), feature.get().name(),
                    owner.qualifiedName(model) + "::" + feature.get().name(), "ATTRIBUTE");
        }
        throw new IllegalStateException("Validated attribute redefinition target is missing: " + id.value());
    }

    private NamedElementDto namedOperation(UmlModel model, de.useweb.backend.domain.uml.UmlOperationId id) {
        for (UmlClass owner : model.classes()) {
            var feature = owner.findOperation(id);
            if (feature.isPresent()) return new NamedElementDto(id.value(), feature.get().name(),
                    owner.qualifiedName(model) + "::" + feature.get().name(), "OPERATION");
        }
        throw new IllegalStateException("Validated operation redefinition target is missing: " + id.value());
    }

    private List<DefinitionProjectionDto> definitionViews(Project project, List<OclDefinition> definitions) {
        UmlModel model = project.umlModel();
        List<DefinitionProjectionDto> result = new ArrayList<>(definitions.stream().map(definition -> {
            NamedElementDto owner = namedClass(model, definition.ownerClassId());
            var range = definition.expression().sourceRange();
            SourceRangeDto sourceRange = range == null ? null : new SourceRangeDto(
                    range.start().line(), range.start().column(), range.start().offset(),
                    range.end().line(), range.end().column(), range.end().offset());
            return new DefinitionProjectionDto(definition.id().value(), definition.kind().name(),
                    definition.featureName(), owner.qualifiedName() + "::" + definition.featureName(), owner,
                    definition.resultType().name(), definition.parameters().stream()
                            .map(parameter -> new NamedElementDto(parameter.id().value(), parameter.name(),
                                    parameter.name(), "PARAMETER")).toList(),
                    definition.expressionText(), sourceRange,
                    project.definitions().stream().noneMatch(value -> value.id().value().equals(definition.id().value())));
        }).toList());
        project.definitions().stream()
                .filter(value -> value.ownerKind() == de.useweb.backend.domain.ocl.OclDefinitionElement.OwnerKind.PACKAGE)
                .map(value -> ProjectDtoMapper.toDto(value, model)).forEach(value -> result.add(
                        new DefinitionProjectionDto(value.id(), value.kind(), value.name(), value.qualifiedName(),
                                new NamedElementDto(value.ownerId(), value.ownerName(), value.ownerName(), "PACKAGE"),
                                value.resultType(), value.parameters().stream().map(parameter ->
                                        new NamedElementDto(parameter.id(), parameter.name(), parameter.name(), "PARAMETER")).toList(),
                                value.expression(), value.sourceRange(), false)));
        return List.copyOf(result);
    }

    private List<ObjectProjectionDto> objects(Project project, List<OclDefinition> definitions,
            List<ValidationErrorDto> diagnostics) {
        OclDefinitionService runtime = new OclDefinitionService(project.umlModel(), definitions);
        return project.objectModel().objects().stream().map(object -> {
            UmlClass type = project.umlModel().findClass(object.classId()).orElseThrow();
            List<SlotProjectionDto> slots = new ArrayList<>();
            for (UmlClassId ownerId : project.umlModel().typeConformanceOrder(type.id())) {
                UmlClass owner = project.umlModel().findClass(ownerId).orElseThrow();
                for (UmlAttribute attribute : owner.attributes()) {
                    if (!attribute.staticAttribute()) {
                        slots.add(slot(project, runtime, object, type, owner, attribute, diagnostics));
                    }
                }
            }
            return new ObjectProjectionDto(object.id().value(), object.name(), namedClass(project.umlModel(), type.id()),
                    List.copyOf(slots));
        }).toList();
    }

    private SlotProjectionDto slot(Project project, OclDefinitionService runtime, ObjectInstance object,
            UmlClass selected, UmlClass owner, UmlAttribute attribute, List<ValidationErrorDto> diagnostics) {
        Slot stored = object.slots().stream().filter(value -> value.attributeId().equals(attribute.id()))
                .findFirst().orElse(null);
        ValueProjectionDto value;
        if (attribute.derived()) {
            try {
                OclValue evaluated = runtime.property(new de.useweb.backend.ocl.value.ObjectValue(object),
                        attribute.name(), new de.useweb.backend.ocl.evaluation.EvaluationContext(
                                project.umlModel(), project.objectModel(), object)).orElse(OclInvalidValue.INSTANCE);
                value = value(evaluated);
            } catch (RuntimeException exception) {
                value = new ValueProjectionDto("INVALID", attribute.type().name(), "INVALID", null,
                        List.of(), Map.of());
            }
        } else {
            value = value(stored == null ? new SlotValue(null, attribute.type()) : stored.value());
        }
        List<ValidationErrorDto> slotDiagnostics = diagnostics.stream()
                .filter(error -> Objects.equals(error.elementId(), object.id().value())
                        || error.targets().stream().anyMatch(target -> Objects.equals(target.elementId(), attribute.id().value())))
                .toList();
        return new SlotProjectionDto(stored == null ? null : stored.id().value(), attribute.id().value(),
                attribute.name(), attribute.type().name(), namedClass(project.umlModel(), owner.id()),
                !owner.id().equals(selected.id()), attribute.derived(), attribute.derived(), value.status(), value,
                slotDiagnostics);
    }

    private ValueProjectionDto value(SlotValue value) {
        if (value.value() == null) return new ValueProjectionDto("NULL", value.valueType().name(), "SCALAR", null,
                List.of(), Map.of());
        Object raw = value.value();
        if (raw instanceof List<?> list) return new ValueProjectionDto("VALUE", value.valueType().name(),
                collectionKind(value.valueType().name()), null,
                list.stream().map(item -> scalar(item, "Any")).toList(), Map.of());
        if (raw instanceof Map<?, ?> map) {
            Map<String, ValueProjectionDto> fields = new LinkedHashMap<>();
            map.forEach((key, item) -> fields.put(String.valueOf(key), scalar(item, "Any")));
            return new ValueProjectionDto("VALUE", value.valueType().name(), "DATATYPE", null, List.of(), fields);
        }
        return scalar(raw, value.valueType().name());
    }

    private ValueProjectionDto value(OclValue value) {
        if (value == null || value == OclVoidValue.INSTANCE)
            return new ValueProjectionDto("NULL", value == null ? "OclVoid" : value.typeName(), "SCALAR", null,
                    List.of(), Map.of());
        if (value == OclInvalidValue.INSTANCE)
            return new ValueProjectionDto("INVALID", value.typeName(), "INVALID", null, List.of(), Map.of());
        if (value instanceof CollectionValue collection)
            return new ValueProjectionDto("VALUE", collection.typeName(), collection.collectionKind().name(), null,
                    collection.values().stream().map(this::value).toList(), Map.of());
        if (value instanceof TupleValue tuple) return structured("TUPLE", tuple.typeName(), tuple.parts());
        if (value instanceof DataTypeValue dataType) return structured("DATATYPE", dataType.typeName(), dataType.properties());
        return scalar(value.rawValue(), value.typeName());
    }

    private ValueProjectionDto structured(String kind, String type, Map<String, OclValue> values) {
        Map<String, ValueProjectionDto> fields = new LinkedHashMap<>();
        values.forEach((name, item) -> fields.put(name, value(item)));
        return new ValueProjectionDto("VALUE", type, kind, null, List.of(), fields);
    }

    private ValueProjectionDto scalar(Object raw, String type) {
        return new ValueProjectionDto(raw == null ? "NULL" : "VALUE", type, "SCALAR", raw, List.of(), Map.of());
    }

    private String collectionKind(String type) {
        for (String kind : List.of("OrderedSet", "Sequence", "Set", "Bag")) if (type.startsWith(kind)) return kind.toUpperCase();
        return "COLLECTION";
    }

    private List<ObjectAssociationProjectionDto> objectAssociations(Project project,
            List<ValidationErrorDto> diagnostics) {
        return project.objectModel().objects().stream().map(object -> new ObjectAssociationProjectionDto(
                object.id().value(), object.name(), project.objectModel().links().stream()
                        .filter(link -> link.ends().stream().anyMatch(end -> end.objectId().equals(object.id())))
                        .map(link -> relatedLink(project, link, diagnostics)).toList(),
                diagnostics.stream().filter(error -> Objects.equals(error.contextObjectId(), object.id().value())
                        || Objects.equals(error.elementId(), object.id().value())).toList())).toList();
    }

    private RelatedLinkDto relatedLink(Project project, ObjectLink link, List<ValidationErrorDto> diagnostics) {
        UmlAssociation association = project.umlModel().findAssociation(link.associationId()).orElseThrow();
        List<ValidationErrorDto> linkDiagnostics = diagnostics.stream()
                .filter(error -> Objects.equals(error.elementId(), link.id().value())
                        || error.relatedElementIds().contains(link.id().value())).toList();
        return new RelatedLinkDto(link.id().value(), association.name(),
                new NamedElementDto(association.id().value(), association.name(), association.name(), "ASSOCIATION"),
                "STORED_LINK", link.associationClassObjectId() == null ? null : link.associationClassObjectId().value(),
                association.ends().stream().map(end -> linkEnd(project, link, association, end)).toList(), linkDiagnostics);
    }

    private LinkEndProjectionDto linkEnd(Project project, ObjectLink link, UmlAssociation association,
            UmlAssociationEnd end) {
        var assignment = link.ends().stream().filter(value -> value.associationEndId().equals(end.id())).findFirst().orElseThrow();
        ObjectInstance object = project.objectModel().findObject(assignment.objectId()).orElseThrow();
        UmlClass classifier = project.umlModel().findClass(end.classId()).orElseThrow();
        Integer position = end.ordered() ? orderedPosition(project, association, end, link) : null;
        return new LinkEndProjectionDto(end.id().value(), end.roleName(), namedClass(project.umlModel(), classifier.id()),
                object.id().value(), object.name(), end.ordered(), end.unique(), position,
                assignment.qualifierValues().stream().map(value -> {
                    var definition = end.qualifiers().stream().filter(q -> q.id().equals(value.qualifierId())).findFirst().orElseThrow();
                    return new QualifierProjectionDto(definition.id().value(), definition.name(), definition.type().name(),
                            value(value.value()));
                }).toList());
    }

    private int orderedPosition(Project project, UmlAssociation association, UmlAssociationEnd end, ObjectLink target) {
        Map<String, Object> targetScope = target.ends().stream()
                .filter(value -> !value.associationEndId().equals(end.id()))
                .collect(java.util.stream.Collectors.toMap(
                        value -> value.associationEndId().value(),
                        value -> List.of(value.objectId(), value.qualifierValues())));
        List<ObjectLink> scope = project.objectModel().links().stream()
                .filter(link -> link.associationId().equals(association.id()))
                .filter(link -> link.ends().stream().anyMatch(value -> value.associationEndId().equals(end.id())))
                .filter(link -> link.ends().stream()
                        .filter(value -> !value.associationEndId().equals(end.id()))
                        .collect(java.util.stream.Collectors.toMap(
                                value -> value.associationEndId().value(),
                                value -> (Object) List.of(value.objectId(), value.qualifierValues())))
                        .equals(targetScope))
                .toList();
        return scope.indexOf(target) + 1;
    }

    private List<ExplorerElementDto> explorer(Project project) {
        UmlModel model = project.umlModel();
        ImportedSources importedSources = importedSources(project);
        Set<String> legacyImportedPackageIds = model.imports().stream()
                .map(modelImport -> modelImport.importedPackageId().value()).collect(java.util.stream.Collectors.toSet());
        List<ExplorerElementDto> result = new ArrayList<>();
        result.add(new ExplorerElementDto("project-root", model.id().value(), null, "Project root", model.name(), "PROJECT_ROOT",
                false, false, null, null));
        importedSources.roots().forEach(source -> result.add(new ExplorerElementDto(
                source.rootNodeId(), source.rootNodeId(), "project-root", source.sourcePath(), source.sourcePath(),
                "IMPORT_ROOT", true, true, null, source.sourcePath())));
        model.packages().stream().filter(pkg -> !legacyImportedPackageIds.contains(pkg.id().value())).forEach(pkg -> result.add(new ExplorerElementDto(pkg.id().value(), pkg.id().value(), packageParent(model, pkg.qualifiedName()),
                lastSegment(pkg.qualifiedName()), pkg.qualifiedName(), "PACKAGE", false, false, null, null)));
        model.classes().stream().filter(type -> type.packageId() == null || !legacyImportedPackageIds.contains(type.packageId().value())).forEach(type -> result.add(classifierExplorer(type.id().value(), type.name(), type.qualifiedName(model),
                type.packageId() == null ? "project-root" : type.packageId().value(), "CLASS",
                importedSources.byElement().get("CLASS:" + type.name()))));
        model.enumerations().stream().filter(type -> type.packageId() == null || !legacyImportedPackageIds.contains(type.packageId().value())).forEach(type -> result.add(classifierExplorer(type.id().value(), type.name(), type.qualifiedName(model),
                type.packageId() == null ? "project-root" : type.packageId().value(), "ENUMERATION",
                importedSources.byElement().get("ENUMERATION:" + type.name()))));
        model.dataTypes().stream().filter(type -> type.packageId() == null || !legacyImportedPackageIds.contains(type.packageId().value())).forEach(type -> result.add(classifierExplorer(type.id().value(), type.name(), type.qualifiedName(model),
                type.packageId() == null ? "project-root" : type.packageId().value(), "DATATYPE",
                importedSources.byElement().get("DATATYPE:" + type.name()))));
        model.imports().forEach(modelImport -> {
            String root = "import-root-" + modelImport.id().value();
            result.add(new ExplorerElementDto(root, modelImport.id().value(), "project-root",
                    modelImport.alias() == null ? "Import root" : modelImport.alias(), modelImport.alias(),
                    "IMPORT_ROOT", true, true, modelImport.id().value(), modelImport.provenance()));
            model.findPackage(modelImport.importedPackageId()).ifPresent(pkg -> {
                String packageNode = root + "::" + pkg.id().value();
                result.add(new ExplorerElementDto(packageNode, pkg.id().value(), root, lastSegment(pkg.qualifiedName()),
                        pkg.qualifiedName(), "PACKAGE", true, true, modelImport.id().value(), modelImport.provenance()));
                model.classes().stream().filter(type -> Objects.equals(type.packageId(), pkg.id())).forEach(type ->
                        result.add(importedClassifier(root, packageNode, type.id().value(), type.name(),
                                type.qualifiedName(model), "CLASS", modelImport.id().value(), modelImport.provenance())));
                model.enumerations().stream().filter(type -> Objects.equals(type.packageId(), pkg.id())).forEach(type ->
                        result.add(importedClassifier(root, packageNode, type.id().value(), type.name(),
                                type.qualifiedName(model), "ENUMERATION", modelImport.id().value(), modelImport.provenance())));
                model.dataTypes().stream().filter(type -> Objects.equals(type.packageId(), pkg.id())).forEach(type ->
                        result.add(importedClassifier(root, packageNode, type.id().value(), type.name(),
                                type.qualifiedName(model), "DATATYPE", modelImport.id().value(), modelImport.provenance())));
            });
        });
        return List.copyOf(result);
    }

    private ImportedSources importedSources(Project project) {
        Map<String, ImportedSource> result = new LinkedHashMap<>();
        if (project.modelText() == null) {
            return new ImportedSources(Map.of(), List.of());
        }
        List<ImportedSource> roots = project.modelText().sources().stream()
                .filter(source -> source.importedBy() != null)
                .map(source -> new ImportedSource(source.sourcePath())).distinct().toList();
        Map<String, ImportedSource> sourceByPath = new LinkedHashMap<>();
        roots.forEach(source -> sourceByPath.put(source.sourcePath(), source));
        if (!sourceByPath.isEmpty()) {
            Map<String, String> files = new LinkedHashMap<>();
            project.modelText().sourceFiles().forEach(source -> files.put(source.sourcePath(), source.text()));
            importResolver.resolve(project.modelText().sourceName(), project.modelText().text(), files)
                    .elementSources().forEach((key, sourcePath) -> {
                        ImportedSource source = sourceByPath.get(sourcePath);
                        if (source != null) result.putIfAbsent(key, source);
                    });
        }
        return new ImportedSources(Map.copyOf(result), roots);
    }

    private ExplorerElementDto classifierExplorer(String id, String name, String qualifiedName, String parentId, String kind,
            ImportedSource importedSource) {
        if (importedSource != null) {
            return importedClassifier(importedSource.rootNodeId(), importedSource.rootNodeId(), id, name, qualifiedName,
                    kind, null, importedSource.sourcePath());
        }
        return new ExplorerElementDto(id, id, parentId, name, qualifiedName, kind, false, false, null, null);
    }

    private ExplorerElementDto importedClassifier(String root, String parentId, String id, String name,
            String qualifiedName, String kind, String importId, String provenance) {
        return new ExplorerElementDto(root + "::" + id, id, parentId, name, qualifiedName, kind,
                true, true, importId, provenance);
    }

    private record ImportedSource(String sourcePath) {
        private String rootNodeId() {
            return "import-source-" + Integer.toUnsignedString(sourcePath.hashCode(), 36);
        }
    }

    private record ImportedSources(Map<String, ImportedSource> byElement, List<ImportedSource> roots) {
    }

    private String packageParent(UmlModel model, String qualifiedName) {
        int separator = qualifiedName.lastIndexOf("::");
        if (separator < 0) return "project-root";
        String parentName = qualifiedName.substring(0, separator);
        return model.packages().stream().filter(pkg -> pkg.qualifiedName().equals(parentName)).findFirst()
                .map(pkg -> pkg.id().value()).orElse("project-root");
    }

    private String lastSegment(String qualifiedName) {
        int separator = qualifiedName.lastIndexOf("::");
        return separator < 0 ? qualifiedName : qualifiedName.substring(separator + 2);
    }

    private NamedElementDto namedClass(UmlModel model, UmlClassId id) {
        UmlClass type = model.findClass(id).orElseThrow();
        return new NamedElementDto(type.id().value(), type.name(), type.qualifiedName(model), "CLASS");
    }
}
