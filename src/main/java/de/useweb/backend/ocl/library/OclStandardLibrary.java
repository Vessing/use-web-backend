package de.useweb.backend.ocl.library;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Optional;

import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.ocl.collection.CollectionKind;
import de.useweb.backend.ocl.typecheck.OclType;

/** Shared signature registry used by both static and runtime call resolution. */
public final class OclStandardLibrary {
    private OclStandardLibrary() {
    }

    public static Optional<OclType> operationType(OclType receiver, String name,
            List<OclType> arguments, UmlModel umlModel) {
        if (name.equals("oclType") && arguments.isEmpty()) {
            return Optional.of(OclType.classifierValueType(receiver));
        }
        if ((name.equals("oclIsUndefined") || name.equals("oclIsInvalid")) && arguments.isEmpty()) {
            return Optional.of(OclType.BOOLEAN);
        }
        if (arguments.isEmpty() && (receiver.kind() == OclType.Kind.VOID
                || receiver.kind() == OclType.Kind.OCL_INVALID)) {
            return switch (name) {
                case "size" -> Optional.of(OclType.INTEGER);
                case "isEmpty", "notEmpty" -> Optional.of(OclType.BOOLEAN);
                default -> Optional.empty();
            };
        }
        Optional<OclType> primitiveType = OclPrimitiveLibrary.operationType(receiver, name, arguments);
        if (primitiveType.isPresent()) return primitiveType;
        if (receiver.isCollection() && arguments.isEmpty()) {
            Optional<OclType> conversion = collectionConversionType(receiver, name);
            if (conversion.isPresent()) return conversion;
            if (name.equals("flatten")) {
                return Optional.of(OclType.collectionOf(receiver.collectionKind(),
                        flattenedElementType(receiver.elementType())));
            }
            return switch (name) {
                case "size" -> Optional.of(OclType.INTEGER);
                case "isEmpty", "notEmpty" -> Optional.of(OclType.BOOLEAN);
                case "max", "min", "sum" -> receiver.elementType().isNumeric()
                        || receiver.elementType().kind() == OclType.Kind.OCL_ANY
                        || receiver.elementType().kind() == OclType.Kind.VOID
                        ? Optional.of(receiver.elementType()) : Optional.empty();
                case "first", "last" -> receiver.collectionKind().ordered()
                        ? Optional.of(receiver.elementType()) : Optional.empty();
                case "reverse" -> receiver.collectionKind().ordered() ? Optional.of(receiver) : Optional.empty();
                default -> Optional.empty();
            };
        }
        if (receiver.isCollection() && arguments.size() == 1) {
            OclType argument = arguments.getFirst();
            if ((name.equals("includes") || name.equals("excludes"))
                    && compatible(receiver.elementType(), argument)) return Optional.of(OclType.BOOLEAN);
            if (name.equals("count") && compatible(receiver.elementType(), argument)) {
                return Optional.of(OclType.INTEGER);
            }
            if (name.equals("including")) {
                OclType elementType = receiver.elementType().leastUpperBound(argument, umlModel);
                return elementType.isInvalid() ? Optional.empty()
                        : Optional.of(OclType.collectionOf(receiver.collectionKind(), elementType));
            }
            if (name.equals("excluding") && compatible(receiver.elementType(), argument)) {
                return Optional.of(receiver);
            }
            if ((name.equals("includesAll") || name.equals("excludesAll")) && argument.isCollection()
                    && compatible(receiver.elementType(), argument.elementType())) {
                return Optional.of(OclType.BOOLEAN);
            }
            if (name.equals("product") && argument.isCollection()) {
                LinkedHashMap<String, OclType> parts = new LinkedHashMap<>();
                parts.put("first", receiver.elementType());
                parts.put("second", argument.elementType());
                return Optional.of(OclType.collectionOf(CollectionKind.SET, OclType.tupleOf(parts)));
            }
            if ((name.equals("append") || name.equals("prepend")) && receiver.collectionKind().ordered()
                    && compatible(receiver.elementType(), argument)) {
                OclType elementType = receiver.elementType().leastUpperBound(argument, umlModel);
                return elementType.isInvalid() ? Optional.empty()
                        : Optional.of(OclType.collectionOf(receiver.collectionKind(), elementType));
            }
            if (name.equals("at") && receiver.collectionKind().ordered()
                    && argument.conformsTo(OclType.INTEGER)) return Optional.of(receiver.elementType());
            if (name.equals("indexOf") && receiver.collectionKind().ordered()
                    && compatible(receiver.elementType(), argument)) return Optional.of(OclType.INTEGER);
            if (name.equals("symmetricDifference") && receiver.collectionKind() == CollectionKind.SET
                    && argument.isCollection() && argument.collectionKind() == CollectionKind.SET) {
                return combinationType(receiver, argument, name, umlModel);
            }
            if (argument.isCollection() && (name.equals("union") || name.equals("intersection"))) {
                return combinationType(receiver, argument, name, umlModel);
            }
        }
        if (receiver.isCollection() && arguments.size() == 2 && receiver.collectionKind().ordered()) {
            OclType first = arguments.get(0);
            OclType second = arguments.get(1);
            if (name.equals("insertAt") && first.conformsTo(OclType.INTEGER)
                    && compatible(receiver.elementType(), second)) {
                OclType elementType = receiver.elementType().leastUpperBound(second, umlModel);
                return elementType.isInvalid() ? Optional.empty()
                        : Optional.of(OclType.collectionOf(receiver.collectionKind(), elementType));
            }
            String sliceName = receiver.collectionKind() == CollectionKind.SEQUENCE
                    ? "subSequence" : "subOrderedSet";
            if (name.equals(sliceName) && first.conformsTo(OclType.INTEGER)
                    && second.conformsTo(OclType.INTEGER)) return Optional.of(receiver);
        }
        return Optional.empty();
    }

    public static boolean hasOperation(OclType receiver, String name) {
        if (name.equals("oclIsUndefined") || name.equals("oclIsInvalid") || name.equals("oclType")) return true;
        if (receiver.kind() == OclType.Kind.STRING) {
            return List.of("size", "concat", "substring", "toUpperCase", "toLowerCase", "indexOf",
                    "equalsIgnoreCase", "at", "characters", "toBoolean", "toInteger", "toReal", "toString")
                    .contains(name);
        }
        if (receiver.isNumeric()) {
            return List.of("abs", "floor", "round", "max", "min", "toInteger", "toString").contains(name);
        }
        if (receiver.sameTypeAs(OclType.BOOLEAN)) return name.equals("toString");
        if (receiver.isCollection() || receiver.kind() == OclType.Kind.VOID
                || receiver.kind() == OclType.Kind.OCL_INVALID) {
            if (!receiver.isCollection()) return List.of("size", "isEmpty", "notEmpty").contains(name);
            if (List.of("size", "isEmpty", "notEmpty", "flatten", "asSet", "asBag", "asSequence",
                    "asOrderedSet", "includes", "excludes", "count", "including", "excluding", "includesAll",
                    "excludesAll", "max", "min", "sum", "product").contains(name)) return true;
            return switch (receiver.collectionKind()) {
                case SET -> List.of("union", "intersection", "symmetricDifference").contains(name);
                case BAG -> List.of("union", "intersection").contains(name);
                case SEQUENCE -> List.of("union", "append", "prepend", "insertAt", "subSequence", "at",
                        "indexOf", "first", "last", "reverse").contains(name);
                case ORDERED_SET -> List.of("union", "intersection", "append", "prepend", "insertAt",
                        "subOrderedSet", "at", "indexOf", "first", "last", "reverse").contains(name);
                case COLLECTION -> false;
            };
        }
        return false;
    }

    private static boolean compatible(OclType left, OclType right) {
        return left.conformsTo(right) || right.conformsTo(left);
    }

    private static Optional<OclType> collectionConversionType(OclType receiver, String name) {
        return switch (name) {
            case "asSet" -> Optional.of(OclType.collectionOf(CollectionKind.SET, receiver.elementType()));
            case "asBag" -> Optional.of(OclType.collectionOf(CollectionKind.BAG, receiver.elementType()));
            case "asSequence" -> Optional.of(OclType.collectionOf(CollectionKind.SEQUENCE, receiver.elementType()));
            case "asOrderedSet" -> Optional.of(OclType.collectionOf(CollectionKind.ORDERED_SET, receiver.elementType()));
            default -> Optional.empty();
        };
    }

    private static OclType flattenedElementType(OclType elementType) {
        OclType flattened = elementType;
        while (flattened.isCollection()) flattened = flattened.elementType();
        return flattened;
    }

    private static Optional<OclType> combinationType(OclType receiver, OclType argument,
            String name, UmlModel umlModel) {
        CollectionKind kind = combinationKind(receiver.collectionKind(), argument.collectionKind(), name);
        if (kind == null) return Optional.empty();
        OclType elementType = receiver.elementType().leastUpperBound(argument.elementType(), umlModel);
        return elementType.isInvalid() ? Optional.empty() : Optional.of(OclType.collectionOf(kind, elementType));
    }

    private static CollectionKind combinationKind(CollectionKind receiver, CollectionKind argument, String name) {
        if (name.equals("union")) {
            if (receiver == CollectionKind.SET && argument == CollectionKind.SET) return CollectionKind.SET;
            if ((receiver == CollectionKind.SET && argument == CollectionKind.BAG)
                    || (receiver == CollectionKind.BAG
                    && (argument == CollectionKind.SET || argument == CollectionKind.BAG))) return CollectionKind.BAG;
            if (receiver == CollectionKind.SEQUENCE && argument == CollectionKind.SEQUENCE) return CollectionKind.SEQUENCE;
            if (receiver == CollectionKind.ORDERED_SET && argument == CollectionKind.ORDERED_SET) return CollectionKind.ORDERED_SET;
        }
        if (name.equals("intersection")) {
            if (receiver == CollectionKind.SET
                    && (argument == CollectionKind.SET || argument == CollectionKind.BAG)) return CollectionKind.SET;
            if (receiver == CollectionKind.BAG && argument == CollectionKind.BAG) return CollectionKind.BAG;
            if (receiver == CollectionKind.BAG && argument == CollectionKind.SET) return CollectionKind.SET;
            if (receiver == CollectionKind.ORDERED_SET && argument == CollectionKind.ORDERED_SET) return CollectionKind.ORDERED_SET;
        }
        if (name.equals("symmetricDifference") && receiver == CollectionKind.SET
                && argument == CollectionKind.SET) return CollectionKind.SET;
        return null;
    }
}
