package de.useweb.backend.modeltext.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ModelTextParserTest {

    private final ModelTextParser parser = new ModelTextParser();

    @Test
    void parsesWildcardSingleAndSelectiveImports() {
        ModelTextParser.ModelTextParseResult result = parser.parse("""
                import * from "all.use"
                import Date from "dates.use"
                import { Member, Book } from "library.use"
                model Imports
                class Imports end
                """);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.imports()).hasSize(3);
        assertThat(result.imports().getFirst().wildcard()).isTrue();
        assertThat(result.imports().get(1).selectedNames()).containsExactly("Date");
        assertThat(result.imports().get(2).selectedNames()).containsExactly("Member", "Book");
    }

    @Test
    void keepsExistingLibraryImportCompatible() {
        ModelTextParser.ModelTextParseResult result = parser.parse("""
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
                """);

        assertThat(result.diagnostics()).as(result.diagnostics().toString()).isEmpty();
        assertThat(result.classes()).hasSize(2);
        assertThat(result.associations()).hasSize(1);
        assertThat(result.invariants()).hasSize(1);
    }

    @Test
    void parsesCommentsSemicolonsMultilineDeclarationsAndBalancedTypes() {
        ModelTextParser.ModelTextParseResult result = parser.parse("""
                \uFEFFmodel TrainModel;

                /* Operations may span multiple lines. */
                class Waggon
                attributes
                  anzahlPlaetze : Integer;
                  labels : Sequence(
                    String
                  ); -- trailing comment
                operations
                  overlaps(
                    first : Bahnhof,
                    second : Set(
                      Bahnhof
                    )
                  ) : Set(
                    Bahnhof
                  ) =
                    if first = first then second else second endif;
                end

                class Bahnhof end

                association Halt between
                  Waggon[1];
                  Bahnhof[2..*] role bahnhoefe ordered;
                end

                constraints
                context Waggon inv ValidCapacity:
                  self.anzahlPlaetze > 0
                """);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.modelName()).isEqualTo("TrainModel");
        assertThat(result.classes()).hasSize(2);
        assertThat(result.classes().getFirst().attributes())
                .extracting(ModelTextParser.ModelTextAttribute::type)
                .containsExactly("Integer", "Sequence(String)");
        assertThat(result.classes().getFirst().operations()).singleElement().satisfies(operation -> {
            assertThat(operation.name()).isEqualTo("overlaps");
            assertThat(operation.returnType()).isEqualTo("Set(Bahnhof)");
            assertThat(operation.parameters())
                    .extracting(ModelTextParser.ModelTextParameter::type)
                    .containsExactly("Bahnhof", "Set(Bahnhof)");
        });
        assertThat(result.associations()).singleElement().satisfies(association ->
                assertThat(association.ends()).extracting(ModelTextParser.ModelTextAssociationEnd::multiplicity)
                        .containsExactly("1", "2..*"));
        assertThat(result.invariants()).singleElement().satisfies(invariant ->
                assertThat(invariant.expression()).isEqualTo("self.anzahlPlaetze > 0"));
    }

    @Test
    void reportsPreciseRangeAndRecoversAtNextMember() {
        ModelTextParser.ModelTextParseResult result = parser.parse("""
                model Recovery
                class Item
                attributes
                  broken Set(String);
                  valid : Integer;
                  unbalanced : Set(String
                  afterBroken : Boolean;
                end
                """);

        assertThat(result.classes()).singleElement().satisfies(modelClass ->
                assertThat(modelClass.attributes())
                        .extracting(ModelTextParser.ModelTextAttribute::name)
                        .containsExactly("valid", "unbalanced", "afterBroken"));
        assertThat(result.diagnostics())
                .extracting(diagnostic -> diagnostic.code())
                .contains("UNSUPPORTED_ATTRIBUTE_SYNTAX", "UNBALANCED_TYPE_DELIMITER");
        assertThat(result.diagnostics().stream()
                .filter(diagnostic -> diagnostic.code().equals("UNSUPPORTED_ATTRIBUTE_SYNTAX"))
                .findFirst()).get().satisfies(diagnostic -> {
                    assertThat(diagnostic.sourceRange().startLine()).isEqualTo(4);
                    assertThat(diagnostic.sourceRange().startColumn()).isEqualTo(3);
                    assertThat(diagnostic.sourceRange().endOffset()).isGreaterThan(diagnostic.sourceRange().startOffset());
                });
    }

    @Test
    void decodesUtf8Utf16AndWindows1252Sources() {
        String source = "model Encodings\nclass Münze end\n";
        byte[] utf8 = source.getBytes(StandardCharsets.UTF_8);
        byte[] utf16Body = source.getBytes(StandardCharsets.UTF_16LE);
        byte[] utf16 = new byte[utf16Body.length + 2];
        utf16[0] = (byte) 0xFF;
        utf16[1] = (byte) 0xFE;
        System.arraycopy(utf16Body, 0, utf16, 2, utf16Body.length);
        byte[] windows1252 = source.getBytes(Charset.forName("windows-1252"));

        assertThat(parser.parse(utf8).classes().getFirst().name()).isEqualTo("Münze");
        assertThat(parser.parse(utf16).classes().getFirst().name()).isEqualTo("Münze");
        assertThat(parser.parse(windows1252).classes().getFirst().name()).isEqualTo("Münze");
    }

    @Test
    void skipsImperativeOperationBodiesWithInternalSemicolons() {
        ModelTextParser.ModelTextParseResult result = parser.parse("""
                model Commands
                class Worker
                operations
                  update(value : Integer) begin
                    self.value := value;
                    if value > 0 then
                      self.active := true;
                    end;
                  end;
                  active() : Boolean
                end
                """);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.classes()).singleElement().satisfies(modelClass ->
                assertThat(modelClass.operations())
                        .extracting(ModelTextParser.ModelTextOperation::name)
                        .containsExactly("update", "active"));
    }

    @Test
    void parsesClassifiersFeaturesAndStructuredTypes() {
        ModelTextParser.ModelTextParseResult result = parser.parse("""
                model Classifiers

                enum Status { OPEN, CLOSED }

                abstract class Named
                attributes
                  name : String init = 'unknown'
                operations
                  label() : String = self.name
                end

                class Invoice < Named, Auditable
                attributes
                  status : Status init: #OPEN
                  totals : Sequence(Tuple(amount : Real, currency : String))
                  complete : Boolean derive = self.status = #CLOSED
                operations
                  close(reason : String) : Boolean =
                    reason.size() > 0
                end

                class Auditable end

                dataType Money
                operations
                  Money(amount : Real, currency : String)
                  multiply(factor : Real) : Money =
                    Money(self.amount * factor, self.currency)
                end
                """);

        assertThat(result.dataTypes()).as(result.toString()).singleElement()
                .satisfies(dataType -> assertThat(dataType.operations()).as(dataType.toString()).hasSize(1));
        assertThat(result.diagnostics()).as(result.diagnostics().toString()).isEmpty();
        assertThat(result.enumerations()).singleElement().satisfies(enumeration -> {
            assertThat(enumeration.name()).isEqualTo("Status");
            assertThat(enumeration.literals()).containsExactly("OPEN", "CLOSED");
        });
        assertThat(result.classes()).hasSize(3);
        assertThat(result.classes().getFirst()).satisfies(modelClass -> {
            assertThat(modelClass.abstractClass()).isTrue();
            assertThat(modelClass.attributes().getFirst().initExpression()).isEqualTo("'unknown'");
            assertThat(modelClass.operations().getFirst().bodyExpression()).isEqualTo("self.name");
        });
        assertThat(result.classes().get(1)).satisfies(modelClass -> {
            assertThat(modelClass.superClassNames()).containsExactly("Named", "Auditable");
            assertThat(modelClass.attributes()).extracting(ModelTextParser.ModelTextAttribute::type)
                    .containsExactly("Status", "Sequence(Tuple(amount:Real, currency:String))", "Boolean");
            assertThat(modelClass.attributes().get(2).deriveExpression()).isEqualTo("self.status = #CLOSED");
            assertThat(modelClass.operations().getFirst().bodyExpression()).isEqualTo("reason.size() > 0");
        });
        assertThat(result.dataTypes()).singleElement().satisfies(dataType -> {
            assertThat(dataType.properties()).extracting(ModelTextParser.ModelTextAttribute::name)
                    .containsExactly("amount", "currency");
            assertThat(dataType.operations()).singleElement().satisfies(operation -> {
                assertThat(operation.name()).isEqualTo("multiply");
                assertThat(operation.returnType()).isEqualTo("Money");
                assertThat(operation.bodyExpression()).isEqualTo("Money(self.amount * factor, self.currency)");
            });
        });
    }

    @Test
    void parsesCompleteAssociationSyntax() {
        ModelTextParser.ModelTextParseResult result = parser.parse("""
                model Associations
                class A end
                class B end
                class C end

                aggregation Shared between
                  A[1] role owner
                  B[1..8,10,15..*] role items ordered qualifier (position : Integer, key : String)
                end

                composition Tree between
                  A[1] role root
                  B[*] role branches
                  C[0..1] role leaf
                end

                association Base between
                  A[*] role baseA union
                  B[*] role baseB
                end

                association Specialized between
                  A[*] role specializedA subsets baseA
                  B[*] role specializedB redefines baseB derive =
                    self.baseB->select(value | true)
                end

                associationclass Employment
                between
                  A[1] role employer
                  B[*] role employees
                attributes
                  salary : Integer
                operations
                  active() : Boolean = true
                end
                """);

        assertThat(result.diagnostics()).as(result.diagnostics().toString()).isEmpty();
        assertThat(result.associations()).hasSize(5);
        assertThat(result.associations().getFirst()).satisfies(association -> {
            assertThat(association.kind()).isEqualTo("AGGREGATION");
            assertThat(association.ends().get(1)).satisfies(end -> {
                assertThat(end.multiplicity()).isEqualTo("1..8, 10, 15..*");
                assertThat(end.ordered()).isTrue();
                assertThat(end.qualifiers()).extracting(ModelTextParser.ModelTextParameter::name)
                        .containsExactly("position", "key");
            });
        });
        assertThat(result.associations().get(1)).satisfies(association -> {
            assertThat(association.kind()).isEqualTo("COMPOSITION");
            assertThat(association.ends()).hasSize(3);
        });
        assertThat(result.associations().get(2).ends().getFirst()).satisfies(end -> {
            assertThat(end.union()).isTrue();
            assertThat(end.derived()).isTrue();
        });
        assertThat(result.associations().get(3).ends()).satisfies(ends -> {
            assertThat(ends.getFirst().subsettedRoleNames()).containsExactly("baseA");
            assertThat(ends.get(1).redefinedRoleNames()).containsExactly("baseB");
            assertThat(ends.get(1).deriveExpression()).isEqualTo("self.baseB->select(value | true)");
        });
        assertThat(result.associations().get(4).associationClassName()).isEqualTo("Employment");
        assertThat(result.classes()).filteredOn(type -> type.name().equals("Employment")).singleElement()
                .satisfies(type -> {
                    assertThat(type.attributes()).extracting(ModelTextParser.ModelTextAttribute::name)
                            .containsExactly("salary");
                    assertThat(type.operations()).extracting(ModelTextParser.ModelTextOperation::name)
                            .containsExactly("active");
                });
    }

    @Test
    void parsesEmbeddedOclContractsAndInvariantVariants() {
        ModelTextParser.ModelTextParseResult result = parser.parse("""
                model Contracts
                class Account
                attributes
                  balance : Integer
                operations
                  deposit(amount : Integer) : Integer = balance + amount
                    pre positiveAmount: amount > 0
                    post resultMatches: result = balance
                constraints
                  inv nonNegative: balance >= 0
                  inv: true
                  existential inv someAccount: balance > 100
                end

                constraints
                context account : Account
                  inv aliasWorks: account.balance >= 0

                context Account::deposit(amount : Integer) : Integer
                  pre externalPositive: amount > 0
                  post externalResult: result >= 0
                """);

        assertThat(result.diagnostics()).as(result.diagnostics().toString()).isEmpty();
        assertThat(result.classes()).singleElement().satisfies(type ->
                assertThat(type.operations()).singleElement().satisfies(operation -> {
                    assertThat(operation.bodyExpression()).isEqualTo("balance + amount");
                    assertThat(operation.contracts())
                            .extracting(ModelTextParser.ModelTextOperationContract::name)
                            .containsExactly("positiveAmount", "resultMatches");
                }));
        assertThat(result.invariants()).hasSize(4);
        assertThat(result.invariants()).filteredOn(ModelTextParser.ModelTextInvariant::existential)
                .singleElement().satisfies(invariant -> assertThat(invariant.name()).isEqualTo("someAccount"));
        assertThat(result.invariants()).filteredOn(invariant -> invariant.name().equals("aliasWorks"))
                .singleElement().satisfies(invariant -> {
                    assertThat(invariant.contextClass()).isEqualTo("Account");
                    assertThat(invariant.contextVariableNames()).containsExactly("account");
                });
        assertThat(result.operationContexts()).singleElement().satisfies(context -> {
            assertThat(context.contextClass()).isEqualTo("Account");
            assertThat(context.operationName()).isEqualTo("deposit");
            assertThat(context.contracts()).extracting(ModelTextParser.ModelTextOperationContract::kind)
                    .containsExactly("PRE", "POST");
        });
    }
}
