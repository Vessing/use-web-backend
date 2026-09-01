package de.useweb.backend.ocl.typecheck;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.useweb.backend.ocl.collection.CollectionKind;
import de.useweb.backend.domain.uml.Multiplicity;
import de.useweb.backend.domain.uml.UmlAssociation;
import de.useweb.backend.domain.uml.UmlAssociationEnd;
import de.useweb.backend.domain.uml.UmlAttribute;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.ocl.ast.BinaryExpression;
import de.useweb.backend.ocl.ast.AllInstancesExpression;
import de.useweb.backend.ocl.ast.AtPreExpression;
import de.useweb.backend.ocl.ast.BinaryOperator;
import de.useweb.backend.ocl.ast.CollectionItem;
import de.useweb.backend.ocl.ast.CollectionLiteralExpression;
import de.useweb.backend.ocl.ast.CollectionRangeItem;
import de.useweb.backend.ocl.ast.EnumLiteralExpression;
import de.useweb.backend.ocl.ast.LiteralExpression;
import de.useweb.backend.ocl.ast.LiteralType;
import de.useweb.backend.ocl.ast.IfExpression;
import de.useweb.backend.ocl.ast.LetExpression;
import de.useweb.backend.ocl.ast.IterateExpression;
import de.useweb.backend.ocl.ast.IteratorExpression;
import de.useweb.backend.ocl.ast.OclAstNode;
import de.useweb.backend.ocl.ast.OperationCallExpression;
import de.useweb.backend.ocl.ast.ParenthesizedExpression;
import de.useweb.backend.ocl.ast.PropertyAccessExpression;
import de.useweb.backend.ocl.ast.QualifiedPropertyAccessExpression;
import de.useweb.backend.ocl.ast.ResultExpression;
import de.useweb.backend.ocl.ast.SelfExpression;
import de.useweb.backend.ocl.ast.TupleExpression;
import de.useweb.backend.ocl.ast.TypeArgumentCallExpression;
import de.useweb.backend.ocl.ast.UnaryExpression;
import de.useweb.backend.ocl.ast.VariableDeclaration;
import de.useweb.backend.ocl.ast.VariableExpression;
import de.useweb.backend.ocl.diagnostics.OclDiagnostic;
import de.useweb.backend.ocl.diagnostics.SourceRange;
import de.useweb.backend.ocl.resolution.OclCallResolutionResult;
import de.useweb.backend.ocl.resolution.OclCallResolver;

public class OclTypeChecker {

    public OclTypecheckResult checkInvariant(UmlModel umlModel, UmlClassId contextClassId, OclAstNode ast) {
        return checkInvariant(umlModel, contextClassId, ast, List.of());
    }

    public OclTypecheckResult checkInvariant(UmlModel umlModel, UmlClassId contextClassId, OclAstNode ast,
            List<String> contextVariableNames) {
        Optional<UmlClass> contextClass = umlModel.findClass(contextClassId);
        if (contextClass.isEmpty()) {
            return OclTypecheckResult.failure(
                    OclType.INVALID,
                    List.of(OclDiagnostic.unknownClass(contextClassId.value(), ast.sourceRange())));
        }

        Map<String, OclType> variables = new LinkedHashMap<>();
        OclType contextType = OclType.classType(contextClass.get(), umlModel);
        (contextVariableNames == null ? List.<String>of() : contextVariableNames)
                .forEach(name -> variables.put(name, contextType));
        List<OclDiagnostic> diagnostics = new ArrayList<>();
        OclType resultType = check(ast, new TypeEnvironment(umlModel, contextClass.get(), variables), diagnostics);
        if (diagnostics.isEmpty() && !resultType.conformsTo(OclType.BOOLEAN)) {
            diagnostics.add(typeError(
                    "An invariant expression must result in Boolean.",
                    ast.sourceRange(),
                    List.of(OclType.BOOLEAN.displayName()),
                    resultType));
        }
        return diagnostics.isEmpty()
                ? OclTypecheckResult.ok(resultType)
                : OclTypecheckResult.failure(resultType, diagnostics);
    }

    public OclTypecheckResult checkExpression(TypeEnvironment environment, OclAstNode ast) {
        List<OclDiagnostic> diagnostics = new ArrayList<>();
        OclType resultType = check(ast, environment, diagnostics);
        return diagnostics.isEmpty()
                ? OclTypecheckResult.ok(resultType)
                : OclTypecheckResult.failure(resultType, diagnostics);
    }

    private OclType check(OclAstNode ast, TypeEnvironment environment, List<OclDiagnostic> diagnostics) {
        return switch (ast) {
            case AllInstancesExpression allInstances -> allInstancesType(allInstances, environment, diagnostics);
            case AtPreExpression atPre -> atPreType(atPre, environment, diagnostics);
            case SelfExpression ignored -> environment.classType(environment.contextClass());
            case EnumLiteralExpression enumLiteral -> enumLiteralType(enumLiteral, environment, diagnostics);
            case LiteralExpression literal -> literalType(literal.literalType());
            case VariableExpression variable -> variableType(variable, environment, diagnostics);
            case IfExpression ifExpression -> ifType(ifExpression, environment, diagnostics);
            case LetExpression letExpression -> letType(letExpression, environment, diagnostics);
            case IterateExpression iterate -> iterateType(iterate, environment, diagnostics);
            case IteratorExpression iterator -> iteratorType(iterator, environment, diagnostics);
            case OperationCallExpression operationCall -> operationCallType(operationCall, environment, diagnostics);
            case TupleExpression tuple -> tupleType(tuple, environment, diagnostics);
            case TypeArgumentCallExpression typeCall -> typeArgumentCallType(typeCall, environment, diagnostics);
            case ParenthesizedExpression parenthesized -> check(parenthesized.expression(), environment, diagnostics);
            case PropertyAccessExpression propertyAccess -> propertyAccessType(propertyAccess, environment, diagnostics);
            case QualifiedPropertyAccessExpression qualified -> qualifiedPropertyAccessType(qualified, environment, diagnostics);
            case ResultExpression result -> resultType(result, environment, diagnostics);
            case CollectionLiteralExpression collectionLiteral -> collectionLiteralType(collectionLiteral, environment, diagnostics);
            case UnaryExpression unary -> unaryType(unary, environment, diagnostics);
            case BinaryExpression binary -> binaryType(binary, environment, diagnostics);
        };
    }

    private OclType allInstancesType(AllInstancesExpression expression, TypeEnvironment environment,
            List<OclDiagnostic> diagnostics) {
        return environment.findClassByName(expression.typeName())
                .map(umlClass -> OclType.collectionOf(CollectionKind.SET, environment.classType(umlClass)))
                .orElseGet(() -> {
                    if (environment.umlModel().resolveClassIgnoringVisibility(expression.typeName(),
                            environment.contextClass()).isPresent()) {
                        diagnostics.add(new OclDiagnostic(
                                de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.TYPECHECK,
                                "INACCESSIBLE_CLASSIFIER", "ERROR",
                                "Classifier '" + expression.typeName() + "' is not visible from the current namespace.",
                                expression.typeRange(), List.of("visible classifier"), expression.typeName()));
                        return OclType.INVALID;
                    }
                    if (environment.umlModel().isAmbiguousClassName(expression.typeName(), environment.contextClass())) {
                        diagnostics.add(new OclDiagnostic(
                                de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.TYPECHECK,
                                "AMBIGUOUS_QUALIFIED_NAME", "ERROR",
                                "Classifier name '" + expression.typeName() + "' is ambiguous.",
                                expression.typeRange(), List.of("qualified classifier name"), expression.typeName()));
                        return OclType.INVALID;
                    }
                    diagnostics.add(OclDiagnostic.unknownClass(expression.typeName(), expression.typeRange()));
                    return OclType.INVALID;
                });
    }

    private OclType resultType(ResultExpression expression, TypeEnvironment environment,
            List<OclDiagnostic> diagnostics) {
        if (environment.contractKind() != de.useweb.backend.ocl.contract.OperationConstraintKind.POSTCONDITION) {
            diagnostics.add(typeError("'result' is only available in a postcondition.", expression.sourceRange(),
                    List.of("postcondition scope"), OclType.INVALID));
            return OclType.INVALID;
        }
        return environment.findVariable("result").orElseGet(() -> {
            diagnostics.add(typeError("The operation context has no result value.", expression.sourceRange(),
                    List.of("operation result"), OclType.INVALID));
            return OclType.INVALID;
        });
    }

    private OclType atPreType(AtPreExpression expression, TypeEnvironment environment,
            List<OclDiagnostic> diagnostics) {
        if (environment.contractKind() != de.useweb.backend.ocl.contract.OperationConstraintKind.POSTCONDITION) {
            diagnostics.add(typeError("'@pre' is only available in a postcondition.", expression.atPreRange(),
                    List.of("postcondition scope"), OclType.INVALID));
            return OclType.INVALID;
        }
        return check(expression.expression(), environment, diagnostics);
    }

    private OclType enumLiteralType(EnumLiteralExpression expression, TypeEnvironment environment,
            List<OclDiagnostic> diagnostics) {
        var enumeration = environment.findEnumerationByName(expression.enumerationName());
        if (enumeration.isEmpty()) {
            boolean ambiguous = environment.umlModel().isAmbiguousEnumerationName(
                    expression.enumerationName(), environment.contextClass());
            diagnostics.add(new OclDiagnostic(
                    de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.TYPECHECK,
                    ambiguous ? "AMBIGUOUS_QUALIFIED_NAME" : "UNKNOWN_ENUMERATION", "ERROR",
                    (ambiguous ? "Ambiguous" : "Unknown") + " enumeration '" + expression.enumerationName() + "'.",
                    expression.enumerationRange(), List.of(ambiguous ? "qualified enumeration name" : "known enumeration"),
                    expression.enumerationName()));
            return OclType.INVALID;
        }
        if (!enumeration.get().containsLiteral(expression.literalName())) {
            diagnostics.add(new OclDiagnostic(
                    de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.TYPECHECK,
                    "UNKNOWN_ENUM_LITERAL", "ERROR",
                    "Unknown literal '" + expression.literalName() + "' for enumeration '"
                            + expression.enumerationName() + "'.",
                    expression.literalRange(), enumeration.get().literals(), expression.literalName()));
            return OclType.INVALID;
        }
        return OclType.enumerationType(enumeration.get().id(), enumeration.get().qualifiedName(environment.umlModel()));
    }

    private OclType letType(LetExpression expression, TypeEnvironment environment,
            List<OclDiagnostic> diagnostics) {
        OclType initializerType = check(expression.initializer(), environment, diagnostics);
        OclType variableType = initializerType;
        if (expression.variable().hasDeclaredType()) {
            variableType = resolveDeclaredType(expression.variable(), environment, diagnostics);
            if (!initializerType.isInvalid() && !variableType.isInvalid()
                    && !initializerType.conformsTo(variableType)) {
                diagnostics.add(new OclDiagnostic(
                        de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.TYPECHECK,
                        "LET_TYPE_MISMATCH", "ERROR",
                        "Let initializer does not conform to the declared variable type.",
                        expression.initializerRange(), List.of(variableType.displayName()),
                        initializerType.displayName()));
            }
        }
        if (initializerType.isInvalid() || variableType.isInvalid()) {
            return OclType.INVALID;
        }
        return check(expression.body(), environment.child(Map.of(expression.variable().name(), variableType)), diagnostics);
    }

    private OclType ifType(IfExpression expression, TypeEnvironment environment,
            List<OclDiagnostic> diagnostics) {
        OclType conditionType = check(expression.condition(), environment, diagnostics);
        if (!conditionType.isInvalid() && !conditionType.conformsTo(OclType.BOOLEAN)) {
            diagnostics.add(new OclDiagnostic(
                    de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.TYPECHECK,
                    "INVALID_IF_CONDITION_TYPE", "ERROR",
                    "If condition must result in Boolean.", expression.conditionRange(),
                    List.of(OclType.BOOLEAN.displayName()), conditionType.displayName()));
        }

        OclType thenType = check(expression.thenExpression(), environment, diagnostics);
        OclType elseType = check(expression.elseExpression(), environment, diagnostics);
        OclType resultType = thenType.leastUpperBound(elseType, environment.umlModel());
        if (!thenType.isInvalid() && !elseType.isInvalid() && resultType.isInvalid()) {
            diagnostics.add(new OclDiagnostic(
                    de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.TYPECHECK,
                    "INCOMPATIBLE_BRANCH_TYPES", "ERROR",
                    "Then and else branches do not have a common result type.", expression.sourceRange(),
                    List.of("compatible branch types"),
                    thenType.displayName() + " / " + elseType.displayName()));
        }
        return diagnostics.isEmpty() ? resultType : OclType.INVALID;
    }

    private OclType variableType(VariableExpression expression, TypeEnvironment environment,
            List<OclDiagnostic> diagnostics) {
        Optional<OclType> variable = environment.findVariable(expression.name());
        if (variable.isPresent()) return variable.get();
        List<OclDiagnostic> implicitDiagnostics = new ArrayList<>();
        OclType implicitProperty = propertyAccessType(new PropertyAccessExpression(
                new SelfExpression(expression.sourceRange()), expression.name(), expression.sourceRange()),
                environment, implicitDiagnostics);
        if (!implicitProperty.isInvalid()) return implicitProperty;
        implicitDiagnostics.stream().filter(diagnostic -> diagnostic.code().equals("INACCESSIBLE_FEATURE")
                || diagnostic.code().equals("AMBIGUOUS_CALL")).forEach(diagnostics::add);
        if (diagnostics.isEmpty()) {
            diagnostics.add(new OclDiagnostic(
                    de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.TYPECHECK,
                    "UNKNOWN_VARIABLE", "ERROR",
                    "Unknown variable '" + expression.name() + "'.",
                    expression.sourceRange(), List.of("visible iterator variable"), expression.name()));
        }
        return OclType.INVALID;
    }

    private OclType iteratorType(IteratorExpression expression, TypeEnvironment environment,
            List<OclDiagnostic> diagnostics) {
        OclType sourceType = check(expression.source(), environment, diagnostics);
        if (sourceType.isInvalid()) {
            return OclType.INVALID;
        }
        OclType elementType;
        if (sourceType.isCollection()) {
            elementType = sourceType.elementType();
        } else if (sourceType.kind() == OclType.Kind.VOID || sourceType.kind() == OclType.Kind.OCL_INVALID) {
            elementType = OclType.OCL_ANY;
        } else {
            diagnostics.add(typeError("Iterator source must be a Collection.", expression.source().sourceRange(),
                    List.of("Collection(T)"), sourceType));
            return OclType.INVALID;
        }

        Map<String, OclType> bindings = new LinkedHashMap<>();
        if (expression.variables().isEmpty() && elementType.kind() == OclType.Kind.TUPLE) {
            bindings.putAll(elementType.tupleParts());
        }
        for (VariableDeclaration variable : expression.variables()) {
            if (bindings.containsKey(variable.name())) {
                diagnostics.add(new OclDiagnostic(
                        de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.TYPECHECK,
                        "DUPLICATE_ITERATOR_VARIABLE", "ERROR",
                        "Iterator variable '" + variable.name() + "' is declared more than once.",
                        variable.nameRange(), List.of("unique variable name"), variable.name()));
                continue;
            }
            OclType variableType = elementType;
            if (variable.hasDeclaredType()) {
                OclType declaredType = resolveDeclaredType(variable, environment, diagnostics);
                if (!declaredType.isInvalid() && !elementType.conformsTo(declaredType)) {
                    diagnostics.add(typeError(
                            "Iterator variable type is incompatible with the collection element type.",
                            variable.typeRange(), List.of(elementType.displayName()), declaredType));
                } else if (!declaredType.isInvalid()) {
                    variableType = declaredType;
                }
            }
            bindings.put(variable.name(), variableType);
        }

        OclType bodyType = check(expression.body(), environment.child(bindings), diagnostics);
        if (expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.CLOSURE) {
            requireSingleIteratorVariable(expression, diagnostics);
            boolean compatibleBody = bodyType.conformsTo(elementType)
                    || bodyType.isCollection() && bodyType.elementType().conformsTo(elementType);
            if (!bodyType.isInvalid() && !compatibleBody) {
                diagnostics.add(new OclDiagnostic(
                        de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.TYPECHECK,
                        "INVALID_ITERATOR_BODY_TYPE", "ERROR",
                        "Iterator body for 'closure' must result in the source element type or a collection of it.",
                        expression.bodyRange(), List.of(elementType.displayName(), "Collection(" + elementType.displayName() + ")"),
                        bodyType.displayName()));
            }
            CollectionKind resultKind = sourceType.isCollection() && sourceType.collectionKind().ordered()
                    ? CollectionKind.ORDERED_SET : CollectionKind.SET;
            return diagnostics.isEmpty() ? OclType.collectionOf(resultKind, elementType) : OclType.INVALID;
        }
        if (expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.FOR_ALL
                || expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.EXISTS
                || expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.ONE) {
            requireBooleanIteratorBody(expression, bodyType, diagnostics);
            return diagnostics.isEmpty() ? OclType.BOOLEAN : OclType.INVALID;
        }
        if (expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.SELECT
                || expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.REJECT) {
            if (expression.variables().size() > 1) {
                diagnostics.add(new OclDiagnostic(
                        de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.TYPECHECK,
                        "INVALID_ITERATOR_ARITY", "ERROR",
                        "Iterator '" + expression.kind().oclName() + "' requires exactly one iterator variable.",
                        expression.variables().get(1).nameRange(), List.of("1 iterator variable"),
                        Integer.toString(expression.variables().size())));
            }
            requireBooleanIteratorBody(expression, bodyType, diagnostics);
            OclType resultType = sourceType.isCollection()
                    ? sourceType
                    : OclType.collectionOf(de.useweb.backend.ocl.collection.CollectionKind.COLLECTION, OclType.OCL_ANY);
            return diagnostics.isEmpty() ? resultType : OclType.INVALID;
        }
        if (expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.COLLECT
                || expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.COLLECT_NESTED) {
            if (expression.variables().size() > 1) {
                diagnostics.add(new OclDiagnostic(
                        de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.TYPECHECK,
                        "INVALID_ITERATOR_ARITY", "ERROR",
                        "Iterator '" + expression.kind().oclName() + "' requires exactly one iterator variable.",
                        expression.variables().get(1).nameRange(), List.of("1 iterator variable"),
                        Integer.toString(expression.variables().size())));
            }
            if (!diagnostics.isEmpty()) {
                return OclType.INVALID;
            }
            CollectionKind sourceKind = sourceType.isCollection()
                    ? sourceType.collectionKind() : CollectionKind.COLLECTION;
            return collectResultType(sourceKind, bodyType,
                    expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.COLLECT);
        }
        if (expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.ANY
                || expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.IS_UNIQUE
                || expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.SORTED_BY) {
            if (!expression.variables().isEmpty()) requireSingleIteratorVariable(expression, diagnostics);
            if (expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.ANY) {
                requireBooleanIteratorBody(expression, bodyType, diagnostics);
            }
            if (expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.SORTED_BY
                    && !isSortableKeyType(bodyType)) {
                diagnostics.add(new OclDiagnostic(
                        de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.TYPECHECK,
                        "NON_COMPARABLE_SORT_KEY", "ERROR",
                        "Iterator body for 'sortedBy' must result in Integer, Real, or String.",
                        expression.bodyRange(), List.of("Integer", "Real", "String"), bodyType.displayName()));
            }
            if (!diagnostics.isEmpty()) {
                return OclType.INVALID;
            }
            return switch (expression.kind()) {
                case ANY -> elementType;
                case IS_UNIQUE -> OclType.BOOLEAN;
                case SORTED_BY -> sortedByResultType(sourceType, elementType);
                default -> OclType.INVALID;
            };
        }
        if (diagnostics.isEmpty()) {
            diagnostics.add(new OclDiagnostic(
                    de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.TYPECHECK,
                    "UNSUPPORTED_ITERATOR_SEMANTICS", "ERROR",
                    "Iterator semantics for '" + expression.kind().oclName() + "' are not implemented yet.",
                    expression.operationRange(), List.of("roadmap step for concrete iterator semantics"),
                    expression.kind().oclName()));
        }
        return OclType.INVALID;
    }

    private OclType iterateType(IterateExpression expression, TypeEnvironment environment,
            List<OclDiagnostic> diagnostics) {
        OclType sourceType = check(expression.source(), environment, diagnostics);
        if (!sourceType.isCollection()) {
            if (!sourceType.isInvalid()) {
                diagnostics.add(typeError("Iterator source must be a Collection.", expression.source().sourceRange(),
                        List.of("Collection(T)"), sourceType));
            }
            return OclType.INVALID;
        }

        OclType elementType = sourceType.elementType();

        OclType accumulatorType = resolveDeclaredType(expression.accumulator(), environment, diagnostics);
        OclType initializerType = check(expression.initializer(), environment, diagnostics);
        if (!accumulatorType.isInvalid() && !initializerType.isInvalid()
                && !initializerType.conformsTo(accumulatorType)) {
            diagnostics.add(invalidAccumulatorType("initializer", expression.initializerRange(), accumulatorType,
                    initializerType));
        }

        Map<String, OclType> bindings = new LinkedHashMap<>();
        for (VariableDeclaration iterator : expression.iterators()) {
            OclType iteratorType = elementType;
            if (iterator.hasDeclaredType()) {
                OclType declaredIteratorType = resolveDeclaredType(iterator, environment, diagnostics);
                if (!declaredIteratorType.isInvalid() && !elementType.conformsTo(declaredIteratorType)) {
                    diagnostics.add(typeError("Iterator variable type is incompatible with the collection element type.",
                            iterator.typeRange(), List.of(elementType.displayName()), declaredIteratorType));
                } else if (!declaredIteratorType.isInvalid()) {
                    iteratorType = declaredIteratorType;
                }
            }
            if (bindings.put(iterator.name(), iteratorType) != null) {
                diagnostics.add(new OclDiagnostic(
                        de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.TYPECHECK,
                        "DUPLICATE_ITERATOR_VARIABLE", "ERROR",
                        "Iterator variable names must be distinct.", iterator.nameRange(),
                        List.of("distinct variable names"), iterator.name()));
            }
        }
        if (bindings.put(expression.accumulator().name(), accumulatorType) != null) {
            diagnostics.add(new OclDiagnostic(
                    de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.TYPECHECK,
                    "DUPLICATE_ITERATOR_VARIABLE", "ERROR",
                    "Iterator and accumulator variables must have different names.",
                    expression.accumulator().nameRange(), List.of("distinct variable names"),
                    expression.accumulator().name()));
        }
        OclType bodyType = check(expression.body(), environment.child(bindings), diagnostics);
        if (!accumulatorType.isInvalid() && !bodyType.isInvalid() && !bodyType.conformsTo(accumulatorType)) {
            diagnostics.add(invalidAccumulatorType("body", expression.bodyRange(), accumulatorType, bodyType));
        }
        return diagnostics.isEmpty() ? accumulatorType : OclType.INVALID;
    }

    private OclDiagnostic invalidAccumulatorType(String part, de.useweb.backend.ocl.diagnostics.SourceRange range,
            OclType expected, OclType actual) {
        return new OclDiagnostic(
                de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.TYPECHECK,
                "INVALID_ACCUMULATOR_TYPE", "ERROR",
                "The iterate " + part + " must conform to accumulator type '" + expected.displayName() + "'.",
                range, List.of(expected.displayName()), actual.displayName());
    }

    private void requireBooleanIteratorBody(IteratorExpression expression, OclType bodyType,
            List<OclDiagnostic> diagnostics) {
        if (!bodyType.isInvalid() && !bodyType.conformsTo(OclType.BOOLEAN)) {
            diagnostics.add(new OclDiagnostic(
                    de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.TYPECHECK,
                    "INVALID_ITERATOR_BODY_TYPE", "ERROR",
                    "Iterator body for '" + expression.kind().oclName() + "' must result in Boolean.",
                    expression.bodyRange(), List.of(OclType.BOOLEAN.displayName()), bodyType.displayName()));
        }
    }

    private void requireSingleIteratorVariable(IteratorExpression expression, List<OclDiagnostic> diagnostics) {
        if (expression.variables().size() != 1) {
            diagnostics.add(new OclDiagnostic(
                    de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.TYPECHECK,
                    "INVALID_ITERATOR_ARITY", "ERROR",
                    "Iterator '" + expression.kind().oclName() + "' requires exactly one iterator variable.",
                    expression.variables().get(1).nameRange(), List.of("1 iterator variable"),
                    Integer.toString(expression.variables().size())));
        }
    }

    private boolean isSortableKeyType(OclType type) {
        return type.isNumeric() || type.sameTypeAs(OclType.STRING);
    }

    private OclType sortedByResultType(OclType sourceType, OclType elementType) {
        CollectionKind resultKind = !sourceType.isCollection() ? CollectionKind.COLLECTION
                : switch (sourceType.collectionKind()) {
                    case SET -> CollectionKind.ORDERED_SET;
                    case BAG, SEQUENCE -> CollectionKind.SEQUENCE;
                    case ORDERED_SET -> CollectionKind.ORDERED_SET;
                    case COLLECTION -> CollectionKind.SEQUENCE;
                };
        return OclType.collectionOf(resultKind, elementType);
    }

    private OclType resolveDeclaredType(VariableDeclaration variable, TypeEnvironment environment,
            List<OclDiagnostic> diagnostics) {
        OclType type = resolveTypeName(variable.declaredTypeName(), environment);
        if (type.isInvalid()) {
            diagnostics.add(new OclDiagnostic(
                    de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.TYPECHECK,
                    "UNKNOWN_TYPE", "ERROR",
                    "Unknown variable type '" + variable.declaredTypeName() + "'.",
                    variable.typeRange(), List.of("known OCL or UML type"), variable.declaredTypeName()));
        }
        return type;
    }

    private OclType resolveTypeName(String typeName, TypeEnvironment environment) {
        int leftParen = typeName.indexOf('(');
        if (leftParen > 0 && typeName.endsWith(")")) {
            String collectionName = typeName.substring(0, leftParen);
            String elementName = typeName.substring(leftParen + 1, typeName.length() - 1);
            return collectionKind(collectionName)
                    .map(kind -> OclType.collectionOf(kind, resolveTypeName(elementName, environment)))
                    .filter(type -> !type.elementType().isInvalid())
                    .orElse(OclType.INVALID);
        }
        return switch (typeName) {
            case "String" -> OclType.STRING;
            case "Integer" -> OclType.INTEGER;
            case "Real" -> OclType.REAL;
            case "Boolean" -> OclType.BOOLEAN;
            case "UnlimitedNatural" -> OclType.UNLIMITED_NATURAL;
            case "OclAny" -> OclType.OCL_ANY;
            case "OclVoid" -> OclType.VOID;
            case "OclInvalid" -> OclType.OCL_INVALID;
            default -> environment.findClassByName(typeName)
                    .map(environment::classType)
                    .orElseGet(() -> environment.findEnumerationByName(typeName)
                            .map(enumeration -> OclType.enumerationType(enumeration.id(), enumeration.name()))
                            .orElseGet(() -> environment.findDataTypeByName(typeName)
                                    .map(dataType -> OclType.dataType(dataType.id(), dataType.name()))
                                    .orElse(OclType.INVALID)));
        };
    }

    private Optional<CollectionKind> collectionKind(String name) {
        if (name.equals(CollectionKind.COLLECTION.oclName())) {
            return Optional.of(CollectionKind.COLLECTION);
        }
        return CollectionKind.fromOclName(name);
    }

    private OclType literalType(LiteralType literalType) {
        return switch (literalType) {
            case STRING -> OclType.STRING;
            case INTEGER -> OclType.INTEGER;
            case REAL -> OclType.REAL;
            case BOOLEAN -> OclType.BOOLEAN;
            case UNLIMITED_NATURAL -> OclType.UNLIMITED_NATURAL;
            case NULL -> OclType.VOID;
            case INVALID -> OclType.OCL_INVALID;
        };
    }

    private OclType propertyAccessType(PropertyAccessExpression expression, TypeEnvironment environment, List<OclDiagnostic> diagnostics) {
        OclType receiverType = check(expression.receiver(), environment, diagnostics);
        if (receiverType.isInvalid()) {
            return OclType.INVALID;
        }
        if (receiverType.kind() == OclType.Kind.TUPLE) {
            OclType partType = receiverType.tupleParts().get(expression.propertyName());
            if (partType != null) {
                return partType;
            }
            diagnostics.add(OclDiagnostic.unknownAttribute(expression.propertyName(), expression.propertyRange()));
            return OclType.INVALID;
        }
        if (receiverType.kind() == OclType.Kind.DATA_TYPE) {
            return environment.umlModel().dataTypes().stream()
                    .filter(dataType -> dataType.id().equals(receiverType.dataTypeId())).findFirst()
                    .flatMap(dataType -> dataType.properties().stream()
                            .filter(property -> property.name().equals(expression.propertyName())).findFirst())
                    .map(property -> OclType.fromUmlType(property.type(), environment))
                    .orElseGet(() -> {
                        diagnostics.add(OclDiagnostic.unknownAttribute(expression.propertyName(), expression.propertyRange()));
                        return OclType.INVALID;
                    });
        }
        OclCallResolver resolver = new OclCallResolver(environment);
        if (receiverType.isCollection()) {
            OclCallResolutionResult elementShorthand = resolver
                    .resolveProperty(receiverType.elementType(), expression.propertyName());
            if (elementShorthand.status() == OclCallResolutionResult.Status.RESOLVED) {
                return collectResultType(receiverType.collectionKind(),
                        elementShorthand.resolution().resultType(), true);
            }
            OclType bodyType = propertyType(receiverType.elementType(), expression, environment, diagnostics);
            return bodyType.isInvalid() ? OclType.INVALID
                    : collectResultType(receiverType.collectionKind(), bodyType, true);
        }
        OclCallResolutionResult shorthand = resolver.resolveProperty(receiverType, expression.propertyName());
        if (shorthand.status() == OclCallResolutionResult.Status.RESOLVED) {
            return shorthand.resolution().resultType();
        }
        if (shorthand.status() == OclCallResolutionResult.Status.INACCESSIBLE
                || shorthand.status() == OclCallResolutionResult.Status.AMBIGUOUS) {
            diagnostics.add(callResolutionError(shorthand, expression.propertyRange(), receiverType));
            return OclType.INVALID;
        }
        return propertyType(receiverType, expression, environment, diagnostics);
    }

    private OclType qualifiedPropertyAccessType(QualifiedPropertyAccessExpression expression,
            TypeEnvironment environment, List<OclDiagnostic> diagnostics) {
        OclType receiverType = check(expression.receiver(), environment, diagnostics);
        OclType representedType = receiverType.kind() == OclType.Kind.OCL_TYPE
                ? receiverType.classifierType() : receiverType;
        if (representedType.kind() != OclType.Kind.CLASS) {
            diagnostics.add(typeError("Qualified navigation requires a class-valued receiver.",
                    expression.sourceRange(), List.of("ClassType"), receiverType));
            return OclType.INVALID;
        }
        Optional<UmlClass> receiverClass = environment.findClass(representedType.classId());
        if (receiverClass.isEmpty()) {
            diagnostics.add(OclDiagnostic.unknownClass(receiverType.displayName(), expression.sourceRange()));
            return OclType.INVALID;
        }
        List<NavigationTarget> targets = findNavigationTargets(environment, receiverClass.get(), expression.propertyName());
        if (targets.size() != 1) {
            diagnostics.add(typeError(targets.isEmpty()
                            ? "Unknown qualified association role '" + expression.propertyName() + "'."
                            : "Qualified association role '" + expression.propertyName() + "' is ambiguous.",
                    expression.propertyRange(), List.of("unique qualified association role"), receiverType));
            return OclType.INVALID;
        }
        NavigationTarget target = targets.getFirst();
        var qualifiers = target.targetEnd().qualifiers();
        if (qualifiers.size() != expression.qualifierArguments().size()) {
            diagnostics.add(typeError("Role '" + expression.propertyName() + "' expects " + qualifiers.size()
                            + " qualifier value(s), but " + expression.qualifierArguments().size() + " were provided.",
                    expression.sourceRange(), List.of(qualifiers.size() + " qualifier value(s)"), receiverType));
            return OclType.INVALID;
        }
        for (int index = 0; index < qualifiers.size(); index++) {
            OclType actual = check(expression.qualifierArguments().get(index), environment, diagnostics);
            OclType expected = OclType.fromUmlType(qualifiers.get(index).type(), environment);
            if (!actual.isInvalid() && !actual.conformsTo(expected)) {
                diagnostics.add(typeError("Qualifier '" + qualifiers.get(index).name() + "' expects "
                                + expected.displayName() + ", but received " + actual.displayName() + ".",
                        expression.qualifierArguments().get(index).sourceRange(), List.of(expected.displayName()), actual));
                return OclType.INVALID;
            }
        }
        OclType targetType = environment.classType(target.targetClass());
        return isCollectionMultiplicity(target.targetEnd().multiplicity())
                ? OclType.collectionOf(navigationCollectionKind(target.targetEnd()), targetType)
                : targetType;
    }

    private OclType tupleType(TupleExpression expression, TypeEnvironment environment,
            List<OclDiagnostic> diagnostics) {
        Map<String, OclType> parts = new LinkedHashMap<>();
        for (var part : expression.parts()) {
            if (parts.containsKey(part.name())) {
                diagnostics.add(typeError("Duplicate tuple part '" + part.name() + "'.", part.nameRange(),
                        List.of("unique tuple part name"), OclType.INVALID));
                return OclType.INVALID;
            }
            OclType valueType = check(part.value(), environment, diagnostics);
            if (valueType.isInvalid()) {
                return OclType.INVALID;
            }
            parts.put(part.name(), valueType);
        }
        return OclType.tupleOf(parts);
    }

    private OclType typeArgumentCallType(TypeArgumentCallExpression expression, TypeEnvironment environment,
            List<OclDiagnostic> diagnostics) {
        OclType receiver = check(expression.receiver(), environment, diagnostics);
        OclType target = resolveTypeName(expression.typeName(), environment);
        if (target.isInvalid()) {
            diagnostics.add(OclDiagnostic.unknownClass(expression.typeName(), expression.typeRange()));
            return OclType.INVALID;
        }
        if (receiver.isCollection()
                && expression.navigationOperator() == de.useweb.backend.ocl.ast.CallNavigationOperator.DOT
                && (expression.operationName().equals("oclIsTypeOf")
                || expression.operationName().equals("oclIsKindOf")
                || expression.operationName().equals("oclAsType"))) {
            OclType elementResult = expression.operationName().equals("oclAsType") ? target : OclType.BOOLEAN;
            return collectResultType(receiver.collectionKind(), elementResult, true);
        }
        return switch (expression.operationName()) {
            case "oclIsTypeOf", "oclIsKindOf" -> OclType.BOOLEAN;
            case "selectByKind", "selectByType" -> {
                if (!receiver.isCollection()) {
                    diagnostics.add(typeError("Operation '" + expression.operationName()
                                    + "' requires a Collection receiver.", expression.operationRange(),
                            List.of("Collection(T)"), receiver));
                    yield OclType.INVALID;
                }
                yield OclType.collectionOf(receiver.collectionKind(), target);
            }
            case "oclAsType" -> {
                if (!receiver.conformsTo(target) && !target.conformsTo(receiver)) {
                    diagnostics.add(typeError("Operation 'oclAsType' requires related source and target types.",
                            expression.operationRange(), List.of(target.displayName()), receiver));
                    yield OclType.INVALID;
                }
                yield target;
            }
            default -> OclType.INVALID;
        };
    }

    private OclType propertyType(OclType receiverType, PropertyAccessExpression expression,
            TypeEnvironment environment, List<OclDiagnostic> diagnostics) {
        if (receiverType.kind() != OclType.Kind.CLASS) {
            diagnostics.add(typeError(
                    "Property access requires a class-valued receiver.",
                    expression.sourceRange(),
                    List.of("ClassType"),
                    receiverType));
            return OclType.INVALID;
        }

        Optional<UmlClass> receiverClass = environment.findClass(receiverType.classId());
        if (receiverClass.isEmpty()) {
            diagnostics.add(OclDiagnostic.unknownClass(receiverType.displayName(), expression.sourceRange()));
            return OclType.INVALID;
        }

        OclCallResolutionResult resolved = new OclCallResolver(environment)
                .resolveProperty(receiverType, expression.propertyName());
        if (resolved.status() == OclCallResolutionResult.Status.RESOLVED) {
            return resolved.resolution().resultType();
        }
        if (resolved.status() == OclCallResolutionResult.Status.INACCESSIBLE
                || resolved.status() == OclCallResolutionResult.Status.AMBIGUOUS) {
            diagnostics.add(callResolutionError(resolved, expression.propertyRange(), receiverType));
            return OclType.INVALID;
        }

        Optional<UmlClass> associationClass = associationClassNavigationTarget(
                environment, receiverClass.get(), expression.propertyName());
        if (associationClass.isPresent()) {
            return OclType.collectionOf(CollectionKind.SET, environment.classType(associationClass.get()));
        }

        List<NavigationTarget> navigationTargets = findNavigationTargets(environment, receiverClass.get(), expression.propertyName());
        if (navigationTargets.size() > 1) {
            diagnostics.add(typeError("Association-end navigation '" + expression.propertyName()
                            + "' is ambiguous for the receiver type.", expression.propertyRange(),
                    List.of("unique navigable association end role"), expression.propertyName()));
            return OclType.INVALID;
        }
        if (!navigationTargets.isEmpty()) {
            NavigationTarget navigationTarget = navigationTargets.getFirst();
            OclType targetType = environment.classType(navigationTarget.targetClass());
            return isCollectionMultiplicity(navigationTarget.targetEnd().multiplicity())
                    ? OclType.collectionOf(navigationCollectionKind(navigationTarget.targetEnd()), targetType)
                    : targetType;
        }

        diagnostics.add(OclDiagnostic.unknownAttribute(expression.propertyName(), expression.propertyRange()));
        return OclType.INVALID;
    }

    private Optional<UmlClass> associationClassNavigationTarget(TypeEnvironment environment,
            UmlClass receiverClass, String propertyName) {
        List<UmlClass> matches = environment.umlModel().associations().stream()
                .filter(association -> association.associationClassId() != null)
                .filter(association -> association.ends().stream().anyMatch(end ->
                        environment.umlModel().isSubtypeOf(receiverClass.id(), end.classId())))
                .map(association -> environment.findClass(association.associationClassId()))
                .flatMap(Optional::stream)
                .filter(candidate -> candidate.name().equals(propertyName)
                        || lowerCamel(candidate.name()).equals(propertyName))
                .distinct().toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    private String lowerCamel(String value) {
        return value.isEmpty() ? value : Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private OclType collectResultType(CollectionKind sourceKind, OclType bodyType, boolean flatten) {
        OclType resultElementType = flatten ? flattenedElementType(bodyType) : bodyType;
        CollectionKind resultKind = switch (sourceKind) {
            case SET, BAG -> CollectionKind.BAG;
            case SEQUENCE, ORDERED_SET -> CollectionKind.SEQUENCE;
            case COLLECTION -> CollectionKind.COLLECTION;
        };
        return OclType.collectionOf(resultKind, resultElementType);
    }

    private List<NavigationTarget> findNavigationTargets(TypeEnvironment environment, UmlClass receiverClass, String roleName) {
        return environment.umlModel().typeConformanceOrder(receiverClass.id()).stream()
                .map(environment::findClass).flatMap(Optional::stream)
                .flatMap(sourceClass -> environment.umlModel().associations().stream()
                        .map(association -> navigationTarget(environment, association, sourceClass, roleName)))
                .flatMap(Optional::stream)
                .distinct()
                .toList();
    }

    private Optional<NavigationTarget> navigationTarget(
            TypeEnvironment environment,
            UmlAssociation association,
            UmlClass receiverClass,
            String roleName) {
        return association.ends().stream()
                .filter(target -> de.useweb.backend.ocl.profile.OclOptionalCompliancePolicy.mayNavigate(target)
                        && target.roleName().equals(roleName))
                .filter(target -> association.ends().stream().anyMatch(source -> !source.id().equals(target.id())
                        && source.classId().equals(receiverClass.id())))
                .findFirst()
                .flatMap(target -> environment.findClass(target.classId())
                        .map(targetClass -> new NavigationTarget(target, targetClass)));
    }

    private boolean isCollectionMultiplicity(Multiplicity multiplicity) {
        return multiplicity.unbounded() || multiplicity.upper() == null || multiplicity.upper() > 1;
    }

    private CollectionKind navigationCollectionKind(UmlAssociationEnd end) {
        if (end.ordered()) return end.unique() ? CollectionKind.ORDERED_SET : CollectionKind.SEQUENCE;
        return end.unique() ? CollectionKind.SET : CollectionKind.BAG;
    }

    private OclType collectionLiteralType(CollectionLiteralExpression expression, TypeEnvironment environment, List<OclDiagnostic> diagnostics) {
        OclType elementType = OclType.VOID;
        for (var part : expression.parts()) {
            OclType partType;
            if (part instanceof CollectionItem item) {
                partType = check(item.expression(), environment, diagnostics);
            } else if (part instanceof CollectionRangeItem rangeItem) {
                OclType firstType = check(rangeItem.first(), environment, diagnostics);
                OclType lastType = check(rangeItem.last(), environment, diagnostics);
                if (!firstType.conformsTo(OclType.INTEGER) || !lastType.conformsTo(OclType.INTEGER)) {
                    diagnostics.add(typeError("Collection ranges require Integer bounds.", rangeItem.sourceRange(),
                            List.of("Integer..Integer"), firstType.displayName() + ".." + lastType.displayName()));
                    partType = OclType.INVALID;
                } else {
                    partType = OclType.INTEGER;
                }
            } else {
                partType = OclType.INVALID;
            }
            if (partType.isInvalid()) {
                return OclType.INVALID;
            }
            elementType = elementType.leastUpperBound(partType, environment.umlModel());
        }
        return OclType.collectionOf(expression.collectionKind(), elementType);
    }

    private OclType unaryType(UnaryExpression expression, TypeEnvironment environment, List<OclDiagnostic> diagnostics) {
        OclType operandType = check(expression.expression(), environment, diagnostics);
        if (operandType.isInvalid()) {
            return OclType.INVALID;
        }
        OclType expectedType = expression.operator() == de.useweb.backend.ocl.ast.UnaryOperator.NOT
                ? OclType.BOOLEAN : OclType.REAL;
        boolean valid = expression.operator() == de.useweb.backend.ocl.ast.UnaryOperator.NOT
                ? operandType.conformsTo(OclType.BOOLEAN) : operandType.isNumeric();
        if (!valid) {
            diagnostics.add(typeError(
                    "Operator '" + expression.operator().symbol() + "' requires a "
                            + (expression.operator() == de.useweb.backend.ocl.ast.UnaryOperator.NOT ? "Boolean" : "numeric") + " operand.",
                    expression.sourceRange(),
                    List.of(expectedType.displayName()),
                    operandType));
            return OclType.INVALID;
        }
        return expression.operator() == de.useweb.backend.ocl.ast.UnaryOperator.NOT ? OclType.BOOLEAN : operandType;
    }

    private OclType binaryType(BinaryExpression expression, TypeEnvironment environment, List<OclDiagnostic> diagnostics) {
        OclType leftType = check(expression.left(), environment, diagnostics);
        OclType rightType = check(expression.right(), environment, diagnostics);
        if (leftType.isInvalid() || rightType.isInvalid()) {
            return OclType.INVALID;
        }
        return switch (expression.operator()) {
            case AND, OR, XOR, IMPLIES -> booleanBinaryType(expression, leftType, rightType, diagnostics);
            case EQUAL, NOT_EQUAL -> equalityType(expression, leftType, rightType, diagnostics);
            case LESS, LESS_EQUAL, GREATER, GREATER_EQUAL -> numericComparisonType(expression, leftType, rightType, diagnostics);
            case ADD, SUBTRACT, MULTIPLY, DIVIDE, INTEGER_DIVIDE, MODULO ->
                    arithmeticType(expression, leftType, rightType, environment.umlModel(), diagnostics);
        };
    }

    private OclType arithmeticType(BinaryExpression expression, OclType leftType, OclType rightType,
            UmlModel umlModel, List<OclDiagnostic> diagnostics) {
        if (expression.operator() == BinaryOperator.SUBTRACT && leftType.isCollection() && rightType.isCollection()
                && leftType.collectionKind() == CollectionKind.SET && rightType.collectionKind() == CollectionKind.SET) {
            OclType elementType = leftType.elementType().leastUpperBound(rightType.elementType(), umlModel);
            return elementType.isInvalid() ? OclType.INVALID : OclType.collectionOf(CollectionKind.SET, elementType);
        }
        if (expression.operator() == BinaryOperator.ADD
                && leftType.sameTypeAs(OclType.STRING) && rightType.sameTypeAs(OclType.STRING)) {
            return OclType.STRING;
        }
        if (!leftType.isNumeric() || !rightType.isNumeric()) {
            diagnostics.add(typeError(
                    "Arithmetic operator '" + expression.operator().symbol() + "' requires numeric operands.",
                    expression.operatorRange(), List.of("Integer or Real"),
                    leftType.displayName() + " and " + rightType.displayName()));
            return OclType.INVALID;
        }
        if (expression.operator() == de.useweb.backend.ocl.ast.BinaryOperator.INTEGER_DIVIDE
                || expression.operator() == de.useweb.backend.ocl.ast.BinaryOperator.MODULO) {
            if (!leftType.conformsTo(OclType.INTEGER) || !rightType.conformsTo(OclType.INTEGER)) {
                diagnostics.add(typeError("Operator '" + expression.operator().symbol() + "' requires Integer operands.",
                        expression.operatorRange(), List.of("Integer and Integer"),
                        leftType.displayName() + " and " + rightType.displayName()));
                return OclType.INVALID;
            }
            return leftType.sameTypeAs(OclType.UNLIMITED_NATURAL)
                    && rightType.sameTypeAs(OclType.UNLIMITED_NATURAL)
                    ? OclType.UNLIMITED_NATURAL : OclType.INTEGER;
        }
        if (expression.operator() == de.useweb.backend.ocl.ast.BinaryOperator.DIVIDE) {
            return OclType.REAL;
        }
        if (leftType.sameTypeAs(OclType.UNLIMITED_NATURAL)
                && rightType.sameTypeAs(OclType.UNLIMITED_NATURAL)
                && (expression.operator() == BinaryOperator.ADD
                || expression.operator() == BinaryOperator.MULTIPLY)) {
            return OclType.UNLIMITED_NATURAL;
        }
        return leftType.conformsTo(OclType.INTEGER) && rightType.conformsTo(OclType.INTEGER)
                ? OclType.INTEGER : OclType.REAL;
    }

    private OclType operationCallType(OperationCallExpression expression, TypeEnvironment environment, List<OclDiagnostic> diagnostics) {
        OclType receiverType = check(expression.receiver(), environment, diagnostics);
        List<OclType> argumentTypes = expression.arguments().stream()
                .map(argument -> check(argument, environment, diagnostics))
                .toList();
        if (receiverType.isInvalid() || argumentTypes.stream().anyMatch(OclType::isInvalid)) {
            return OclType.INVALID;
        }
        if (expression.operationName().equals("oclIsNew") && argumentTypes.isEmpty()
                && receiverType.kind() == OclType.Kind.CLASS) {
            if (environment.contractKind() == de.useweb.backend.ocl.contract.OperationConstraintKind.POSTCONDITION) {
                return OclType.BOOLEAN;
            }
            diagnostics.add(typeError("'oclIsNew()' is only available in a postcondition.",
                    expression.operationRange(), List.of("postcondition scope"), receiverType));
            return OclType.INVALID;
        }
        OclCallResolver resolver = new OclCallResolver(environment);
        if (receiverType.isCollection()
                && expression.navigationOperator() == de.useweb.backend.ocl.ast.CallNavigationOperator.DOT
                && !isOclAnyOperation(expression.operationName())) {
            OclCallResolutionResult elementCall = resolver
                    .resolveOperation(receiverType.elementType(), expression.operationName(), argumentTypes);
            if (elementCall.status() == OclCallResolutionResult.Status.RESOLVED) {
                return collectResultType(receiverType.collectionKind(), elementCall.resolution().resultType(), true);
            }
        }
        OclCallResolutionResult resolved = resolver.resolveOperation(
                receiverType, expression.operationName(), argumentTypes);
        if (resolved.status() == OclCallResolutionResult.Status.RESOLVED) {
            return resolved.resolution().resultType();
        }
        if (receiverType.isCollection()
                && expression.navigationOperator() == de.useweb.backend.ocl.ast.CallNavigationOperator.DOT
                && resolved.status() == OclCallResolutionResult.Status.UNKNOWN) {
            OclCallResolutionResult elementCall = resolver
                    .resolveOperation(receiverType.elementType(), expression.operationName(), argumentTypes);
            if (elementCall.status() == OclCallResolutionResult.Status.RESOLVED) {
                return collectResultType(receiverType.collectionKind(), elementCall.resolution().resultType(), true);
            }
            diagnostics.add(callResolutionError(elementCall, expression.operationRange(), receiverType.elementType()));
            return OclType.INVALID;
        }
        diagnostics.add(callResolutionError(resolved, expression.operationRange(), receiverType));
        return OclType.INVALID;
    }

    private OclDiagnostic callResolutionError(OclCallResolutionResult result, SourceRange range,
            OclType receiverType) {
        String code = switch (result.status()) {
            case INACCESSIBLE -> "INACCESSIBLE_FEATURE";
            case ARGUMENT_MISMATCH -> "INVALID_OPERATION";
            case AMBIGUOUS -> "AMBIGUOUS_CALL";
            case UNKNOWN -> "UNKNOWN_FEATURE";
            case RESOLVED -> throw new IllegalArgumentException("Resolved call has no diagnostic");
        };
        return new OclDiagnostic(de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.TYPECHECK,
                code, "ERROR", result.message(), range, List.of("uniquely resolvable visible feature"),
                receiverType.displayName());
    }

    private boolean isOclAnyOperation(String name) {
        return name.equals("oclIsUndefined") || name.equals("oclIsInvalid") || name.equals("oclType");
    }

    private OclDiagnostic accessDenied(String featureKind, String featureName,
            de.useweb.backend.ocl.diagnostics.SourceRange range, UmlClass owner) {
        return new OclDiagnostic(de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.TYPECHECK,
                "INACCESSIBLE_FEATURE", "ERROR",
                "The " + featureKind + " '" + featureName + "' is not visible from the current classifier.",
                range, List.of("visible " + featureKind), owner.name() + "::" + featureName);
    }

    private OclType flattenedElementType(OclType elementType) {
        OclType flattened = elementType;
        while (flattened.isCollection()) {
            flattened = flattened.elementType();
        }
        return flattened;
    }

    private OclType booleanBinaryType(BinaryExpression expression, OclType leftType, OclType rightType, List<OclDiagnostic> diagnostics) {
        if (leftType.conformsTo(OclType.BOOLEAN) && rightType.conformsTo(OclType.BOOLEAN)) {
            return OclType.BOOLEAN;
        }
        diagnostics.add(typeError(
                "Boolean operator '" + expression.operator().symbol() + "' requires Boolean operands.",
                expression.operatorRange(),
                List.of("Boolean and Boolean"),
                leftType.displayName() + " and " + rightType.displayName()));
        return OclType.INVALID;
    }

    private OclType equalityType(BinaryExpression expression, OclType leftType, OclType rightType, List<OclDiagnostic> diagnostics) {
        if (leftType.conformsTo(rightType) || rightType.conformsTo(leftType)
                || (leftType.isNumeric() && rightType.isNumeric())) {
            return OclType.BOOLEAN;
        }
        diagnostics.add(typeError(
                "Equality operator '" + expression.operator().symbol() + "' requires compatible operand types.",
                expression.operatorRange(),
                List.of(leftType.displayName()),
                rightType.displayName()));
        return OclType.INVALID;
    }

    private OclType numericComparisonType(BinaryExpression expression, OclType leftType, OclType rightType, List<OclDiagnostic> diagnostics) {
        if (leftType.sameTypeAs(OclType.STRING) && rightType.sameTypeAs(OclType.STRING)) return OclType.BOOLEAN;
        if ((leftType.isNumeric() || leftType.kind() == OclType.Kind.VOID || leftType.kind() == OclType.Kind.OCL_INVALID)
                && (rightType.isNumeric() || rightType.kind() == OclType.Kind.VOID || rightType.kind() == OclType.Kind.OCL_INVALID)) {
            return OclType.BOOLEAN;
        }
        diagnostics.add(typeError(
                "Comparison operator '" + expression.operator().symbol() + "' requires numeric operands.",
                expression.operatorRange(),
                List.of("Integer and Integer", "Integer and Real", "Real and Integer", "Real and Real"),
                leftType.displayName() + " and " + rightType.displayName()));
        return OclType.INVALID;
    }

    private OclDiagnostic typeError(String message, SourceRange sourceRange, List<String> expected, OclType actual) {
        return OclDiagnostic.typeError(message, sourceRange, expected, actual.displayName());
    }

    private OclDiagnostic typeError(String message, SourceRange sourceRange, List<String> expected, String actual) {
        return OclDiagnostic.typeError(message, sourceRange, expected, actual);
    }

    private record NavigationTarget(UmlAssociationEnd targetEnd, UmlClass targetClass) {
    }
}
