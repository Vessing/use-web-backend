package de.useweb.backend.ocl.typecheck;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlEnumerationId;
import de.useweb.backend.domain.uml.UmlDataTypeId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.ocl.collection.CollectionKind;

public record OclType(Kind kind, String name, UmlClassId classId, OclType elementType,
        CollectionKind collectionKind, List<ClassTypeRef> classConformance, UmlEnumerationId enumerationId,
        Map<String, OclType> tupleParts, UmlDataTypeId dataTypeId, OclType classifierType) {

    public record ClassTypeRef(UmlClassId id, String name) {
    }

    public enum Kind {
        STRING,
        INTEGER,
        REAL,
        BOOLEAN,
        UNLIMITED_NATURAL,
        ENUM,
        DATA_TYPE,
        OCL_TYPE,
        OCL_ANY,
        CLASS,
        COLLECTION,
        TUPLE,
        VOID,
        OCL_INVALID,
        INVALID
    }

    public static final OclType STRING = simple(Kind.STRING, "String");
    public static final OclType INTEGER = simple(Kind.INTEGER, "Integer");
    public static final OclType REAL = simple(Kind.REAL, "Real");
    public static final OclType BOOLEAN = simple(Kind.BOOLEAN, "Boolean");
    public static final OclType UNLIMITED_NATURAL = simple(Kind.UNLIMITED_NATURAL, "UnlimitedNatural");
    public static final OclType OCL_ANY = simple(Kind.OCL_ANY, "OclAny");
    public static final OclType VOID = simple(Kind.VOID, "OclVoid");
    public static final OclType OCL_INVALID = simple(Kind.OCL_INVALID, "OclInvalid");
    public static final OclType INVALID = simple(Kind.INVALID, "Invalid");

    public OclType(Kind kind, String name, UmlClassId classId, OclType elementType,
            CollectionKind collectionKind) {
        this(kind, name, classId, elementType, collectionKind, List.of(), null, Map.of(), null, null);
    }

    public OclType(Kind kind, String name, UmlClassId classId, OclType elementType) {
        this(kind, name, classId, elementType, kind == Kind.COLLECTION ? CollectionKind.COLLECTION : null,
                List.of(), null, Map.of(), null, null);
    }

    public OclType {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (kind == Kind.CLASS && classId == null) {
            throw new IllegalArgumentException("classId must not be null for class types");
        }
        if (kind == Kind.COLLECTION && elementType == null) {
            throw new IllegalArgumentException("elementType must not be null for collection types");
        }
        if (kind == Kind.COLLECTION && collectionKind == null) {
            throw new IllegalArgumentException("collectionKind must not be null for collection types");
        }
        if (kind == Kind.ENUM && enumerationId == null) {
            throw new IllegalArgumentException("enumerationId must not be null for enum types");
        }
        if (kind == Kind.DATA_TYPE && dataTypeId == null) {
            throw new IllegalArgumentException("dataTypeId must not be null for data types");
        }
        if (kind == Kind.OCL_TYPE && classifierType == null) {
            throw new IllegalArgumentException("classifierType must not be null for OclType values");
        }
        if (kind == Kind.TUPLE && (tupleParts == null || tupleParts.isEmpty())) {
            throw new IllegalArgumentException("tupleParts must not be empty for tuple types");
        }
        classConformance = List.copyOf(classConformance == null ? List.of() : classConformance);
        tupleParts = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(
                tupleParts == null ? Map.of() : tupleParts));
    }

    private static OclType simple(Kind kind, String name) {
        return new OclType(kind, name, null, null, null, List.of(), null, Map.of(), null, null);
    }

    public static OclType classType(UmlClassId classId, String className) {
        return new OclType(Kind.CLASS, className, classId, null, null,
                List.of(new ClassTypeRef(classId, className)), null, Map.of(), null, null);
    }

    public static OclType classType(UmlClass umlClass, UmlModel model) {
        List<ClassTypeRef> conformance = model.typeConformanceOrder(umlClass.id()).stream()
                .map(id -> model.findClass(id).orElseThrow())
                .map(candidate -> new ClassTypeRef(candidate.id(), candidate.name()))
                .toList();
        return new OclType(Kind.CLASS, umlClass.name(), umlClass.id(), null, null, conformance, null, Map.of(), null, null);
    }

    public static OclType enumerationType(UmlEnumerationId id, String name) {
        return new OclType(Kind.ENUM, name, null, null, null, List.of(), id, Map.of(), null, null);
    }

    public static OclType dataType(UmlDataTypeId id, String name) {
        return new OclType(Kind.DATA_TYPE, name, null, null, null, List.of(), null, Map.of(), id, null);
    }

    public static OclType classifierValueType(OclType representedType) {
        return new OclType(Kind.OCL_TYPE, "OclType(" + representedType.displayName() + ")", null, null, null,
                List.of(), null, Map.of(), null, representedType);
    }

    public static OclType collectionOf(OclType elementType) {
        return collectionOf(CollectionKind.COLLECTION, elementType);
    }

    public static OclType collectionOf(CollectionKind collectionKind, OclType elementType) {
        return new OclType(Kind.COLLECTION,
                collectionKind.oclName() + "(" + elementType.displayName() + ")",
                null, elementType, collectionKind, List.of(), null, Map.of(), null, null);
    }

    public static OclType tupleOf(Map<String, OclType> parts) {
        String displayName = "Tuple(" + parts.entrySet().stream()
                .map(entry -> entry.getKey() + " : " + entry.getValue().displayName())
                .reduce((left, right) -> left + ", " + right).orElse("") + ")";
        return new OclType(Kind.TUPLE, displayName, null, null, null, List.of(), null, parts, null, null);
    }

    public static OclType fromUmlType(UmlType umlType, TypeEnvironment environment) {
        String typeName = umlType.name().trim();
        int leftParen = typeName.indexOf('(');
        if (leftParen > 0 && typeName.endsWith(")")) {
            String collectionName = typeName.substring(0, leftParen).trim();
            CollectionKind kind = switch (collectionName) {
                case "Collection" -> CollectionKind.COLLECTION;
                case "Set" -> CollectionKind.SET;
                case "Bag" -> CollectionKind.BAG;
                case "Sequence" -> CollectionKind.SEQUENCE;
                case "OrderedSet" -> CollectionKind.ORDERED_SET;
                default -> null;
            };
            if (kind != null) {
                OclType elementType = fromUmlType(
                        new UmlType(typeName.substring(leftParen + 1, typeName.length() - 1).trim()), environment);
                return elementType.isInvalid() ? INVALID : collectionOf(kind, elementType);
            }
        }
        return umlType.primitiveType()
                .map(primitiveType -> switch (primitiveType) {
                    case STRING -> STRING;
                    case INTEGER -> INTEGER;
                    case REAL -> REAL;
                    case BOOLEAN -> BOOLEAN;
                })
                .orElseGet(() -> environment.findClassByName(umlType.name())
                        .map(environment::classType)
                        .orElseGet(() -> environment.findEnumerationByName(umlType.name())
                                .map(enumeration -> enumerationType(enumeration.id(), enumeration.name()))
                                .orElseGet(() -> environment.findDataTypeByName(umlType.name())
                                        .map(dataType -> dataType(dataType.id(), dataType.name()))
                                        .orElse(umlType.name().equals("UnlimitedNatural") ? UNLIMITED_NATURAL : INVALID))));
    }

    public String displayName() {
        return name;
    }

    public boolean isBoolean() {
        return kind == Kind.BOOLEAN;
    }

    public boolean isNumeric() {
        return kind == Kind.INTEGER || kind == Kind.REAL || kind == Kind.UNLIMITED_NATURAL;
    }

    public boolean isInvalid() {
        return kind == Kind.INVALID;
    }

    public boolean isCollection() {
        return kind == Kind.COLLECTION;
    }

    public boolean sameTypeAs(OclType other) {
        if (other == null) {
            return false;
        }
        if (kind != other.kind) {
            return false;
        }
        return switch (kind) {
            case CLASS -> classId.equals(other.classId);
            case ENUM -> enumerationId.equals(other.enumerationId);
            case DATA_TYPE -> dataTypeId.equals(other.dataTypeId);
            case OCL_TYPE -> classifierType.sameTypeAs(other.classifierType);
            case COLLECTION -> collectionKind == other.collectionKind && elementType.sameTypeAs(other.elementType);
            case TUPLE -> tupleParts.keySet().equals(other.tupleParts.keySet())
                    && tupleParts.entrySet().stream()
                            .allMatch(entry -> entry.getValue().sameTypeAs(other.tupleParts.get(entry.getKey())));
            default -> true;
        };
    }

    public boolean conformsTo(OclType target) {
        if (target == null || isInvalid() || target.isInvalid()) {
            return false;
        }
        if (kind == Kind.VOID || kind == Kind.OCL_INVALID) {
            return true;
        }
        if (sameTypeAs(target)) {
            return true;
        }
        if (target.kind == Kind.OCL_ANY) {
            return true;
        }
        if (kind == Kind.CLASS && target.kind == Kind.CLASS) {
            return classConformance.stream().anyMatch(type -> type.id().equals(target.classId));
        }
        if (kind == Kind.COLLECTION && target.kind == Kind.COLLECTION
                && (collectionKind == target.collectionKind || target.collectionKind == CollectionKind.COLLECTION)) {
            return elementType.conformsTo(target.elementType);
        }
        if (kind == Kind.TUPLE && target.kind == Kind.TUPLE) {
            return target.tupleParts.entrySet().stream()
                    .allMatch(entry -> tupleParts.containsKey(entry.getKey())
                            && tupleParts.get(entry.getKey()).conformsTo(entry.getValue()));
        }
        if (kind == Kind.OCL_TYPE && target.kind == Kind.OCL_TYPE) {
            return classifierType.conformsTo(target.classifierType);
        }
        return kind == Kind.INTEGER && target.kind == Kind.REAL
                || kind == Kind.UNLIMITED_NATURAL
                        && (target.kind == Kind.INTEGER || target.kind == Kind.REAL);
    }

    public OclType leastUpperBound(OclType other) {
        return leastUpperBound(other, null);
    }

    public OclType leastUpperBound(OclType other, UmlModel model) {
        if (other == null || isInvalid() || other.isInvalid()) {
            return INVALID;
        }
        if (kind == Kind.VOID || kind == Kind.OCL_INVALID) {
            return other;
        }
        if (other.kind == Kind.VOID || other.kind == Kind.OCL_INVALID || sameTypeAs(other)) {
            return this;
        }
        if (isNumeric() && other.isNumeric()) {
            if (kind == Kind.REAL || other.kind == Kind.REAL) return REAL;
            return INTEGER;
        }
        if (kind == Kind.CLASS && other.kind == Kind.CLASS) {
            if (model != null) {
                return model.leastCommonSuperClass(classId, other.classId)
                        .map(common -> classType(common, model))
                        .orElse(OCL_ANY);
            }
            for (ClassTypeRef candidate : classConformance) {
                if (other.classConformance.stream().anyMatch(type -> type.id().equals(candidate.id()))) {
                    List<ClassTypeRef> commonOrder = classConformance.stream()
                            .dropWhile(type -> !type.id().equals(candidate.id())).toList();
                    return new OclType(Kind.CLASS, candidate.name(), candidate.id(), null, null,
                            commonOrder, null, Map.of(), null, null);
                }
            }
            return OCL_ANY;
        }
        if (kind == Kind.COLLECTION && other.kind == Kind.COLLECTION) {
            OclType commonElementType = elementType.leastUpperBound(other.elementType, model);
            if (commonElementType.isInvalid()) {
                return INVALID;
            }
            CollectionKind commonKind = collectionKind == other.collectionKind
                    ? collectionKind : CollectionKind.COLLECTION;
            return collectionOf(commonKind, commonElementType);
        }
        if (kind == Kind.TUPLE && other.kind == Kind.TUPLE) {
            LinkedHashMap<String, OclType> common = new LinkedHashMap<>();
            for (Map.Entry<String, OclType> entry : tupleParts.entrySet()) {
                OclType right = other.tupleParts.get(entry.getKey());
                if (right != null) common.put(entry.getKey(), entry.getValue().leastUpperBound(right, model));
            }
            return common.isEmpty() || common.values().stream().anyMatch(OclType::isInvalid)
                    ? OCL_ANY : tupleOf(common);
        }
        return OCL_ANY;
    }
}
