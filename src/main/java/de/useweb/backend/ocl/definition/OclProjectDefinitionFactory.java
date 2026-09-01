package de.useweb.backend.ocl.definition;

import java.util.ArrayList;
import java.util.List;

import de.useweb.backend.domain.ocl.OclDefinitionElement;
import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.ocl.parser.OclParser;

/** Builds one immutable runtime registry from persisted and model-owned definitions. */
public final class OclProjectDefinitionFactory {
    private final OclParser parser = new OclParser();

    public List<OclDefinition> definitions(Project project) {
        List<OclDefinition> result = new ArrayList<>(new OclModelDefinitionFactory().definitions(project.umlModel()));
        project.definitions().stream()
                .filter(value -> value.ownerKind() == OclDefinitionElement.OwnerKind.CLASS)
                .map(this::definition).forEach(result::add);
        return List.copyOf(result);
    }

    private OclDefinition definition(OclDefinitionElement value) {
        var parsed = parser.parse(value.expression());
        if (!parsed.success()) throw new IllegalArgumentException("Stored definition does not parse: " + value.id().value());
        return new OclDefinition(new OclDefinitionId(value.id().value()),
                value.kind() == OclDefinitionElement.Kind.PROPERTY_DEF
                        ? OclDefinitionKind.PROPERTY_DEF : OclDefinitionKind.OPERATION_DEF,
                new UmlClassId(value.ownerId()), value.name(), null, null, value.resultType(), value.parameters(),
                value.expression(), parsed.ast());
    }
}
