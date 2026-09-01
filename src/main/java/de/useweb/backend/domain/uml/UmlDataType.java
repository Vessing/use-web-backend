package de.useweb.backend.domain.uml;

import java.util.HashSet;
import java.util.List;

public record UmlDataType(UmlDataTypeId id, String name, List<UmlDataTypeProperty> properties,
        UmlPackageId packageId, List<UmlOperation> operations) {
    public UmlDataType(UmlDataTypeId id, String name, List<UmlDataTypeProperty> properties) {
        this(id, name, properties, null, List.of());
    }

    public UmlDataType(UmlDataTypeId id, String name, List<UmlDataTypeProperty> properties,
            UmlPackageId packageId) {
        this(id, name, properties, packageId, List.of());
    }

    public UmlDataType {
        if (id == null || name == null || name.isBlank()) {
            throw new IllegalArgumentException("DataType identity and name must not be blank");
        }
        properties = List.copyOf(properties == null ? List.of() : properties);
        operations = List.copyOf(operations == null ? List.of() : operations);
        if (new HashSet<>(properties.stream().map(UmlDataTypeProperty::id).toList()).size() != properties.size()
                || new HashSet<>(properties.stream().map(UmlDataTypeProperty::name).toList()).size() != properties.size()) {
            throw new IllegalArgumentException("DataType property ids and names must be unique");
        }
        if (new HashSet<>(operations.stream().map(UmlOperation::id).toList()).size() != operations.size()) {
            throw new IllegalArgumentException("DataType operation ids must be unique");
        }
    }

    public String qualifiedName(UmlModel model) {
        if (packageId == null) return name;
        return model.findPackage(packageId).map(pkg -> pkg.qualifiedName() + "::" + name).orElse(name);
    }
}
