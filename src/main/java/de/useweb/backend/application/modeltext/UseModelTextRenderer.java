package de.useweb.backend.application.modeltext;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import de.useweb.backend.domain.uml.AggregationKind;
import de.useweb.backend.domain.uml.Multiplicity;
import de.useweb.backend.domain.uml.UmlAssociation;
import de.useweb.backend.domain.uml.UmlAssociationEnd;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlDataType;
import de.useweb.backend.domain.uml.UmlEnumeration;
import de.useweb.backend.domain.uml.UmlInvariant;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlOperation;

/**
 * Produces the deterministic USE subset understood by {@code ModelTextParser}.
 */
@Component
public class UseModelTextRenderer {

    public String render(UmlModel model) {
        Map<UmlClassId, UmlClass> classesById = model.classes().stream()
                .collect(Collectors.toMap(UmlClass::id, Function.identity()));
        List<String> lines = new java.util.ArrayList<>();
        lines.add("model " + model.name());

        appendSeparated(lines, model.enumerations().stream().sorted(byName()).map(this::renderEnumeration).toList());
        appendSeparated(lines, model.dataTypes().stream().sorted(byName()).map(this::renderDataType).toList());
        appendSeparated(lines, model.classes().stream().sorted(byName())
                .map(umlClass -> renderClass(umlClass, classesById)).toList());
        appendSeparated(lines, model.associations().stream().sorted(byName())
                .map(association -> renderAssociation(association, classesById)).toList());

        List<String> constraints = new java.util.ArrayList<>();
        model.invariants().stream().sorted(byName()).forEach(invariant -> {
            UmlClass context = classesById.get(invariant.contextClassId());
            if (context != null && invariant.enabled()) {
                constraints.add("context " + context.name() + " inv " + invariant.name() + ":");
                constraints.add("  " + invariant.expression().text());
            }
        });
        model.classes().stream().sorted(byName()).forEach(owner -> owner.operations().stream().sorted(byName())
                .forEach(operation -> appendContracts(constraints, owner, operation)));
        model.dataTypes().stream().sorted(byName()).forEach(owner -> owner.operations().stream().sorted(byName())
                .forEach(operation -> appendContracts(constraints, owner.name(), operation)));
        if (!constraints.isEmpty()) {
            lines.add("");
            lines.add("constraints");
            lines.addAll(constraints);
        }
        return String.join("\n", lines) + "\n";
    }

    private void appendSeparated(List<String> target, List<List<String>> declarations) {
        for (List<String> declaration : declarations) {
            target.add("");
            target.addAll(declaration);
        }
    }

    private List<String> renderEnumeration(UmlEnumeration value) {
        List<String> lines = new java.util.ArrayList<>();
        String literals = value.literalDefinitions().stream().map(literal -> literal.name()).sorted()
                .collect(Collectors.joining(", "));
        lines.add("enum " + value.name() + " { " + literals + " }");
        return lines;
    }

    private List<String> renderDataType(UmlDataType value) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("datatype " + value.name());
        if (!value.properties().isEmpty()) {
            lines.add("attributes");
            value.properties().stream().sorted(byName()).forEach(property ->
                    lines.add("  " + property.name() + " : " + property.type().name()));
        }
        appendOperations(lines, value.operations());
        lines.add("end");
        return lines;
    }

    private List<String> renderClass(UmlClass value, Map<UmlClassId, UmlClass> classesById) {
        List<String> lines = new java.util.ArrayList<>();
        String header = (value.abstractClass() ? "abstract class " : "class ") + value.name();
        List<String> parents = value.superClassIds().stream().map(classesById::get)
                .filter(java.util.Objects::nonNull).map(UmlClass::name).sorted().toList();
        lines.add(parents.isEmpty() ? header : header + " < " + String.join(", ", parents));
        if (!value.attributes().isEmpty()) {
            lines.add("attributes");
            value.attributes().stream().sorted(byName()).forEach(attribute -> {
                String rendered = attribute.name() + " : " + attribute.type().name();
                if (attribute.derived()) rendered += " derive = " + attribute.deriveExpression();
                else if (attribute.initExpression() != null) rendered += " init = " + attribute.initExpression();
                lines.add("  " + rendered);
            });
        }
        appendOperations(lines, value.operations());
        lines.add("end");
        return lines;
    }

    private void appendOperations(List<String> lines, List<UmlOperation> operations) {
        if (operations.isEmpty()) return;
        lines.add("operations");
        operations.stream().sorted(byName()).forEach(operation -> {
            String parameters = operation.parameters().stream()
                    .sorted(Comparator.comparingInt(parameter -> parameter.position()))
                    .map(parameter -> parameter.name() + " : " + parameter.type().name())
                    .collect(Collectors.joining(", "));
            String rendered = operation.name() + "(" + parameters + ") : " + operation.returnType().name();
            if (operation.bodyExpression() != null) rendered += " = " + operation.bodyExpression();
            lines.add("  " + rendered);
        });
    }

    private List<String> renderAssociation(UmlAssociation association, Map<UmlClassId, UmlClass> classesById) {
        AggregationKind kind = association.ends().stream().map(UmlAssociationEnd::aggregationKind)
                .filter(value -> value != AggregationKind.NONE).findFirst().orElse(AggregationKind.NONE);
        List<String> lines = new java.util.ArrayList<>();
        lines.add(switch (kind) {
            case COMPOSITE -> "composition ";
            case SHARED -> "aggregation ";
            case NONE -> "association ";
        } + association.name() + " between");
        association.ends().forEach(end -> {
            UmlClass type = classesById.get(end.classId());
            String rendered = (type == null ? end.classId().value() : type.name()) + "[" + multiplicity(end.multiplicity()) + "]";
            if (end.roleName() != null) rendered += " role " + end.roleName();
            if (end.ordered()) rendered += " ordered";
            if (!end.unique()) rendered += " nonunique";
            if (end.union()) rendered += " union";
            if (end.derived()) rendered += " derived" + (end.deriveExpression() == null ? "" : " = " + end.deriveExpression());
            lines.add("  " + rendered);
        });
        lines.add("end");
        return lines;
    }

    private void appendContracts(List<String> lines, UmlClass owner, UmlOperation operation) {
        appendContracts(lines, owner.name(), operation);
    }

    private void appendContracts(List<String> lines, String ownerName, UmlOperation operation) {
        String parameters = operation.parameters().stream().sorted(Comparator.comparingInt(parameter -> parameter.position()))
                .map(parameter -> parameter.name() + " : " + parameter.type().name()).collect(Collectors.joining(", "));
        operation.contracts().stream().filter(contract -> contract.enabled()).sorted(byName()).forEach(contract -> {
            lines.add("context " + ownerName + "::" + operation.name() + "(" + parameters + ") "
                    + contract.kind().name().toLowerCase() + " " + contract.name() + ":");
            lines.add("  " + contract.expression());
        });
    }

    private String multiplicity(Multiplicity value) {
        if (value.raw() != null && !value.raw().isBlank()) return value.raw().replaceAll("\\s+", "");
        String upper = value.unbounded() ? "*" : String.valueOf(value.upper());
        return value.lower() == value.upper() ? upper : value.lower() + ".." + upper;
    }

    private static <T> Comparator<T> byName() {
        return Comparator.comparing(value -> {
            try {
                return (String) value.getClass().getMethod("name").invoke(value);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Rendered declaration has no name", exception);
            }
        });
    }
}
