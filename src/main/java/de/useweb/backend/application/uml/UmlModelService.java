package de.useweb.backend.application.uml;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import de.useweb.backend.api.dto.project.ProjectDto;
import de.useweb.backend.api.dto.command.AssociationClassAggregateDto;
import de.useweb.backend.api.dto.ocl.OclExpressionDto;
import de.useweb.backend.api.dto.uml.MultiplicityDto;
import de.useweb.backend.api.dto.uml.UmlAssociationDto;
import de.useweb.backend.api.dto.uml.UmlAssociationEndDto;
import de.useweb.backend.api.dto.uml.UmlAttributeDto;
import de.useweb.backend.api.dto.uml.UmlClassDto;
import de.useweb.backend.api.dto.uml.UmlDataTypeDto;
import de.useweb.backend.api.dto.uml.UmlEnumerationDto;
import de.useweb.backend.api.dto.uml.UmlInvariantDto;
import de.useweb.backend.api.dto.uml.UmlModelDto;
import de.useweb.backend.api.dto.uml.UmlOperationDto;
import de.useweb.backend.api.dto.uml.UmlPackageDto;
import de.useweb.backend.api.dto.uml.UmlModelImportDto;
import de.useweb.backend.api.dto.uml.UmlParameterDto;
import de.useweb.backend.api.mapper.ProjectDtoMapper;
import de.useweb.backend.application.project.ProjectService;
import de.useweb.backend.application.modeltext.UseModelTextRenderer;
import de.useweb.backend.domain.modeltext.ModelText;
import de.useweb.backend.domain.layout.DiagramLayout;
import de.useweb.backend.domain.layout.LayoutInformation;
import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.project.ProjectId;
import de.useweb.backend.domain.snapshot.ObjectInstance;
import de.useweb.backend.domain.snapshot.ObjectLink;
import de.useweb.backend.domain.snapshot.ObjectModel;
import de.useweb.backend.domain.uml.Multiplicity;
import de.useweb.backend.domain.uml.PrimitiveType;
import de.useweb.backend.domain.uml.UmlAssociation;
import de.useweb.backend.domain.uml.UmlAssociationEnd;
import de.useweb.backend.domain.uml.UmlAssociationEndId;
import de.useweb.backend.domain.uml.UmlAssociationId;
import de.useweb.backend.domain.uml.UmlAttribute;
import de.useweb.backend.domain.uml.UmlAttributeId;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlClassifierValue;
import de.useweb.backend.domain.uml.UmlDataType;
import de.useweb.backend.domain.uml.UmlDataTypeId;
import de.useweb.backend.domain.uml.UmlEnumeration;
import de.useweb.backend.domain.uml.UmlEnumerationId;
import de.useweb.backend.domain.uml.UmlEnumerationLiteral;
import de.useweb.backend.domain.uml.UmlEnumerationLiteralId;
import de.useweb.backend.domain.uml.UmlDataTypeProperty;
import de.useweb.backend.domain.uml.UmlInvariant;
import de.useweb.backend.domain.uml.UmlInvariantId;
import de.useweb.backend.domain.uml.UmlGeneralizationException;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlOperation;
import de.useweb.backend.domain.uml.UmlOperationContract;
import de.useweb.backend.domain.uml.UmlOperationId;
import de.useweb.backend.domain.uml.UmlParameter;
import de.useweb.backend.domain.uml.UmlParameterId;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.domain.uml.UmlPackageId;
import de.useweb.backend.domain.uml.UmlPackage;
import de.useweb.backend.domain.uml.UmlModelImport;
import de.useweb.backend.domain.uml.UmlModelImportId;
import de.useweb.backend.domain.uml.UmlVisibility;
import de.useweb.backend.ocl.lexer.OclLexer;
import de.useweb.backend.ocl.lexer.OclTokenType;
import de.useweb.backend.error.UmlModelException;

@Service
public class UmlModelService {

    private static final String TYPE_ERROR = "TYPE_ERROR";
    private static final String UNKNOWN_CLASS = "UNKNOWN_CLASS";
    private static final String UNKNOWN_ATTRIBUTE = "UNKNOWN_ATTRIBUTE";

    private final ProjectService projectService;
    private final UseModelTextRenderer modelTextRenderer;
    private final StructuredUmlTypeService structuredTypes = new StructuredUmlTypeService();

    public UmlModelService(ProjectService projectService) {
        this.projectService = projectService;
        this.modelTextRenderer = new UseModelTextRenderer();
    }

    public UmlModelDto getUmlModel(ProjectId projectId) {
        return ProjectDtoMapper.toDto(projectService.loadProject(projectId).umlModel());
    }

    public UmlClassDto createClass(ProjectId projectId, UmlClassDto input) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        String name = requireName(input.name(), "className");
        UmlPackageId packageId = packageId(input.packageId());
        requireUniqueClassName(model, name, packageId, null);
        UmlClassId classId = new UmlClassId(idOrGenerated(input.id(), "class"));
        UmlClass typeContext = new UmlClass(classId, name, List.of(), List.of(), input.abstractClass(),
                safeList(input.superClassIds()).stream().map(UmlClassId::new).toList(),
                visibility(input.visibility()), packageId);

        UmlClass umlClass = new UmlClass(
                classId,
                name,
                safeList(input.attributes()).stream()
                        .map(attribute -> attributeWithGeneratedId(attribute, model, typeContext))
                        .toList(),
                safeList(input.operations()).stream()
                        .map(operation -> operationWithGeneratedId(operation, model))
                        .toList(),
                input.abstractClass(),
                safeList(input.superClassIds()).stream().map(UmlClassId::new).toList(),
                visibility(input.visibility()), packageId);

        UmlModel updatedModel = validatedModel(model, append(model.classes(), umlClass));
        save(project, updatedModel);
        return ProjectDtoMapper.toDto(updatedModel).classes().stream()
                .filter(dto -> dto.id().equals(umlClass.id().value())).findFirst().orElseThrow();
    }

    public UmlClassDto updateClass(ProjectId projectId, UmlClassId classId, UmlClassDto input) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        UmlClass current = requireClass(model, classId);
        String name = requireName(input.name(), "className");
        requireUniqueClassName(model, name, packageId(input.packageId()), classId);

        if (input.abstractClass() && !current.abstractClass()) {
            List<ObjectInstance> directInstances = project.objectModel().objects().stream()
                    .filter(object -> object.classId().equals(classId)).toList();
            if (!directInstances.isEmpty()) {
                throw error("ABSTRACT_CLASS_HAS_INSTANCES",
                        "Class cannot become abstract while direct instances exist: " + name,
                        "Die Klasse kann nicht abstrakt werden, solange direkte Objekte existieren.",
                        Map.of("classId", classId.value(), "className", name,
                                "objectNames", directInstances.stream().map(ObjectInstance::name).toList()));
            }
        }

        UmlClass replacement;
        try {
            replacement = new UmlClass(current.id(), name, current.attributes(), current.operations(),
                    input.abstractClass(), safeList(input.superClassIds()).stream().map(UmlClassId::new).toList(),
                    visibility(input.visibility()), packageId(input.packageId()));
        } catch (UmlGeneralizationException exception) {
            throw generalizationError(exception);
        }

        List<UmlClass> classes = model.classes().stream()
                .map(umlClass -> umlClass.id().equals(classId) ? replacement : umlClass)
                .toList();
        UmlModel updated = validatedModel(model, classes);
        save(project, updated);
        return ProjectDtoMapper.toDto(updated).classes().stream()
                .filter(dto -> dto.id().equals(replacement.id().value())).findFirst().orElseThrow();
    }

    public ProjectDto deleteClass(ProjectId projectId, UmlClassId classId) {
        Project project = projectService.loadProject(projectId);
        requireClass(project.umlModel(), classId);
        return ProjectDtoMapper.toDto(deleteClassCascade(project, classId));
    }

    private Project deleteClassCascade(Project project, UmlClassId classId) {
        UmlModel model = project.umlModel();
        ObjectModel objectModel = project.objectModel();
        if (model.classes().stream().anyMatch(umlClass -> umlClass.superClassIds().contains(classId))) {
            throw error(TYPE_ERROR, "Class is still used as a superclass: " + classId.value(),
                    "Eine Oberklasse kann nicht geloescht werden, solange Unterklassen auf sie verweisen.",
                    Map.of("classId", classId.value()));
        }

        Set<String> removedClassIds = Set.of(classId.value());
        Set<UmlAssociationId> removedAssociationIds = model.associations().stream()
                .filter(association -> association.ends().stream().anyMatch(end -> end.classId().equals(classId)))
                .map(UmlAssociation::id)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> removedAssociationLayoutIds = removedAssociationIds.stream().map(UmlAssociationId::value).collect(java.util.stream.Collectors.toSet());
        Set<String> removedInvariantIds = model.invariants().stream()
                .filter(invariant -> invariant.contextClassId().equals(classId))
                .map(invariant -> invariant.id().value())
                .collect(java.util.stream.Collectors.toSet());
        Set<String> removedObjectIds = objectModel.objects().stream()
                .filter(object -> object.classId().equals(classId))
                .map(object -> object.id().value())
                .collect(java.util.stream.Collectors.toSet());

        List<ObjectInstance> objects = objectModel.objects().stream()
                .filter(object -> !object.classId().equals(classId))
                .toList();
        List<ObjectLink> links = objectModel.links().stream()
                .filter(link -> !removedAssociationIds.contains(link.associationId()))
                .filter(link -> link.ends().stream().noneMatch(end -> removedObjectIds.contains(end.objectId().value())))
                .toList();
        Set<String> remainingLinkIds = links.stream().map(link -> link.id().value()).collect(java.util.stream.Collectors.toSet());
        Set<String> removedLinkIds = objectModel.links().stream()
                .map(link -> link.id().value())
                .filter(linkId -> !remainingLinkIds.contains(linkId))
                .collect(java.util.stream.Collectors.toSet());

        List<UmlClass> classes = model.classes().stream()
                .filter(umlClass -> !umlClass.id().equals(classId))
                .toList();
        List<UmlAssociation> associations = model.associations().stream()
                .filter(association -> !removedAssociationIds.contains(association.id()))
                .toList();
        List<UmlInvariant> invariants = model.invariants().stream()
                .filter(invariant -> !invariant.contextClassId().equals(classId))
                .toList();

        return save(project,
                new UmlModel(model.id(), model.name(), classes, associations, invariants, model.enumerations(), model.packages(), model.imports(), model.dataTypes()),
                new ObjectModel(objectModel.id(), objectModel.name(), objects, links),
                pruneLayout(project.layout(), union(removedClassIds, removedObjectIds), union(removedAssociationLayoutIds, removedInvariantIds, removedLinkIds)));
    }

    public ProjectDto deleteAttribute(ProjectId projectId, UmlClassId classId, UmlAttributeId attributeId) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        ObjectModel objectModel = project.objectModel();
        UmlClass owner = requireClass(model, classId);
        requireAttribute(owner, attributeId);

        List<UmlClass> classes = model.classes().stream()
                .map(umlClass -> umlClass.id().equals(classId)
                        ? new UmlClass(
                                umlClass.id(),
                                umlClass.name(),
                                umlClass.attributes().stream().filter(attribute -> !attribute.id().equals(attributeId)).toList(),
                                umlClass.operations(), umlClass.abstractClass(), umlClass.superClassIds())
                        : umlClass)
                .toList();
        List<ObjectInstance> objects = objectModel.objects().stream()
                .map(object -> model.isSubtypeOf(object.classId(), classId)
                        ? new ObjectInstance(
                                object.id(),
                                object.name(),
                                object.classId(),
                                object.slots().stream().filter(slot -> !slot.attributeId().equals(attributeId)).toList())
                        : object)
                .toList();

        Project saved = save(project,
                new UmlModel(model.id(), model.name(), classes, model.associations(), model.invariants(), model.enumerations(), model.packages(), model.imports(), model.dataTypes()),
                new ObjectModel(objectModel.id(), objectModel.name(), objects, objectModel.links()),
                project.layout());
        return ProjectDtoMapper.toDto(saved);
    }

    public ProjectDto deleteOperation(ProjectId projectId, UmlClassId classId, UmlOperationId operationId) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        UmlClass owner = requireClass(model, classId);
        requireOperation(owner, operationId);

        List<UmlClass> classes = model.classes().stream()
                .map(umlClass -> umlClass.id().equals(classId)
                        ? new UmlClass(
                                umlClass.id(),
                                umlClass.name(),
                                umlClass.attributes(),
                                umlClass.operations().stream().filter(operation -> !operation.id().equals(operationId)).toList(),
                                umlClass.abstractClass(), umlClass.superClassIds(), umlClass.visibility(),
                                umlClass.packageId())
                        : umlClass)
                .toList();

        Project saved = save(project, new UmlModel(model.id(), model.name(), classes, model.associations(), model.invariants(), model.enumerations(), model.packages(), model.imports(), model.dataTypes()));
        return ProjectDtoMapper.toDto(saved);
    }

    public ProjectDto deleteAssociation(ProjectId projectId, UmlAssociationId associationId) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        ObjectModel objectModel = project.objectModel();
        requireAssociation(model, associationId);

        List<UmlAssociation> associations = model.associations().stream()
                .filter(association -> !association.id().equals(associationId))
                .toList();
        List<ObjectLink> links = objectModel.links().stream()
                .filter(link -> !link.associationId().equals(associationId))
                .toList();
        Set<String> removedLinkIds = objectModel.links().stream()
                .filter(link -> link.associationId().equals(associationId))
                .map(link -> link.id().value())
                .collect(java.util.stream.Collectors.toSet());

        Project saved = save(project,
                new UmlModel(model.id(), model.name(), model.classes(), associations, model.invariants(), model.enumerations(), model.packages(), model.imports(), model.dataTypes()),
                new ObjectModel(objectModel.id(), objectModel.name(), objectModel.objects(), links),
                pruneLayout(project.layout(), Set.of(), union(Set.of(associationId.value()), removedLinkIds)));
        return ProjectDtoMapper.toDto(saved);
    }

    public ProjectDto deleteInvariant(ProjectId projectId, UmlInvariantId invariantId) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        requireInvariant(model, invariantId);

        List<UmlInvariant> invariants = model.invariants().stream()
                .filter(invariant -> !invariant.id().equals(invariantId))
                .toList();
        Project saved = save(
                project,
                new UmlModel(model.id(), model.name(), model.classes(), model.associations(), invariants, model.enumerations(), model.packages(), model.imports(), model.dataTypes()),
                project.objectModel(),
                pruneLayout(project.layout(), Set.of(), Set.of(invariantId.value())));
        return ProjectDtoMapper.toDto(saved);
    }

    public UmlAttributeDto addAttribute(ProjectId projectId, UmlClassId classId, UmlAttributeDto input) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        UmlClass owner = requireClass(model, classId);
        String name = requireName(input.name(), "attributeName");
        validateStaticAttribute(input);
        if (owner.attributes().stream().anyMatch(attribute -> attribute.name().equals(name))) {
            throw error(TYPE_ERROR, "Duplicate attribute name: " + name, "Der Attributname ist in der Klasse bereits vorhanden.",
                    Map.of("classId", classId.value(), "attributeName", name));
        }
        UmlAttribute attribute = new UmlAttribute(
                new UmlAttributeId(idOrGenerated(input.id(), "attr")),
                name,
                requireKnownType(model, input.type(), owner, false),
                Boolean.TRUE.equals(input.derived()), input.deriveExpression(), input.initExpression(),
                visibility(input.visibility()), safeList(input.redefinedAttributeIds()).stream()
                        .map(UmlAttributeId::new).toList(), Boolean.TRUE.equals(input.staticAttribute()),
                classifierValue(input, model, owner));

        List<UmlClass> classes = model.classes().stream()
                .map(umlClass -> umlClass.id().equals(classId)
                        ? new UmlClass(umlClass.id(), umlClass.name(), append(umlClass.attributes(), attribute), umlClass.operations(),
                                umlClass.abstractClass(), umlClass.superClassIds(), umlClass.visibility(), umlClass.packageId())
                        : umlClass)
                .toList();
        save(project, new UmlModel(model.id(), model.name(), classes, model.associations(), model.invariants(), model.enumerations(), model.packages(), model.imports(), model.dataTypes()));
        return ProjectDtoMapper.toDto(attribute);
    }

    public UmlAttributeDto updateAttribute(ProjectId projectId, UmlClassId classId, UmlAttributeId attributeId,
            UmlAttributeDto input) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        UmlClass owner = requireClass(model, classId);
        requireAttribute(owner, attributeId);
        validateStaticAttribute(input);
        UmlAttribute replacement = new UmlAttribute(attributeId, requireName(input.name(), "attributeName"),
                requireKnownType(model, input.type(), owner, false), Boolean.TRUE.equals(input.derived()),
                input.deriveExpression(), input.initExpression(), visibility(input.visibility()),
                safeList(input.redefinedAttributeIds()).stream().map(UmlAttributeId::new).toList(),
                Boolean.TRUE.equals(input.staticAttribute()), classifierValue(input, model, owner));
        List<UmlClass> classes = model.classes().stream().map(umlClass -> umlClass.id().equals(classId)
                ? new UmlClass(umlClass.id(), umlClass.name(), umlClass.attributes().stream()
                        .map(attribute -> attribute.id().equals(attributeId) ? replacement : attribute).toList(),
                        umlClass.operations(), umlClass.abstractClass(), umlClass.superClassIds(), umlClass.visibility(),
                        umlClass.packageId())
                : umlClass).toList();
        save(project, new UmlModel(model.id(), model.name(), classes, model.associations(), model.invariants(),
                model.enumerations(), model.packages(), model.imports(), model.dataTypes()));
        return ProjectDtoMapper.toDto(replacement);
    }

    public UmlOperationDto addOperation(ProjectId projectId, UmlClassId classId, UmlOperationDto input) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        UmlClass owner = requireClass(model, classId);
        validateOperationDraft(input);
        UmlOperation operation = operationWithGeneratedId(input, model);
        if (owner.operations().stream().anyMatch(existing -> sameOperationSignature(existing, operation))) {
            throw duplicateOperation(classId, operation);
        }

        List<UmlClass> classes = model.classes().stream()
                .map(umlClass -> umlClass.id().equals(classId)
                        ? new UmlClass(umlClass.id(), umlClass.name(), umlClass.attributes(), append(umlClass.operations(), operation),
                                umlClass.abstractClass(), umlClass.superClassIds(), umlClass.visibility(), umlClass.packageId())
                        : umlClass)
                .toList();
        save(project, new UmlModel(model.id(), model.name(), classes, model.associations(), model.invariants(), model.enumerations(), model.packages(), model.imports(), model.dataTypes()));
        return ProjectDtoMapper.toDto(operation);
    }

    public UmlOperationDto updateOperation(ProjectId projectId, UmlClassId classId, UmlOperationId operationId,
            UmlOperationDto input) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        UmlClass owner = requireClass(model, classId);
        requireOperation(owner, operationId);
        validateOperationDraft(input);
        UmlOperation replacement = new UmlOperation(operationId, requireName(input.name(), "operationName"),
                requireKnownType(model, input.returnType(), true), safeList(input.parameters()).stream()
                        .map(parameter -> parameterWithGeneratedId(parameter, model)).toList(),
                input.bodyExpression(), visibility(input.visibility()),
                Boolean.TRUE.equals(input.abstractOperation()), Boolean.TRUE.equals(input.query()),
                Boolean.TRUE.equals(input.staticOperation()),
                contracts(input), safeList(input.redefinedOperationIds()).stream().map(UmlOperationId::new).toList());
        if (owner.operations().stream().filter(operation -> !operation.id().equals(operationId))
                .anyMatch(existing -> sameOperationSignature(existing, replacement))) {
            throw duplicateOperation(classId, replacement);
        }
        List<UmlClass> classes = model.classes().stream().map(umlClass -> umlClass.id().equals(classId)
                ? new UmlClass(umlClass.id(), umlClass.name(), umlClass.attributes(), umlClass.operations().stream()
                        .map(operation -> operation.id().equals(operationId) ? replacement : operation).toList(),
                        umlClass.abstractClass(), umlClass.superClassIds(), umlClass.visibility(), umlClass.packageId())
                : umlClass).toList();
        save(project, new UmlModel(model.id(), model.name(), classes, model.associations(), model.invariants(),
                model.enumerations(), model.packages(), model.imports(), model.dataTypes()));
        return ProjectDtoMapper.toDto(replacement);
    }

    public UmlClassDto updateFeatureRedefinition(ProjectId projectId, UmlClassId classId, String featureKind,
            String localFeatureId, List<String> redefinedFeatureIds, List<String> supertypeIds) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        UmlClass owner = requireClass(model, classId);
        List<UmlAttribute> attributes = owner.attributes();
        List<UmlOperation> operations = owner.operations();
        if ("ATTRIBUTE".equals(featureKind)) {
            UmlAttributeId featureId = new UmlAttributeId(localFeatureId);
            UmlAttribute current = requireAttribute(owner, featureId);
            UmlAttribute replacement = new UmlAttribute(current.id(), current.name(), current.type(), current.derived(),
                    current.deriveExpression(), current.initExpression(), current.visibility(),
                    redefinedFeatureIds.stream().map(UmlAttributeId::new).toList(), current.staticAttribute(),
                    current.classifierValue());
            attributes = attributes.stream().map(feature -> feature.id().equals(featureId) ? replacement : feature).toList();
        } else if ("OPERATION".equals(featureKind)) {
            UmlOperationId featureId = new UmlOperationId(localFeatureId);
            UmlOperation current = requireOperation(owner, featureId);
            UmlOperation replacement = new UmlOperation(current.id(), current.name(), current.returnType(),
                    current.parameters(), current.bodyExpression(), current.visibility(), current.abstractOperation(),
                    current.query(), current.staticOperation(), current.contracts(), redefinedFeatureIds.stream().map(UmlOperationId::new).toList());
            operations = operations.stream().map(feature -> feature.id().equals(featureId) ? replacement : feature).toList();
        } else {
            throw error("INVALID_FEATURE_KIND", "Unknown feature kind: " + featureKind,
                    "Die Feature-Art muss ATTRIBUTE oder OPERATION sein.", Map.of("featureKind", featureKind));
        }
        List<UmlClassId> nextSupertypes = supertypeIds == null ? owner.superClassIds()
                : supertypeIds.stream().map(UmlClassId::new).toList();
        UmlClass replacement = new UmlClass(owner.id(), owner.name(), attributes, operations, owner.abstractClass(),
                nextSupertypes, owner.visibility(), owner.packageId());
        List<UmlClass> classes = model.classes().stream()
                .map(type -> type.id().equals(classId) ? replacement : type).toList();
        UmlModel updated = validatedModel(model, classes);
        save(project, updated);
        return ProjectDtoMapper.toDto(updated).classes().stream().filter(type -> type.id().equals(classId.value()))
                .findFirst().orElseThrow();
    }

    public UmlPackageDto createPackage(ProjectId projectId, UmlPackageDto input) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        UmlPackage umlPackage = packageValue(new UmlPackageId(idOrGenerated(input.id(), "package")),
                input.qualifiedName());
        UmlModel updated = namespaceModel(model, append(model.packages(), umlPackage), model.imports());
        save(project, updated);
        return ProjectDtoMapper.toDto(umlPackage);
    }

    public UmlPackageDto updatePackage(ProjectId projectId, UmlPackageId packageId, UmlPackageDto input) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        UmlPackage current = model.findPackage(packageId).orElseThrow(() -> error("UNKNOWN_PACKAGE",
                "Unknown package: " + packageId.value(), "Das Package existiert nicht.",
                Map.of("packageId", packageId.value())));
        UmlPackage replacement = packageValue(packageId, input.qualifiedName());
        String oldPrefix = current.qualifiedName() + "::";
        String newPrefix = replacement.qualifiedName() + "::";
        if (replacement.qualifiedName().startsWith(oldPrefix)) {
            throw error("PACKAGE_CYCLE", "A package cannot be moved below itself",
                    "Ein Package kann nicht unter sich selbst verschoben werden.",
                    Map.of("packageId", packageId.value(), "qualifiedName", replacement.qualifiedName()));
        }
        List<UmlPackage> packages = model.packages().stream().map(candidate -> {
            if (candidate.id().equals(packageId)) return replacement;
            if (candidate.qualifiedName().startsWith(oldPrefix)) {
                return packageValue(candidate.id(), newPrefix + candidate.qualifiedName().substring(oldPrefix.length()));
            }
            return candidate;
        }).toList();
        UmlModel updated = namespaceModel(model, packages, model.imports());
        save(project, updated);
        return ProjectDtoMapper.toDto(replacement);
    }

    public UmlModelImportDto createImport(ProjectId projectId, UmlModelImportDto input) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        UmlModelImport modelImport = importValue(new UmlModelImportId(idOrGenerated(input.id(), "import")), input);
        UmlModel updated = namespaceModel(model, model.packages(), append(model.imports(), modelImport));
        save(project, updated);
        return ProjectDtoMapper.toDto(modelImport);
    }

    public UmlModelImportDto updateImport(ProjectId projectId, UmlModelImportId importId, UmlModelImportDto input) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        if (model.imports().stream().noneMatch(modelImport -> modelImport.id().equals(importId))) {
            throw error("UNKNOWN_IMPORT", "Unknown model import: " + importId.value(),
                    "Der Modellimport existiert nicht.", Map.of("importId", importId.value()));
        }
        UmlModelImport replacement = importValue(importId, input);
        List<UmlModelImport> imports = model.imports().stream()
                .map(modelImport -> modelImport.id().equals(importId) ? replacement : modelImport).toList();
        UmlModel updated = namespaceModel(model, model.packages(), imports);
        save(project, updated);
        return ProjectDtoMapper.toDto(replacement);
    }

    public ProjectDto deleteImport(ProjectId projectId, UmlModelImportId importId) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        if (model.imports().stream().noneMatch(modelImport -> modelImport.id().equals(importId))) {
            throw error(TYPE_ERROR, "Unknown model import: " + importId.value(),
                    "Der Modellimport existiert nicht.", Map.of("importId", importId.value()));
        }
        UmlModel updated = new UmlModel(model.id(), model.name(), model.classes(), model.associations(),
                model.invariants(), model.enumerations(), model.packages(), model.imports().stream()
                        .filter(modelImport -> !modelImport.id().equals(importId)).toList(), model.dataTypes());
        save(project, updated);
        return ProjectDtoMapper.toDto(projectService.loadProject(projectId));
    }

    public ProjectDto deletePackageWithDependencies(ProjectId projectId, UmlPackageId packageId) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        UmlPackage target = model.findPackage(packageId).orElseThrow(() -> error("UNKNOWN_PACKAGE",
                "Unknown package: " + packageId.value(), "Das Package existiert nicht.",
                Map.of("packageId", packageId.value())));
        String descendantPrefix = target.qualifiedName() + "::";
        Set<UmlPackageId> removedPackageIds = model.packages().stream()
                .filter(candidate -> candidate.id().equals(packageId)
                        || candidate.qualifiedName().startsWith(descendantPrefix))
                .map(UmlPackage::id).collect(java.util.stream.Collectors.toSet());
        Set<UmlClassId> removedClassIds = model.classes().stream()
                .filter(candidate -> candidate.packageId() != null && removedPackageIds.contains(candidate.packageId()))
                .map(UmlClass::id).collect(java.util.stream.Collectors.toSet());
        Set<UmlAssociationId> removedAssociationIds = model.associations().stream()
                .filter(association -> association.associationClassId() != null
                        && removedClassIds.contains(association.associationClassId())
                        || association.ends().stream().anyMatch(end -> removedClassIds.contains(end.classId())))
                .map(UmlAssociation::id).collect(java.util.stream.Collectors.toSet());
        Set<String> removedInvariantIds = model.invariants().stream()
                .filter(invariant -> removedClassIds.contains(invariant.contextClassId()))
                .map(invariant -> invariant.id().value()).collect(java.util.stream.Collectors.toSet());
        Set<String> removedObjectIds = project.objectModel().objects().stream()
                .filter(object -> removedClassIds.contains(object.classId()))
                .map(object -> object.id().value()).collect(java.util.stream.Collectors.toSet());

        List<ObjectInstance> objects = project.objectModel().objects().stream()
                .filter(object -> !removedObjectIds.contains(object.id().value())).toList();
        List<ObjectLink> links = project.objectModel().links().stream()
                .filter(link -> !removedAssociationIds.contains(link.associationId()))
                .filter(link -> link.ends().stream().noneMatch(end -> removedObjectIds.contains(end.objectId().value())))
                .filter(link -> link.associationClassObjectId() == null
                        || !removedObjectIds.contains(link.associationClassObjectId().value()))
                .toList();
        Set<String> remainingLinkIds = links.stream().map(link -> link.id().value())
                .collect(java.util.stream.Collectors.toSet());
        Set<String> removedLinkIds = project.objectModel().links().stream().map(link -> link.id().value())
                .filter(id -> !remainingLinkIds.contains(id)).collect(java.util.stream.Collectors.toSet());

        UmlModel updatedModel = new UmlModel(model.id(), model.name(),
                model.classes().stream().filter(candidate -> !removedClassIds.contains(candidate.id())).toList(),
                model.associations().stream().filter(candidate -> !removedAssociationIds.contains(candidate.id())).toList(),
                model.invariants().stream().filter(candidate -> !removedInvariantIds.contains(candidate.id().value())).toList(),
                model.enumerations().stream().filter(candidate -> candidate.packageId() == null
                        || !removedPackageIds.contains(candidate.packageId())).toList(),
                model.packages().stream().filter(candidate -> !removedPackageIds.contains(candidate.id())).toList(),
                model.imports().stream().filter(candidate -> !removedPackageIds.contains(candidate.importingPackageId())
                        && !removedPackageIds.contains(candidate.importedPackageId())).toList(),
                model.dataTypes().stream().filter(candidate -> candidate.packageId() == null
                        || !removedPackageIds.contains(candidate.packageId())).toList());
        Set<String> removedDefinitionIds = project.definitions().stream()
                .filter(definition -> definition.ownerKind() == de.useweb.backend.domain.ocl.OclDefinitionElement.OwnerKind.PACKAGE
                        && removedPackageIds.stream().anyMatch(id -> id.value().equals(definition.ownerId()))
                        || definition.ownerKind() == de.useweb.backend.domain.ocl.OclDefinitionElement.OwnerKind.CLASS
                        && removedClassIds.stream().anyMatch(id -> id.value().equals(definition.ownerId())))
                .map(definition -> definition.id().value()).collect(java.util.stream.Collectors.toSet());
        Set<String> removedNodeIds = union(removedPackageIds.stream().map(UmlPackageId::value)
                        .collect(java.util.stream.Collectors.toSet()),
                removedClassIds.stream().map(UmlClassId::value).collect(java.util.stream.Collectors.toSet()),
                removedObjectIds);
        Set<String> removedEdgeIds = union(removedAssociationIds.stream().map(UmlAssociationId::value)
                        .collect(java.util.stream.Collectors.toSet()), removedInvariantIds, removedLinkIds,
                removedDefinitionIds);
        Project saved = projectService.saveProject(new Project(project.id(), project.metadata(), project.modelText(),
                updatedModel, new ObjectModel(project.objectModel().id(), project.objectModel().name(), objects, links),
                pruneLayout(project.layout(), removedNodeIds, removedEdgeIds),
                project.definitions().stream().filter(value -> !removedDefinitionIds.contains(value.id().value())).toList()));
        return ProjectDtoMapper.toDto(saved);
    }

    private UmlPackage packageValue(UmlPackageId id, String qualifiedName) {
        try {
            return new UmlPackage(id, requireName(qualifiedName, "qualifiedName"));
        } catch (UmlModelException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw error("INVALID_PACKAGE", exception.getMessage(), "Das Package ist ungueltig.",
                    Map.of("packageId", id.value(), "qualifiedName", qualifiedName == null ? "" : qualifiedName));
        }
    }

    private UmlModelImport importValue(UmlModelImportId id, UmlModelImportDto input) {
        try {
            return new UmlModelImport(id, new UmlPackageId(input.importingPackageId()),
                    new UmlPackageId(input.importedPackageId()), input.alias(), input.source(), input.provenance());
        } catch (IllegalArgumentException exception) {
            throw error("INVALID_IMPORT", exception.getMessage(), "Der Modellimport ist ungueltig.",
                    Map.of("importId", id.value()));
        }
    }

    private UmlModel namespaceModel(UmlModel current, List<UmlPackage> packages, List<UmlModelImport> imports) {
        try {
            return new UmlModel(current.id(), current.name(), current.classes(), current.associations(),
                    current.invariants(), current.enumerations(), packages, imports, current.dataTypes());
        } catch (de.useweb.backend.domain.uml.UmlNamespaceException exception) {
            throw error(exception.code(), exception.getMessage(),
                    "Der Namespace oder Import im UML-Modell ist ungueltig.", exception.details());
        } catch (IllegalArgumentException exception) {
            throw error("QUALIFIED_NAME_CONFLICT", exception.getMessage(),
                    "Der qualifizierte Name steht bereits in Konflikt mit einem Modellelement.", Map.of());
        }
    }

    public UmlAssociationDto createAssociation(ProjectId projectId, UmlAssociationDto input) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        String name = requireName(input.name(), "associationName");
        if (model.associations().stream().anyMatch(association -> association.name().equals(name))) {
            throw error(TYPE_ERROR, "Duplicate association name: " + name, "Der Assoziationsname ist bereits vorhanden.",
                    Map.of("associationName", name));
        }
        List<UmlAssociationEndDto> inputEnds = safeList(input.ends());
        if (inputEnds.size() < 2) {
            throw error("NARY_END_REQUIRED", "Associations must have at least two ends",
                    "Eine Assoziation muss mindestens zwei Enden haben.",
                    Map.of("associationName", name, "field", "ends", "endCount", inputEnds.size()));
        }
        UmlAssociation association = new UmlAssociation(
                new UmlAssociationId(idOrGenerated(input.id(), "assoc")),
                name,
                inputEnds.stream().map(end -> associationEndWithGeneratedId(end, model)).toList(),
                associationClassId(input.associationClassId(), model, null));

        save(project, new UmlModel(model.id(), model.name(), model.classes(), append(model.associations(), association),
                model.invariants(), model.enumerations(), model.packages(), model.imports(), model.dataTypes()));
        return ProjectDtoMapper.toDto(association);
    }

    public UmlAssociationDto updateAssociation(ProjectId projectId, UmlAssociationId associationId,
            UmlAssociationDto input) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        UmlAssociation current = requireAssociation(model, associationId);
        String name = requireName(input.name(), "associationName");
        if (model.associations().stream().anyMatch(association -> !association.id().equals(associationId)
                && association.name().equals(name))) {
            throw error(TYPE_ERROR, "Duplicate association name: " + name,
                    "Der Assoziationsname ist bereits vorhanden.", Map.of("associationName", name));
        }
        List<UmlAssociationEndDto> inputEnds = safeList(input.ends());
        if (inputEnds.size() < 2) {
            throw error("NARY_END_REQUIRED", "Associations must have at least two ends",
                    "Eine Assoziation muss mindestens zwei Enden haben.",
                    Map.of("associationId", associationId.value(), "field", "ends", "endCount", inputEnds.size()));
        }
        Set<UmlAssociationEndId> currentEndIds = current.ends().stream().map(UmlAssociationEnd::id)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> requestedEndIds = inputEnds.stream().map(UmlAssociationEndDto::id)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        boolean shapeChanged = requestedEndIds.size() != currentEndIds.size()
                || currentEndIds.stream().anyMatch(id -> !requestedEndIds.contains(id.value()));
        boolean associationClassChanged = !Objects.equals(
                current.associationClassId() == null ? null : current.associationClassId().value(),
                input.associationClassId());
        if ((shapeChanged || associationClassChanged) && project.objectModel().links().stream()
                .anyMatch(link -> link.associationId().equals(associationId))) {
            throw error(TYPE_ERROR, "Association shape or association class cannot change while object links exist",
                    "Vorhandene Objektlinks blockieren diese Strukturänderung der Assoziation.",
                    Map.of("associationId", associationId.value()));
        }
        UmlAssociation updated = new UmlAssociation(associationId, name,
                inputEnds.stream().map(end -> associationEndForUpdate(end, model, currentEndIds, shapeChanged)).toList(),
                associationClassId(input.associationClassId(), model, associationId));
        List<UmlAssociation> associations = model.associations().stream()
                .map(association -> association.id().equals(associationId) ? updated : association).toList();
        save(project, new UmlModel(model.id(), model.name(), model.classes(), associations,
                model.invariants(), model.enumerations(), model.packages(), model.imports(), model.dataTypes()));
        return ProjectDtoMapper.toDto(updated);
    }

    public AssociationClassAggregateDto createAssociationClass(ProjectId projectId,
            UmlAssociationId associationId, UmlClassDto input) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        UmlAssociation association = requireAssociation(model, associationId);
        if (association.associationClassId() != null) {
            throw error("ASSOCIATION_CLASS_ALREADY_BOUND",
                    "Association already has an association class: " + associationId.value(),
                    "Die Assoziation besitzt bereits eine Association Class.",
                    Map.of("associationId", associationId.value(),
                            "associationClassId", association.associationClassId().value()));
        }

        String name = requireName(input.name(), "className");
        requireUniqueClassName(model, name, packageId(input.packageId()), null);
        UmlClass associationClass = new UmlClass(
                new UmlClassId(idOrGenerated(input.id(), "class")),
                name,
                safeList(input.attributes()).stream()
                        .map(attribute -> attributeWithGeneratedId(attribute, model)).toList(),
                safeList(input.operations()).stream()
                        .peek(this::validateOperationDraft)
                        .map(operation -> operationWithGeneratedId(operation, model)).toList(),
                input.abstractClass(),
                safeList(input.superClassIds()).stream().map(UmlClassId::new).toList(),
                visibility(input.visibility()), packageId(input.packageId()));
        UmlAssociation boundAssociation = new UmlAssociation(
                association.id(), association.name(), association.ends(), associationClass.id());
        List<UmlAssociation> associations = model.associations().stream()
                .map(candidate -> candidate.id().equals(associationId) ? boundAssociation : candidate).toList();
        UmlModel updated = new UmlModel(model.id(), model.name(), append(model.classes(), associationClass),
                associations, model.invariants(), model.enumerations(), model.packages(), model.imports(),
                model.dataTypes());
        save(project, updated);
        UmlModelDto dto = ProjectDtoMapper.toDto(updated);
        UmlClassDto classDto = dto.classes().stream()
                .filter(candidate -> candidate.id().equals(associationClass.id().value())).findFirst().orElseThrow();
        UmlAssociationDto associationDto = dto.associations().stream()
                .filter(candidate -> candidate.id().equals(associationId.value())).findFirst().orElseThrow();
        return new AssociationClassAggregateDto(associationDto, classDto);
    }

    public UmlInvariantDto createInvariant(ProjectId projectId, UmlInvariantDto input) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        String name = requireName(input.name(), "invariantName");
        requireClass(model, new UmlClassId(input.contextClassId()));
        if (model.invariants().stream().anyMatch(invariant -> invariant.name().equals(name))) {
            throw error(TYPE_ERROR, "Duplicate invariant name: " + name, "Der Invariantenname ist bereits vorhanden.",
                    Map.of("invariantName", name));
        }
        OclExpressionDto expression = input.expression();
        if (expression == null || expression.text() == null || expression.text().isBlank()) {
            throw error(TYPE_ERROR, "Invariant expression must not be blank", "Der OCL-Ausdruck der Invariante darf nicht leer sein.",
                    Map.of("invariantName", name));
        }
        UmlInvariant invariant = ProjectDtoMapper.toDomain(new UmlInvariantDto(
                idOrGenerated(input.id(), "inv"),
                name,
                input.contextClassId(),
                new OclExpressionDto(
                        idOrGenerated(expression.id(), "expr"),
                        expression.text(),
                        expression.language() == null ? "OCL" : expression.language(),
                        expression.languageVersion() == null ? "MVP" : expression.languageVersion()),
                input.enabled()));

        save(project, new UmlModel(model.id(), model.name(), model.classes(), model.associations(),
                append(model.invariants(), invariant), model.enumerations(), model.packages(), model.imports(), model.dataTypes()));
        return ProjectDtoMapper.toDto(invariant);
    }

    public UmlInvariantDto updateInvariant(ProjectId projectId, UmlInvariantId invariantId, UmlInvariantDto input) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        requireInvariant(model, invariantId);
        String name = requireName(input.name(), "invariantName");
        requireClass(model, new UmlClassId(input.contextClassId()));
        if (model.invariants().stream().anyMatch(invariant -> !invariant.id().equals(invariantId)
                && invariant.name().equals(name))) {
            throw error(TYPE_ERROR, "Duplicate invariant name: " + name,
                    "Der Invariantenname ist bereits vorhanden.", Map.of("invariantName", name));
        }
        OclExpressionDto expression = input.expression();
        if (expression == null || expression.text() == null || expression.text().isBlank()) {
            throw error(TYPE_ERROR, "Invariant expression must not be blank",
                    "Der OCL-Ausdruck der Invariante darf nicht leer sein.", Map.of("invariantId", invariantId.value()));
        }
        UmlInvariant replacement = ProjectDtoMapper.toDomain(new UmlInvariantDto(
                invariantId.value(), name, input.contextClassId(),
                new OclExpressionDto(idOrGenerated(expression.id(), "expr"), expression.text(),
                        expression.language() == null ? "OCL" : expression.language(),
                        expression.languageVersion() == null ? "MVP" : expression.languageVersion()),
                input.enabled()));
        List<UmlInvariant> invariants = model.invariants().stream()
                .map(invariant -> invariant.id().equals(invariantId) ? replacement : invariant).toList();
        save(project, new UmlModel(model.id(), model.name(), model.classes(), model.associations(), invariants,
                model.enumerations(), model.packages(), model.imports(), model.dataTypes()));
        return ProjectDtoMapper.toDto(replacement);
    }

    public UmlDataTypeDto createDataType(ProjectId projectId, UmlDataTypeDto input) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        UmlDataTypeDto normalized = new UmlDataTypeDto(idOrGenerated(input.id(), "datatype"),
                requireName(input.name(), "dataTypeName"), safeList(input.properties()), input.packageId(), input.qualifiedName(),
                safeList(input.operations()));
        UmlDataType dataType = dataType(normalized, model);
        UmlModel updated = new UmlModel(model.id(), model.name(), model.classes(), model.associations(), model.invariants(),
                model.enumerations(), model.packages(), model.imports(), append(model.dataTypes(), dataType));
        save(project, updated);
        return ProjectDtoMapper.toDto(dataType, updated);
    }

    public UmlEnumerationDto createEnumeration(ProjectId projectId, UmlEnumerationDto input) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        String id = idOrGenerated(input.id(), "enumeration");
        UmlEnumeration enumeration = enumeration(input, new UmlEnumerationId(id), model);
        UmlModel updated = new UmlModel(model.id(), model.name(), model.classes(), model.associations(), model.invariants(),
                append(model.enumerations(), enumeration), model.packages(), model.imports(), model.dataTypes());
        save(project, updated);
        return ProjectDtoMapper.toDto(enumeration);
    }

    public UmlEnumerationDto updateEnumeration(ProjectId projectId, UmlEnumerationId enumerationId,
            UmlEnumerationDto input) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        if (model.enumerations().stream().noneMatch(value -> value.id().equals(enumerationId))) {
            throw error("UNKNOWN_ENUMERATION", "Unknown Enumeration: " + enumerationId.value(),
                    "Die Enumeration existiert nicht.", Map.of("enumerationId", enumerationId.value()));
        }
        UmlEnumeration replacement = enumeration(input, enumerationId, model);
        UmlModel updated = new UmlModel(model.id(), model.name(), model.classes(), model.associations(), model.invariants(),
                model.enumerations().stream().map(value -> value.id().equals(enumerationId) ? replacement : value).toList(),
                model.packages(), model.imports(), model.dataTypes());
        save(project, updated);
        return ProjectDtoMapper.toDto(replacement);
    }

    public UmlEnumerationDto deleteEnumerationLiteral(ProjectId projectId, UmlEnumerationId enumerationId,
            UmlEnumerationLiteralId literalId) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        UmlEnumeration current = model.enumerations().stream().filter(value -> value.id().equals(enumerationId))
                .findFirst().orElseThrow(() -> error("UNKNOWN_ENUMERATION", "Unknown Enumeration: " + enumerationId.value(),
                        "Die Enumeration existiert nicht.", Map.of("enumerationId", enumerationId.value())));
        List<UmlEnumerationLiteral> literals = current.literalDefinitions().stream()
                .filter(value -> !value.id().equals(literalId)).toList();
        if (literals.size() == current.literalDefinitions().size()) {
            throw error("UNKNOWN_ENUMERATION_LITERAL", "Unknown Enumeration literal: " + literalId.value(),
                    "Das Enumeration-Literal existiert nicht.", Map.of("literalId", literalId.value()));
        }
        UmlEnumeration replacement = new UmlEnumeration(current.id(), current.name(), literals, current.packageId(),
                current.visibility());
        UmlModel updated = new UmlModel(model.id(), model.name(), model.classes(), model.associations(), model.invariants(),
                model.enumerations().stream().map(value -> value.id().equals(enumerationId) ? replacement : value).toList(),
                model.packages(), model.imports(), model.dataTypes());
        save(project, updated);
        return ProjectDtoMapper.toDto(replacement);
    }

    public UmlEnumerationDto deleteEnumeration(ProjectId projectId, UmlEnumerationId enumerationId) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        UmlEnumeration current = model.enumerations().stream().filter(value -> value.id().equals(enumerationId))
                .findFirst().orElseThrow(() -> error("UNKNOWN_ENUMERATION", "Unknown Enumeration: " + enumerationId.value(),
                        "Die Enumeration existiert nicht.", Map.of("enumerationId", enumerationId.value())));
        UmlModel updated = new UmlModel(model.id(), model.name(), model.classes(), model.associations(), model.invariants(),
                model.enumerations().stream().filter(value -> !value.id().equals(enumerationId)).toList(),
                model.packages(), model.imports(), model.dataTypes());
        save(project, updated);
        return ProjectDtoMapper.toDto(current);
    }

    private UmlEnumeration enumeration(UmlEnumerationDto input, UmlEnumerationId id, UmlModel model) {
        List<UmlEnumerationLiteral> literals;
        if (input.literalDefinitions() != null && !input.literalDefinitions().isEmpty()) {
            literals = input.literalDefinitions().stream().map(value -> new UmlEnumerationLiteral(
                    new UmlEnumerationLiteralId(idOrGenerated(value.id(), "enum-literal")),
                    requireName(value.name(), "literalName"))).toList();
        } else {
            literals = safeList(input.literals()).stream().map(name -> new UmlEnumerationLiteral(
                    new UmlEnumerationLiteralId(idOrGenerated(null, "enum-literal")),
                    requireName(name, "literalName"))).toList();
        }
        try {
            return new UmlEnumeration(id, requireName(input.name(), "enumerationName"), literals,
                    packageId(input.packageId()), visibility(input.visibility()));
        } catch (IllegalArgumentException exception) {
            throw error("INVALID_ENUMERATION", exception.getMessage(), "Die Enumeration ist ungueltig.",
                    Map.of("enumerationId", id.value()));
        }
    }

    public UmlDataTypeDto updateDataType(ProjectId projectId, UmlDataTypeId dataTypeId, UmlDataTypeDto input) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        if (model.dataTypes().stream().noneMatch(dataType -> dataType.id().equals(dataTypeId))) {
            throw error("UNKNOWN_DATATYPE", "Unknown DataType: " + dataTypeId.value(),
                    "Der DataType existiert nicht.", Map.of("dataTypeId", dataTypeId.value()));
        }
        UmlDataType replacement = dataType(new UmlDataTypeDto(dataTypeId.value(),
                requireName(input.name(), "dataTypeName"), safeList(input.properties()), input.packageId(), input.qualifiedName(),
                safeList(input.operations())), model);
        UmlModel updated = new UmlModel(model.id(), model.name(), model.classes(), model.associations(), model.invariants(),
                model.enumerations(), model.packages(), model.imports(), model.dataTypes().stream()
                        .map(dataType -> dataType.id().equals(dataTypeId) ? replacement : dataType).toList());
        save(project, updated);
        return ProjectDtoMapper.toDto(replacement, updated);
    }

    public UmlDataTypeDto deleteDataType(ProjectId projectId, UmlDataTypeId dataTypeId) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        UmlDataType current = model.dataTypes().stream().filter(value -> value.id().equals(dataTypeId)).findFirst()
                .orElseThrow(() -> error("UNKNOWN_DATATYPE", "Unknown DataType: " + dataTypeId.value(),
                        "Der DataType existiert nicht.", Map.of("dataTypeId", dataTypeId.value())));
        UmlModel updated = new UmlModel(model.id(), model.name(), model.classes(), model.associations(), model.invariants(),
                model.enumerations(), model.packages(), model.imports(),
                model.dataTypes().stream().filter(value -> !value.id().equals(dataTypeId)).toList());
        save(project, updated);
        return ProjectDtoMapper.toDto(current, model);
    }

    public UmlDataTypeDto deleteDataTypeProperty(ProjectId projectId, UmlDataTypeId dataTypeId, String propertyId) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        UmlDataType current = model.dataTypes().stream().filter(value -> value.id().equals(dataTypeId)).findFirst()
                .orElseThrow(() -> error("UNKNOWN_DATATYPE", "Unknown DataType: " + dataTypeId.value(),
                        "Der DataType existiert nicht.", Map.of("dataTypeId", dataTypeId.value())));
        if (current.properties().stream().noneMatch(property -> property.id().equals(propertyId))) {
            throw error("UNKNOWN_DATATYPE_PROPERTY", "Unknown DataType property: " + propertyId,
                    "Die DataType-Property existiert nicht.",
                    Map.of("dataTypeId", dataTypeId.value(), "propertyId", propertyId));
        }
        UmlDataType replacement = new UmlDataType(current.id(), current.name(), current.properties().stream()
                .filter(property -> !property.id().equals(propertyId)).toList(), current.packageId(), current.operations());
        UmlModel updated = new UmlModel(model.id(), model.name(), model.classes(), model.associations(), model.invariants(),
                model.enumerations(), model.packages(), model.imports(), model.dataTypes().stream()
                        .map(dataType -> dataType.id().equals(dataTypeId) ? replacement : dataType).toList());
        save(project, updated);
        return ProjectDtoMapper.toDto(replacement, updated);
    }

    private UmlDataType dataType(UmlDataTypeDto input, UmlModel model) {
        UmlPackageId packageId = packageId(input.packageId());
        UmlClass typeContext = new UmlClass(new UmlClassId("datatype-context-" + input.id()), input.name(),
                List.of(), List.of(), false, List.of(), UmlVisibility.PUBLIC, packageId);
        return new UmlDataType(new UmlDataTypeId(input.id()), input.name(), safeList(input.properties()).stream()
                .map(property -> new UmlDataTypeProperty(
                        idOrGenerated(property.id(), "datatype-property"),
                        requireName(property.name(), "dataTypePropertyName"),
                        requireKnownType(model, property.type(), typeContext, false)))
                .toList(), packageId, safeList(input.operations()).stream().map(ProjectDtoMapper::toDomain).toList());
    }

    private UmlAttribute attributeWithGeneratedId(UmlAttributeDto input, UmlModel model) {
        return attributeWithGeneratedId(input, model, null);
    }

    private UmlAttribute attributeWithGeneratedId(UmlAttributeDto input, UmlModel model, UmlClass contextClass) {
        validateStaticAttribute(input);
        return new UmlAttribute(
                new UmlAttributeId(idOrGenerated(input.id(), "attr")),
                requireName(input.name(), "attributeName"),
                requireKnownType(model, input.type(), contextClass, false),
                Boolean.TRUE.equals(input.derived()), input.deriveExpression(), input.initExpression(),
                visibility(input.visibility()), safeList(input.redefinedAttributeIds()).stream()
                        .map(UmlAttributeId::new).toList(), Boolean.TRUE.equals(input.staticAttribute()),
                classifierValue(input, model, contextClass));
    }

    private UmlClassifierValue classifierValue(UmlAttributeDto input, UmlModel model) {
        return classifierValue(input, model, null);
    }

    private UmlClassifierValue classifierValue(UmlAttributeDto input, UmlModel model, UmlClass contextClass) {
        if (input.classifierValue() == null) return null;
        var resolvedValueType = resolveKnownType(model, input.classifierValue().type(), contextClass, false);
        var resolvedAttributeType = resolveKnownType(model, input.type(), contextClass, false);
        UmlType type = resolvedValueType.umlType();
        if (!type.name().equals(resolvedAttributeType.umlType().name())) {
            throw error("STATIC_VALUE_TYPE_MISMATCH", "Classifier value type does not match attribute type",
                    "Der Classifier-Wert passt nicht zum Attributtyp.",
                    Map.of("attributeId", input.id() == null ? "" : input.id(), "expectedType", input.type(),
                            "actualType", input.classifierValue().type()));
        }
        Object normalized = validateClassifierValue(input.classifierValue().value(), resolvedValueType, input.id());
        return new UmlClassifierValue(type, normalized);
    }

    private void validateStaticAttribute(UmlAttributeDto input) {
        if (Boolean.TRUE.equals(input.staticAttribute())
                && (containsSelf(input.deriveExpression()) || containsSelf(input.initExpression()))) {
            throw error("STATIC_CONTEXT_SELF_REFERENCE",
                    "Static attribute expression must not reference self",
                    "Ein statischer Ausdruck darf nicht auf self zugreifen.",
                    Map.of("attributeId", input.id() == null ? "" : input.id(),
                            "field", containsSelf(input.deriveExpression()) ? "deriveExpression" : "initExpression"));
        }
        if (!Boolean.TRUE.equals(input.staticAttribute()) && input.classifierValue() != null) {
            throw error("CLASSIFIER_VALUE_REQUIRES_STATIC_ATTRIBUTE",
                    "Classifier value requires a static attribute",
                    "Ein Classifier-Wert setzt ein statisches Attribut voraus.",
                    Map.of("attributeId", input.id() == null ? "" : input.id(), "field", "classifierValue"));
        }
        if (Boolean.TRUE.equals(input.derived()) && input.classifierValue() != null) {
            throw error("DERIVED_STATIC_VALUE_READ_ONLY",
                    "Derived static attributes must not store a classifier value",
                    "Abgeleitete statische Werte sind schreibgeschützt.",
                    Map.of("attributeId", input.id() == null ? "" : input.id(), "field", "classifierValue"));
        }
    }

    private void validateOperationDraft(UmlOperationDto input) {
        if (Boolean.TRUE.equals(input.abstractOperation()) && input.bodyExpression() != null
                && !input.bodyExpression().isBlank()) {
            throw error("ABSTRACT_OPERATION_BODY_NOT_ALLOWED",
                    "Abstract operations must not define a body expression",
                    "Abstrakte Operationen duerfen keinen Body-Ausdruck besitzen.",
                    Map.of("operationId", input.id() == null ? "" : input.id(), "field", "bodyExpression"));
        }
        if (!Boolean.TRUE.equals(input.staticOperation())) return;
        if (containsSelf(input.bodyExpression())) {
            throw error("STATIC_CONTEXT_SELF_REFERENCE", "Static operation body must not reference self",
                    "Eine statische Operation darf nicht auf self zugreifen.",
                    Map.of("operationId", input.id() == null ? "" : input.id(), "field", "bodyExpression"));
        }
        safeList(input.contracts()).stream().filter(contract -> containsSelf(contract.expression())).findFirst()
                .ifPresent(contract -> {
                    throw error("STATIC_CONTEXT_SELF_REFERENCE", "Static operation contract must not reference self",
                            "Ein Vertrag einer statischen Operation darf nicht auf self zugreifen.",
                            Map.of("operationId", input.id() == null ? "" : input.id(),
                                    "contractId", contract.id() == null ? "" : contract.id(), "field", "contracts"));
                });
    }

    private boolean containsSelf(String expression) {
        return expression != null && new OclLexer().tokenize(expression).tokens().stream()
                .anyMatch(token -> token.type() == OclTokenType.SELF);
    }

    private Object validateClassifierValue(Object value, StructuredUmlTypeService.ResolvedType type, String attributeId) {
        try {
            return structuredTypes.normalizeValue(value, type, "classifierValue.value");
        } catch (StructuredUmlTypeService.TypeException exception) {
            throw error("STATIC_VALUE_TYPE_MISMATCH", exception.getMessage(),
                    "Der Classifier-Wert passt nicht zum Attributtyp.",
                    Map.of("attributeId", attributeId == null ? "" : attributeId,
                            "expectedType", type.umlType().name(), "fieldPath", exception.path(),
                            "reason", exception.reason(), "actualValue", String.valueOf(exception.actualValue())));
        }
    }

    private UmlOperation operationWithGeneratedId(UmlOperationDto input, UmlModel model) {
        return new UmlOperation(
                new UmlOperationId(idOrGenerated(input.id(), "op")),
                requireName(input.name(), "operationName"),
                requireKnownType(model, input.returnType(), true),
                safeList(input.parameters()).stream().map(parameter -> parameterWithGeneratedId(parameter, model)).toList(),
                input.bodyExpression(), visibility(input.visibility()),
                Boolean.TRUE.equals(input.abstractOperation()), Boolean.TRUE.equals(input.query()),
                Boolean.TRUE.equals(input.staticOperation()),
                contracts(input), safeList(input.redefinedOperationIds()).stream().map(UmlOperationId::new).toList());
    }

    private List<UmlOperationContract> contracts(UmlOperationDto input) {
        return safeList(input.contracts()).stream().map(contract -> new UmlOperationContract(
                idOrGenerated(contract.id(), "contract"), requireName(contract.name(), "contractName"),
                UmlOperationContract.Kind.valueOf(contract.kind().toUpperCase()),
                requireName(contract.expression(), "contractExpression"),
                !Boolean.FALSE.equals(contract.enabled()))).toList();
    }

    private UmlParameter parameterWithGeneratedId(UmlParameterDto input, UmlModel model) {
        return new UmlParameter(
                new UmlParameterId(idOrGenerated(input.id(), "param")),
                requireName(input.name(), "parameterName"),
                requireKnownType(model, input.type(), false),
                input.direction() == null ? de.useweb.backend.domain.uml.ParameterDirection.IN
                        : de.useweb.backend.domain.uml.ParameterDirection.valueOf(input.direction().toUpperCase()),
                input.position() == null ? 0 : input.position());
    }

    private boolean sameOperationSignature(UmlOperation left, UmlOperation right) {
        return left.name().equals(right.name())
                && left.parameters().stream().map(parameter -> parameter.type()).toList()
                        .equals(right.parameters().stream().map(parameter -> parameter.type()).toList());
    }

    private UmlModelException duplicateOperation(UmlClassId classId, UmlOperation operation) {
        return error(TYPE_ERROR, "Duplicate operation signature: " + operation.name(),
                "Eine Operation mit derselben Signatur ist in der Klasse bereits vorhanden.",
                Map.of("classId", classId.value(), "operationName", operation.name(),
                        "parameterTypes", operation.parameters().stream()
                                .map(parameter -> parameter.type().name()).toList()));
    }

    private UmlAssociationEnd associationEndWithGeneratedId(UmlAssociationEndDto input, UmlModel model) {
        UmlClassId classId = new UmlClassId(input.classId());
        requireClass(model, classId);
        return new UmlAssociationEnd(
                new UmlAssociationEndId(idOrGenerated(input.id(), "end")),
                classId,
                optionalRoleName(input.roleName()),
                multiplicity(input.multiplicity()),
                input.navigable(), Boolean.TRUE.equals(input.ordered()), !Boolean.FALSE.equals(input.unique()),
                Boolean.TRUE.equals(input.derived()), Boolean.TRUE.equals(input.union()),
                safeList(input.subsettedEndIds()).stream().map(UmlAssociationEndId::new).toList(),
                safeList(input.redefinedEndIds()).stream().map(UmlAssociationEndId::new).toList(),
                qualifierDefinitions(input, model), aggregationKind(input.aggregationKind()), input.deriveExpression());
    }

    private UmlAssociationEnd associationEndForUpdate(UmlAssociationEndDto input, UmlModel model,
            Set<UmlAssociationEndId> currentEndIds, boolean shapeChanged) {
        if (!shapeChanged && (input.id() == null || input.id().isBlank())) {
            throw error(TYPE_ERROR, "Association end id is required when updating an association",
                    "Beim Aktualisieren muss jedes Assoziationsende seine stabile ID behalten.", Map.of());
        }
        UmlAssociationEndId endId = new UmlAssociationEndId(idOrGenerated(input.id(), "end"));
        if (!shapeChanged && !currentEndIds.contains(endId)) {
            throw error(TYPE_ERROR, "Unknown association end for update: " + input.id(),
                    "Das Assoziationsende gehört nicht zur aktualisierten Assoziation.",
                    Map.of("associationEndId", input.id()));
        }
        UmlClassId classId = new UmlClassId(input.classId());
        requireClass(model, classId);
        return new UmlAssociationEnd(endId, classId, optionalRoleName(input.roleName()),
                multiplicity(input.multiplicity()), input.navigable(), Boolean.TRUE.equals(input.ordered()),
                !Boolean.FALSE.equals(input.unique()), Boolean.TRUE.equals(input.derived()),
                Boolean.TRUE.equals(input.union()),
                safeList(input.subsettedEndIds()).stream().map(UmlAssociationEndId::new).toList(),
                safeList(input.redefinedEndIds()).stream().map(UmlAssociationEndId::new).toList(),
                qualifierDefinitions(input, model), aggregationKind(input.aggregationKind()), input.deriveExpression());
    }

    private UmlClassId associationClassId(String input, UmlModel model, UmlAssociationId currentAssociationId) {
        if (input == null || input.isBlank()) return null;
        UmlClassId classId = new UmlClassId(input);
        requireClass(model, classId);
        if (model.associations().stream().anyMatch(association -> !association.id().equals(currentAssociationId)
                && association.associationClassId() != null
                && association.associationClassId().equals(classId))) {
            throw error(TYPE_ERROR, "Class is already bound to another association",
                    "Die Klasse ist bereits mit einer anderen Assoziation verbunden.", Map.of("classId", input));
        }
        return classId;
    }

    private de.useweb.backend.domain.uml.AggregationKind aggregationKind(String input) {
        try {
            return input == null ? de.useweb.backend.domain.uml.AggregationKind.NONE
                    : de.useweb.backend.domain.uml.AggregationKind.valueOf(input.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw error(TYPE_ERROR, "Unknown aggregation kind: " + input,
                    "Die Aggregationsart ist ungültig.", Map.of("aggregationKind", input));
        }
    }

    private List<de.useweb.backend.domain.uml.UmlQualifierDefinition> qualifierDefinitions(
            UmlAssociationEndDto input, UmlModel model) {
        return safeList(input.qualifiers()).stream().map(qualifier ->
                new de.useweb.backend.domain.uml.UmlQualifierDefinition(
                        new de.useweb.backend.domain.uml.UmlQualifierId(idOrGenerated(qualifier.id(), "qualifier")),
                        requireName(qualifier.name(), "qualifierName"),
                        requireKnownType(model, qualifier.type(), false),
                        qualifier.order() == null ? 0 : qualifier.order())).toList();
    }

    private Multiplicity multiplicity(MultiplicityDto input) {
        if (input == null) {
            throw error(TYPE_ERROR, "Multiplicity must not be null", "Die Multiplizität darf nicht fehlen.", Map.of());
        }
        try {
            return ProjectDtoMapper.toDomain(input);
        } catch (IllegalArgumentException exception) {
            throw error(TYPE_ERROR, exception.getMessage(), "Die Multiplizität ist ungültig.", Map.of("raw", input.raw()));
        }
    }

    private UmlClass requireClass(UmlModel model, UmlClassId classId) {
        return model.findClass(classId)
                .orElseThrow(() -> error(UNKNOWN_CLASS, "Unknown class: " + classId.value(), "Die referenzierte Klasse existiert nicht.",
                        Map.of("classId", classId.value())));
    }

    private UmlAttribute requireAttribute(UmlClass owner, UmlAttributeId attributeId) {
        return owner.findAttribute(attributeId)
                .orElseThrow(() -> error(UNKNOWN_ATTRIBUTE, "Unknown attribute: " + attributeId.value(), "Das Attribut existiert nicht.",
                        Map.of("classId", owner.id().value(), "attributeId", attributeId.value())));
    }

    private UmlOperation requireOperation(UmlClass owner, UmlOperationId operationId) {
        return owner.findOperation(operationId)
                .orElseThrow(() -> error(TYPE_ERROR, "Unknown operation: " + operationId.value(), "Die Operation existiert nicht.",
                        Map.of("classId", owner.id().value(), "operationId", operationId.value())));
    }

    private UmlAssociation requireAssociation(UmlModel model, UmlAssociationId associationId) {
        return model.findAssociation(associationId)
                .orElseThrow(() -> error(TYPE_ERROR, "Unknown association: " + associationId.value(), "Die Assoziation existiert nicht.",
                        Map.of("associationId", associationId.value())));
    }

    private UmlInvariant requireInvariant(UmlModel model, UmlInvariantId invariantId) {
        return model.findInvariant(invariantId)
                .orElseThrow(() -> error(TYPE_ERROR, "Unknown invariant: " + invariantId.value(), "Die Invariante existiert nicht.",
                        Map.of("invariantId", invariantId.value())));
    }

    private UmlType requireKnownType(UmlModel model, String typeName, boolean allowVoid) {
        return requireKnownType(model, typeName, null, allowVoid);
    }

    private UmlType requireKnownType(UmlModel model, String typeName, UmlClass contextClass, boolean allowVoid) {
        return resolveKnownType(model, typeName, contextClass, allowVoid).umlType();
    }

    private StructuredUmlTypeService.ResolvedType resolveKnownType(UmlModel model, String typeName,
            UmlClass contextClass, boolean allowVoid) {
        try {
            return structuredTypes.resolve(model, requireName(typeName, "type"), contextClass, allowVoid);
        } catch (StructuredUmlTypeService.TypeException exception) {
            throw error(TYPE_ERROR, exception.getMessage(), "Der angegebene Typ ist im Modell nicht bekannt.",
                    Map.of("type", typeName == null ? "" : typeName, "fieldPath", exception.path(),
                            "reason", exception.reason()));
        }
    }

    private void requireUniqueClassName(UmlModel model, String name, UmlPackageId packageId, UmlClassId ignoredClassId) {
        boolean duplicate = model.classes().stream()
                .anyMatch(umlClass -> umlClass.name().equals(name)
                        && java.util.Objects.equals(umlClass.packageId(), packageId)
                        && !umlClass.id().equals(ignoredClassId));
        if (duplicate) {
            throw error(TYPE_ERROR, "Duplicate class name: " + name, "Der Klassenname ist bereits vorhanden.",
                    Map.of("className", name));
        }
    }

    private String requireName(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw error(TYPE_ERROR, "Required field is blank: " + fieldName, "Ein Pflichtfeld ist leer.",
                    Map.of("field", fieldName));
        }
        return value.trim();
    }

    private String optionalRoleName(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private UmlModel validatedModel(UmlModel current, List<UmlClass> classes) {
        try {
            return new UmlModel(current.id(), current.name(), classes, current.associations(), current.invariants(),
                    current.enumerations(), current.packages(), current.imports(), current.dataTypes());
        } catch (UmlGeneralizationException exception) {
            throw generalizationError(exception);
        }
    }

    private UmlVisibility visibility(String value) {
        try {
            return value == null || value.isBlank() ? UmlVisibility.PUBLIC : UmlVisibility.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw error(TYPE_ERROR, "Unknown UML visibility: " + value,
                    "Die ausgewaehlte UML-Sichtbarkeit ist ungueltig.", Map.of("visibility", value));
        }
    }

    private UmlPackageId packageId(String value) {
        return value == null || value.isBlank() ? null : new UmlPackageId(value);
    }

    private UmlModelException generalizationError(UmlGeneralizationException exception) {
        String userMessage = switch (exception.code()) {
            case "UNKNOWN_SUPERCLASS" -> "Die ausgewaehlte Oberklasse existiert nicht.";
            case "SELF_GENERALIZATION" -> "Eine Klasse kann nicht ihre eigene Oberklasse sein.";
            case "DUPLICATE_SUPERCLASS" -> "Eine direkte Oberklasse darf nur einmal ausgewaehlt werden.";
            case "GENERALIZATION_CYCLE" -> "Die Vererbung wuerde einen Zyklus erzeugen.";
            case "AMBIGUOUS_INHERITED_FEATURE" -> "Mehrere Oberklassen liefern dasselbe Feature mehrdeutig.";
            case "UNKNOWN_REDEFINED_FEATURE" -> "Das ausgewaehlte Redefinitionsziel existiert nicht.";
            case "INVALID_REDEFINITION_OWNER" -> "Redefinitionsziele muessen von einer Oberklasse geerbt werden.";
            case "INCOMPATIBLE_REDEFINED_FEATURE" -> "Das lokale Feature ist mit dem Redefinitionsziel nicht kompatibel.";
            case "DUPLICATE_FEATURE_ID" -> "Feature-IDs muessen modellweit eindeutig sein.";
            default -> "Die Vererbung ist ungueltig.";
        };
        return error(exception.code(), exception.getMessage(), userMessage, exception.details());
    }

    private String idOrGenerated(String id, String prefix) {
        return id == null || id.isBlank() ? prefix + "-" + UUID.randomUUID() : id;
    }

    private Project save(Project project, UmlModel updatedModel) {
        return projectService.saveProject(new Project(
                project.id(),
                project.metadata(),
                canonicalModelText(project, updatedModel),
                updatedModel,
                project.objectModel(),
                project.layout(),
                project.definitions()));
    }

    private Project save(Project project, UmlModel updatedModel, ObjectModel updatedObjectModel, LayoutInformation updatedLayout) {
        return projectService.saveProject(new Project(
                project.id(),
                project.metadata(),
                canonicalModelText(project, updatedModel),
                updatedModel,
                updatedObjectModel,
                updatedLayout,
                project.definitions()));
    }

    private ModelText canonicalModelText(Project project, UmlModel model) {
        ModelText existing = project.modelText();
        return new ModelText(modelTextRenderer.render(model),
                existing == null ? "USE_MODEL_TEXT" : existing.language(),
                existing == null ? "mvp-subset" : existing.languageVersion(),
                project.metadata().updatedAt(),
                existing == null ? null : existing.sourceName(),
                existing == null ? "generated" : existing.sourceOrigin(),
                existing == null ? List.of() : existing.sources(),
                existing == null ? List.of() : existing.sourceFiles());
    }

    @SafeVarargs
    private final Set<String> union(Set<String>... sets) {
        Set<String> result = new HashSet<>();
        for (Set<String> set : sets) {
            result.addAll(set);
        }
        return Set.copyOf(result);
    }

    private LayoutInformation pruneLayout(LayoutInformation layout, Set<String> removedNodeIds, Set<String> removedEdgeIds) {
        return new LayoutInformation(
                pruneDiagramLayout(layout.classDiagram(), removedNodeIds, removedEdgeIds),
                pruneDiagramLayout(layout.objectDiagram(), removedNodeIds, removedEdgeIds));
    }

    private DiagramLayout pruneDiagramLayout(DiagramLayout layout, Set<String> removedNodeIds, Set<String> removedEdgeIds) {
        return new DiagramLayout(
                layout.nodes().stream().filter(node -> !removedNodeIds.contains(node.elementId())).toList(),
                layout.edges().stream().filter(edge -> !removedEdgeIds.contains(edge.elementId())).toList(),
                layout.viewport());
    }

    private <T> List<T> append(List<T> existing, T element) {
        List<T> elements = new ArrayList<>(existing);
        elements.add(element);
        return List.copyOf(elements);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private UmlModelException error(String code, String message, String userMessage, Map<String, Object> details) {
        return new UmlModelException(code, message, userMessage, details);
    }
}
