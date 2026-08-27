package de.useweb.backend.domain.uml;

import java.util.List;
import java.util.Optional;
import java.util.HashSet;
import java.util.Map;

public record UmlClass(
        UmlClassId id,
        String name,
        List<UmlAttribute> attributes,
        List<UmlOperation> operations,
        boolean abstractClass,
        List<UmlClassId> superClassIds,
        UmlVisibility visibility,
        UmlPackageId packageId) {

    public UmlClass(UmlClassId id, String name, List<UmlAttribute> attributes, List<UmlOperation> operations,
            boolean abstractClass, List<UmlClassId> superClassIds) {
        this(id, name, attributes, operations, abstractClass, superClassIds, UmlVisibility.PUBLIC, null);
    }

    public UmlClass(UmlClassId id, String name, List<UmlAttribute> attributes, List<UmlOperation> operations) {
        this(id, name, attributes, operations, false, List.of(), UmlVisibility.PUBLIC, null);
    }

    public UmlClass {
        if (id == null) {
            throw new IllegalArgumentException("UmlClass id must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("UmlClass name must not be blank");
        }
        attributes = List.copyOf(attributes == null ? List.of() : attributes);
        operations = List.copyOf(operations == null ? List.of() : operations);
        superClassIds = List.copyOf(superClassIds == null ? List.of() : superClassIds);
        visibility = UmlVisibility.defaulted(visibility);
        if (superClassIds.contains(id)) {
            throw new UmlGeneralizationException("SELF_GENERALIZATION",
                    "A UML class must not generalize itself",
                    Map.of("classId", id.value()));
        }
        if (new HashSet<>(superClassIds).size() != superClassIds.size()) {
            throw new UmlGeneralizationException("DUPLICATE_SUPERCLASS",
                    "Direct superclasses must be unique",
                    Map.of("classId", id.value()));
        }
    }

    public String qualifiedName(UmlModel model) {
        return packageId == null ? name : model.findPackage(packageId)
                .map(pkg -> pkg.qualifiedName() + "::" + name).orElse(name);
    }

    public Optional<UmlAttribute> findAttribute(UmlAttributeId attributeId) {
        return attributes.stream()
                .filter(attribute -> attribute.id().equals(attributeId))
                .findFirst();
    }

    public Optional<UmlOperation> findOperation(UmlOperationId operationId) {
        return operations.stream()
                .filter(operation -> operation.id().equals(operationId))
                .findFirst();
    }
}
