package de.useweb.backend.ocl.resolution;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.ocl.definition.OclDefinitionSignature;
import de.useweb.backend.ocl.definition.OclDefinitionTypeResolver;
import de.useweb.backend.ocl.library.OclStandardLibrary;
import de.useweb.backend.ocl.typecheck.OclType;
import de.useweb.backend.ocl.typecheck.TypeEnvironment;

/** Resolves the semantic target before either type checking or evaluation executes it. */
public final class OclCallResolver {
    private final TypeEnvironment environment;

    public OclCallResolver(TypeEnvironment environment) {
        this.environment = environment;
    }

    public OclCallResolutionResult resolveProperty(OclType receiver, String name) {
        Optional<OclType> standard = OclStandardLibrary.operationType(receiver, name, List.of(), environment.umlModel());
        if (standard.isPresent()) {
            return resolved(OclCallKind.STANDARD_LIBRARY, standard.get(), "ocl-library:" + name, name);
        }
        boolean classifierReceiver = receiver.kind() == OclType.Kind.OCL_TYPE;
        OclType representedType = classifierReceiver ? receiver.classifierType() : receiver;
        if (representedType.kind() != OclType.Kind.CLASS) return unknown("Receiver is not class-valued");
        Optional<UmlClass> receiverClass = environment.findClass(representedType.classId());
        if (receiverClass.isEmpty()) return unknown("Unknown receiver classifier");

        Optional<UmlModel.ResolvedAttribute> attribute = environment.umlModel()
                .resolveAttribute(receiverClass.get().id(), name);
        if (attribute.isPresent()) {
            if (classifierReceiver && !attribute.get().attribute().staticAttribute()) {
                return unknown("Instance attribute '" + name + "' cannot be read from a classifier value");
            }
            if (!de.useweb.backend.ocl.profile.OclOptionalCompliancePolicy.mayAccess(environment.umlModel(),
                    attribute.get().attribute().visibility(), attribute.get().owner(), environment.contextClass())) {
                return unresolved(OclCallResolutionResult.Status.INACCESSIBLE,
                        "Attribute '" + name + "' is not visible");
            }
            OclType type = OclType.fromUmlType(attribute.get().attribute().type(), environment);
            return resolved(OclCallKind.UML_ATTRIBUTE, type, attribute.get().attribute().id().value(),
                    attribute.get().owner().name() + "::" + name);
        }

        if (environment.definitionTypeResolver() != null) {
            List<OclDefinitionSignature> definitions = environment.definitionTypeResolver()
                    .propertySignatures(representedType.classId(), name);
            if (definitions.size() == 1) {
                OclDefinitionSignature definition = definitions.getFirst();
                return resolved(OclCallKind.DEFINITION_PROPERTY, definition.resultType(),
                        definition.id().value(), definition.name());
            }
            if (definitions.size() > 1) {
                return unresolved(OclCallResolutionResult.Status.AMBIGUOUS,
                        "Property definition '" + name + "' is ambiguous");
            }
            Optional<OclType> legacy = environment.definitionTypeResolver().propertyType(representedType.classId(), name);
            if (legacy.isPresent()) {
                return resolved(OclCallKind.DEFINITION_PROPERTY, legacy.get(), "definition:" + name, name);
            }
        }
        return unknown("Unknown property '" + name + "'");
    }

    public OclCallResolutionResult resolveOperation(OclType receiver, String name, List<OclType> arguments) {
        Optional<OclType> standard = OclStandardLibrary.operationType(receiver, name, arguments, environment.umlModel());
        if (standard.isPresent()) {
            return resolved(OclCallKind.STANDARD_LIBRARY, standard.get(), "ocl-library:" + name, name);
        }
        if (OclStandardLibrary.hasOperation(receiver, name)) {
            return unresolved(OclCallResolutionResult.Status.ARGUMENT_MISMATCH,
                    "Unknown operation overload '" + name + "' for the supplied argument types");
        }
        if (receiver.kind() != OclType.Kind.CLASS) return unknown("Receiver is not class-valued");

        List<Candidate> candidates = new ArrayList<>();
        List<UmlModel.ResolvedOperation> named = environment.umlModel()
                .resolveOperations(receiver.classId(), name, arguments.size());
        boolean inaccessible = false;
        for (UmlModel.ResolvedOperation resolved : named) {
            if (!de.useweb.backend.ocl.profile.OclOptionalCompliancePolicy.mayAccess(environment.umlModel(),
                    resolved.operation().visibility(), resolved.owner(), environment.contextClass())) {
                inaccessible = true;
                continue;
            }
            List<OclType> parameters = resolved.operation().parameters().stream()
                    .map(parameter -> OclType.fromUmlType(parameter.type(), environment)).toList();
            candidates.add(new Candidate(OclCallKind.UML_OPERATION, resolved.operation().id().value(),
                    resolved.owner().name() + "::" + name, parameters,
                    OclType.fromUmlType(resolved.operation().returnType(), environment),
                    ownerDistance(receiver, resolved.owner().id())));
        }

        OclDefinitionTypeResolver definitions = environment.definitionTypeResolver();
        if (definitions != null) {
            for (OclDefinitionSignature definition : definitions.operationSignatures(
                    receiver.classId(), name, arguments.size())) {
                candidates.add(new Candidate(OclCallKind.DEFINITION_OPERATION, definition.id().value(),
                        definition.name(), definition.parameterTypes(), definition.resultType(),
                        ownerDistance(receiver, definition.ownerClassId())));
            }
        }

        List<Candidate> applicable = candidates.stream().filter(candidate -> candidate.accepts(arguments)).toList();
        if (!applicable.isEmpty()) {
            List<Candidate> mostSpecific = applicable.stream()
                    .filter(candidate -> applicable.stream().noneMatch(other -> other != candidate
                            && other.moreSpecificThan(candidate))).toList();
            if (mostSpecific.size() == 1) {
                Candidate selected = mostSpecific.getFirst();
                return resolved(selected.kind, selected.resultType, selected.id, selected.qualifiedName);
            }
            return unresolved(OclCallResolutionResult.Status.AMBIGUOUS,
                    "Operation '" + name + "' has multiple equally specific overloads");
        }
        if (!candidates.isEmpty()) {
            return unresolved(OclCallResolutionResult.Status.ARGUMENT_MISMATCH,
                    "No overload of '" + name + "' accepts the supplied argument types");
        }
        if (inaccessible) {
            return unresolved(OclCallResolutionResult.Status.INACCESSIBLE,
                    "Operation '" + name + "' is not visible");
        }
        if (definitions != null) {
            Optional<OclType> legacy = definitions.operationType(receiver.classId(), name, arguments);
            if (legacy.isPresent()) {
                return resolved(OclCallKind.DEFINITION_OPERATION, legacy.get(), "definition:" + name, name);
            }
        }
        return unknown("Unknown operation '" + name + "'");
    }

    private int ownerDistance(OclType receiver, de.useweb.backend.domain.uml.UmlClassId owner) {
        return environment.umlModel().typeConformanceOrder(receiver.classId()).indexOf(owner);
    }

    private OclCallResolutionResult resolved(OclCallKind kind, OclType type, String id, String qualifiedName) {
        return OclCallResolutionResult.resolved(new OclCallResolution(kind, type, id, qualifiedName));
    }

    private OclCallResolutionResult unknown(String message) {
        return unresolved(OclCallResolutionResult.Status.UNKNOWN, message);
    }

    private OclCallResolutionResult unresolved(OclCallResolutionResult.Status status, String message) {
        return OclCallResolutionResult.unresolved(status, message);
    }

    private record Candidate(OclCallKind kind, String id, String qualifiedName, List<OclType> parameterTypes,
            OclType resultType, int ownerDistance) {
        boolean accepts(List<OclType> arguments) {
            if (parameterTypes.size() != arguments.size()) return false;
            for (int index = 0; index < arguments.size(); index++) {
                if (!arguments.get(index).conformsTo(parameterTypes.get(index))) return false;
            }
            return true;
        }

        boolean moreSpecificThan(Candidate other) {
            boolean strict = ownerDistance >= 0 && (other.ownerDistance < 0 || ownerDistance < other.ownerDistance);
            for (int index = 0; index < parameterTypes.size(); index++) {
                OclType mine = parameterTypes.get(index);
                OclType theirs = other.parameterTypes.get(index);
                if (!mine.conformsTo(theirs)) return false;
                strict |= !mine.sameTypeAs(theirs);
            }
            return strict;
        }
    }
}
