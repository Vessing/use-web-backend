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
import de.useweb.backend.api.dto.ocl.OclExpressionDto;
import de.useweb.backend.api.dto.uml.MultiplicityDto;
import de.useweb.backend.api.dto.uml.UmlAssociationDto;
import de.useweb.backend.api.dto.uml.UmlAssociationEndDto;
import de.useweb.backend.api.dto.uml.UmlAttributeDto;
import de.useweb.backend.api.dto.uml.UmlClassDto;
import de.useweb.backend.api.dto.uml.UmlInvariantDto;
import de.useweb.backend.api.dto.uml.UmlModelDto;
import de.useweb.backend.api.dto.uml.UmlOperationDto;
import de.useweb.backend.api.dto.uml.UmlPackageDto;
import de.useweb.backend.api.dto.uml.UmlModelImportDto;
import de.useweb.backend.api.dto.uml.UmlParameterDto;
import de.useweb.backend.api.mapper.ProjectDtoMapper;
import de.useweb.backend.application.project.ProjectService;
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
import de.useweb.backend.error.UmlModelException;

@Service
public class UmlModelService {

    private static final String TYPE_ERROR = "TYPE_ERROR";
    private static final String UNKNOWN_CLASS = "UNKNOWN_CLASS";
    private static final String UNKNOWN_ATTRIBUTE = "UNKNOWN_ATTRIBUTE";

    private final ProjectService projectService;

    public UmlModelService(ProjectService projectService) {
        this.projectService = projectService;
    }

    public UmlModelDto getUmlModel(ProjectId projectId) {
        return ProjectDtoMapper.toDto(projectService.loadProject(projectId).umlModel());
    }

    public UmlClassDto createClass(ProjectId projectId, UmlClassDto input) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        String name = requireName(input.name(), "className");
        requireUniqueClassName(model, name, packageId(input.packageId()), null);

        UmlClass umlClass = new UmlClass(
                new UmlClassId(idOrGenerated(input.id(), "class")),
                name,
                safeList(input.attributes()).stream()
                        .map(attribute -> attributeWithGeneratedId(attribute, model))
                        .toList(),
                safeList(input.operations()).stream()
                        .map(operation -> operationWithGeneratedId(operation, model))
                        .toList(),
                input.abstractClass(),
                safeList(input.superClassIds()).stream().map(UmlClassId::new).toList(),
                visibility(input.visibility()), packageId(input.packageId()));

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
                                umlClass.abstractClass(), umlClass.superClassIds())
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
        if (owner.attributes().stream().anyMatch(attribute -> attribute.name().equals(name))) {
            throw error(TYPE_ERROR, "Duplicate attribute name: " + name, "Der Attributname ist in der Klasse bereits vorhanden.",
                    Map.of("classId", classId.value(), "attributeName", name));
        }
        UmlAttribute attribute = new UmlAttribute(
                new UmlAttributeId(idOrGenerated(input.id(), "attr")),
                name,
                requireKnownType(model, input.type(), false),
                Boolean.TRUE.equals(input.derived()), input.deriveExpression(), input.initExpression());

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
        UmlAttribute replacement = new UmlAttribute(attributeId, requireName(input.name(), "attributeName"),
                requireKnownType(model, input.type(), false), Boolean.TRUE.equals(input.derived()),
                input.deriveExpression(), input.initExpression(), visibility(input.visibility()));
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
        UmlOperation replacement = new UmlOperation(operationId, requireName(input.name(), "operationName"),
                requireKnownType(model, input.returnType(), true), safeList(input.parameters()).stream()
                        .map(parameter -> parameterWithGeneratedId(parameter, model)).toList(),
                input.bodyExpression(), visibility(input.visibility()),
                Boolean.TRUE.equals(input.abstractOperation()), Boolean.TRUE.equals(input.query()),
                contracts(input));
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

    public UmlPackageDto createPackage(ProjectId projectId, UmlPackageDto input) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        UmlPackage umlPackage = new UmlPackage(new UmlPackageId(idOrGenerated(input.id(), "package")),
                requireName(input.qualifiedName(), "qualifiedName"));
        UmlModel updated = new UmlModel(model.id(), model.name(), model.classes(), model.associations(),
                model.invariants(), model.enumerations(), append(model.packages(), umlPackage), model.imports(), model.dataTypes());
        save(project, updated);
        return ProjectDtoMapper.toDto(umlPackage);
    }

    public UmlModelImportDto createImport(ProjectId projectId, UmlModelImportDto input) {
        Project project = projectService.loadProject(projectId);
        UmlModel model = project.umlModel();
        UmlModelImport modelImport = new UmlModelImport(
                new UmlModelImportId(idOrGenerated(input.id(), "import")),
                new UmlPackageId(input.importingPackageId()), new UmlPackageId(input.importedPackageId()),
                input.alias(), input.source(), input.provenance());
        UmlModel updated = new UmlModel(model.id(), model.name(), model.classes(), model.associations(),
                model.invariants(), model.enumerations(), model.packages(), append(model.imports(), modelImport), model.dataTypes());
        save(project, updated);
        return ProjectDtoMapper.toDto(modelImport);
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
            throw error(TYPE_ERROR, "Associations must have at least two ends", "Eine Assoziation muss mindestens zwei Enden haben.",
                    Map.of("associationName", name, "endCount", inputEnds.size()));
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
            throw error(TYPE_ERROR, "Associations must have at least two ends",
                    "Eine Assoziation muss mindestens zwei Enden haben.",
                    Map.of("associationId", associationId.value(), "endCount", inputEnds.size()));
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

    private UmlAttribute attributeWithGeneratedId(UmlAttributeDto input, UmlModel model) {
        return new UmlAttribute(
                new UmlAttributeId(idOrGenerated(input.id(), "attr")),
                requireName(input.name(), "attributeName"),
                requireKnownType(model, input.type(), false),
                Boolean.TRUE.equals(input.derived()), input.deriveExpression(), input.initExpression(),
                visibility(input.visibility()));
    }

    private UmlOperation operationWithGeneratedId(UmlOperationDto input, UmlModel model) {
        return new UmlOperation(
                new UmlOperationId(idOrGenerated(input.id(), "op")),
                requireName(input.name(), "operationName"),
                requireKnownType(model, input.returnType(), true),
                safeList(input.parameters()).stream().map(parameter -> parameterWithGeneratedId(parameter, model)).toList(),
                input.bodyExpression(), visibility(input.visibility()),
                Boolean.TRUE.equals(input.abstractOperation()), Boolean.TRUE.equals(input.query()),
                contracts(input));
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
                requireName(input.roleName(), "roleName"),
                multiplicity(input.multiplicity()),
                input.navigable(), Boolean.TRUE.equals(input.ordered()), !Boolean.FALSE.equals(input.unique()),
                Boolean.TRUE.equals(input.derived()), Boolean.TRUE.equals(input.union()),
                safeList(input.subsettedEndIds()).stream().map(UmlAssociationEndId::new).toList(),
                safeList(input.redefinedEndIds()).stream().map(UmlAssociationEndId::new).toList(),
                qualifierDefinitions(input, model), aggregationKind(input.aggregationKind()));
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
        return new UmlAssociationEnd(endId, classId, requireName(input.roleName(), "roleName"),
                multiplicity(input.multiplicity()), input.navigable(), Boolean.TRUE.equals(input.ordered()),
                !Boolean.FALSE.equals(input.unique()), Boolean.TRUE.equals(input.derived()),
                Boolean.TRUE.equals(input.union()),
                safeList(input.subsettedEndIds()).stream().map(UmlAssociationEndId::new).toList(),
                safeList(input.redefinedEndIds()).stream().map(UmlAssociationEndId::new).toList(),
                qualifierDefinitions(input, model), aggregationKind(input.aggregationKind()));
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
        String normalizedType = requireName(typeName, "type");
        if (allowVoid && Objects.equals("Void", normalizedType)) {
            return UmlType.VOID;
        }
        for (PrimitiveType primitiveType : PrimitiveType.values()) {
            if (primitiveType.displayName().equals(normalizedType)) {
                return switch (primitiveType) {
                    case STRING -> UmlType.STRING;
                    case INTEGER -> UmlType.INTEGER;
                    case REAL -> UmlType.REAL;
                    case BOOLEAN -> UmlType.BOOLEAN;
                };
            }
        }
        boolean classTypeExists = model.classes().stream().anyMatch(umlClass -> umlClass.name().equals(normalizedType));
        if (classTypeExists) {
            return UmlType.classType(normalizedType);
        }
        if (normalizedType.equals("UnlimitedNatural")) {
            return UmlType.UNLIMITED_NATURAL;
        }
        if (model.findEnumerationByName(normalizedType).isPresent()) {
            return UmlType.enumerationType(normalizedType);
        }
        throw error(TYPE_ERROR, "Unknown type: " + normalizedType, "Der angegebene Typ ist im Modell nicht bekannt.",
                Map.of("type", normalizedType));
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
                project.modelText(),
                updatedModel,
                project.objectModel(),
                project.layout()));
    }

    private Project save(Project project, UmlModel updatedModel, ObjectModel updatedObjectModel, LayoutInformation updatedLayout) {
        return projectService.saveProject(new Project(
                project.id(),
                project.metadata(),
                project.modelText(),
                updatedModel,
                updatedObjectModel,
                updatedLayout));
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
