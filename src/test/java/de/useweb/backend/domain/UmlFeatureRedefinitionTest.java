package de.useweb.backend.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.useweb.backend.api.mapper.ProjectDtoMapper;
import de.useweb.backend.domain.uml.UmlAttribute;
import de.useweb.backend.domain.uml.UmlAttributeId;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlGeneralizationException;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlModelId;
import de.useweb.backend.domain.uml.UmlOperation;
import de.useweb.backend.domain.uml.UmlOperationId;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.domain.uml.UmlVisibility;

class UmlFeatureRedefinitionTest {

    @Test
    void resolvesMultipleInheritanceOnlyWithExplicitStableTargets() {
        UmlClass left = type("left", "Left", attribute("left-name"), operation("left-display"), List.of());
        UmlClass right = type("right", "Right", attribute("right-name"), operation("right-display"), List.of());
        UmlAttribute localAttribute = new UmlAttribute(new UmlAttributeId("local-name"), "name", UmlType.STRING,
                false, null, null, UmlVisibility.PUBLIC,
                List.of(new UmlAttributeId("left-name"), new UmlAttributeId("right-name")));
        UmlOperation localOperation = new UmlOperation(new UmlOperationId("local-display"), "displayName",
                UmlType.STRING, List.of(), null, UmlVisibility.PUBLIC, false, true, List.of(),
                List.of(new UmlOperationId("left-display"), new UmlOperationId("right-display")));
        UmlClass child = new UmlClass(new UmlClassId("child"), "Child", List.of(localAttribute),
                List.of(localOperation), false, List.of(left.id(), right.id()));

        UmlModel model = model(left, right, child);

        assertThat(model.resolveAttribute(child.id(), "name").orElseThrow().attribute().id().value())
                .isEqualTo("local-name");
        assertThat(model.resolveOperation(child.id(), "displayName", 0).orElseThrow().operation().id().value())
                .isEqualTo("local-display");
        assertThat(ProjectDtoMapper.toDomain(ProjectDtoMapper.toDto(model))).isEqualTo(model);
    }

    @Test
    void reportsConflictAndRejectsUnknownNonInheritedOrIncompatibleTargets() {
        UmlClass left = type("left", "Left", attribute("left-name"), operation("left-display"), List.of());
        UmlClass right = type("right", "Right", attribute("right-name"), operation("right-display"), List.of());
        UmlClass unresolved = type("child", "Child", attribute("local-name"), operation("local-display"),
                List.of(left.id(), right.id()));
        assertThatThrownBy(() -> model(left, right, unresolved))
                .isInstanceOf(UmlGeneralizationException.class)
                .satisfies(error -> {
                    UmlGeneralizationException conflict = (UmlGeneralizationException) error;
                    assertThat(conflict.code()).isEqualTo("AMBIGUOUS_INHERITED_FEATURE");
                    assertThat(conflict.details()).containsKeys("featureIds", "superClassIds", "featureName");
                });

        UmlAttribute unknown = new UmlAttribute(new UmlAttributeId("local-name"), "name", UmlType.STRING,
                false, null, null, UmlVisibility.PUBLIC, List.of(new UmlAttributeId("missing")));
        assertThatThrownBy(() -> model(left, new UmlClass(new UmlClassId("unknown"), "Unknown", List.of(unknown),
                List.of(), false, List.of(left.id()))))
                .isInstanceOf(UmlGeneralizationException.class)
                .extracting(error -> ((UmlGeneralizationException) error).code()).isEqualTo("UNKNOWN_REDEFINED_FEATURE");

        UmlAttribute incompatible = new UmlAttribute(new UmlAttributeId("local-name"), "name", UmlType.INTEGER,
                false, null, null, UmlVisibility.PUBLIC, List.of(new UmlAttributeId("left-name")));
        assertThatThrownBy(() -> model(left, new UmlClass(new UmlClassId("bad"), "Bad", List.of(incompatible),
                List.of(), false, List.of(left.id()))))
                .isInstanceOf(UmlGeneralizationException.class)
                .extracting(error -> ((UmlGeneralizationException) error).code())
                .isEqualTo("INCOMPATIBLE_REDEFINED_FEATURE");

        UmlClass unrelated = type("unrelated", "Unrelated", attribute("unrelated-name"),
                operation("unrelated-display"), List.of());
        UmlAttribute foreignTarget = new UmlAttribute(new UmlAttributeId("local-name"), "name", UmlType.STRING,
                false, null, null, UmlVisibility.PUBLIC, List.of(new UmlAttributeId("unrelated-name")));
        assertThatThrownBy(() -> model(left, unrelated,
                new UmlClass(new UmlClassId("foreign"), "Foreign", List.of(foreignTarget), List.of(), false,
                        List.of(left.id()))))
                .isInstanceOf(UmlGeneralizationException.class)
                .extracting(error -> ((UmlGeneralizationException) error).code())
                .isEqualTo("INVALID_REDEFINITION_OWNER");

        UmlClass duplicateLeft = type("duplicate-left", "DuplicateLeft", attribute("shared-name"),
                operation("duplicate-left-display"), List.of());
        UmlClass duplicateRight = type("duplicate-right", "DuplicateRight", attribute("shared-name"),
                operation("duplicate-right-display"), List.of());
        UmlAttribute duplicateTarget = new UmlAttribute(new UmlAttributeId("local-name"), "name", UmlType.STRING,
                false, null, null, UmlVisibility.PUBLIC, List.of(new UmlAttributeId("shared-name")));
        assertThatThrownBy(() -> model(duplicateLeft, duplicateRight,
                new UmlClass(new UmlClassId("duplicate-child"), "DuplicateChild", List.of(duplicateTarget),
                        List.of(), false, List.of(duplicateLeft.id(), duplicateRight.id()))))
                .isInstanceOf(UmlGeneralizationException.class)
                .extracting(error -> ((UmlGeneralizationException) error).code())
                .isEqualTo("DUPLICATE_FEATURE_ID");
    }

    private static UmlClass type(String id, String name, UmlAttribute attribute, UmlOperation operation,
            List<UmlClassId> parents) {
        return new UmlClass(new UmlClassId(id), name, List.of(attribute), List.of(operation), false, parents);
    }

    private static UmlAttribute attribute(String id) { return new UmlAttribute(new UmlAttributeId(id), "name", UmlType.STRING); }
    private static UmlOperation operation(String id) { return new UmlOperation(new UmlOperationId(id), "displayName", UmlType.STRING, List.of()); }
    private static UmlModel model(UmlClass... classes) {
        return new UmlModel(new UmlModelId("model"), "Model", List.of(classes), List.of(), List.of());
    }
}
