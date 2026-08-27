package de.useweb.backend.ocl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.useweb.backend.api.mapper.ProjectDtoMapper;
import de.useweb.backend.domain.uml.UmlAttribute;
import de.useweb.backend.domain.uml.UmlAttributeId;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlModelId;
import de.useweb.backend.domain.uml.UmlModelImport;
import de.useweb.backend.domain.uml.UmlModelImportId;
import de.useweb.backend.domain.uml.UmlNamespaceException;
import de.useweb.backend.domain.uml.UmlPackage;
import de.useweb.backend.domain.uml.UmlPackageId;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.domain.uml.UmlVisibility;
import de.useweb.backend.ocl.collection.CollectionKind;
import de.useweb.backend.ocl.parser.OclParser;
import de.useweb.backend.ocl.typecheck.OclType;
import de.useweb.backend.ocl.typecheck.OclTypeChecker;
import de.useweb.backend.ocl.typecheck.TypeEnvironment;

class UmlNamespaceVisibilityTest {
    private final OclParser parser = new OclParser();
    private final OclTypeChecker typeChecker = new OclTypeChecker();

    @Test
    void resolvesLocalImportedAliasedAndQualifiedClassNamesDeterministically() {
        Fixture fixture = fixture();
        TypeEnvironment environment = new TypeEnvironment(fixture.model(), fixture.student());

        assertThat(type("Identifier.allInstances()", environment).elementType().classId())
                .isEqualTo(fixture.identifier().id());
        assertThat(type("shared::Identifier.allInstances()", environment).elementType().classId())
                .isEqualTo(fixture.identifier().id());
        assertThat(type("shared::core::Identifier.allInstances()", environment).elementType().classId())
                .isEqualTo(fixture.identifier().id());
        assertThat(type("self.oclIsKindOf(shared::core::Owner)", environment)).isEqualTo(OclType.BOOLEAN);
        assertThat(parser.parse("shared::core::Identifier.allInstances()").success()).isTrue();
    }

    @Test
    void enforcesPrivateProtectedPackageAndPublicVisibilityInTypechecker() {
        Fixture fixture = fixture();
        TypeEnvironment studentEnvironment = new TypeEnvironment(fixture.model(), fixture.student(),
                Map.of("owner", OclType.classType(fixture.owner(), fixture.model())));

        assertThat(type("owner.publicValue", studentEnvironment)).isEqualTo(OclType.STRING);
        assertThat(type("owner.protectedValue", studentEnvironment)).isEqualTo(OclType.STRING);
        assertDiagnostic("owner.privateValue", studentEnvironment, "INACCESSIBLE_FEATURE");
        assertDiagnostic("owner.packageValue", studentEnvironment, "INACCESSIBLE_FEATURE");

        TypeEnvironment samePackage = new TypeEnvironment(fixture.model(), fixture.identifier(),
                Map.of("owner", OclType.classType(fixture.owner(), fixture.model())));
        assertThat(type("owner.packageValue", samePackage)).isEqualTo(OclType.STRING);
        assertDiagnostic("owner.protectedValue", samePackage, "INACCESSIBLE_FEATURE");
    }

    @Test
    void rejectsImportCyclesAndPreservesNamespaceProvenanceInDtos() {
        Fixture fixture = fixture();
        var dto = ProjectDtoMapper.toDto(fixture.model());
        UmlModel restored = ProjectDtoMapper.toDomain(dto);

        assertThat(dto.classes()).anySatisfy(umlClass -> {
            if (umlClass.id().equals(fixture.identifier().id().value())) {
                assertThat(umlClass.qualifiedName()).isEqualTo("shared::core::Identifier");
            }
        });
        assertThat(restored.imports().getFirst().provenance()).isEqualTo("local:shared-core.use");
        assertThat(restored.findClass(fixture.owner().id()).orElseThrow().attributes())
                .extracting(UmlAttribute::visibility)
                .containsExactly(UmlVisibility.PUBLIC, UmlVisibility.PRIVATE,
                        UmlVisibility.PROTECTED, UmlVisibility.PACKAGE);

        UmlModelImport reverse = new UmlModelImport(new UmlModelImportId("import-core-people"),
                fixture.corePackage().id(), fixture.peoplePackage().id(), null, "people.use", "local:people.use");
        assertThatThrownBy(() -> new UmlModel(new UmlModelId("cycle"), "Cycle", fixture.model().classes(),
                List.of(), List.of(), List.of(), fixture.model().packages(),
                List.of(fixture.model().imports().getFirst(), reverse)))
                .isInstanceOf(UmlNamespaceException.class)
                .satisfies(error -> assertThat(((UmlNamespaceException) error).code()).isEqualTo("IMPORT_CYCLE"));
    }

    private OclType type(String expression, TypeEnvironment environment) {
        var parse = parser.parse(expression);
        assertThat(parse.diagnostics()).isEmpty();
        var result = typeChecker.checkExpression(environment, parse.ast());
        assertThat(result.diagnostics()).isEmpty();
        return result.resultType();
    }

    private void assertDiagnostic(String expression, TypeEnvironment environment, String code) {
        var parse = parser.parse(expression);
        assertThat(parse.diagnostics()).isEmpty();
        assertThat(typeChecker.checkExpression(environment, parse.ast()).diagnostics())
                .anyMatch(diagnostic -> diagnostic.code().equals(code));
    }

    private Fixture fixture() {
        UmlPackage peoplePackage = new UmlPackage(new UmlPackageId("package-people"), "university::people");
        UmlPackage corePackage = new UmlPackage(new UmlPackageId("package-core"), "shared::core");
        UmlClass owner = new UmlClass(new UmlClassId("class-owner"), "Owner", List.of(
                attribute("public", UmlVisibility.PUBLIC), attribute("private", UmlVisibility.PRIVATE),
                attribute("protected", UmlVisibility.PROTECTED), attribute("package", UmlVisibility.PACKAGE)),
                List.of(), false, List.of(), UmlVisibility.PUBLIC, corePackage.id());
        UmlClass student = new UmlClass(new UmlClassId("class-student"), "Student", List.of(), List.of(),
                false, List.of(owner.id()), UmlVisibility.PUBLIC, peoplePackage.id());
        UmlClass identifier = new UmlClass(new UmlClassId("class-identifier"), "Identifier", List.of(), List.of(),
                false, List.of(), UmlVisibility.PUBLIC, corePackage.id());
        UmlModelImport modelImport = new UmlModelImport(new UmlModelImportId("import-people-core"),
                peoplePackage.id(), corePackage.id(), "shared", "shared-core.use", "local:shared-core.use");
        UmlModel model = new UmlModel(new UmlModelId("namespaces"), "Namespaces",
                List.of(owner, student, identifier), List.of(), List.of(), List.of(),
                List.of(peoplePackage, corePackage), List.of(modelImport));
        return new Fixture(model, owner, student, identifier, peoplePackage, corePackage);
    }

    private UmlAttribute attribute(String prefix, UmlVisibility visibility) {
        return new UmlAttribute(new UmlAttributeId("attr-" + prefix), prefix + "Value", UmlType.STRING,
                false, null, null, visibility);
    }

    private record Fixture(UmlModel model, UmlClass owner, UmlClass student, UmlClass identifier,
            UmlPackage peoplePackage, UmlPackage corePackage) {}
}
