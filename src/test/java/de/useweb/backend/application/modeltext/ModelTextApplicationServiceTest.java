package de.useweb.backend.application.modeltext;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.useweb.backend.api.dto.modeltext.ApplyModelTextRequestDto;
import de.useweb.backend.api.dto.modeltext.ApplyModelTextResponseDto;
import de.useweb.backend.application.ocl.OclParseService;
import de.useweb.backend.application.project.ProjectService;
import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlInvariantId;
import de.useweb.backend.domain.uml.AggregationKind;
import de.useweb.backend.modeltext.parser.ModelTextParser;
import de.useweb.backend.persistence.json.ProjectJsonSerializer;
import de.useweb.backend.persistence.project.InMemoryProjectRepository;

class ModelTextApplicationServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-23T08:00:00Z"), ZoneOffset.UTC);
    private final InMemoryProjectRepository repository = new InMemoryProjectRepository();
    private final ProjectService projectService = new ProjectService(repository, new ProjectJsonSerializer(), clock);
    private final ModelTextApplicationService service = new ModelTextApplicationService(
            projectService,
            new ModelTextParser(),
            new OclParseService(),
            clock);

    @Test
    void appliesTransitiveFileBundleAndPersistsImportProvenance() {
        Project project = projectService.createProject("B56", null);

        ApplyModelTextResponseDto response = service.applyModelText(project.id(), new ApplyModelTextRequestDto(
                """
                import { Member } from "domain/members.use"
                model Application
                class Application end
                """,
                "USE_MODEL_TEXT", "REPLACE_UML_MODEL", true, "models/main.use", "use", "file-upload", null,
                Map.of(
                        "models/domain/members.use", """
                                import Date from "../shared/dates.use"
                                model Members
                                class Member
                                attributes
                                  joined : Date
                                end
                                class InternalOnly end
                                """,
                        "models/shared/dates.use", """
                                model Dates
                                dataType Date
                                end
                                """)));

        assertThat(response.success()).as(response.diagnostics().toString()).isTrue();
        Project saved = projectService.loadProject(project.id());
        assertThat(saved.umlModel().classes()).extracting(type -> type.name())
                .containsExactly("Member", "Application");
        assertThat(saved.umlModel().dataTypes()).extracting(type -> type.name()).containsExactly("Date");
        assertThat(saved.modelText().sources()).hasSize(3);
        assertThat(saved.modelText().sources()).extracting(source -> source.sourcePath())
                .containsExactly("models/main.use", "models/domain/members.use", "models/shared/dates.use");
        assertThat(saved.modelText().sourceFiles()).extracting(source -> source.sourcePath())
                .containsExactlyInAnyOrder("models/domain/members.use", "models/shared/dates.use");

        Project restored = new ProjectJsonSerializer().deserialize(new ProjectJsonSerializer().serialize(saved));
        assertThat(restored.modelText().sources()).isEqualTo(saved.modelText().sources());
        assertThat(restored.modelText().sourceFiles()).isEqualTo(saved.modelText().sourceFiles());
    }

    @Test
    void retainsPersistedSourceBundleWhenEditorReappliesRootText() {
        Project project = projectService.createProject("Bundle", null);
        service.applyModelText(project.id(), new ApplyModelTextRequestDto(
                """
                import * from "shared/member.use"
                model Bundle
                class Root end
                """,
                "USE_MODEL_TEXT", "REPLACE_UML_MODEL", true, "bundle.use", "use", "open-existing", null,
                Map.of("shared/member.use", "model Member\nclass Member end\n")));

        ApplyModelTextResponseDto response = service.applyModelText(project.id(), new ApplyModelTextRequestDto(
                """
                import * from "shared/member.use"
                model Bundle
                class ChangedRoot end
                """,
                "USE_MODEL_TEXT", "REPLACE_UML_MODEL", true, "bundle.use", "use", "ocl-editor", null));

        Project saved = projectService.loadProject(project.id());
        assertThat(response.success()).as(response.diagnostics().toString()).isTrue();
        assertThat(saved.modelText().sourceFiles()).singleElement()
                .satisfies(source -> {
                    assertThat(source.sourcePath()).isEqualTo("shared/member.use");
                    assertThat(source.text()).contains("class Member");
                });
        assertThat(saved.umlModel().classes()).extracting(type -> type.name())
                .containsExactly("Member", "ChangedRoot");
    }

    @Test
    void appliesLibraryModelTextToUmlModelAndStoresText() {
        Project project = projectService.createProject("Library", null);

        ApplyModelTextResponseDto response = service.applyModelText(project.id(), new ApplyModelTextRequestDto(
                libraryModelText(),
                "USE_MODEL_TEXT",
                "REPLACE_UML_MODEL",
                true,
                "library.use",
                "use",
                "open-existing",
                null));

        Project saved = projectService.loadProject(project.id());
        assertThat(response.success()).isTrue();
        assertThat(response.status()).isEqualTo("APPLIED");
        assertThat(response.diagnostics()).isEmpty();
        assertThat(response.changedElementIds()).contains("class-user", "class-book", "assoc-borrows", "inv-max-books");
        assertThat(saved.modelText().text()).contains("context User");
        assertThat(saved.modelText().text()).contains("inv maxBooks:");
        assertThat(saved.umlModel().findClass(new UmlClassId("class-user"))).isPresent();
        assertThat(saved.umlModel().findClass(new UmlClassId("class-book"))).isPresent();
        assertThat(saved.umlModel().associations()).singleElement()
                .satisfies(association -> assertThat(association.name()).isEqualTo("Borrows"));
        assertThat(saved.umlModel().findInvariant(new UmlInvariantId("inv-max-books")))
                .get()
                .satisfies(invariant -> assertThat(invariant.expression().text()).isEqualTo("self.books <= 5"));
    }

    @Test
    void returnsStructuredDiagnosticWithoutApplyingMalformedImportText() {
        Project project = projectService.createProject("Import", null);

        ApplyModelTextResponseDto response = service.applyModelText(project.id(), new ApplyModelTextRequestDto(
                "import other.use",
                "USE_MODEL_TEXT",
                "REPLACE_UML_MODEL",
                true,
                "unsupported.use",
                "use",
                "open-existing",
                null));

        Project saved = projectService.loadProject(project.id());
        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo("NOT_APPLIED");
        assertThat(response.changedElementIds()).isEmpty();
        assertThat(response.diagnostics()).singleElement()
                .satisfies(diagnostic -> {
                    assertThat(diagnostic.code()).isEqualTo("INVALID_IMPORT_SYNTAX");
                    assertThat(diagnostic.severity()).isEqualTo("ERROR");
                });
        assertThat(saved.modelText().text()).isEqualTo("import other.use");
        assertThat(saved.umlModel().classes()).isEmpty();
    }

    @Test
    void appliesAssociationsThatReferenceInlineEmptyClasses() {
        Project project = projectService.createProject("Train", null);

        ApplyModelTextResponseDto response = service.applyModelText(project.id(), new ApplyModelTextRequestDto(
                """
                model TrainModel

                class Train end
                class Journey end

                association Assignment between
                  Journey[*]
                  Train[1]
                end
                """,
                "USE_MODEL_TEXT",
                "REPLACE_UML_MODEL",
                true,
                "train.use",
                "use",
                "open-existing",
                null));

        Project saved = projectService.loadProject(project.id());
        assertThat(response.success()).isTrue();
        assertThat(response.diagnostics()).isEmpty();
        assertThat(saved.umlModel().findClass(new UmlClassId("class-train"))).isPresent();
        assertThat(saved.umlModel().findClass(new UmlClassId("class-journey"))).isPresent();
        assertThat(saved.umlModel().associations()).singleElement()
                .satisfies(association -> assertThat(association.ends())
                        .extracting(end -> end.classId().value())
                        .containsExactly("class-journey", "class-train"));
        assertThat(saved.umlModel().associations().getFirst().ends())
                .extracting(end -> end.roleName()).containsExactly("journey", "train");
        assertThat(saved.umlModel().associations().getFirst().ends())
                .extracting(end -> end.id().value()).containsExactly("assocend-assignment-journey", "assocend-assignment-train");
    }

    @Test
    void derivesUseRoleNameFromClassifierAndTypechecksNavigation() {
        Project project = projectService.createProject("Implicit roles", null);

        ApplyModelTextResponseDto response = service.applyModelText(project.id(), new ApplyModelTextRequestDto(
                """
                model InvoiceModel

                class Invoice
                  constraints
                    inv lineItemsAccessible: self.lineItem->size() >= 0
                end

                class LineItem end

                composition Invoice_LineItem between
                  Invoice[1]
                  LineItem[0..*]
                end
                """,
                "USE_MODEL_TEXT",
                "REPLACE_UML_MODEL",
                true,
                "invoice.use",
                "use",
                "open-existing",
                null));

        assertThat(response.success()).as(response.diagnostics().toString()).isTrue();
        assertThat(response.diagnostics()).isEmpty();
        assertThat(projectService.loadProject(project.id()).umlModel().associations()).singleElement()
                .satisfies(association -> assertThat(association.ends())
                        .extracting(end -> end.roleName())
                        .containsExactly("invoice", "lineItem"));
    }

    @Test
    void importsClassifierFeatureAndTypeMetadataAndPreservesItInJson() {
        Project project = projectService.createProject("B53", null);

        ApplyModelTextResponseDto response = service.applyModelText(project.id(), new ApplyModelTextRequestDto(
                """
                model B53
                enum Status { OPEN, CLOSED }
                abstract class Named
                attributes
                  name : String init = 'unknown'
                operations
                  label() : String = self.name
                end
                class Auditable end
                class Invoice < Named, Auditable
                attributes
                  status : Status init = #OPEN
                  total : Real derive = 1.0
                  history : Sequence(Tuple(amount : Real, status : Status))
                operations
                  close(reason : String) : Boolean = reason.size() > 0
                end
                dataType Money
                operations
                  Money(amount : Real, currency : String)
                  multiply(factor : Real) : Money = Money(self.amount * factor, self.currency)
                end
                """,
                "USE_MODEL_TEXT", "REPLACE_UML_MODEL", true, "b53.use", "use", "open-existing", null));

        assertThat(response.success()).isTrue();
        assertThat(response.diagnostics()).isEmpty();
        Project saved = projectService.loadProject(project.id());
        assertThat(saved.umlModel().classes()).filteredOn(type -> type.name().equals("Named")).singleElement()
                .satisfies(type -> {
                    assertThat(type.abstractClass()).isTrue();
                    assertThat(type.attributes().getFirst().initExpression()).isEqualTo("'unknown'");
                    assertThat(type.operations().getFirst().bodyExpression()).isEqualTo("self.name");
                });
        assertThat(saved.umlModel().classes()).filteredOn(type -> type.name().equals("Invoice")).singleElement()
                .satisfies(type -> {
                    assertThat(type.superClassIds()).extracting(UmlClassId::value)
                            .containsExactly("class-named", "class-auditable");
                    assertThat(type.attributes().get(1).deriveExpression()).isEqualTo("1.0");
                    assertThat(type.attributes().get(2).type().name())
                            .isEqualTo("Sequence(Tuple(amount:Real, status:Status))");
                    assertThat(type.operations().getFirst().bodyExpression()).isEqualTo("reason.size() > 0");
                });
        assertThat(saved.umlModel().enumerations()).singleElement()
                .satisfies(type -> assertThat(type.literals()).containsExactly("OPEN", "CLOSED"));
        assertThat(saved.umlModel().dataTypes()).singleElement().satisfies(type -> {
            assertThat(type.properties()).extracting(property -> property.name())
                    .containsExactly("amount", "currency");
            assertThat(type.operations()).singleElement()
                    .satisfies(operation -> assertThat(operation.bodyExpression())
                            .isEqualTo("Money(self.amount * factor, self.currency)"));
        });

        Project restored = new ProjectJsonSerializer().deserialize(new ProjectJsonSerializer().serialize(saved));
        assertThat(restored.umlModel()).isEqualTo(saved.umlModel());
    }

    @Test
    void importsAssociationMetadataAndPreservesItInJson() {
        Project project = projectService.createProject("B54", null);

        ApplyModelTextResponseDto response = service.applyModelText(project.id(), new ApplyModelTextRequestDto(
                """
                model B54
                class Company end
                class Person end
                class Office end
                class BaseA end
                class BaseB end
                class SubA < BaseA end
                class SubB < BaseB end

                aggregation Staffing between
                  Company[1] role company
                  Person[1..8,10,15..*] role staff ordered qualifier (number : Integer)
                end

                composition Locations between
                  Company[1] role owner
                  Office[*] role offices
                  Person[0..1] role manager
                end

                association DerivedStaff between
                  Company[1] role derivedCompany
                  Person[*] role selectedStaff derive = self.staff->select(person | true)
                end

                association BaseRelation between
                  BaseA[*] role baseOwners union
                  BaseB[*] role baseItems
                end

                association SpecializedRelation between
                  SubA[*] role subOwners subsets baseOwners
                  SubB[*] role subItems redefines baseItems
                end

                associationclass Employment
                between
                  Company[1] role employer
                  Person[*] role employees
                attributes
                  salary : Integer
                operations
                  active() : Boolean = true
                end
                """,
                "USE_MODEL_TEXT", "REPLACE_UML_MODEL", true, "b54.use", "use", "open-existing", null));

        assertThat(response.success()).as(response.diagnostics().toString()).isTrue();
        assertThat(response.diagnostics()).isEmpty();
        Project saved = projectService.loadProject(project.id());
        assertThat(saved.umlModel().associations()).filteredOn(association -> association.name().equals("Staffing"))
                .singleElement().satisfies(association -> {
                    assertThat(association.ends().getFirst().aggregationKind()).isEqualTo(AggregationKind.SHARED);
                    assertThat(association.ends().get(1)).satisfies(end -> {
                        assertThat(end.ordered()).isTrue();
                        assertThat(end.qualifiers()).singleElement()
                                .satisfies(qualifier -> assertThat(qualifier.name()).isEqualTo("number"));
                        assertThat(end.multiplicity().raw()).isEqualTo("1..8,10,15..*");
                        assertThat(end.multiplicity().contains(8)).isTrue();
                        assertThat(end.multiplicity().contains(9)).isFalse();
                        assertThat(end.multiplicity().contains(10)).isTrue();
                        assertThat(end.multiplicity().contains(14)).isFalse();
                        assertThat(end.multiplicity().contains(15)).isTrue();
                    });
                });
        assertThat(saved.umlModel().associations()).filteredOn(association -> association.name().equals("Locations"))
                .singleElement().satisfies(association -> {
                    assertThat(association.ends()).hasSize(3);
                    assertThat(association.ends().getFirst().aggregationKind()).isEqualTo(AggregationKind.COMPOSITE);
                });
        assertThat(saved.umlModel().associations()).filteredOn(association -> association.name().equals("DerivedStaff"))
                .singleElement().satisfies(association -> assertThat(association.ends().get(1).deriveExpression())
                        .isEqualTo("self.staff->select(person | true)"));
        assertThat(saved.umlModel().associations())
                .filteredOn(association -> association.name().equals("SpecializedRelation"))
                .singleElement().satisfies(association -> {
                    assertThat(association.ends().getFirst().subsettedEndIds()).extracting(id -> id.value())
                            .containsExactly("assocend-base-relation-base-owners");
                    assertThat(association.ends().get(1).redefinedEndIds()).extracting(id -> id.value())
                            .containsExactly("assocend-base-relation-base-items");
                });
        assertThat(saved.umlModel().associations()).filteredOn(association -> association.name().equals("Employment"))
                .singleElement().satisfies(association -> assertThat(association.associationClassId().value())
                        .isEqualTo("class-employment"));
        assertThat(saved.umlModel().classes()).filteredOn(type -> type.name().equals("Employment"))
                .singleElement().satisfies(type -> {
                    assertThat(type.attributes()).extracting(attribute -> attribute.name()).containsExactly("salary");
                    assertThat(type.operations()).extracting(operation -> operation.name()).containsExactly("active");
                });
        assertThat(response.changedElementIds()).contains("qualifier-staffing-staff-number");

        Project restored = new ProjectJsonSerializer().deserialize(new ProjectJsonSerializer().serialize(saved));
        assertThat(restored.umlModel()).isEqualTo(saved.umlModel());
    }

    @Test
    void importsAndTypechecksEmbeddedOclContracts() {
        Project project = projectService.createProject("B55", null);

        ApplyModelTextResponseDto response = service.applyModelText(project.id(), new ApplyModelTextRequestDto(
                """
                model B55
                class Account
                attributes
                  balance : Integer
                operations
                  deposit(amount : Integer) : Integer = balance + amount
                    pre positiveAmount: amount > 0
                    post resultMatches: result = balance + amount
                constraints
                  inv nonNegative: balance >= 0
                  existential inv richAccount: balance > 100
                end

                constraints
                context account : Account
                  inv aliasWorks: account.balance >= 0

                context Account::deposit(amount : Integer) : Integer
                  pre externalPositive: amount > 0
                  post externalResult: result >= 0
                """,
                "USE_MODEL_TEXT", "REPLACE_UML_MODEL", true, "b55.use", "use", "open-existing", null));

        assertThat(response.success()).as(response.diagnostics().toString()).isTrue();
        assertThat(response.diagnostics()).isEmpty();
        Project saved = projectService.loadProject(project.id());
        assertThat(saved.umlModel().classes()).singleElement().satisfies(type ->
                assertThat(type.operations()).singleElement().satisfies(operation -> {
                    assertThat(operation.bodyExpression()).isEqualTo("balance + amount");
                    assertThat(operation.contracts()).extracting(contract -> contract.name())
                            .containsExactly("positiveAmount", "resultMatches", "externalPositive", "externalResult");
                    assertThat(operation.contracts()).extracting(contract -> contract.kind().name())
                            .containsExactly("PRE", "POST", "PRE", "POST");
                }));
        assertThat(saved.umlModel().invariants()).hasSize(3);
        assertThat(saved.umlModel().invariants()).filteredOn(invariant -> invariant.name().equals("richAccount"))
                .singleElement().satisfies(invariant -> assertThat(invariant.existential()).isTrue());
        assertThat(saved.umlModel().invariants()).filteredOn(invariant -> invariant.name().equals("aliasWorks"))
                .singleElement().satisfies(invariant -> assertThat(invariant.contextVariableNames())
                        .containsExactly("account"));

        Project restored = new ProjectJsonSerializer().deserialize(new ProjectJsonSerializer().serialize(saved));
        assertThat(restored.umlModel()).isEqualTo(saved.umlModel());
    }

    @Test
    void importsUseDefinednessAliasesInOperationContracts() {
        Project project = projectService.createProject("Employee", null);

        ApplyModelTextResponseDto response = service.applyModelText(project.id(), new ApplyModelTextRequestDto(
                """
                model Employee

                class Person
                attributes
                  name : String
                  age : Integer
                  salary : Real
                operations
                  raiseSalary(rate : Real) : Real
                end

                class Company
                attributes
                  name : String
                  location : String
                operations
                  hire(p : Person)
                  fire(p : Person)
                end

                association WorksFor between
                  Person[*] role employee
                  Company[0..1] role employer
                end

                constraints
                context Person::raiseSalary(rate : Real) : Real
                  post raiseSalaryPost: salary = salary@pre * (1.0 + rate)
                  post resultPost: result = salary

                context Company::hire(p : Person)
                  pre personProvided: p.isDefined()
                  pre personNotEmployed: employee->excludes(p)
                  post personEmployed: employee->includes(p)

                context Company::fire(p : Person)
                  pre personEmployed: employee->includes(p)
                  post personNoLongerEmployed: employee->excludes(p)
                """,
                "USE_MODEL_TEXT", "REPLACE_UML_MODEL", true, "Employee.use", "use", "open-existing", null));

        assertThat(response.success()).as(response.diagnostics().toString()).isTrue();
        assertThat(response.diagnostics()).isEmpty();
    }

    @Test
    void rejectsEmbeddedContractWithNonBooleanResult() {
        Project project = projectService.createProject("B55 invalid", null);

        ApplyModelTextResponseDto response = service.applyModelText(project.id(), new ApplyModelTextRequestDto(
                """
                model InvalidContract
                class Account
                operations
                  deposit(amount : Integer) : Integer = amount
                    pre invalidContract: amount
                end
                """,
                "USE_MODEL_TEXT", "REPLACE_UML_MODEL", true, "invalid-b55.use", "use", "open-existing", null));

        assertThat(response.success()).isFalse();
        assertThat(response.status()).isEqualTo("NOT_APPLIED");
        assertThat(response.diagnostics()).extracting(diagnostic -> diagnostic.code())
                .contains("OCL_EMBEDDED_TYPE_MISMATCH");
        assertThat(projectService.loadProject(project.id()).umlModel().classes()).isEmpty();
    }

    private String libraryModelText() {
        return """
                model Library

                class User
                attributes
                  name : String
                  books : Integer
                operations
                  canBorrow(count : Integer) : Boolean
                end

                class Book
                attributes
                  title : String
                  available : Boolean
                end

                association Borrows between
                  User[1] role borrower
                  Book[0..*] role borrowedBooks
                end

                constraints
                context User
                  inv maxBooks:
                  self.books <= 5
                """;
    }
}
