package de.useweb.backend.domain.uml;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.function.Function;

public record UmlModel(
        UmlModelId id,
        String name,
        List<UmlClass> classes,
        List<UmlAssociation> associations,
        List<UmlInvariant> invariants,
        List<UmlEnumeration> enumerations,
        List<UmlPackage> packages,
        List<UmlModelImport> imports,
        List<UmlDataType> dataTypes) {

    public UmlModel(UmlModelId id, String name, List<UmlClass> classes, List<UmlAssociation> associations,
            List<UmlInvariant> invariants, List<UmlEnumeration> enumerations, List<UmlPackage> packages,
            List<UmlModelImport> imports) {
        this(id, name, classes, associations, invariants, enumerations, packages, imports, List.of());
    }

    public UmlModel(UmlModelId id, String name, List<UmlClass> classes,
            List<UmlAssociation> associations, List<UmlInvariant> invariants, List<UmlEnumeration> enumerations) {
        this(id, name, classes, associations, invariants, enumerations, List.of(), List.of(), List.of());
    }

    public UmlModel(UmlModelId id, String name, List<UmlClass> classes,
            List<UmlAssociation> associations, List<UmlInvariant> invariants) {
        this(id, name, classes, associations, invariants, List.of(), List.of(), List.of(), List.of());
    }

    public UmlModel {
        if (id == null) {
            throw new IllegalArgumentException("UmlModel id must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("UmlModel name must not be blank");
        }
        classes = List.copyOf(classes == null ? List.of() : classes);
        associations = List.copyOf(associations == null ? List.of() : associations);
        invariants = List.copyOf(invariants == null ? List.of() : invariants);
        enumerations = List.copyOf(enumerations == null ? List.of() : enumerations);
        packages = List.copyOf(packages == null ? List.of() : packages);
        imports = List.copyOf(imports == null ? List.of() : imports);
        dataTypes = List.copyOf(dataTypes == null ? List.of() : dataTypes);
        validateNamespaces(classes, enumerations, dataTypes, packages, imports);
        validateClassifierNames(classes, enumerations, dataTypes, packages);
        validateGeneralizations(classes);
        validateInheritedFeatureResolution(classes);
        validateAssociationMetadata(classes, associations);
    }

    private static void validateAssociationMetadata(List<UmlClass> classes, List<UmlAssociation> associations) {
        Map<UmlAssociationEndId, UmlAssociationEnd> ends = associations.stream()
                .flatMap(association -> association.ends().stream())
                .collect(java.util.stream.Collectors.toMap(UmlAssociationEnd::id, Function.identity(),
                        (left, right) -> { throw associationError("DUPLICATE_ASSOCIATION_END_ID",
                                "Duplicate association end id '" + left.id().value() + "'",
                                Map.of("associationEndId", left.id().value())); }));
        Set<UmlClassId> classIds = classes.stream().map(UmlClass::id).collect(java.util.stream.Collectors.toSet());
        Set<UmlClassId> associationClassIds = new java.util.HashSet<>();
        for (UmlAssociation association : associations) {
            if (association.associationClassId() != null) {
                if (!classIds.contains(association.associationClassId())) {
                    throw associationError("UNKNOWN_ASSOCIATION_CLASS",
                            "Association references unknown association class '" + association.associationClassId().value() + "'",
                            Map.of("associationId", association.id().value(),
                                    "associationClassId", association.associationClassId().value()));
                }
                if (!associationClassIds.add(association.associationClassId())) {
                    throw associationError("DUPLICATE_ASSOCIATION_CLASS_BINDING",
                            "A class may classify only one association",
                            Map.of("associationClassId", association.associationClassId().value()));
                }
            }
            for (UmlAssociationEnd end : association.ends()) {
                if (!classIds.contains(end.classId())) {
                    throw associationError("UNKNOWN_ASSOCIATION_END_CLASS",
                            "Association end references unknown class '" + end.classId().value() + "'",
                            Map.of("associationId", association.id().value(), "associationEndId", end.id().value(),
                                    "classId", end.classId().value()));
                }
            }
            if (association.ends().stream().map(UmlAssociationEnd::roleName).distinct().count()
                    != association.ends().size()) {
                boolean reflexive = association.ends().stream().map(UmlAssociationEnd::classId).distinct().count() == 1;
                throw associationError(reflexive ? "AMBIGUOUS_REFLEXIVE_ROLE" : "AMBIGUOUS_ASSOCIATION_ROLE",
                        "Association ends must have distinct role names",
                        Map.of("associationId", association.id().value()));
            }
        }
        for (UmlAssociation association : associations) {
            for (UmlAssociationEnd end : association.ends()) {
                validateEndReferences(end, association, end.subsettedEndIds(), "SUBSETS", ends, associations, classes);
                validateEndReferences(end, association, end.redefinedEndIds(), "REDEFINES", ends, associations, classes);
            }
        }
        detectEndReferenceCycles(ends, true);
        detectEndReferenceCycles(ends, false);
    }

    private static void validateEndReferences(UmlAssociationEnd end, UmlAssociation association,
            List<UmlAssociationEndId> references, String relation,
            Map<UmlAssociationEndId, UmlAssociationEnd> ends, List<UmlAssociation> associations,
            List<UmlClass> classes) {
        for (UmlAssociationEndId referenceId : references) {
            UmlAssociationEnd referenced = ends.get(referenceId);
            if (referenced == null) {
                throw associationError("UNKNOWN_" + relation + "_END",
                        relation + " references unknown association end '" + referenceId.value() + "'",
                        Map.of("associationEndId", end.id().value(), "referencedEndId", referenceId.value()));
            }
            if (!isSubtype(end.classId(), referenced.classId(), classes)
                    || !multiplicityConforms(end.multiplicity(), referenced.multiplicity())) {
                throw associationError("INCOMPATIBLE_" + relation + "_END",
                        relation + " requires a conforming target type and multiplicity",
                        Map.of("associationEndId", end.id().value(), "referencedEndId", referenceId.value()));
            }
            if (relation.equals("REDEFINES")) {
                UmlAssociation referencedAssociation = associationContaining(referenceId, associations);
                List<UmlClassId> sources = association.ends().stream().filter(candidate -> !candidate.id().equals(end.id()))
                        .map(UmlAssociationEnd::classId).toList();
                List<UmlClassId> referencedSources = referencedAssociation.ends().stream()
                        .filter(candidate -> !candidate.id().equals(referenceId)).map(UmlAssociationEnd::classId).toList();
                boolean contextConforms = referencedSources.stream().allMatch(base ->
                        sources.stream().anyMatch(source -> isSubtype(source, base, classes)));
                if (!contextConforms) {
                    throw associationError("INVALID_REDEFINITION_CONTEXT",
                            "A redefined end must be owned by a more specific association context",
                            Map.of("associationEndId", end.id().value(), "referencedEndId", referenceId.value()));
                }
            }
        }
    }

    private static boolean multiplicityConforms(Multiplicity candidate, Multiplicity base) {
        if (candidate.lower() < base.lower()) return false;
        if (base.unbounded() || base.upper() == null) return true;
        return !candidate.unbounded() && candidate.upper() != null && candidate.upper() <= base.upper();
    }

    private static UmlAssociation associationContaining(UmlAssociationEndId endId, List<UmlAssociation> associations) {
        return associations.stream().filter(a -> a.findEnd(endId).isPresent()).findFirst().orElseThrow();
    }

    private static boolean isSubtype(UmlClassId candidate, UmlClassId base, List<UmlClass> classes) {
        if (candidate.equals(base)) return true;
        UmlClass umlClass = classes.stream().filter(c -> c.id().equals(candidate)).findFirst().orElse(null);
        return umlClass != null && umlClass.superClassIds().stream().anyMatch(parent -> isSubtype(parent, base, classes));
    }

    private static void detectEndReferenceCycles(Map<UmlAssociationEndId, UmlAssociationEnd> ends, boolean subsets) {
        for (UmlAssociationEndId origin : ends.keySet()) {
            detectEndReferenceCycle(origin, origin, ends, subsets, new LinkedHashSet<>());
        }
    }

    private static void detectEndReferenceCycle(UmlAssociationEndId origin, UmlAssociationEndId current,
            Map<UmlAssociationEndId, UmlAssociationEnd> ends, boolean subsets, Set<UmlAssociationEndId> path) {
        if (!path.add(current)) return;
        List<UmlAssociationEndId> next = subsets ? ends.get(current).subsettedEndIds() : ends.get(current).redefinedEndIds();
        for (UmlAssociationEndId target : next) {
            if (target.equals(origin)) {
                String relation = subsets ? "SUBSETS" : "REDEFINES";
                throw associationError(relation + "_CYCLE", relation + " relation contains a cycle",
                        Map.of("associationEndId", origin.value()));
            }
            detectEndReferenceCycle(origin, target, ends, subsets, new LinkedHashSet<>(path));
        }
    }

    private static UmlAssociationMetadataException associationError(String code, String message,
            Map<String, Object> details) {
        return new UmlAssociationMetadataException(code, message, details);
    }

    public Optional<UmlClass> findClass(UmlClassId classId) {
        return classes.stream()
                .filter(umlClass -> umlClass.id().equals(classId))
                .findFirst();
    }

    public Optional<UmlAttribute> findAttribute(UmlAttributeId attributeId) {
        return classes.stream()
                .flatMap(umlClass -> umlClass.attributes().stream())
                .filter(attribute -> attribute.id().equals(attributeId))
                .findFirst();
    }

    public Optional<UmlAssociation> findAssociation(UmlAssociationId associationId) {
        return associations.stream()
                .filter(association -> association.id().equals(associationId))
                .findFirst();
    }

    public Optional<UmlInvariant> findInvariant(UmlInvariantId invariantId) {
        return invariants.stream()
                .filter(invariant -> invariant.id().equals(invariantId))
                .findFirst();
    }

    public Optional<UmlClass> findClassByName(String className) {
        return classes.stream().filter(umlClass -> umlClass.name().equals(className)).findFirst();
    }

    public Optional<UmlPackage> findPackage(UmlPackageId packageId) {
        return packages.stream().filter(pkg -> pkg.id().equals(packageId)).findFirst();
    }

    public Optional<UmlPackage> findPackageByQualifiedName(String qualifiedName) {
        return packages.stream().filter(pkg -> pkg.qualifiedName().equals(qualifiedName)).findFirst();
    }

    public Optional<UmlClass> resolveClass(String name, UmlClass contextClass) {
        String currentNamespace = namespaceOf(contextClass);
        List<UmlClass> candidates = classCandidates(name, currentNamespace);
        List<UmlClass> visible = candidates.stream().filter(candidate -> isVisible(candidate.visibility(), candidate,
                contextClass)).toList();
        return visible.size() == 1 ? Optional.of(visible.getFirst()) : Optional.empty();
    }

    public Optional<UmlClass> resolveClassIgnoringVisibility(String name, UmlClass contextClass) {
        List<UmlClass> candidates = classCandidates(name, namespaceOf(contextClass));
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    public boolean isAmbiguousClassName(String name, UmlClass contextClass) {
        return classCandidates(name, namespaceOf(contextClass)).stream()
                .filter(candidate -> isVisible(candidate.visibility(), candidate, contextClass)).count() > 1;
    }

    public String namespaceOf(UmlClass umlClass) {
        return umlClass == null || umlClass.packageId() == null ? "" : findPackage(umlClass.packageId())
                .map(UmlPackage::qualifiedName).orElse("");
    }

    public boolean isVisible(UmlVisibility visibility, UmlClass owner, UmlClass accessingClass) {
        return switch (UmlVisibility.defaulted(visibility)) {
            case PUBLIC -> true;
            case PRIVATE -> owner.id().equals(accessingClass.id());
            case PROTECTED -> isSubtypeOf(accessingClass.id(), owner.id());
            case PACKAGE -> namespaceOf(owner).equals(namespaceOf(accessingClass));
        };
    }

    public Optional<ResolvedAttribute> resolveAttribute(UmlClassId receiverClassId, String name) {
        return typeConformanceOrder(receiverClassId).stream().map(this::findClass).flatMap(Optional::stream)
                .flatMap(owner -> owner.attributes().stream().filter(attribute -> attribute.name().equals(name))
                        .map(attribute -> new ResolvedAttribute(owner, attribute)))
                .findFirst();
    }

    public Optional<ResolvedOperation> resolveOperation(UmlClassId receiverClassId, String name, int parameterCount) {
        return resolveOperations(receiverClassId, name, parameterCount).stream().findFirst();
    }

    public List<ResolvedOperation> resolveOperations(UmlClassId receiverClassId, String name, int parameterCount) {
        return typeConformanceOrder(receiverClassId).stream().map(this::findClass).flatMap(Optional::stream)
                .flatMap(owner -> owner.operations().stream()
                        .filter(operation -> operation.name().equals(name) && operation.parameters().size() == parameterCount)
                        .map(operation -> new ResolvedOperation(owner, operation)))
                .toList();
    }

    public Optional<UmlEnumeration> findEnumerationByName(String enumerationName) {
        List<UmlEnumeration> candidates = enumerationCandidates(enumerationName, "");
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    public Optional<UmlEnumeration> resolveEnumeration(String name, UmlClass contextClass) {
        List<UmlEnumeration> candidates = enumerationCandidates(name, namespaceOf(contextClass));
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    public boolean isAmbiguousEnumerationName(String name, UmlClass contextClass) {
        return enumerationCandidates(name, namespaceOf(contextClass)).size() > 1;
    }

    public Optional<UmlDataType> findDataTypeByName(String name) {
        List<UmlDataType> candidates = dataTypeCandidates(name, "");
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    public Optional<UmlDataType> resolveDataType(String name, UmlClass contextClass) {
        List<UmlDataType> candidates = dataTypeCandidates(name, namespaceOf(contextClass));
        return candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    public boolean isSubtypeOf(UmlClassId candidate, UmlClassId target) {
        return candidate.equals(target) || superClassesOf(candidate).stream().anyMatch(target::equals);
    }

    public List<UmlClassId> typeConformanceOrder(UmlClassId classId) {
        LinkedHashSet<UmlClassId> result = new LinkedHashSet<>();
        ArrayDeque<UmlClassId> pending = new ArrayDeque<>();
        pending.add(classId);
        while (!pending.isEmpty()) {
            UmlClassId current = pending.removeFirst();
            if (result.add(current)) {
                findClass(current).ifPresent(umlClass -> pending.addAll(umlClass.superClassIds()));
            }
        }
        return List.copyOf(result);
    }

    public List<UmlClassId> superClassesOf(UmlClassId classId) {
        List<UmlClassId> order = typeConformanceOrder(classId);
        return order.size() <= 1 ? List.of() : order.subList(1, order.size());
    }

    public List<UmlClass> concreteSubtypesOf(UmlClassId classId) {
        return classes.stream()
                .filter(candidate -> !candidate.abstractClass() && isSubtypeOf(candidate.id(), classId))
                .toList();
    }

    public Optional<UmlClass> leastCommonSuperClass(UmlClassId left, UmlClassId right) {
        if (left.equals(right)) {
            return findClass(left);
        }
        Set<UmlClassId> rightTypes = new LinkedHashSet<>(typeConformanceOrder(right));
        List<UmlClassId> common = typeConformanceOrder(left).stream().filter(rightTypes::contains).toList();
        List<UmlClassId> minimal = common.stream()
                .filter(candidate -> common.stream().noneMatch(other -> !other.equals(candidate)
                        && isSubtypeOf(other, candidate)))
                .toList();
        return minimal.size() == 1 ? findClass(minimal.getFirst()) : Optional.empty();
    }

    public Optional<UmlAttribute> findAttribute(UmlClassId classId, String attributeName) {
        return resolveAttribute(classId, attributeName).map(ResolvedAttribute::attribute);
    }

    private List<UmlClass> classCandidates(String requestedName, String currentNamespace) {
        if (requestedName.contains("::")) {
            List<UmlClass> direct = classes.stream()
                    .filter(candidate -> candidate.qualifiedName(this).equals(requestedName)).toList();
            if (!direct.isEmpty()) return direct;
            String qualified = expandAlias(requestedName, currentNamespace);
            return classes.stream().filter(candidate -> candidate.qualifiedName(this).equals(qualified)).toList();
        }
        List<UmlClass> local = classes.stream().filter(candidate -> candidate.name().equals(requestedName)
                && namespaceOf(candidate).equals(currentNamespace)).toList();
        if (!local.isEmpty()) return local;
        Set<String> importedNamespaces = imports.stream()
                .filter(modelImport -> namespaceOf(modelImport.importingPackageId()).equals(currentNamespace))
                .map(modelImport -> namespaceOf(modelImport.importedPackageId()))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<UmlClass> imported = classes.stream().filter(candidate -> candidate.name().equals(requestedName)
                && importedNamespaces.contains(namespaceOf(candidate))).toList();
        if (!imported.isEmpty()) return imported;
        return classes.stream().filter(candidate -> candidate.name().equals(requestedName)).toList();
    }

    private List<UmlEnumeration> enumerationCandidates(String requestedName, String currentNamespace) {
        return classifierCandidates(requestedName, currentNamespace, enumerations,
                UmlEnumeration::name, value -> value.qualifiedName(this), UmlEnumeration::packageId);
    }

    private List<UmlDataType> dataTypeCandidates(String requestedName, String currentNamespace) {
        return classifierCandidates(requestedName, currentNamespace, dataTypes,
                UmlDataType::name, value -> value.qualifiedName(this), UmlDataType::packageId);
    }

    private <T> List<T> classifierCandidates(String requestedName, String currentNamespace, List<T> values,
            Function<T, String> name, Function<T, String> qualifiedName, Function<T, UmlPackageId> packageId) {
        if (requestedName.contains("::")) {
            List<T> direct = values.stream().filter(value -> qualifiedName.apply(value).equals(requestedName)).toList();
            if (!direct.isEmpty()) return direct;
            String expanded = expandAlias(requestedName, currentNamespace);
            return values.stream().filter(value -> qualifiedName.apply(value).equals(expanded)).toList();
        }
        List<T> local = values.stream().filter(value -> name.apply(value).equals(requestedName)
                && namespaceOf(packageId.apply(value)).equals(currentNamespace)).toList();
        if (!local.isEmpty()) return local;
        Set<String> imported = imports.stream()
                .filter(modelImport -> namespaceOf(modelImport.importingPackageId()).equals(currentNamespace))
                .map(modelImport -> namespaceOf(modelImport.importedPackageId()))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<T> visible = values.stream().filter(value -> name.apply(value).equals(requestedName)
                && imported.contains(namespaceOf(packageId.apply(value)))).toList();
        if (!visible.isEmpty()) return visible;
        return values.stream().filter(value -> name.apply(value).equals(requestedName)).toList();
    }

    private String expandAlias(String requestedName, String currentNamespace) {
        int separator = requestedName.indexOf("::");
        String prefix = requestedName.substring(0, separator);
        return imports.stream().filter(modelImport -> namespaceOf(modelImport.importingPackageId()).equals(currentNamespace))
                .filter(modelImport -> prefix.equals(modelImport.alias()))
                .findFirst().map(modelImport -> namespaceOf(modelImport.importedPackageId())
                        + requestedName.substring(separator)).orElse(requestedName);
    }

    private String namespaceOf(UmlPackageId packageId) {
        return findPackage(packageId).map(UmlPackage::qualifiedName).orElse("");
    }

    private static void validateNamespaces(List<UmlClass> classes, List<UmlEnumeration> enumerations,
            List<UmlDataType> dataTypes, List<UmlPackage> packages,
            List<UmlModelImport> imports) {
        Set<UmlPackageId> packageIds = new LinkedHashSet<>();
        Set<String> packageNames = new LinkedHashSet<>();
        for (UmlPackage pkg : packages) {
            if (!packageIds.add(pkg.id()) || !packageNames.add(pkg.qualifiedName())) {
                throw namespaceError("DUPLICATE_NAMESPACE", "Duplicate UML package", Map.of("packageId", pkg.id().value()));
            }
        }
        for (UmlClass umlClass : classes) {
            if (umlClass.packageId() != null && !packageIds.contains(umlClass.packageId())) {
                throw namespaceError("UNKNOWN_NAMESPACE", "Class references unknown package",
                        Map.of("classId", umlClass.id().value(), "packageId", umlClass.packageId().value()));
            }
        }
        for (UmlEnumeration enumeration : enumerations) requireKnownPackage(packageIds, enumeration.packageId(),
                "Enumeration", enumeration.id().value());
        for (UmlDataType dataType : dataTypes) requireKnownPackage(packageIds, dataType.packageId(),
                "DataType", dataType.id().value());
        Set<UmlModelImportId> importIds = new LinkedHashSet<>();
        Set<String> aliases = new LinkedHashSet<>();
        for (UmlModelImport modelImport : imports) {
            if (!importIds.add(modelImport.id())) {
                throw namespaceError("DUPLICATE_IMPORT", "Duplicate model import", Map.of("importId", modelImport.id().value()));
            }
            if (!packageIds.contains(modelImport.importingPackageId()) || !packageIds.contains(modelImport.importedPackageId())) {
                throw namespaceError("UNKNOWN_IMPORT_NAMESPACE", "Import references unknown package",
                        Map.of("importId", modelImport.id().value()));
            }
            String aliasKey = modelImport.importingPackageId().value() + ":" + modelImport.alias();
            if (modelImport.alias() != null && !aliases.add(aliasKey)) {
                throw namespaceError("DUPLICATE_IMPORT_ALIAS", "Import alias is ambiguous",
                        Map.of("importId", modelImport.id().value(), "alias", modelImport.alias()));
            }
        }
        for (UmlPackageId packageId : packageIds) detectImportCycle(packageId, packageId, imports, new LinkedHashSet<>());
        Set<String> qualifiedClassNames = new LinkedHashSet<>();
        for (UmlClass umlClass : classes) {
            String namespace = umlClass.packageId() == null ? "" : packages.stream()
                    .filter(pkg -> pkg.id().equals(umlClass.packageId())).findFirst().map(UmlPackage::qualifiedName).orElse("");
            String qualifiedName = namespace.isEmpty() ? umlClass.name() : namespace + "::" + umlClass.name();
            if (!qualifiedClassNames.add(qualifiedName)) {
                throw namespaceError("DUPLICATE_QUALIFIED_NAME", "Duplicate qualified classifier name",
                        Map.of("qualifiedName", qualifiedName));
            }
        }
    }

    private static void requireKnownPackage(Set<UmlPackageId> known, UmlPackageId packageId,
            String classifierKind, String classifierId) {
        if (packageId != null && !known.contains(packageId)) {
            throw namespaceError("UNKNOWN_NAMESPACE", classifierKind + " references unknown package",
                    Map.of("classifierId", classifierId, "packageId", packageId.value()));
        }
    }

    private static void detectImportCycle(UmlPackageId origin, UmlPackageId current, List<UmlModelImport> imports,
            Set<UmlPackageId> path) {
        if (!path.add(current)) return;
        for (UmlModelImport modelImport : imports) {
            if (!modelImport.importingPackageId().equals(current)) continue;
            if (modelImport.importedPackageId().equals(origin)) {
                throw namespaceError("IMPORT_CYCLE", "Package import cycle detected",
                        Map.of("packageId", origin.value(), "importId", modelImport.id().value()));
            }
            detectImportCycle(origin, modelImport.importedPackageId(), imports, new LinkedHashSet<>(path));
        }
    }

    private static UmlNamespaceException namespaceError(String code, String message, Map<String, Object> details) {
        return new UmlNamespaceException(code, message, details);
    }

    public record ResolvedAttribute(UmlClass owner, UmlAttribute attribute) {}
    public record ResolvedOperation(UmlClass owner, UmlOperation operation) {}

    private static void validateGeneralizations(List<UmlClass> classes) {
        Set<UmlClassId> known = classes.stream().map(UmlClass::id).collect(java.util.stream.Collectors.toSet());
        for (UmlClass umlClass : classes) {
            for (UmlClassId superClassId : umlClass.superClassIds()) {
                if (!known.contains(superClassId)) {
                    throw new UmlGeneralizationException("UNKNOWN_SUPERCLASS",
                            "Unknown superclass '" + superClassId.value() + "'",
                            Map.of("classId", umlClass.id().value(), "superClassId", superClassId.value()));
                }
            }
            detectCycle(umlClass.id(), umlClass.id(), classes, new LinkedHashSet<>());
        }
    }

    private static void validateClassifierNames(List<UmlClass> classes, List<UmlEnumeration> enumerations,
            List<UmlDataType> dataTypes, List<UmlPackage> packages) {
        Set<UmlClassId> classIds = new LinkedHashSet<>();
        Set<UmlEnumerationId> enumerationIds = new LinkedHashSet<>();
        for (UmlClass umlClass : classes) {
            if (!classIds.add(umlClass.id())) {
                throw new IllegalArgumentException("Duplicate UML classifier: " + umlClass.name());
            }
        }
        Set<String> names = classes.stream().map(umlClass -> qualifiedName(umlClass.packageId(), umlClass.name(), packages))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (UmlEnumeration enumeration : enumerations) {
            if (!enumerationIds.add(enumeration.id())
                    || !names.add(qualifiedName(enumeration.packageId(), enumeration.name(), packages))) {
                throw new IllegalArgumentException("Duplicate UML classifier: " + enumeration.name());
            }
        }
        Set<UmlDataTypeId> dataTypeIds = new LinkedHashSet<>();
        for (UmlDataType dataType : dataTypes) {
            if (!dataTypeIds.add(dataType.id())
                    || !names.add(qualifiedName(dataType.packageId(), dataType.name(), packages))) {
                throw new IllegalArgumentException("Duplicate UML classifier: " + dataType.name());
            }
        }
    }

    private static String qualifiedName(UmlPackageId packageId, String name, List<UmlPackage> packages) {
        if (packageId == null) return name;
        return packages.stream().filter(pkg -> pkg.id().equals(packageId)).findFirst()
                .map(pkg -> pkg.qualifiedName() + "::" + name).orElse(name);
    }

    private static void detectCycle(UmlClassId origin, UmlClassId current, List<UmlClass> classes,
            Set<UmlClassId> path) {
        if (!path.add(current)) {
            throw new UmlGeneralizationException("GENERALIZATION_CYCLE",
                    "Generalization cycle involving '" + origin.value() + "'",
                    Map.of("classId", origin.value(), "cycleAtClassId", current.value()));
        }
        classes.stream().filter(candidate -> candidate.id().equals(current)).findFirst().ifPresent(umlClass -> {
            for (UmlClassId parent : umlClass.superClassIds()) {
                detectCycle(origin, parent, classes, new LinkedHashSet<>(path));
            }
        });
    }

    private static void validateInheritedFeatureResolution(List<UmlClass> classes) {
        UmlModelView view = new UmlModelView(classes);
        for (UmlClass umlClass : classes) {
            validateInheritedFeatures(view, umlClass, "ATTRIBUTE", UmlClass::attributes,
                    attribute -> attribute.name());
            validateInheritedFeatures(view, umlClass, "OPERATION", UmlClass::operations,
                    operation -> operation.name() + "(" + operation.parameters().stream()
                            .map(parameter -> parameter.type().name()).reduce((a, b) -> a + "," + b).orElse("") + ")");
        }
    }

    private static <T> void validateInheritedFeatures(UmlModelView view, UmlClass target, String featureKind,
            Function<UmlClass, List<T>> features, Function<T, String> key) {
        Set<String> localKeys = features.apply(target).stream().map(key).collect(java.util.stream.Collectors.toSet());
        Map<String, List<UmlClassId>> ownersByFeature = new java.util.LinkedHashMap<>();
        for (UmlClassId ancestorId : view.superClassesOf(target.id())) {
            UmlClass ancestor = view.requireClass(ancestorId);
            for (T feature : features.apply(ancestor)) {
                if (!localKeys.contains(key.apply(feature))) {
                    ownersByFeature.computeIfAbsent(key.apply(feature), ignored -> new java.util.ArrayList<>())
                            .add(ancestor.id());
                }
            }
        }
        ownersByFeature.forEach((featureName, owners) -> {
            List<UmlClassId> effectiveOwners = owners.stream().distinct()
                    .filter(owner -> owners.stream().noneMatch(other -> !other.equals(owner)
                            && view.isSubtypeOf(other, owner)))
                    .toList();
            if (effectiveOwners.size() > 1) {
                throw new UmlGeneralizationException("AMBIGUOUS_INHERITED_FEATURE",
                        "Ambiguous inherited " + featureKind.toLowerCase() + " '" + featureName
                                + "' in class '" + target.name() + "'",
                        Map.of("classId", target.id().value(), "className", target.name(),
                                "featureKind", featureKind, "featureName", featureName,
                                "superClassIds", effectiveOwners.stream().map(UmlClassId::value).toList()));
            }
        });
    }

    private record UmlModelView(List<UmlClass> classes) {
        UmlClass requireClass(UmlClassId id) {
            return classes.stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElseThrow();
        }

        boolean isSubtypeOf(UmlClassId candidate, UmlClassId target) {
            return candidate.equals(target) || superClassesOf(candidate).contains(target);
        }

        List<UmlClassId> superClassesOf(UmlClassId classId) {
            LinkedHashSet<UmlClassId> result = new LinkedHashSet<>();
            ArrayDeque<UmlClassId> pending = new ArrayDeque<>(requireClass(classId).superClassIds());
            while (!pending.isEmpty()) {
                UmlClassId current = pending.removeFirst();
                if (result.add(current)) {
                    pending.addAll(requireClass(current).superClassIds());
                }
            }
            return List.copyOf(result);
        }
    }
}
