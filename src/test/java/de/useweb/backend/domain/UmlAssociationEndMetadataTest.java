package de.useweb.backend.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.useweb.backend.api.mapper.ProjectDtoMapper;
import de.useweb.backend.domain.uml.Multiplicity;
import de.useweb.backend.domain.uml.UmlAssociation;
import de.useweb.backend.domain.uml.UmlAssociationEnd;
import de.useweb.backend.domain.uml.UmlAssociationEndId;
import de.useweb.backend.domain.uml.UmlAssociationId;
import de.useweb.backend.domain.uml.UmlAssociationMetadataException;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlModelId;

class UmlAssociationEndMetadataTest {

    @Test
    void preservesB6MetadataAndCalculatedNavigationTypeInDtoRoundtrip() {
        UmlClass left = umlClass("left", "Left");
        UmlClass right = new UmlClass(new UmlClassId("right"), "Right", List.of(), List.of(), false,
                List.of(left.id()));
        UmlAssociationEnd leftEnd = end("left-end", left.id(), "owners", Multiplicity.zeroToMany());
        UmlAssociationEnd rightEnd = new UmlAssociationEnd(new UmlAssociationEndId("right-end"), right.id(),
                "items", Multiplicity.zeroToMany(), true, true, true, true, true,
                List.of(leftEnd.id()), List.of());
        UmlModel model = model(List.of(left, right), List.of(new UmlAssociation(
                new UmlAssociationId("association"), "Association", List.of(leftEnd, rightEnd))));

        var dto = ProjectDtoMapper.toDto(model).associations().getFirst().ends().get(1);

        assertThat(dto.ordered()).isTrue();
        assertThat(dto.unique()).isTrue();
        assertThat(dto.derived()).isTrue();
        assertThat(dto.union()).isTrue();
        assertThat(dto.subsettedEndIds()).containsExactly("left-end");
        assertThat(dto.navigationType()).isEqualTo("ORDERED_SET");
        assertThat(ProjectDtoMapper.toDomain(ProjectDtoMapper.toDto(model))).isEqualTo(model);
    }

    @Test
    void rejectsAmbiguousReflexiveRolesUnknownReferencesCyclesAndUnionWithoutDerived() {
        UmlClass node = umlClass("node", "Node");
        assertThatThrownBy(() -> model(List.of(node), List.of(new UmlAssociation(
                new UmlAssociationId("reflexive"), "Reflexive", List.of(
                        end("source", node.id(), "node", Multiplicity.exactlyOne()),
                        end("target", node.id(), "node", Multiplicity.zeroToMany()))))))
                .isInstanceOf(UmlAssociationMetadataException.class)
                .extracting(error -> ((UmlAssociationMetadataException) error).code())
                .isEqualTo("AMBIGUOUS_REFLEXIVE_ROLE");

        assertThatThrownBy(() -> new UmlAssociationEnd(new UmlAssociationEndId("union"), node.id(), "nodes",
                Multiplicity.zeroToMany(), true, false, true, false, true, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("derived");

        UmlAssociationEnd unknown = new UmlAssociationEnd(new UmlAssociationEndId("unknown"), node.id(), "others",
                Multiplicity.zeroToMany(), true, false, true, false, false,
                List.of(new UmlAssociationEndId("missing")), List.of());
        assertThatThrownBy(() -> model(List.of(node), List.of(new UmlAssociation(
                new UmlAssociationId("unknown-reference"), "UnknownReference",
                List.of(end("owner", node.id(), "owner", Multiplicity.exactlyOne()), unknown)))))
                .isInstanceOf(UmlAssociationMetadataException.class)
                .extracting(error -> ((UmlAssociationMetadataException) error).code())
                .isEqualTo("UNKNOWN_SUBSETS_END");
    }

    private static UmlAssociationEnd end(String id, UmlClassId classId, String role, Multiplicity multiplicity) {
        return new UmlAssociationEnd(new UmlAssociationEndId(id), classId, role, multiplicity, true);
    }

    private static UmlClass umlClass(String id, String name) {
        return new UmlClass(new UmlClassId(id), name, List.of(), List.of());
    }

    private static UmlModel model(List<UmlClass> classes, List<UmlAssociation> associations) {
        return new UmlModel(new UmlModelId("model"), "Model", classes, associations, List.of());
    }
}
