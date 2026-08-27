package de.useweb.backend.ocl.evaluation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;

import de.useweb.backend.domain.snapshot.ObjectInstance;
import de.useweb.backend.domain.snapshot.ObjectLink;
import de.useweb.backend.domain.snapshot.Slot;
import de.useweb.backend.domain.snapshot.SlotValue;
import de.useweb.backend.domain.uml.Multiplicity;
import de.useweb.backend.domain.uml.PrimitiveType;
import de.useweb.backend.domain.uml.UmlAssociation;
import de.useweb.backend.domain.uml.UmlAssociationEnd;
import de.useweb.backend.domain.uml.UmlAttribute;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.ocl.ast.BinaryExpression;
import de.useweb.backend.ocl.ast.BinaryOperator;
import de.useweb.backend.ocl.ast.AllInstancesExpression;
import de.useweb.backend.ocl.ast.AtPreExpression;
import de.useweb.backend.ocl.ast.CollectionItem;
import de.useweb.backend.ocl.ast.CollectionLiteralExpression;
import de.useweb.backend.ocl.ast.CollectionRangeItem;
import de.useweb.backend.ocl.ast.EnumLiteralExpression;
import de.useweb.backend.ocl.ast.LiteralExpression;
import de.useweb.backend.ocl.ast.IteratorExpression;
import de.useweb.backend.ocl.ast.IfExpression;
import de.useweb.backend.ocl.ast.LetExpression;
import de.useweb.backend.ocl.ast.IterateExpression;
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
import de.useweb.backend.ocl.ast.VariableExpression;
import de.useweb.backend.ocl.diagnostics.OclDiagnostic;
import de.useweb.backend.ocl.library.OclPrimitiveLibrary;
import de.useweb.backend.ocl.definition.OclDefinitionId;
import de.useweb.backend.ocl.definition.OclDefinitionTypeResolver;
import de.useweb.backend.ocl.resolution.OclCallKind;
import de.useweb.backend.ocl.resolution.OclCallResolutionResult;
import de.useweb.backend.ocl.resolution.OclCallResolver;
import de.useweb.backend.ocl.resolution.OclRuntimeType;
import de.useweb.backend.ocl.typecheck.TypeEnvironment;
import de.useweb.backend.ocl.typecheck.OclType;
import de.useweb.backend.ocl.value.BooleanValue;
import de.useweb.backend.ocl.value.BagValue;
import de.useweb.backend.ocl.value.CollectionValue;
import de.useweb.backend.ocl.value.EnumValue;
import de.useweb.backend.ocl.value.IntegerValue;
import de.useweb.backend.ocl.value.ObjectValue;
import de.useweb.backend.ocl.value.OclBooleanLogic;
import de.useweb.backend.ocl.value.OclInvalidValue;
import de.useweb.backend.ocl.value.OclValue;
import de.useweb.backend.ocl.value.OclValueEquality;
import de.useweb.backend.ocl.value.OclVoidValue;
import de.useweb.backend.ocl.value.RealValue;
import de.useweb.backend.ocl.value.SequenceValue;
import de.useweb.backend.ocl.value.SetValue;
import de.useweb.backend.ocl.value.StringValue;
import de.useweb.backend.ocl.value.TupleValue;
import de.useweb.backend.ocl.value.DataTypeValue;
import de.useweb.backend.ocl.value.ClassifierValue;
import de.useweb.backend.ocl.value.UnlimitedNaturalValue;
import de.useweb.backend.ocl.profile.OclComplianceProfile;
import de.useweb.backend.ocl.value.OrderedSetValue;

public class OclEvaluator {

    private static final long MAX_ITERATOR_BINDINGS = OclComplianceProfile.MAX_ITERATOR_BINDINGS;
    private static final long MAX_RESULT_ELEMENTS = OclComplianceProfile.MAX_RESULT_ELEMENTS;
    private final int maxEvaluationDepth;
    private final long maxEvaluationNanos;
    private final LongSupplier nanoTime;
    private final ThreadLocal<EvaluationBudget> activeBudget = new ThreadLocal<>();

    public OclEvaluator() {
        this(Math.toIntExact(OclComplianceProfile.MAX_AST_DEPTH),
                OclComplianceProfile.MAX_EVALUATION_MILLIS * 1_000_000L, System::nanoTime);
    }

    public OclEvaluator(int maxEvaluationDepth, long maxEvaluationNanos, LongSupplier nanoTime) {
        if (maxEvaluationDepth < 1 || maxEvaluationNanos < 1 || nanoTime == null) {
            throw new IllegalArgumentException("Evaluation limits and clock must be positive.");
        }
        this.maxEvaluationDepth = maxEvaluationDepth;
        this.maxEvaluationNanos = maxEvaluationNanos;
        this.nanoTime = nanoTime;
    }

    public OclEvaluationResult evaluate(OclAstNode ast, EvaluationContext context) {
        List<OclDiagnostic> diagnostics = new ArrayList<>();
        EvaluationBudget previous = activeBudget.get();
        boolean owner = previous == null;
        if (owner) activeBudget.set(new EvaluationBudget(nanoTime.getAsLong(), maxEvaluationNanos));
        try {
            OclValue value = evaluate(ast, context, diagnostics);
            return diagnostics.isEmpty()
                    ? OclEvaluationResult.ok(value)
                    : OclEvaluationResult.failure(diagnostics);
        } finally {
            if (owner) activeBudget.remove();
        }
    }

    private OclValue evaluate(OclAstNode ast, EvaluationContext context, List<OclDiagnostic> diagnostics) {
        EvaluationBudget budget = activeBudget.get();
        if (budget != null && !budget.enter(ast, diagnostics)) return OclInvalidValue.INSTANCE;
        try {
            OclValue value = switch (ast) {
            case AllInstancesExpression allInstances -> allInstancesValue(allInstances, context, diagnostics);
            case AtPreExpression atPre -> atPreValue(atPre, context, diagnostics);
            case SelfExpression ignored -> new ObjectValue(context.self());
            case EnumLiteralExpression enumLiteral -> enumLiteralValue(enumLiteral, context, diagnostics);
            case LiteralExpression literal -> literalValue(literal);
            case VariableExpression variable -> variableValue(variable, context, diagnostics);
            case IfExpression ifExpression -> ifValue(ifExpression, context, diagnostics);
            case LetExpression letExpression -> letValue(letExpression, context, diagnostics);
            case IterateExpression iterate -> iterateValue(iterate, context, diagnostics);
            case IteratorExpression iterator -> iteratorValue(iterator, context, diagnostics);
            case OperationCallExpression operationCall -> operationCallValue(operationCall, context, diagnostics);
            case TupleExpression tuple -> tupleValue(tuple, context, diagnostics);
            case TypeArgumentCallExpression typeCall -> typeArgumentCallValue(typeCall, context, diagnostics);
            case ParenthesizedExpression parenthesized -> evaluate(parenthesized.expression(), context, diagnostics);
            case PropertyAccessExpression propertyAccess -> propertyAccessValue(propertyAccess, context, diagnostics);
            case QualifiedPropertyAccessExpression qualified -> qualifiedPropertyAccessValue(qualified, context, diagnostics);
            case ResultExpression result -> variableValue(
                    new VariableExpression("result", result.sourceRange()), context, diagnostics);
            case CollectionLiteralExpression collectionLiteral -> collectionLiteralValue(collectionLiteral, context, diagnostics);
            case UnaryExpression unary -> unaryValue(unary, context, diagnostics);
            case BinaryExpression binary -> binaryValue(binary, context, diagnostics);
            };
            return enforceResultLimit(value, ast.sourceRange(), diagnostics);
        } finally {
            if (budget != null) budget.exit();
        }
    }

    private OclValue allInstancesValue(AllInstancesExpression expression, EvaluationContext context,
            List<OclDiagnostic> diagnostics) {
        var umlClass = context.umlModel().classes().stream()
                .filter(candidate -> candidate.name().equals(expression.typeName()))
                .findFirst();
        if (umlClass.isEmpty()) {
            diagnostics.add(OclDiagnostic.unknownClass(expression.typeName(), expression.typeRange()));
            return OclInvalidValue.INSTANCE;
        }
        List<de.useweb.backend.domain.snapshot.ObjectInstance> objects = context.objectsOfType(umlClass.get().id());
        if (objects.size() > MAX_ITERATOR_BINDINGS) {
            return iterationLimitValue(expression.sourceRange(), objects.size(), diagnostics);
        }
        return new SetValue(objects.stream()
                .map(object -> (OclValue) new ObjectValue(object))
                .toList());
    }

    private OclValue atPreValue(AtPreExpression expression, EvaluationContext context,
            List<OclDiagnostic> diagnostics) {
        try {
            return evaluate(expression.expression(), context.atPre(), diagnostics);
        } catch (IllegalStateException exception) {
            diagnostics.add(OclDiagnostic.evaluationError(exception.getMessage(), expression.atPreRange()));
            return OclInvalidValue.INSTANCE;
        }
    }

    private OclValue enumLiteralValue(EnumLiteralExpression expression, EvaluationContext context,
            List<OclDiagnostic> diagnostics) {
        var enumeration = context.umlModel().findClass(context.self().classId())
                .flatMap(contextClass -> context.umlModel().resolveEnumeration(expression.enumerationName(), contextClass));
        if (enumeration.isEmpty() || !enumeration.get().containsLiteral(expression.literalName())) {
            diagnostics.add(OclDiagnostic.evaluationError(
                    "Unknown enumeration literal '" + expression.enumerationName() + "::"
                            + expression.literalName() + "'.",
                    expression.sourceRange()));
            return OclInvalidValue.INSTANCE;
        }
        return new EnumValue(enumeration.get().id(), enumeration.get().qualifiedName(context.umlModel()), expression.literalName());
    }

    private OclValue letValue(LetExpression expression, EvaluationContext context,
            List<OclDiagnostic> diagnostics) {
        OclValue initializer = evaluate(expression.initializer(), context, diagnostics);
        if (initializer instanceof OclInvalidValue || !diagnostics.isEmpty()) {
            return OclInvalidValue.INSTANCE;
        }
        Map<String, String> declaredTypes = expression.variable().hasDeclaredType()
                ? Map.of(expression.variable().name(), expression.variable().declaredTypeName()) : Map.of();
        return evaluate(expression.body(), context.child(
                Map.of(expression.variable().name(), initializer), declaredTypes), diagnostics);
    }

    private OclValue ifValue(IfExpression expression, EvaluationContext context,
            List<OclDiagnostic> diagnostics) {
        OclValue condition = evaluate(expression.condition(), context, diagnostics);
        if (condition instanceof OclInvalidValue || condition instanceof OclVoidValue || !diagnostics.isEmpty()) {
            return OclInvalidValue.INSTANCE;
        }
        if (!(condition instanceof BooleanValue booleanValue)) {
            return evaluationError("If condition must evaluate to Boolean.", expression.conditionRange(), diagnostics);
        }
        return booleanValue.value()
                ? evaluate(expression.thenExpression(), context, diagnostics)
                : evaluate(expression.elseExpression(), context, diagnostics);
    }

    private OclValue variableValue(VariableExpression expression, EvaluationContext context,
            List<OclDiagnostic> diagnostics) {
        Optional<OclValue> variable = context.findVariable(expression.name());
        if (variable.isPresent()) return variable.get();
        Optional<OclValue> implicit = context.implicitSourceValue();
        if (implicit.isPresent()) {
            OclValue source = implicit.get();
            if (source instanceof TupleValue tuple && tuple.parts().containsKey(expression.name())) {
                return tuple.parts().get(expression.name());
            }
            PropertyAccessExpression access = new PropertyAccessExpression(
                    new SelfExpression(expression.sourceRange()), expression.name(), expression.sourceRange());
            if (source instanceof ObjectValue object) return objectPropertyValue(object, access, context, diagnostics);
            return standardOperation(source, expression.name(), List.of(), context, expression.sourceRange(), diagnostics)
                    .orElseGet(() -> evaluationError("Unknown implicit iterator feature '" + expression.name() + "'.",
                            expression.sourceRange(), diagnostics));
        }
        return propertyAccessValue(new PropertyAccessExpression(new SelfExpression(expression.sourceRange()),
                expression.name(), expression.sourceRange()), context, diagnostics);
    }

    private OclValue iteratorValue(IteratorExpression expression, EvaluationContext context,
            List<OclDiagnostic> diagnostics) {
        boolean quantifier = expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.FOR_ALL
                || expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.EXISTS
                || expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.ONE;
        boolean filter = expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.SELECT
                || expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.REJECT;
        boolean transformation = expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.COLLECT
                || expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.COLLECT_NESTED;
        boolean advancedQuery = expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.ANY
                || expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.IS_UNIQUE
                || expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.SORTED_BY
                || expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.CLOSURE;
        if (!quantifier && !filter && !transformation && !advancedQuery) {
            return unsupportedIteratorValue(expression, diagnostics);
        }
        OclValue source = evaluate(expression.source(), context, diagnostics);
        if (source instanceof OclInvalidValue || source instanceof OclVoidValue) {
            return OclInvalidValue.INSTANCE;
        }
        if (!(source instanceof CollectionValue collection)) {
            return evaluationError("Iterator source must evaluate to a collection.",
                    expression.source().sourceRange(), diagnostics);
        }
        if (exceedsBindingBudget(collection.values().size(), expression.variables().size())) {
            diagnostics.add(new OclDiagnostic(
                    de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.EVALUATION,
                    "ITERATION_LIMIT_EXCEEDED", "ERROR",
                    "Iterator evaluation exceeds the binding limit of " + MAX_ITERATOR_BINDINGS + ".",
                    expression.sourceRange(), List.of("at most " + MAX_ITERATOR_BINDINGS + " bindings"),
                    collection.values().size() + "^" + expression.variables().size()));
            return OclInvalidValue.INSTANCE;
        }

        if (filter) {
            return filterValue(expression, collection, context, diagnostics);
        }
        if (transformation) {
            return collectValue(expression, collection, context, diagnostics);
        }
        if (advancedQuery) {
            return advancedIteratorValue(expression, collection, context, diagnostics);
        }

        QuantifierState state = new QuantifierState(expression.kind());
        if (expression.variables().isEmpty()) {
            for (OclValue element : collection.values()) {
                OclValue value = evaluate(expression.body(), context.withImplicitSource(element), diagnostics);
                if (state.accept(value, expression, diagnostics)) break;
            }
            return state.result();
        }
        evaluateBindings(expression, collection.values(), 0, context, new java.util.LinkedHashMap<>(), state,
                diagnostics);
        return state.result();
    }

    private OclValue filterValue(IteratorExpression expression, CollectionValue source,
            EvaluationContext context, List<OclDiagnostic> diagnostics) {
        if (expression.variables().size() > 1) {
            diagnostics.add(new OclDiagnostic(
                    de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.EVALUATION,
                    "INVALID_ITERATOR_ARITY", "ERROR",
                    "Iterator '" + expression.kind().oclName() + "' requires exactly one iterator variable.",
                    expression.sourceRange(), List.of("1 iterator variable"),
                    Integer.toString(expression.variables().size())));
            return OclInvalidValue.INSTANCE;
        }

        String variableName = expression.variables().isEmpty() ? null : expression.variables().getFirst().name();
        List<OclValue> selected = new ArrayList<>();
        for (OclValue element : source.values()) {
            EvaluationContext bodyContext = variableName == null
                    ? context.withImplicitSource(element) : context.child(Map.of(variableName, element));
            OclValue predicate = evaluate(expression.body(), bodyContext, diagnostics);
            if (predicate instanceof OclInvalidValue || predicate instanceof OclVoidValue) {
                return OclInvalidValue.INSTANCE;
            }
            if (!(predicate instanceof BooleanValue booleanValue)) {
                diagnostics.add(new OclDiagnostic(
                        de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.EVALUATION,
                        "INVALID_ITERATOR_BODY_TYPE", "ERROR",
                        "Iterator body must evaluate to Boolean.", expression.bodyRange(), List.of("Boolean"),
                        predicate.typeName()));
                return OclInvalidValue.INSTANCE;
            }
            boolean include = expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.SELECT
                    ? booleanValue.value() : !booleanValue.value();
            if (include) {
                selected.add(element);
            }
        }
        return collectionValue(source, selected);
    }

    private OclValue collectValue(IteratorExpression expression, CollectionValue source,
            EvaluationContext context, List<OclDiagnostic> diagnostics) {
        if (expression.variables().size() > 1) {
            diagnostics.add(new OclDiagnostic(
                    de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.EVALUATION,
                    "INVALID_ITERATOR_ARITY", "ERROR",
                    "Iterator '" + expression.kind().oclName() + "' requires exactly one iterator variable.",
                    expression.sourceRange(), List.of("1 iterator variable"),
                    Integer.toString(expression.variables().size())));
            return OclInvalidValue.INSTANCE;
        }

        String variableName = expression.variables().isEmpty() ? null : expression.variables().getFirst().name();
        List<OclValue> values = new ArrayList<>();
        for (OclValue element : source.values()) {
            EvaluationContext bodyContext = variableName == null
                    ? context.withImplicitSource(element) : context.child(Map.of(variableName, element));
            OclValue value = evaluate(expression.body(), bodyContext, diagnostics);
            if (value instanceof OclInvalidValue || !diagnostics.isEmpty()) {
                return OclInvalidValue.INSTANCE;
            }
            values.add(value);
        }
        CollectionValue nested = collectNestedValue(source, values);
        return expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.COLLECT
                ? flatten(nested) : nested;
    }

    private OclValue advancedIteratorValue(IteratorExpression expression, CollectionValue source,
            EvaluationContext context, List<OclDiagnostic> diagnostics) {
        if (expression.variables().size() != 1
                && !((expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.ANY
                || expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.IS_UNIQUE
                || expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.SORTED_BY
                || expression.kind() == de.useweb.backend.ocl.ast.IteratorKind.CLOSURE)
                && expression.variables().isEmpty())) {
            diagnostics.add(new OclDiagnostic(
                    de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.EVALUATION,
                    "INVALID_ITERATOR_ARITY", "ERROR",
                    "Iterator '" + expression.kind().oclName() + "' requires exactly one iterator variable.",
                    expression.sourceRange(), List.of("1 iterator variable"),
                    Integer.toString(expression.variables().size())));
            return OclInvalidValue.INSTANCE;
        }
        return switch (expression.kind()) {
            case ANY -> anyValue(expression, source, context, diagnostics);
            case IS_UNIQUE -> isUniqueValue(expression, source, context, diagnostics);
            case SORTED_BY -> sortedByValue(expression, source, context, diagnostics);
            case CLOSURE -> closureValue(expression, source, context, diagnostics);
            default -> unsupportedIteratorValue(expression, diagnostics);
        };
    }

    private OclValue iterateValue(IterateExpression expression, EvaluationContext context,
            List<OclDiagnostic> diagnostics) {
        OclValue source = evaluate(expression.source(), context, diagnostics);
        if (source instanceof OclInvalidValue || source instanceof OclVoidValue) {
            return OclInvalidValue.INSTANCE;
        }
        if (!(source instanceof CollectionValue collection)) {
            return evaluationError("Iterator source must evaluate to a collection.",
                    expression.source().sourceRange(), diagnostics);
        }
        if (exceedsBindingBudget(collection.values().size(), expression.iterators().size())) {
            return iterationLimitValue(expression.sourceRange(), MAX_ITERATOR_BINDINGS + 1, diagnostics);
        }
        OclValue accumulator = evaluate(expression.initializer(), context, diagnostics);
        if (accumulator instanceof OclInvalidValue || !diagnostics.isEmpty()) {
            return OclInvalidValue.INSTANCE;
        }
        return iterateBindings(expression, collection.values(), 0, context, new LinkedHashMap<>(), accumulator,
                diagnostics);
    }

    private OclValue iterateBindings(IterateExpression expression, List<OclValue> values, int iteratorIndex,
            EvaluationContext context, Map<String, OclValue> bindings, OclValue accumulator,
            List<OclDiagnostic> diagnostics) {
        if (iteratorIndex == expression.iterators().size()) {
            Map<String, OclValue> bodyBindings = new LinkedHashMap<>(bindings);
            bodyBindings.put(expression.accumulator().name(), accumulator);
            return evaluate(expression.body(), context.child(bodyBindings), diagnostics);
        }
        OclValue current = accumulator;
        String variableName = expression.iterators().get(iteratorIndex).name();
        for (OclValue value : values) {
            if (timeBudgetExceeded(expression.sourceRange(), diagnostics)) return OclInvalidValue.INSTANCE;
            bindings.put(variableName, value);
            current = iterateBindings(expression, values, iteratorIndex + 1, context, bindings, current, diagnostics);
            if (current instanceof OclInvalidValue || !diagnostics.isEmpty()) return OclInvalidValue.INSTANCE;
        }
        bindings.remove(variableName);
        return current;
    }

    private OclValue closureValue(IteratorExpression expression, CollectionValue source,
            EvaluationContext context, List<OclDiagnostic> diagnostics) {
        List<OclValue> result = new ArrayList<>();
        for (OclValue value : source.values()) {
            if (timeBudgetExceeded(expression.sourceRange(), diagnostics)) return OclInvalidValue.INSTANCE;
            if (!addClosureValue(result, value, expression, diagnostics)) {
                return OclInvalidValue.INSTANCE;
            }
        }
        int cursor = 0;
        String variableName = expression.variables().isEmpty() ? null : expression.variables().getFirst().name();
        while (cursor < result.size()) {
            if (timeBudgetExceeded(expression.sourceRange(), diagnostics)) return OclInvalidValue.INSTANCE;
            OclValue current = result.get(cursor++);
            EvaluationContext bodyContext = variableName == null
                    ? context.withImplicitSource(current) : context.child(java.util.Map.of(variableName, current));
            OclValue next = evaluate(expression.body(), bodyContext, diagnostics);
            if (next instanceof OclInvalidValue || next instanceof OclVoidValue || !diagnostics.isEmpty()) {
                return OclInvalidValue.INSTANCE;
            }
            if (next instanceof CollectionValue collection) {
                for (OclValue value : collection.values()) {
                    if (!addClosureValue(result, value, expression, diagnostics)) {
                        return OclInvalidValue.INSTANCE;
                    }
                }
            } else if (!addClosureValue(result, next, expression, diagnostics)) {
                return OclInvalidValue.INSTANCE;
            }
        }
        return source.collectionKind().ordered() ? new OrderedSetValue(result) : new SetValue(result);
    }

    private boolean addClosureValue(List<OclValue> result, OclValue value, IteratorExpression expression,
            List<OclDiagnostic> diagnostics) {
        if (containsValue(result, value)) {
            return true;
        }
        if (result.size() >= MAX_ITERATOR_BINDINGS) {
            iterationLimitValue(expression.sourceRange(), result.size() + 1L, diagnostics);
            return false;
        }
        result.add(value);
        return true;
    }

    private void addDistinct(List<OclValue> values, OclValue value) {
        if (!containsValue(values, value)) {
            values.add(value);
        }
    }

    private boolean containsValue(List<OclValue> values, OclValue target) {
        return values.stream().anyMatch(value -> OclValueEquality.equal(value, target));
    }

    private OclValue iterationLimitValue(de.useweb.backend.ocl.diagnostics.SourceRange range, long observed,
            List<OclDiagnostic> diagnostics) {
        diagnostics.add(new OclDiagnostic(
                de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.EVALUATION,
                "ITERATION_LIMIT_EXCEEDED", "ERROR",
                "Iterator evaluation exceeds the binding limit of " + MAX_ITERATOR_BINDINGS + ".",
                range, List.of("at most " + MAX_ITERATOR_BINDINGS + " bindings"), Long.toString(observed)));
        return OclInvalidValue.INSTANCE;
    }

    private boolean timeBudgetExceeded(de.useweb.backend.ocl.diagnostics.SourceRange range,
            List<OclDiagnostic> diagnostics) {
        EvaluationBudget budget = activeBudget.get();
        return budget != null && budget.timeExceeded(range, diagnostics);
    }

    private OclValue anyValue(IteratorExpression expression, CollectionValue source,
            EvaluationContext context, List<OclDiagnostic> diagnostics) {
        String variableName = expression.variables().isEmpty() ? null : expression.variables().getFirst().name();
        for (OclValue element : source.values()) {
            EvaluationContext bodyContext = variableName == null
                    ? context.withImplicitSource(element) : context.child(java.util.Map.of(variableName, element));
            OclValue predicate = evaluate(expression.body(), bodyContext, diagnostics);
            if (predicate instanceof BooleanValue booleanValue && booleanValue.value()) {
                return element;
            }
            if (!(predicate instanceof BooleanValue) && !(predicate instanceof OclVoidValue)
                    && !(predicate instanceof OclInvalidValue)) {
                return invalidIteratorBodyValue(expression, predicate, diagnostics);
            }
        }
        return OclInvalidValue.INSTANCE;
    }

    private OclValue isUniqueValue(IteratorExpression expression, CollectionValue source,
            EvaluationContext context, List<OclDiagnostic> diagnostics) {
        List<OclValue> keys = new ArrayList<>();
        for (OclValue element : source.values()) {
            EvaluationContext bodyContext;
            if (expression.variables().isEmpty()) {
                Map<String, OclValue> tupleParts = element instanceof TupleValue tuple ? tuple.parts() : Map.of();
                bodyContext = context.child(tupleParts).withImplicitSource(element);
            } else {
                bodyContext = context.child(Map.of(expression.variables().getFirst().name(), element));
            }
            OclValue key = evaluate(expression.body(), bodyContext, diagnostics);
            if (key instanceof OclInvalidValue || !diagnostics.isEmpty()) {
                return OclInvalidValue.INSTANCE;
            }
            keys.add(key);
        }
        for (int left = 0; left < keys.size(); left++) {
            for (int right = left + 1; right < keys.size(); right++) {
                if (OclValueEquality.equal(keys.get(left), keys.get(right))) {
                    return new BooleanValue(false);
                }
            }
        }
        return new BooleanValue(true);
    }

    private OclValue sortedByValue(IteratorExpression expression, CollectionValue source,
            EvaluationContext context, List<OclDiagnostic> diagnostics) {
        String variableName = expression.variables().isEmpty() ? null : expression.variables().getFirst().name();
        List<SortEntry> entries = new ArrayList<>();
        for (OclValue element : source.values()) {
            EvaluationContext bodyContext = variableName == null
                    ? context.withImplicitSource(element) : context.child(java.util.Map.of(variableName, element));
            OclValue key = evaluate(expression.body(), bodyContext, diagnostics);
            if (key instanceof OclInvalidValue || key instanceof OclVoidValue || !isSortableKeyValue(key)) {
                if (!(key instanceof OclInvalidValue) && !(key instanceof OclVoidValue)) {
                    diagnostics.add(new OclDiagnostic(
                            de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.EVALUATION,
                            "NON_COMPARABLE_SORT_KEY", "ERROR",
                            "Iterator body for 'sortedBy' must evaluate to Integer, Real, or String.",
                            expression.bodyRange(), List.of("Integer", "Real", "String"), key.typeName()));
                }
                return OclInvalidValue.INSTANCE;
            }
            entries.add(new SortEntry(element, key));
        }
        entries.sort((left, right) -> compareSortKeys(left.key(), right.key()));
        List<OclValue> values = entries.stream().map(SortEntry::element).toList();
        de.useweb.backend.ocl.collection.CollectionKind resultKind = source.collectionKind();
        if (expression.source() instanceof VariableExpression variable
                && context.findDeclaredVariableType(variable.name()).orElse("").startsWith("Collection(")) {
            resultKind = de.useweb.backend.ocl.collection.CollectionKind.COLLECTION;
        }
        return switch (resultKind) {
            case SET, ORDERED_SET -> new OrderedSetValue(values);
            case BAG, SEQUENCE, COLLECTION -> new SequenceValue(values);
        };
    }

    private OclValue invalidIteratorBodyValue(IteratorExpression expression, OclValue value,
            List<OclDiagnostic> diagnostics) {
        diagnostics.add(new OclDiagnostic(
                de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.EVALUATION,
                "INVALID_ITERATOR_BODY_TYPE", "ERROR",
                "Iterator body must evaluate to Boolean.", expression.bodyRange(), List.of("Boolean"),
                value.typeName()));
        return OclInvalidValue.INSTANCE;
    }

    private boolean isSortableKeyValue(OclValue value) {
        return value instanceof IntegerValue || value instanceof RealValue || value instanceof StringValue;
    }

    private int compareSortKeys(OclValue left, OclValue right) {
        if ((left instanceof IntegerValue || left instanceof RealValue)
                && (right instanceof IntegerValue || right instanceof RealValue)) {
            return Double.compare(number(left), number(right));
        }
        if (left instanceof StringValue leftString && right instanceof StringValue rightString) {
            return leftString.value().compareTo(rightString.value());
        }
        throw new IllegalStateException("Incompatible sortedBy key values passed type checking.");
    }

    private record SortEntry(OclValue element, OclValue key) {
    }

    private boolean evaluateBindings(
            IteratorExpression expression,
            List<OclValue> sourceValues,
            int variableIndex,
            EvaluationContext context,
            java.util.Map<String, OclValue> bindings,
            QuantifierState state,
            List<OclDiagnostic> diagnostics) {
        if (variableIndex == expression.variables().size()) {
            OclValue body = evaluate(expression.body(), context.child(bindings), diagnostics);
            return state.accept(body, expression, diagnostics);
        }
        String variableName = expression.variables().get(variableIndex).name();
        for (OclValue value : sourceValues) {
            bindings.put(variableName, value);
            if (evaluateBindings(expression, sourceValues, variableIndex + 1, context, bindings, state, diagnostics)) {
                bindings.remove(variableName);
                return true;
            }
        }
        bindings.remove(variableName);
        return false;
    }

    private boolean exceedsBindingBudget(int sourceSize, int variableCount) {
        long bindings = 1;
        for (int index = 0; index < variableCount; index++) {
            if (sourceSize == 0) {
                return false;
            }
            if (bindings > MAX_ITERATOR_BINDINGS / sourceSize) {
                return true;
            }
            bindings *= sourceSize;
        }
        return bindings > MAX_ITERATOR_BINDINGS;
    }

    private OclValue unsupportedIteratorValue(IteratorExpression expression, List<OclDiagnostic> diagnostics) {
        return evaluationError(
                "Iterator semantics for '" + expression.kind().oclName() + "' are not implemented yet.",
                expression.operationRange(), diagnostics);
    }

    private static final class QuantifierState {
        private final de.useweb.backend.ocl.ast.IteratorKind kind;
        private boolean decisive;
        private int trueCount;
        private boolean sawInvalid;
        private boolean sawVoid;

        private QuantifierState(de.useweb.backend.ocl.ast.IteratorKind kind) {
            this.kind = kind;
        }

        private boolean accept(OclValue value, IteratorExpression expression, List<OclDiagnostic> diagnostics) {
            if (value instanceof BooleanValue booleanValue) {
                if (kind == de.useweb.backend.ocl.ast.IteratorKind.ONE) {
                    if (booleanValue.value()) decisive = ++trueCount > 1;
                } else {
                    decisive = kind == de.useweb.backend.ocl.ast.IteratorKind.FOR_ALL
                            ? !booleanValue.value() : booleanValue.value();
                }
                return decisive;
            }
            if (value instanceof OclInvalidValue) {
                sawInvalid = true;
                return false;
            }
            if (value instanceof OclVoidValue) {
                sawVoid = true;
                return false;
            }
            diagnostics.add(new OclDiagnostic(
                    de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.EVALUATION,
                    "INVALID_ITERATOR_BODY_TYPE", "ERROR",
                    "Iterator body must evaluate to Boolean.", expression.bodyRange(), List.of("Boolean"),
                    value.typeName()));
            sawInvalid = true;
            return false;
        }

        private OclValue result() {
            if (decisive) {
                if (kind == de.useweb.backend.ocl.ast.IteratorKind.ONE) return new BooleanValue(false);
                return new BooleanValue(kind == de.useweb.backend.ocl.ast.IteratorKind.EXISTS);
            }
            if (sawInvalid) {
                return OclInvalidValue.INSTANCE;
            }
            if (sawVoid) {
                return OclVoidValue.INSTANCE;
            }
            if (kind == de.useweb.backend.ocl.ast.IteratorKind.ONE) return new BooleanValue(trueCount == 1);
            return new BooleanValue(kind == de.useweb.backend.ocl.ast.IteratorKind.FOR_ALL);
        }
    }

    private final class EvaluationBudget {
        private final long startedAt;
        private final long allowedNanos;
        private int depth;
        private boolean exceeded;

        private EvaluationBudget(long startedAt, long allowedNanos) {
            this.startedAt = startedAt;
            this.allowedNanos = allowedNanos;
        }

        private boolean enter(OclAstNode ast, List<OclDiagnostic> diagnostics) {
            if (exceeded) return false;
            if (depth >= maxEvaluationDepth) {
                exceeded = true;
                diagnostics.add(new OclDiagnostic(
                        de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.EVALUATION,
                        "EVALUATION_DEPTH_LIMIT_EXCEEDED", "ERROR",
                        "OCL evaluation exceeds the depth limit of " + maxEvaluationDepth + ".",
                        ast.sourceRange(), List.of("depth at most " + maxEvaluationDepth),
                        Integer.toString(depth + 1)));
                return false;
            }
            if (timeExceeded(ast.sourceRange(), diagnostics)) return false;
            depth++;
            return true;
        }

        private boolean timeExceeded(de.useweb.backend.ocl.diagnostics.SourceRange range,
                List<OclDiagnostic> diagnostics) {
            if (exceeded) return true;
            if (nanoTime.getAsLong() - startedAt < allowedNanos) return false;
            exceeded = true;
            diagnostics.add(new OclDiagnostic(
                    de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.EVALUATION,
                    "EVALUATION_TIME_LIMIT_EXCEEDED", "ERROR",
                    "OCL evaluation exceeded its time budget.", range,
                    List.of("at most " + OclComplianceProfile.MAX_EVALUATION_MILLIS + " ms"),
                    "time budget exceeded"));
            return true;
        }

        private void exit() {
            if (depth > 0) depth--;
        }
    }

    private OclValue literalValue(LiteralExpression literal) {
        return switch (literal.literalType()) {
            case STRING -> new StringValue((String) literal.value());
            case INTEGER -> new IntegerValue((Integer) literal.value());
            case REAL -> new RealValue((Double) literal.value());
            case BOOLEAN -> new BooleanValue((Boolean) literal.value());
            case UNLIMITED_NATURAL -> UnlimitedNaturalValue.UNLIMITED;
            case NULL -> OclVoidValue.INSTANCE;
            case INVALID -> OclInvalidValue.INSTANCE;
        };
    }

    private OclValue propertyAccessValue(PropertyAccessExpression expression, EvaluationContext context, List<OclDiagnostic> diagnostics) {
        OclValue receiver = evaluate(expression.receiver(), context, diagnostics);
        if (!diagnostics.isEmpty()) {
            return OclInvalidValue.INSTANCE;
        }
        if (receiver instanceof OclInvalidValue || receiver instanceof OclVoidValue) {
            return OclInvalidValue.INSTANCE;
        }
        if (receiver instanceof TupleValue tuple) {
            OclValue part = tuple.parts().get(expression.propertyName());
            if (part != null) {
                return part;
            }
            diagnostics.add(OclDiagnostic.evaluationError(
                    "Unknown tuple part '" + expression.propertyName() + "'.", expression.propertyRange()));
            return OclInvalidValue.INSTANCE;
        }
        if (receiver instanceof DataTypeValue dataType) {
            OclValue property = dataType.properties().get(expression.propertyName());
            if (property != null) return property;
            diagnostics.add(OclDiagnostic.evaluationError(
                    "Unknown DataType property '" + expression.propertyName() + "'.", expression.propertyRange()));
            return OclInvalidValue.INSTANCE;
        }
        if (receiver instanceof CollectionValue collection) {
            return implicitCollectPropertyValue(collection, expression, context, diagnostics);
        }
        OclCallResolutionResult shorthand = callResolver(context)
                .resolveProperty(OclRuntimeType.of(receiver, context.umlModel()), expression.propertyName());
        if (shorthand.status() == OclCallResolutionResult.Status.RESOLVED
                && shorthand.resolution().kind() == OclCallKind.STANDARD_LIBRARY) {
            return standardOperation(receiver, expression.propertyName(), List.of(), context,
                    expression.sourceRange(), diagnostics).orElse(OclInvalidValue.INSTANCE);
        }
        if (!(receiver instanceof ObjectValue objectValue)) {
            diagnostics.add(OclDiagnostic.evaluationError(
                    "Property access requires an object-valued receiver.",
                    expression.sourceRange()));
            return OclInvalidValue.INSTANCE;
        }

        return objectPropertyValue(objectValue, expression, context, diagnostics);
    }

    private OclValue qualifiedPropertyAccessValue(QualifiedPropertyAccessExpression expression,
            EvaluationContext context, List<OclDiagnostic> diagnostics) {
        OclValue receiver = evaluate(expression.receiver(), context, diagnostics);
        if (!(receiver instanceof ObjectValue objectValue) || !diagnostics.isEmpty()) {
            diagnostics.add(OclDiagnostic.evaluationError(
                    "Qualified navigation requires an object-valued receiver.", expression.sourceRange()));
            return OclInvalidValue.INSTANCE;
        }
        Optional<UmlClass> receiverClass = context.umlModel().findClass(objectValue.object().classId());
        if (receiverClass.isEmpty()) {
            diagnostics.add(OclDiagnostic.unknownClass(objectValue.object().classId().value(), expression.sourceRange()));
            return OclInvalidValue.INSTANCE;
        }
        List<NavigationTarget> targets = findNavigationTargets(context, receiverClass.get(), expression.propertyName());
        if (targets.size() != 1) {
            diagnostics.add(OclDiagnostic.evaluationError(
                    targets.isEmpty() ? "Unknown qualified association role '" + expression.propertyName() + "'."
                            : "Qualified association role '" + expression.propertyName() + "' is ambiguous.",
                    expression.propertyRange()));
            return OclInvalidValue.INSTANCE;
        }
        NavigationTarget target = targets.getFirst();
        if (target.targetEnd().qualifiers().size() != expression.qualifierArguments().size()) {
            diagnostics.add(OclDiagnostic.evaluationError(
                    "Wrong number of qualifier values for role '" + expression.propertyName() + "'.",
                    expression.sourceRange()));
            return OclInvalidValue.INSTANCE;
        }
        List<OclValue> qualifierValues = expression.qualifierArguments().stream()
                .map(argument -> evaluate(argument, context, diagnostics))
                .toList();
        if (!diagnostics.isEmpty()) {
            return OclInvalidValue.INSTANCE;
        }
        PropertyAccessExpression plainExpression = new PropertyAccessExpression(expression.receiver(),
                expression.propertyName(), expression.propertyRange(), expression.sourceRange());
        return qualifiedNavigationValue(context, objectValue.object(), target, qualifierValues, plainExpression, diagnostics);
    }

    private OclValue qualifiedNavigationValue(EvaluationContext context, ObjectInstance sourceObject,
            NavigationTarget target, List<OclValue> qualifierValues, PropertyAccessExpression expression,
            List<OclDiagnostic> diagnostics) {
        List<OclValue> values = new ArrayList<>();
        for (ObjectLink link : context.objectModel().links()) {
            if (!link.associationId().equals(target.association().id()) || link.ends().stream().noneMatch(end ->
                    end.associationEndId().equals(target.sourceEnd().id()) && end.objectId().equals(sourceObject.id()))) {
                continue;
            }
            var targetValue = link.ends().stream()
                    .filter(end -> end.associationEndId().equals(target.targetEnd().id())).findFirst();
            if (targetValue.isEmpty() || targetValue.get().qualifierValues().size() != qualifierValues.size()) {
                continue;
            }
            boolean matches = true;
            for (int index = 0; index < qualifierValues.size(); index++) {
                var definition = target.targetEnd().qualifiers().get(index);
                var stored = targetValue.get().qualifierValues().stream()
                        .filter(value -> value.qualifierId().equals(definition.id())).findFirst();
                if (stored.isEmpty() || !slotValue(stored.get().value(), expression, context, diagnostics)
                        .equals(qualifierValues.get(index))) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                context.objectModel().findObject(targetValue.get().objectId())
                        .ifPresent(object -> values.add(new ObjectValue(object)));
            }
        }
        if (!diagnostics.isEmpty()) {
            return OclInvalidValue.INSTANCE;
        }
        return isCollectionMultiplicity(target.targetEnd().multiplicity())
                ? navigationCollectionValue(target.targetEnd(), values)
                : singleNavigationValue(sourceObject, target, values, expression, diagnostics);
    }

    private OclValue tupleValue(TupleExpression expression, EvaluationContext context,
            List<OclDiagnostic> diagnostics) {
        Map<String, OclValue> parts = new LinkedHashMap<>();
        for (var part : expression.parts()) {
            if (parts.containsKey(part.name())) {
                diagnostics.add(OclDiagnostic.evaluationError(
                        "Duplicate tuple part '" + part.name() + "'.", part.nameRange()));
                return OclInvalidValue.INSTANCE;
            }
            OclValue value = evaluate(part.value(), context, diagnostics);
            if (!diagnostics.isEmpty()) {
                return OclInvalidValue.INSTANCE;
            }
            parts.put(part.name(), value);
        }
        return new TupleValue(parts);
    }

    private OclValue typeArgumentCallValue(TypeArgumentCallExpression expression, EvaluationContext context,
            List<OclDiagnostic> diagnostics) {
        OclValue receiver = evaluate(expression.receiver(), context, diagnostics);
        if (receiver instanceof OclInvalidValue) {
            return OclInvalidValue.INSTANCE;
        }
        if (receiver instanceof CollectionValue collection
                && expression.navigationOperator() == de.useweb.backend.ocl.ast.CallNavigationOperator.DOT
                && (expression.operationName().equals("oclIsTypeOf")
                || expression.operationName().equals("oclIsKindOf")
                || expression.operationName().equals("oclAsType"))) {
            List<OclValue> values = collection.values().stream()
                    .map(value -> typeArgumentValue(value, expression, context)).toList();
            return flatten(collectNestedValue(collection, values));
        }
        return typeArgumentValue(receiver, expression, context);
    }

    private OclValue typeArgumentValue(OclValue receiver, TypeArgumentCallExpression expression,
            EvaluationContext context) {
        boolean kindOf = valueConformsToType(receiver, expression.typeName(), context);
        boolean exactType = valueHasExactType(receiver, expression.typeName(), context);
        return switch (expression.operationName()) {
            case "oclIsKindOf" -> new BooleanValue(kindOf);
            case "oclIsTypeOf" -> new BooleanValue(exactType);
            case "oclAsType" -> kindOf || receiver instanceof OclVoidValue
                    ? receiver
                    : OclInvalidValue.INSTANCE;
            case "selectByKind", "selectByType" -> receiver instanceof CollectionValue collection
                    ? collectionValue(collection, collection.values().stream()
                            .filter(value -> expression.operationName().equals("selectByKind")
                                    ? valueConformsToType(value, expression.typeName(), context)
                                    : valueHasExactType(value, expression.typeName(), context))
                            .toList())
                    : OclInvalidValue.INSTANCE;
            default -> OclInvalidValue.INSTANCE;
        };
    }

    private boolean valueHasExactType(OclValue value, String typeName, EvaluationContext context) {
        OclType target = resolveRuntimeTypeName(typeName, context);
        if (!target.isInvalid()) return OclRuntimeType.of(value, context.umlModel()).sameTypeAs(target);
        if (value instanceof ObjectValue object) {
            return context.umlModel().findClass(object.object().classId())
                    .map(type -> type.name().equals(typeName)).orElse(false);
        }
        return value.typeName().equals(typeName);
    }

    private boolean valueConformsToType(OclValue value, String typeName, EvaluationContext context) {
        OclType resolvedTarget = resolveRuntimeTypeName(typeName, context);
        if (!resolvedTarget.isInvalid()) return OclRuntimeType.of(value, context.umlModel()).conformsTo(resolvedTarget);
        if (value instanceof ObjectValue object) {
            var target = context.umlModel().findClassByName(typeName);
            return target.isPresent() && context.umlModel().typeConformanceOrder(object.object().classId()).stream()
                    .anyMatch(classId -> classId.equals(target.get().id()));
        }
        if (typeName.equals("Real") && value instanceof IntegerValue) {
            return true;
        }
        return value.typeName().equals(typeName);
    }

    private OclType resolveRuntimeTypeName(String typeName, EvaluationContext context) {
        int leftParen = typeName.indexOf('(');
        if (leftParen > 0 && typeName.endsWith(")")) {
            de.useweb.backend.ocl.collection.CollectionKind kind = switch (typeName.substring(0, leftParen)) {
                case "Collection" -> de.useweb.backend.ocl.collection.CollectionKind.COLLECTION;
                case "Set" -> de.useweb.backend.ocl.collection.CollectionKind.SET;
                case "Bag" -> de.useweb.backend.ocl.collection.CollectionKind.BAG;
                case "Sequence" -> de.useweb.backend.ocl.collection.CollectionKind.SEQUENCE;
                case "OrderedSet" -> de.useweb.backend.ocl.collection.CollectionKind.ORDERED_SET;
                default -> null;
            };
            if (kind == null) return OclType.INVALID;
            OclType elementType = resolveRuntimeTypeName(
                    typeName.substring(leftParen + 1, typeName.length() - 1).trim(), context);
            return elementType.isInvalid() ? OclType.INVALID : OclType.collectionOf(kind, elementType);
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
            default -> {
                var contextClass = context.umlModel().findClass(context.self().classId()).orElseThrow();
                TypeEnvironment environment = new TypeEnvironment(context.umlModel(), contextClass);
                yield environment.findClassByName(typeName).map(environment::classType)
                        .orElseGet(() -> environment.findEnumerationByName(typeName)
                                .map(enumeration -> OclType.enumerationType(enumeration.id(), enumeration.name()))
                                .orElseGet(() -> environment.findDataTypeByName(typeName)
                                        .map(dataType -> OclType.dataType(dataType.id(), dataType.name()))
                                        .orElse(OclType.INVALID)));
            }
        };
    }

    private OclValue implicitCollectPropertyValue(CollectionValue source, PropertyAccessExpression expression,
            EvaluationContext context, List<OclDiagnostic> diagnostics) {
        List<OclValue> values = new ArrayList<>();
        for (OclValue element : source.values()) {
            OclCallResolutionResult shorthand = callResolver(context).resolveProperty(
                    OclRuntimeType.of(element, context.umlModel()), expression.propertyName());
            OclValue value;
            if (shorthand.status() == OclCallResolutionResult.Status.RESOLVED
                    && shorthand.resolution().kind() == OclCallKind.STANDARD_LIBRARY) {
                value = standardOperation(element, expression.propertyName(), List.of(), context,
                        expression.sourceRange(), diagnostics).orElse(OclInvalidValue.INSTANCE);
            } else if (element instanceof ObjectValue objectValue) {
                value = objectPropertyValue(objectValue, expression, context, diagnostics);
            } else {
                return evaluationError("Implicit collect property access cannot resolve property '"
                                + expression.propertyName() + "' on element type '" + element.typeName() + "'.",
                        expression.propertyRange(), diagnostics);
            }
            if (value instanceof OclInvalidValue || !diagnostics.isEmpty()) {
                return OclInvalidValue.INSTANCE;
            }
            values.add(value);
        }
        return flatten(collectNestedValue(source, values));
    }

    private OclValue objectPropertyValue(ObjectValue objectValue, PropertyAccessExpression expression,
            EvaluationContext context, List<OclDiagnostic> diagnostics) {

        Optional<UmlClass> receiverClass = context.umlModel().findClass(objectValue.object().classId());
        if (receiverClass.isEmpty()) {
            diagnostics.add(OclDiagnostic.evaluationError(
                    "Object '" + objectValue.object().name() + "' references unknown class '" + objectValue.object().classId().value() + "'.",
                    expression.sourceRange()));
            return OclInvalidValue.INSTANCE;
        }

        OclCallResolutionResult resolved = callResolver(context).resolveProperty(
                OclRuntimeType.of(objectValue, context.umlModel()), expression.propertyName());
        if (resolved.status() == OclCallResolutionResult.Status.INACCESSIBLE
                || resolved.status() == OclCallResolutionResult.Status.AMBIGUOUS) {
            diagnostics.add(OclDiagnostic.evaluationError("CALL_RESOLUTION_ERROR", resolved.message(),
                    expression.propertyRange()));
            return OclInvalidValue.INSTANCE;
        }
        if (resolved.status() == OclCallResolutionResult.Status.RESOLVED
                && resolved.resolution().kind() == OclCallKind.UML_ATTRIBUTE) {
            Optional<UmlAttribute> attribute = context.umlModel().findAttribute(
                    new de.useweb.backend.domain.uml.UmlAttributeId(resolved.resolution().featureId()));
            if (attribute.isEmpty()) {
                diagnostics.add(OclDiagnostic.evaluationError("RESOLVED_FEATURE_MISSING",
                        "Resolved attribute no longer exists.", expression.propertyRange()));
                return OclInvalidValue.INSTANCE;
            }
            if (context.definitionRuntime() != null) {
                try {
                    Optional<OclValue> derived = context.definitionRuntime()
                            .property(objectValue, expression.propertyName(), context);
                    if (derived.isPresent()) return derived.get();
                } catch (de.useweb.backend.ocl.definition.OclDefinitionEvaluationException exception) {
                    diagnostics.add(OclDiagnostic.evaluationError(exception.code(), exception.getMessage(),
                            expression.sourceRange()));
                    return OclInvalidValue.INSTANCE;
                }
            }
            return attributeValue(objectValue.object(), attribute.get(), expression, context, diagnostics);
        }
        if (resolved.status() == OclCallResolutionResult.Status.RESOLVED
                && resolved.resolution().kind() == OclCallKind.DEFINITION_PROPERTY
                && context.definitionRuntime() != null) {
            try {
                Optional<OclValue> defined = context.definitionRuntime()
                        .property(new OclDefinitionId(resolved.resolution().featureId()), objectValue, context);
                if (defined.isPresent()) return defined.get();
            } catch (de.useweb.backend.ocl.definition.OclDefinitionEvaluationException exception) {
                diagnostics.add(OclDiagnostic.evaluationError(exception.code(), exception.getMessage(),
                        expression.sourceRange()));
                return OclInvalidValue.INSTANCE;
            }
        }

        Optional<OclValue> associationClassNavigation = associationClassNavigationValue(
                context, receiverClass.get(), objectValue.object(), expression.propertyName());
        if (associationClassNavigation.isPresent()) {
            return associationClassNavigation.get();
        }

        List<NavigationTarget> navigationTargets = findNavigationTargets(context, receiverClass.get(), expression.propertyName());
        if (navigationTargets.size() > 1) {
            diagnostics.add(OclDiagnostic.evaluationError(
                    "Association-end navigation '" + expression.propertyName() + "' is ambiguous.",
                    expression.propertyRange()));
            return OclInvalidValue.INSTANCE;
        }
        if (!navigationTargets.isEmpty()) {
            return navigationValue(context, objectValue.object(), navigationTargets.getFirst(), expression, diagnostics);
        }

        diagnostics.add(OclDiagnostic.evaluationError(
                "Property '" + expression.propertyName() + "' cannot be evaluated for object '" + objectValue.object().name() + "'.",
                expression.sourceRange()));
        return OclInvalidValue.INSTANCE;
    }

    private Optional<OclValue> associationClassNavigationValue(EvaluationContext context, UmlClass receiverClass,
            ObjectInstance receiver, String propertyName) {
        List<UmlAssociation> associations = context.umlModel().associations().stream()
                .filter(association -> association.associationClassId() != null)
                .filter(association -> association.ends().stream().anyMatch(end ->
                        context.umlModel().isSubtypeOf(receiverClass.id(), end.classId())))
                .filter(association -> context.umlModel().findClass(association.associationClassId())
                        .map(candidate -> candidate.name().equals(propertyName)
                                || lowerCamel(candidate.name()).equals(propertyName)).orElse(false))
                .toList();
        if (associations.size() != 1) return Optional.empty();
        UmlAssociation association = associations.getFirst();
        List<OclValue> linkObjects = context.objectModel().links().stream()
                .filter(link -> link.associationId().equals(association.id()))
                .filter(link -> link.ends().stream().anyMatch(end -> end.objectId().equals(receiver.id())))
                .map(ObjectLink::associationClassObjectId).filter(java.util.Objects::nonNull)
                .map(context.objectModel()::findObject).flatMap(Optional::stream)
                .map(object -> (OclValue) new ObjectValue(object)).toList();
        return Optional.of(new SetValue(linkObjects));
    }

    private String lowerCamel(String value) {
        return value.isEmpty() ? value : Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private OclValue attributeValue(
            ObjectInstance object,
            UmlAttribute attribute,
            PropertyAccessExpression expression,
            EvaluationContext context,
            List<OclDiagnostic> diagnostics) {
        Optional<Slot> slot = object.findSlot(attribute.id());
        if (slot.isEmpty()) {
            diagnostics.add(OclDiagnostic.evaluationError(
                    "Slot value for attribute '" + attribute.name() + "' is missing on object '" + object.name() + "'.",
                    expression.sourceRange()));
            return OclInvalidValue.INSTANCE;
        }
        return slotValue(slot.get().value(), expression, context, diagnostics);
    }

    private OclValue slotValue(SlotValue slotValue, PropertyAccessExpression expression,
            EvaluationContext context, List<OclDiagnostic> diagnostics) {
        if (slotValue.value() == null) {
            return OclVoidValue.INSTANCE;
        }
        Optional<PrimitiveType> primitiveType = slotValue.valueType().primitiveType();
        if (primitiveType.isEmpty()) {
            var enumeration = context.umlModel().findClass(context.self().classId())
                    .flatMap(contextClass -> context.umlModel().resolveEnumeration(slotValue.valueType().name(), contextClass));
            if (enumeration.isPresent() && slotValue.value() instanceof String literal
                    && enumeration.get().containsLiteral(literal)) {
                return new EnumValue(enumeration.get().id(), enumeration.get().name(), literal);
            }
            var dataType = context.umlModel().findClass(context.self().classId())
                    .flatMap(contextClass -> context.umlModel().resolveDataType(slotValue.valueType().name(), contextClass));
            if (dataType.isPresent() && slotValue.value() instanceof Map<?, ?> values) {
                Map<String, OclValue> properties = new LinkedHashMap<>();
                for (var property : dataType.get().properties()) {
                    if (!values.containsKey(property.name())) {
                        diagnostics.add(OclDiagnostic.evaluationError(
                                "Missing DataType property '" + property.name() + "'.", expression.sourceRange()));
                        return OclInvalidValue.INSTANCE;
                    }
                    OclValue propertyValue = rawValue(values.get(property.name()), property.type(), context);
                    if (propertyValue instanceof OclInvalidValue) {
                        diagnostics.add(OclDiagnostic.evaluationError(
                                "Invalid value for DataType property '" + property.name() + "'.", expression.sourceRange()));
                        return propertyValue;
                    }
                    properties.put(property.name(), propertyValue);
                }
                return new DataTypeValue(dataType.get().id(), dataType.get().qualifiedName(context.umlModel()), properties);
            }
            var objectType = context.umlModel().findClassByName(slotValue.valueType().name());
            if (objectType.isPresent() && slotValue.value() instanceof String objectReference) {
                return context.objectModel().objects().stream()
                        .filter(candidate -> candidate.name().equals(objectReference)
                                || candidate.id().value().equals(objectReference))
                        .filter(candidate -> context.umlModel().isSubtypeOf(candidate.classId(), objectType.get().id()))
                        .findFirst()
                        .<OclValue>map(ObjectValue::new)
                        .orElseGet(() -> {
                            diagnostics.add(OclDiagnostic.evaluationError(
                                    "Object-valued slot references an unknown or incompatible object '"
                                            + objectReference + "'.",
                                    expression.sourceRange()));
                            return OclInvalidValue.INSTANCE;
                        });
            }
            diagnostics.add(OclDiagnostic.evaluationError(
                    "Slot value type '" + slotValue.valueType().name() + "' or its value is not supported.",
                    expression.sourceRange()));
            return OclInvalidValue.INSTANCE;
        }
        try {
            return switch (primitiveType.get()) {
                case STRING -> new StringValue((String) slotValue.value());
                case INTEGER -> new IntegerValue((Integer) slotValue.value());
                case REAL -> new RealValue((Double) slotValue.value());
                case BOOLEAN -> new BooleanValue((Boolean) slotValue.value());
            };
        } catch (ClassCastException exception) {
            diagnostics.add(OclDiagnostic.evaluationError(
                    "Slot value does not match declared type '" + slotValue.valueType().name() + "'.",
                    expression.sourceRange()));
            return OclInvalidValue.INSTANCE;
        }
    }

    private OclValue rawValue(Object value, de.useweb.backend.domain.uml.UmlType type, EvaluationContext context) {
        if (value == null) return OclVoidValue.INSTANCE;
        return type.primitiveType().map(primitive -> switch (primitive) {
            case STRING -> value instanceof String text ? new StringValue(text) : OclInvalidValue.INSTANCE;
            case INTEGER -> value instanceof Number number ? new IntegerValue(number.intValue()) : OclInvalidValue.INSTANCE;
            case REAL -> value instanceof Number number ? new RealValue(number.doubleValue()) : OclInvalidValue.INSTANCE;
            case BOOLEAN -> value instanceof Boolean bool ? new BooleanValue(bool) : OclInvalidValue.INSTANCE;
        }).orElseGet(() -> {
            var enumeration = context.umlModel().findEnumerationByName(type.name());
            if (enumeration.isPresent() && value instanceof String literal
                    && enumeration.get().containsLiteral(literal)) {
                return new EnumValue(enumeration.get().id(), enumeration.get().qualifiedName(context.umlModel()), literal);
            }
            var dataType = context.umlModel().findDataTypeByName(type.name());
            if (dataType.isPresent() && value instanceof Map<?, ?> structured) {
                Map<String, OclValue> properties = new LinkedHashMap<>();
                for (var property : dataType.get().properties()) {
                    if (!structured.containsKey(property.name())) return OclInvalidValue.INSTANCE;
                    OclValue nested = rawValue(structured.get(property.name()), property.type(), context);
                    if (nested instanceof OclInvalidValue) return nested;
                    properties.put(property.name(), nested);
                }
                return new DataTypeValue(dataType.get().id(), dataType.get().qualifiedName(context.umlModel()), properties);
            }
            return OclInvalidValue.INSTANCE;
        });
    }

    private List<NavigationTarget> findNavigationTargets(EvaluationContext context, UmlClass receiverClass, String roleName) {
        return context.umlModel().typeConformanceOrder(receiverClass.id()).stream()
                .map(context.umlModel()::findClass).flatMap(Optional::stream)
                .flatMap(sourceClass -> context.umlModel().associations().stream()
                        .map(association -> navigationTarget(association, sourceClass, roleName)))
                .flatMap(Optional::stream)
                .distinct()
                .toList();
    }

    private Optional<NavigationTarget> navigationTarget(UmlAssociation association, UmlClass receiverClass, String roleName) {
        return association.ends().stream()
                .filter(target -> de.useweb.backend.ocl.profile.OclOptionalCompliancePolicy.mayNavigate(target)
                        && target.roleName().equals(roleName))
                .flatMap(target -> association.ends().stream()
                        .filter(source -> !source.id().equals(target.id())
                                && source.classId().equals(receiverClass.id()))
                        .map(source -> new NavigationTarget(association, source, target)))
                .findFirst();
    }

    private OclValue navigationValue(
            EvaluationContext context,
            ObjectInstance sourceObject,
            NavigationTarget navigationTarget,
            PropertyAccessExpression expression,
            List<OclDiagnostic> diagnostics) {
        List<OclValue> targets = new ArrayList<>();
        for (ObjectLink link : context.objectModel().links()) {
            if (!link.associationId().equals(navigationTarget.association().id())) {
                continue;
            }
            boolean sourceParticipates = link.ends().stream()
                    .anyMatch(end -> end.associationEndId().equals(navigationTarget.sourceEnd().id())
                            && end.objectId().equals(sourceObject.id()));
            if (!sourceParticipates) {
                continue;
            }
            link.ends().stream()
                    .filter(end -> end.associationEndId().equals(navigationTarget.targetEnd().id()))
                    .findFirst()
                    .ifPresent(targetEnd -> context.objectModel().findObject(targetEnd.objectId())
                            .ifPresentOrElse(
                                    targetObject -> targets.add(new ObjectValue(targetObject)),
                                    () -> diagnostics.add(OclDiagnostic.evaluationError(
                                            "Object link '" + link.id().value() + "' references missing target object '" + targetEnd.objectId().value() + "'.",
                                            expression.sourceRange()))));
        }
        if (!diagnostics.isEmpty()) {
            return OclInvalidValue.INSTANCE;
        }
        return isCollectionMultiplicity(navigationTarget.targetEnd().multiplicity())
                ? navigationCollectionValue(navigationTarget.targetEnd(), targets)
                : singleNavigationValue(sourceObject, navigationTarget, targets, expression, diagnostics);
    }

    private OclValue navigationCollectionValue(UmlAssociationEnd end, List<OclValue> targets) {
        if (end.ordered()) return end.unique() ? new OrderedSetValue(targets) : new SequenceValue(targets);
        return end.unique() ? new SetValue(targets) : new BagValue(targets);
    }

    private OclValue singleNavigationValue(
            ObjectInstance sourceObject,
            NavigationTarget navigationTarget,
            List<OclValue> targets,
            PropertyAccessExpression expression,
            List<OclDiagnostic> diagnostics) {
        if (targets.isEmpty() && navigationTarget.targetEnd().multiplicity().lower() == 0) {
            return OclVoidValue.INSTANCE;
        }
        if (targets.size() != 1) {
            diagnostics.add(OclDiagnostic.evaluationError(
                    "Single-valued navigation '" + navigationTarget.targetEnd().roleName() + "' from object '" + sourceObject.name()
                            + "' expected exactly one target but found " + targets.size() + ".",
                    expression.sourceRange()));
            return OclInvalidValue.INSTANCE;
        }
        return targets.getFirst();
    }

    private boolean isCollectionMultiplicity(Multiplicity multiplicity) {
        return multiplicity.unbounded() || multiplicity.upper() == null || multiplicity.upper() > 1;
    }

    private OclValue collectionLiteralValue(CollectionLiteralExpression expression, EvaluationContext context, List<OclDiagnostic> diagnostics) {
        List<OclValue> values = new ArrayList<>();
        for (var part : expression.parts()) {
            if (part instanceof CollectionItem item) {
                values.add(evaluate(item.expression(), context, diagnostics));
            } else if (part instanceof CollectionRangeItem rangeItem) {
                OclValue first = evaluate(rangeItem.first(), context, diagnostics);
                OclValue last = evaluate(rangeItem.last(), context, diagnostics);
                if (!(first instanceof IntegerValue firstInteger) || !(last instanceof IntegerValue lastInteger)) {
                    return OclInvalidValue.INSTANCE;
                }
                long rangeSize = lastInteger.value() < firstInteger.value()
                        ? 0L : (long) lastInteger.value() - firstInteger.value() + 1L;
                if ((long) values.size() + rangeSize > MAX_RESULT_ELEMENTS) {
                    return resultLimitValue(expression.sourceRange(), (long) values.size() + rangeSize, diagnostics);
                }
                for (int value = firstInteger.value(); value <= lastInteger.value(); value++) {
                    values.add(new IntegerValue(value));
                    if (value == Integer.MAX_VALUE) break;
                }
            }
            if (!diagnostics.isEmpty()) return OclInvalidValue.INSTANCE;
        }
        return switch (expression.collectionKind()) {
            case SET -> new SetValue(values);
            case BAG -> new BagValue(values);
            case SEQUENCE -> new SequenceValue(values);
            case ORDERED_SET -> new OrderedSetValue(values);
            case COLLECTION -> OclInvalidValue.INSTANCE;
        };
    }

    private OclValue unaryValue(UnaryExpression expression, EvaluationContext context, List<OclDiagnostic> diagnostics) {
        OclValue value = evaluate(expression.expression(), context, diagnostics);
        if (!diagnostics.isEmpty()) {
            return OclInvalidValue.INSTANCE;
        }
        return switch (expression.operator()) {
            case NOT -> OclBooleanLogic.not(value);
            case NEGATE -> OclPrimitiveLibrary.negate(value);
        };
    }

    private OclValue binaryValue(BinaryExpression expression, EvaluationContext context, List<OclDiagnostic> diagnostics) {
        if (switch (expression.operator()) {
            case AND, OR, XOR, IMPLIES -> true;
            default -> false;
        }) {
            return booleanBinaryValue(expression, context, diagnostics);
        }
        OclValue left = evaluate(expression.left(), context, diagnostics);
        if (!diagnostics.isEmpty()) {
            return OclInvalidValue.INSTANCE;
        }

        OclValue right = evaluate(expression.right(), context, diagnostics);
        if (!diagnostics.isEmpty()) {
            return OclInvalidValue.INSTANCE;
        }

        return switch (expression.operator()) {
            case AND, OR, XOR, IMPLIES -> throw new IllegalStateException("Boolean operator was not dispatched centrally.");
            case EQUAL -> OclBooleanLogic.equal(left, right);
            case NOT_EQUAL -> OclBooleanLogic.notEqual(left, right);
            case LESS, LESS_EQUAL, GREATER, GREATER_EQUAL ->
                    OclPrimitiveLibrary.compare(left, right, expression.operator());
            case SUBTRACT -> left instanceof SetValue leftSet && right instanceof SetValue rightSet
                    ? setDifference(leftSet, rightSet)
                    : OclPrimitiveLibrary.arithmetic(left, right, expression.operator());
            case ADD, MULTIPLY, DIVIDE, INTEGER_DIVIDE, MODULO ->
                    OclPrimitiveLibrary.arithmetic(left, right, expression.operator());
        };
    }

    private OclValue booleanBinaryValue(BinaryExpression expression, EvaluationContext context,
            List<OclDiagnostic> diagnostics) {
        List<OclDiagnostic> operandDiagnostics = new ArrayList<>();
        OclValue left = evaluate(expression.left(), context, operandDiagnostics);
        OclValue right = evaluate(expression.right(), context, operandDiagnostics);
        OclValue result = switch (expression.operator()) {
            case AND -> OclBooleanLogic.and(left, right);
            case OR -> OclBooleanLogic.or(left, right);
            case XOR -> OclBooleanLogic.xor(left, right);
            case IMPLIES -> OclBooleanLogic.implies(left, right);
            default -> throw new IllegalArgumentException("Expected a Boolean operator.");
        };
        if (result instanceof OclInvalidValue) diagnostics.addAll(operandDiagnostics);
        return result;
    }

    private OclValue operationCallValue(OperationCallExpression expression, EvaluationContext context, List<OclDiagnostic> diagnostics) {
        OclValue receiver = expression.navigationOperator() == de.useweb.backend.ocl.ast.CallNavigationOperator.NONE
                && expression.receiver() instanceof SelfExpression && context.implicitSourceValue().isPresent()
                ? context.implicitSourceValue().orElseThrow()
                : evaluate(expression.receiver(), context, diagnostics);
        List<OclValue> arguments = expression.arguments().stream()
                .map(argument -> evaluate(argument, context, diagnostics))
                .toList();
        if (!diagnostics.isEmpty()) return OclInvalidValue.INSTANCE;
        if (expression.operationName().equals("oclIsNew") && arguments.isEmpty()
                && receiver instanceof ObjectValue object) {
            return new BooleanValue(context.preState().findObject(object.object().id()).isEmpty());
        }
        if (receiver instanceof OclVoidValue || receiver instanceof OclInvalidValue) {
            Optional<OclValue> propagated = standardOperation(receiver, expression.operationName(), arguments,
                    context, expression.sourceRange(), diagnostics);
            if (propagated.isPresent()) return propagated.get();
            return OclInvalidValue.INSTANCE;
        }
        OclCallResolver resolver = callResolver(context);
        if (receiver instanceof CollectionValue collection
                && expression.navigationOperator() == de.useweb.backend.ocl.ast.CallNavigationOperator.DOT
                && !isOclAnyOperation(expression.operationName())
                && !collection.values().isEmpty()) {
            OclCallResolutionResult elementCall = resolver.resolveOperation(
                    OclRuntimeType.of(collection.values().getFirst(), context.umlModel()), expression.operationName(),
                    arguments.stream().map(argument -> OclRuntimeType.of(argument, context.umlModel())).toList());
            if (elementCall.status() == OclCallResolutionResult.Status.RESOLVED) {
                return implicitCollectOperationValue(collection, expression, arguments, context, diagnostics);
            }
        }
        OclCallResolutionResult resolved = resolver.resolveOperation(
                OclRuntimeType.of(receiver, context.umlModel()), expression.operationName(),
                arguments.stream().map(argument -> OclRuntimeType.of(argument, context.umlModel())).toList());
        if (resolved.status() == OclCallResolutionResult.Status.UNKNOWN
                && receiver instanceof CollectionValue collection
                && expression.navigationOperator() == de.useweb.backend.ocl.ast.CallNavigationOperator.DOT) {
            return implicitCollectOperationValue(collection, expression, arguments, context, diagnostics);
        }
        if (resolved.status() != OclCallResolutionResult.Status.RESOLVED) {
            diagnostics.add(OclDiagnostic.evaluationError("CALL_RESOLUTION_ERROR", resolved.message(),
                    expression.operationRange()));
            return OclInvalidValue.INSTANCE;
        }
        return executeResolvedOperation(receiver, expression, arguments, resolved, context, diagnostics);
    }

    private OclValue implicitCollectOperationValue(CollectionValue source, OperationCallExpression expression,
            List<OclValue> arguments, EvaluationContext context, List<OclDiagnostic> diagnostics) {
        List<OclValue> values = new ArrayList<>();
        for (OclValue element : source.values()) {
            OclCallResolutionResult elementCall = callResolver(context).resolveOperation(
                    OclRuntimeType.of(element, context.umlModel()), expression.operationName(),
                    arguments.stream().map(argument -> OclRuntimeType.of(argument, context.umlModel())).toList());
            if (elementCall.status() != OclCallResolutionResult.Status.RESOLVED) {
                diagnostics.add(OclDiagnostic.evaluationError("CALL_RESOLUTION_ERROR", elementCall.message(),
                        expression.operationRange()));
                return OclInvalidValue.INSTANCE;
            }
            OclValue value = executeResolvedOperation(element, expression, arguments, elementCall, context, diagnostics);
            if (value instanceof OclInvalidValue || !diagnostics.isEmpty()) {
                return OclInvalidValue.INSTANCE;
            }
            values.add(value);
        }
        return flatten(collectNestedValue(source, values));
    }

    private OclValue executeResolvedOperation(OclValue receiver, OperationCallExpression expression,
            List<OclValue> arguments, OclCallResolutionResult resolved, EvaluationContext context,
            List<OclDiagnostic> diagnostics) {
        if (resolved.resolution().kind() == OclCallKind.STANDARD_LIBRARY) {
            return standardOperation(receiver, expression.operationName(), arguments, context,
                    expression.sourceRange(), diagnostics).orElse(OclInvalidValue.INSTANCE);
        }
        if (receiver instanceof ObjectValue object && context.definitionRuntime() != null) {
            try {
                Optional<OclValue> defined = switch (resolved.resolution().kind()) {
                    case UML_OPERATION -> context.definitionRuntime().operationForFeature(
                            resolved.resolution().featureId(), object, arguments, context);
                    case DEFINITION_OPERATION -> context.definitionRuntime().operation(
                            new OclDefinitionId(resolved.resolution().featureId()), object, arguments, context);
                    default -> Optional.empty();
                };
                if (defined.isPresent()) return defined.get();
            } catch (de.useweb.backend.ocl.definition.OclDefinitionEvaluationException exception) {
                diagnostics.add(OclDiagnostic.evaluationError(exception.code(), exception.getMessage(),
                        expression.operationRange()));
                return OclInvalidValue.INSTANCE;
            }
        }
        diagnostics.add(OclDiagnostic.evaluationError(
                "Operation '" + expression.operationName() + "' has no executable standard implementation.",
                expression.operationRange()));
        return OclInvalidValue.INSTANCE;
    }

    private OclCallResolver callResolver(EvaluationContext context) {
        var contextClass = context.umlModel().findClass(context.self().classId()).orElseThrow();
        OclDefinitionTypeResolver definitions = context.definitionRuntime() instanceof OclDefinitionTypeResolver resolver
                ? resolver : null;
        return new OclCallResolver(new TypeEnvironment(context.umlModel(), contextClass, Map.of(), null, definitions));
    }

    private boolean isOclAnyOperation(String name) {
        return name.equals("oclIsUndefined") || name.equals("oclIsInvalid") || name.equals("oclType");
    }

    private Optional<OclValue> standardOperation(OclValue receiver, String name, List<OclValue> arguments,
            EvaluationContext context, de.useweb.backend.ocl.diagnostics.SourceRange sourceRange,
            List<OclDiagnostic> diagnostics) {
        if (name.equals("oclType") && arguments.isEmpty()) {
            OclType represented = OclRuntimeType.of(receiver, context.umlModel());
            return Optional.of(classifierValue(represented, context));
        }
        if (name.equals("oclIsUndefined") && arguments.isEmpty()) return Optional.of(new BooleanValue(isUndefined(receiver)));
        if (name.equals("oclIsInvalid") && arguments.isEmpty()) return Optional.of(new BooleanValue(receiver instanceof OclInvalidValue));
        Optional<OclValue> primitive = OclPrimitiveLibrary.evaluate(receiver, name, arguments);
        if (primitive.isPresent()) return primitive;
        if (isUndefined(receiver) && isProducingCollectionOperation(name)) {
            return Optional.of(OclInvalidValue.INSTANCE);
        }
        if (arguments.isEmpty() && receiver instanceof OclInvalidValue
                && (name.equals("size") || name.equals("isEmpty") || name.equals("notEmpty"))) {
            return Optional.of(OclInvalidValue.INSTANCE);
        }
        if (arguments.isEmpty() && receiver instanceof OclVoidValue) {
            return switch (name) {
                case "isEmpty" -> Optional.of(new BooleanValue(true));
                case "notEmpty" -> Optional.of(new BooleanValue(false));
                case "size" -> Optional.of(OclInvalidValue.INSTANCE);
                default -> Optional.empty();
            };
        }
        if (receiver instanceof CollectionValue collection && arguments.isEmpty()) {
            return switch (name) {
                case "size" -> Optional.of(new IntegerValue(collection.values().size()));
                case "isEmpty" -> Optional.of(new BooleanValue(collection.values().isEmpty()));
                case "notEmpty" -> Optional.of(new BooleanValue(!collection.values().isEmpty()));
                case "flatten" -> Optional.of(flatten(collection));
                case "asSet" -> Optional.of(new SetValue(collection.values()));
                case "asBag" -> Optional.of(new BagValue(collection.values()));
                case "asSequence" -> Optional.of(new SequenceValue(collection.values()));
                case "asOrderedSet" -> Optional.of(new OrderedSetValue(collection.values()));
                case "max" -> Optional.of(collectionExtremum(collection, true));
                case "min" -> Optional.of(collectionExtremum(collection, false));
                case "sum" -> Optional.of(collectionSum(collection));
                case "first" -> Optional.of(collection.values().isEmpty()
                        ? OclInvalidValue.INSTANCE : collection.values().getFirst());
                case "last" -> Optional.of(collection.values().isEmpty()
                        ? OclInvalidValue.INSTANCE : collection.values().getLast());
                case "reverse" -> Optional.of(reverse(collection));
                default -> Optional.empty();
            };
        }
        if (receiver instanceof CollectionValue collection && arguments.size() == 1) {
            OclValue argument = arguments.getFirst();
            if ((name.equals("includes") || name.equals("excludes") || name.equals("including")
                    || name.equals("excluding") || name.equals("count")) && argument instanceof OclInvalidValue) {
                return Optional.of(OclInvalidValue.INSTANCE);
            }
            if (name.equals("includes") || name.equals("excludes")) {
                OclValue included = collectionIncludes(collection, argument);
                return Optional.of(name.equals("includes") ? included : OclBooleanLogic.not(included));
            }
            if (name.equals("count")) {
                return Optional.of(collectionCount(collection, argument));
            }
            if (name.equals("including")) {
                List<OclValue> values = new ArrayList<>(collection.values());
                values.add(argument);
                return Optional.of(collectionValue(collection, values));
            }
            if (name.equals("excluding")) {
                List<OclValue> values = collection.values().stream()
                        .filter(value -> !OclValueEquality.equal(value, argument))
                        .toList();
                return Optional.of(collectionValue(collection, values));
            }
            if ((name.equals("includesAll") || name.equals("excludesAll")) && argument instanceof OclInvalidValue) {
                return Optional.of(OclInvalidValue.INSTANCE);
            }
            if ((name.equals("includesAll") || name.equals("excludesAll")) && argument instanceof CollectionValue other) {
                return Optional.of(collectionIncludesAll(collection, other, name.equals("excludesAll")));
            }
            if (name.equals("product") && argument instanceof CollectionValue other) {
                long productSize = (long) collection.values().size() * other.values().size();
                if (productSize > MAX_RESULT_ELEMENTS) {
                    return Optional.of(resultLimitValue(sourceRange, productSize, diagnostics));
                }
                List<OclValue> tuples = new ArrayList<>();
                for (OclValue first : collection.values()) {
                    for (OclValue second : other.values()) {
                        Map<String, OclValue> parts = new LinkedHashMap<>();
                        parts.put("first", first);
                        parts.put("second", second);
                        tuples.add(new TupleValue(parts));
                    }
                }
                return Optional.of(new SetValue(tuples));
            }
            if (name.equals("append") || name.equals("prepend")) {
                return orderedAdd(collection, argument, name.equals("prepend"));
            }
            if (name.equals("at") && argument instanceof IntegerValue index) {
                return Optional.of(orderedAt(collection, index.value()));
            }
            if (name.equals("indexOf")) {
                int index = indexOf(collection.values(), argument);
                return Optional.of(index < 0 ? OclInvalidValue.INSTANCE : new IntegerValue(index + 1));
            }
            if (name.equals("symmetricDifference") && collection instanceof SetValue sourceSet
                    && argument instanceof SetValue otherSet) {
                return Optional.of(symmetricDifference(sourceSet, otherSet));
            }
            if ((name.equals("union") || name.equals("intersection")) && argument instanceof OclInvalidValue) {
                return Optional.of(OclInvalidValue.INSTANCE);
            }
            if (argument instanceof CollectionValue other) {
                if (name.equals("union")) return collectionUnion(collection, other);
                if (name.equals("intersection")) return collectionIntersection(collection, other);
            }
        }
        if (receiver instanceof CollectionValue collection && arguments.size() == 2
                && arguments.get(0) instanceof IntegerValue first) {
            if (name.equals("insertAt")) {
                return Optional.of(orderedInsertAt(collection, first.value(), arguments.get(1)));
            }
            if (arguments.get(1) instanceof IntegerValue second
                    && (name.equals("subSequence") || name.equals("subOrderedSet"))) {
                return Optional.of(orderedSlice(collection, first.value(), second.value()));
            }
        }
        return Optional.empty();
    }

    private ClassifierValue classifierValue(OclType type, EvaluationContext context) {
        if (type.kind() == OclType.Kind.CLASS) {
            var umlClass = context.umlModel().findClass(type.classId()).orElseThrow();
            return new ClassifierValue(type.classId().value(), umlClass.qualifiedName(context.umlModel()), type);
        }
        if (type.kind() == OclType.Kind.ENUM) {
            var enumeration = context.umlModel().enumerations().stream()
                    .filter(value -> value.id().equals(type.enumerationId())).findFirst().orElseThrow();
            return new ClassifierValue(type.enumerationId().value(), enumeration.qualifiedName(context.umlModel()), type);
        }
        if (type.kind() == OclType.Kind.DATA_TYPE) {
            var dataType = context.umlModel().dataTypes().stream()
                    .filter(value -> value.id().equals(type.dataTypeId())).findFirst().orElseThrow();
            return new ClassifierValue(type.dataTypeId().value(), dataType.qualifiedName(context.umlModel()), type);
        }
        return new ClassifierValue("ocl:" + type.displayName(), type.displayName(), type);
    }

    private boolean contains(CollectionValue collection, OclValue target) {
        return collection.values().stream().anyMatch(value -> OclValueEquality.equal(value, target));
    }

    private OclValue collectionIncludes(CollectionValue collection, OclValue target) {
        if (target instanceof OclInvalidValue) return OclInvalidValue.INSTANCE;
        boolean sawInvalid = false;
        for (OclValue value : collection.values()) {
            OclValue equal = OclValueEquality.semanticEqual(value, target);
            if (equal.equals(new BooleanValue(true))) return equal;
            sawInvalid |= equal instanceof OclInvalidValue;
        }
        return sawInvalid ? OclInvalidValue.INSTANCE : new BooleanValue(false);
    }

    private OclValue collectionCount(CollectionValue collection, OclValue target) {
        if (target instanceof OclInvalidValue) return OclInvalidValue.INSTANCE;
        int count = 0;
        for (OclValue value : collection.values()) {
            OclValue equal = OclValueEquality.semanticEqual(value, target);
            if (equal instanceof OclInvalidValue) return OclInvalidValue.INSTANCE;
            if (equal.equals(new BooleanValue(true))) count++;
        }
        return new IntegerValue(count);
    }

    private OclValue collectionIncludesAll(CollectionValue source, CollectionValue other, boolean excludesAll) {
        OclValue result = new BooleanValue(true);
        for (OclValue value : other.values()) {
            OclValue includes = collectionIncludes(source, value);
            result = OclBooleanLogic.and(result, excludesAll ? OclBooleanLogic.not(includes) : includes);
            if (result.equals(new BooleanValue(false))) return result;
        }
        return result;
    }

    private OclValue collectionExtremum(CollectionValue collection, boolean maximum) {
        if (collection.values().isEmpty()) return OclInvalidValue.INSTANCE;
        OclValue result = collection.values().getFirst();
        for (OclValue value : collection.values().subList(1, collection.values().size())) {
            result = OclPrimitiveLibrary.evaluate(result, maximum ? "max" : "min", List.of(value))
                    .orElse(OclInvalidValue.INSTANCE);
            if (result instanceof OclInvalidValue) return result;
        }
        return result;
    }

    private OclValue collectionSum(CollectionValue collection) {
        if (collection.values().isEmpty()) return new IntegerValue(0);
        OclValue result = collection.values().getFirst();
        for (OclValue value : collection.values().subList(1, collection.values().size())) {
            result = OclPrimitiveLibrary.arithmetic(result, value, BinaryOperator.ADD);
            if (result instanceof OclInvalidValue) return result;
        }
        return result;
    }

    private OclValue setDifference(SetValue source, SetValue other) {
        return new SetValue(source.values().stream().filter(value -> !contains(other, value)).toList());
    }

    private SetValue symmetricDifference(SetValue source, SetValue other) {
        List<OclValue> values = new ArrayList<>();
        source.values().stream().filter(value -> !contains(other, value)).forEach(values::add);
        other.values().stream().filter(value -> !contains(source, value)).forEach(values::add);
        return new SetValue(values);
    }

    private Optional<OclValue> orderedAdd(CollectionValue source, OclValue value, boolean prepend) {
        if (!source.collectionKind().ordered()) return Optional.empty();
        List<OclValue> values = new ArrayList<>(source.values());
        if (source instanceof OrderedSetValue) values.removeIf(existing -> OclValueEquality.equal(existing, value));
        values.add(prepend ? 0 : values.size(), value);
        return Optional.of(collectionValue(source, values));
    }

    private OclValue orderedAt(CollectionValue source, int oneBasedIndex) {
        if (!source.collectionKind().ordered() || oneBasedIndex < 1 || oneBasedIndex > source.values().size()) {
            return OclInvalidValue.INSTANCE;
        }
        return source.values().get(oneBasedIndex - 1);
    }

    private OclValue orderedInsertAt(CollectionValue source, int oneBasedIndex, OclValue value) {
        if (!source.collectionKind().ordered() || oneBasedIndex < 1 || oneBasedIndex > source.values().size() + 1) {
            return OclInvalidValue.INSTANCE;
        }
        List<OclValue> values = new ArrayList<>(source.values());
        if (source instanceof OrderedSetValue) {
            int existing = indexOf(values, value);
            if (existing >= 0) {
                values.remove(existing);
                if (existing < oneBasedIndex - 1) oneBasedIndex--;
            }
        }
        values.add(oneBasedIndex - 1, value);
        return collectionValue(source, values);
    }

    private OclValue orderedSlice(CollectionValue source, int lower, int upper) {
        if (!source.collectionKind().ordered() || lower < 1 || upper < lower || upper > source.values().size()) {
            return OclInvalidValue.INSTANCE;
        }
        return collectionValue(source, source.values().subList(lower - 1, upper));
    }

    private CollectionValue reverse(CollectionValue source) {
        List<OclValue> values = new ArrayList<>(source.values());
        java.util.Collections.reverse(values);
        return collectionValue(source, values);
    }

    private boolean isProducingCollectionOperation(String name) {
        return name.equals("union") || name.equals("intersection") || name.equals("flatten")
                || name.equals("asSet") || name.equals("asBag") || name.equals("asSequence")
                || name.equals("asOrderedSet");
    }

    private CollectionValue collectionValue(CollectionValue source, List<OclValue> values) {
        return switch (source.collectionKind()) {
            case SET -> new SetValue(values);
            case BAG -> new BagValue(values);
            case SEQUENCE -> new SequenceValue(values);
            case ORDERED_SET -> new OrderedSetValue(values);
            case COLLECTION -> throw new IllegalStateException("Abstract Collection values cannot be evaluated.");
        };
    }

    private CollectionValue collectNestedValue(CollectionValue source, List<OclValue> values) {
        return switch (source.collectionKind()) {
            case SET, BAG -> new BagValue(values);
            case SEQUENCE, ORDERED_SET -> new SequenceValue(values);
            case COLLECTION -> throw new IllegalStateException("Abstract Collection values cannot be evaluated.");
        };
    }

    private Optional<OclValue> collectionUnion(CollectionValue source, CollectionValue other) {
        List<OclValue> values = new ArrayList<>(source.values());
        values.addAll(other.values());
        return switch (source.collectionKind()) {
            case SET -> switch (other.collectionKind()) {
                case SET -> Optional.of(new SetValue(values));
                case BAG -> Optional.of(new BagValue(values));
                default -> Optional.empty();
            };
            case BAG -> switch (other.collectionKind()) {
                case SET, BAG -> Optional.of(new BagValue(values));
                default -> Optional.empty();
            };
            case SEQUENCE -> other instanceof SequenceValue ? Optional.of(new SequenceValue(values)) : Optional.empty();
            case ORDERED_SET -> other instanceof OrderedSetValue ? Optional.of(new OrderedSetValue(values)) : Optional.empty();
            case COLLECTION -> Optional.empty();
        };
    }

    private Optional<OclValue> collectionIntersection(CollectionValue source, CollectionValue other) {
        if (source instanceof SetValue && (other instanceof SetValue || other instanceof BagValue)) {
            return Optional.of(new SetValue(source.values().stream().filter(value -> contains(other, value)).toList()));
        }
        if (source instanceof BagValue && other instanceof SetValue) {
            return Optional.of(new SetValue(source.values().stream().filter(value -> contains(other, value)).toList()));
        }
        if (source instanceof BagValue && other instanceof BagValue) {
            List<OclValue> unmatched = new ArrayList<>(other.values());
            List<OclValue> result = new ArrayList<>();
            for (OclValue value : source.values()) {
                int index = indexOf(unmatched, value);
                if (index >= 0) {
                    result.add(value);
                    unmatched.remove(index);
                }
            }
            return Optional.of(new BagValue(result));
        }
        if (source instanceof OrderedSetValue && other instanceof OrderedSetValue) {
            return Optional.of(new OrderedSetValue(source.values().stream().filter(value -> contains(other, value)).toList()));
        }
        return Optional.empty();
    }

    private int indexOf(List<OclValue> values, OclValue target) {
        for (int index = 0; index < values.size(); index++) {
            if (OclValueEquality.equal(values.get(index), target)) return index;
        }
        return -1;
    }

    private CollectionValue flatten(CollectionValue source) {
        List<OclValue> values = new ArrayList<>();
        appendFlattened(source.values(), values);
        return collectionValue(source, values);
    }

    private OclValue enforceResultLimit(OclValue value,
            de.useweb.backend.ocl.diagnostics.SourceRange range, List<OclDiagnostic> diagnostics) {
        if (value instanceof CollectionValue collection && collection.values().size() > MAX_RESULT_ELEMENTS) {
            return resultLimitValue(range, collection.values().size(), diagnostics);
        }
        return value;
    }

    private OclValue resultLimitValue(de.useweb.backend.ocl.diagnostics.SourceRange range, long observed,
            List<OclDiagnostic> diagnostics) {
        diagnostics.add(new OclDiagnostic(
                de.useweb.backend.ocl.diagnostics.OclDiagnosticPhase.EVALUATION,
                "RESULT_LIMIT_EXCEEDED", "ERROR",
                "OCL evaluation exceeds the result limit of " + MAX_RESULT_ELEMENTS + " elements.",
                range, List.of("result with at most " + MAX_RESULT_ELEMENTS + " elements"),
                Long.toString(observed)));
        return OclInvalidValue.INSTANCE;
    }

    private void appendFlattened(List<OclValue> source, List<OclValue> result) {
        for (OclValue value : source) {
            if (value instanceof CollectionValue nested) {
                appendFlattened(nested.values(), result);
            } else {
                result.add(value);
            }
        }
    }

    private OclValue evaluationError(String message, de.useweb.backend.ocl.diagnostics.SourceRange range, List<OclDiagnostic> diagnostics) {
        diagnostics.add(OclDiagnostic.evaluationError(message, range));
        return OclInvalidValue.INSTANCE;
    }

    private boolean isNumericValue(OclValue value) {
        return value instanceof IntegerValue || value instanceof RealValue;
    }

    private boolean isUndefined(OclValue value) {
        return value instanceof OclVoidValue || value instanceof OclInvalidValue;
    }

    private boolean isTrue(OclValue value) {
        return value instanceof BooleanValue booleanValue && booleanValue.value();
    }

    private boolean isFalse(OclValue value) {
        return value instanceof BooleanValue booleanValue && !booleanValue.value();
    }

    private double number(OclValue value) {
        return switch (value) {
            case IntegerValue integerValue -> integerValue.value();
            case RealValue realValue -> realValue.value();
            default -> Double.NaN;
        };
    }

    private record NavigationTarget(UmlAssociation association, UmlAssociationEnd sourceEnd, UmlAssociationEnd targetEnd) {
    }
}
