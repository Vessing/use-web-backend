package de.useweb.backend.ocl.definition;

import java.util.ArrayList;
import java.util.List;

import de.useweb.backend.domain.uml.UmlModel;

public final class OclModelDefinitionFactory {
    private final OclDefinitionParser parser = new OclDefinitionParser();

    public List<OclDefinition> definitions(UmlModel model) {
        List<OclDefinition> definitions = new ArrayList<>();
        model.classes().forEach(owner -> {
            owner.attributes().forEach(attribute -> {
                if (attribute.deriveExpression() != null) {
                    definitions.add(parse(model, new OclDefinitionId("derive-" + attribute.id().value()),
                            "context " + owner.name() + "::" + attribute.name() + " : "
                                    + attribute.type().name() + " derive: " + attribute.deriveExpression()));
                }
                if (attribute.initExpression() != null) {
                    definitions.add(parse(model, new OclDefinitionId("init-" + attribute.id().value()),
                            "context " + owner.name() + "::" + attribute.name() + " : "
                                    + attribute.type().name() + " init: " + attribute.initExpression()));
                }
            });
            owner.operations().stream().filter(operation -> operation.bodyExpression() != null).forEach(operation ->
                    definitions.add(parse(model, new OclDefinitionId("body-" + operation.id().value()),
                            "context " + owner.name() + "::" + operation.name() + "("
                                    + operation.parameters().stream()
                                            .map(parameter -> parameter.name() + " : " + parameter.type().name())
                                            .collect(java.util.stream.Collectors.joining(", ")) + ") : "
                                    + operation.returnType().name() + " body: " + operation.bodyExpression())));
        });
        return List.copyOf(definitions);
    }

    private OclDefinition parse(UmlModel model, OclDefinitionId id, String source) {
        OclDefinitionParseResult result = parser.parse(id, source, model);
        if (!result.success()) {
            throw new IllegalArgumentException(result.diagnostics().getFirst().message());
        }
        return result.definition();
    }
}
