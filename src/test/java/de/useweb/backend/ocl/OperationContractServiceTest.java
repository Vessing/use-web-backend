package de.useweb.backend.ocl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.useweb.backend.domain.snapshot.ObjectInstance;
import de.useweb.backend.domain.snapshot.ObjectInstanceId;
import de.useweb.backend.domain.snapshot.ObjectModel;
import de.useweb.backend.domain.snapshot.ObjectModelId;
import de.useweb.backend.domain.snapshot.Slot;
import de.useweb.backend.domain.snapshot.SlotId;
import de.useweb.backend.domain.snapshot.SlotValue;
import de.useweb.backend.domain.uml.UmlAttribute;
import de.useweb.backend.domain.uml.UmlAttributeId;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlModelId;
import de.useweb.backend.domain.uml.UmlOperation;
import de.useweb.backend.domain.uml.UmlOperationId;
import de.useweb.backend.domain.uml.UmlParameter;
import de.useweb.backend.domain.uml.UmlParameterId;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.ocl.ast.AtPreExpression;
import de.useweb.backend.ocl.ast.BinaryExpression;
import de.useweb.backend.ocl.contract.OperationConstraintKind;
import de.useweb.backend.ocl.contract.OperationContext;
import de.useweb.backend.ocl.contract.OperationContextReference;
import de.useweb.backend.ocl.contract.OperationContract;
import de.useweb.backend.ocl.contract.OperationContractId;
import de.useweb.backend.ocl.contract.OperationContractParser;
import de.useweb.backend.ocl.contract.OperationContractResult;
import de.useweb.backend.ocl.contract.OperationContractService;
import de.useweb.backend.ocl.contract.OperationInvocationId;
import de.useweb.backend.ocl.contract.OperationResultSlot;
import de.useweb.backend.ocl.value.BooleanValue;
import de.useweb.backend.ocl.value.StringValue;

class OperationContractServiceTest {
    private static final UmlClassId USER = new UmlClassId("class-user");
    private static final UmlClassId BOOK = new UmlClassId("class-book");
    private static final UmlOperationId RENAME = new UmlOperationId("operation-rename");
    private static final UmlParameterId NEW_NAME = new UmlParameterId("parameter-new-name");
    private static final UmlAttributeId USER_NAME = new UmlAttributeId("attribute-user-name");
    private static final UmlAttributeId BOOK_TITLE = new UmlAttributeId("attribute-book-title");
    private static final ObjectInstanceId ALICE = new ObjectInstanceId("object-alice");

    private final OperationContractParser parser = new OperationContractParser();
    private final OperationContractService service = new OperationContractService();

    @Test
    void parsesContractDeclarationsAndDedicatedResultAndAtPreNodes() {
        var parsed = parser.parse(new OperationContractId("contract-post"),
                "context User::rename(newName : String) post Changed: self.name@pre <> self.name and result",
                model());

        assertThat(parsed.success()).isTrue();
        assertThat(parsed.contract().kind()).isEqualTo(OperationConstraintKind.POSTCONDITION);
        assertThat(parsed.contract().expression()).isInstanceOf(BinaryExpression.class);
        BinaryExpression root = (BinaryExpression) parsed.contract().expression();
        assertThat(root.left()).as("left side contains @pre comparison").isInstanceOf(BinaryExpression.class);
        assertThat(((BinaryExpression) root.left()).left()).isInstanceOf(AtPreExpression.class);
    }

    @Test
    void evaluatesSatisfiedAndViolatedPreconditionsInThePreState() {
        OperationContext valid = context("Alicia", beforeObjects(), afterObjects(), new BooleanValue(true));
        OperationContext invalid = context("", beforeObjects(), afterObjects(), new BooleanValue(true));
        OperationContract contract = parse("contract-pre",
                "context User::rename(newName : String) pre NotBlank: newName <> ''");

        assertThat(service.evaluate(valid, contract).status()).isEqualTo(OperationContractResult.Status.SATISFIED);
        OperationContractResult violation = service.evaluate(invalid, contract);
        assertThat(violation.status()).isEqualTo(OperationContractResult.Status.VIOLATED);
        assertThat(violation.diagnostics()).extracting("code").containsExactly("PRECONDITION_VIOLATION");
        assertThat(violation.receiverId()).isEqualTo(ALICE);
    }

    @Test
    void evaluatesPostconditionsWithResultAndAtPreAgainstSeparateSnapshots() {
        OperationContext context = context("Alicia", beforeObjects(), afterObjects(), new BooleanValue(true));
        OperationContract satisfied = parse("contract-post-ok",
                "context User::rename(newName : String) post Changed: self.name@pre = 'Alice' and self.name = newName and result");
        OperationContract violated = parse("contract-post-fail",
                "context User::rename(newName : String) post WrongName: self.name = 'Bob'");

        assertThat(service.evaluate(context, satisfied).status()).isEqualTo(OperationContractResult.Status.SATISFIED);
        OperationContractResult violation = service.evaluate(context, violated);
        assertThat(violation.status()).isEqualTo(OperationContractResult.Status.VIOLATED);
        assertThat(violation.diagnostics()).extracting("code").containsExactly("POSTCONDITION_VIOLATION");
    }

    @Test
    void evaluatesNewnessAndPreStateExtentForCreatedAndDeletedObjects() {
        OperationContext context = context("Alicia", beforeObjects(), afterObjects(), new BooleanValue(true));
        OperationContract contract = parse("contract-lifetime",
                "context User::rename(newName : String) post Lifetime: "
                        + "Book.allInstances()->exists(b | b.oclIsNew()) and "
                        + "Book.allInstances()@pre->exists(b | b.title = 'Old')");

        assertThat(service.evaluate(context, contract).status()).isEqualTo(OperationContractResult.Status.SATISFIED);
    }

    @Test
    void reportsPhaseAndContextErrorsWithoutTreatingThemAsViolations() {
        OperationContext noResult = context("Alicia", beforeObjects(), afterObjects(), null);
        OperationContract illegalPreResult = parse("contract-pre-result",
                "context User::rename(newName : String) pre IllegalResult: result");
        OperationContract post = parse("contract-post-result",
                "context User::rename(newName : String) post HasResult: result");

        assertThat(service.evaluate(noResult, illegalPreResult).status())
                .isEqualTo(OperationContractResult.Status.CONTEXT_ERROR);
        OperationContractResult missingResult = service.evaluate(noResult, post);
        assertThat(missingResult.status()).isEqualTo(OperationContractResult.Status.CONTEXT_ERROR);
        assertThat(missingResult.diagnostics()).isNotEmpty();
    }

    private OperationContract parse(String id, String source) {
        return parser.parse(new OperationContractId(id), source, model()).optionalContract().orElseThrow();
    }

    private static OperationContext context(String newName, List<ObjectInstance> before, List<ObjectInstance> after,
            BooleanValue result) {
        return new OperationContext(new OperationInvocationId("invocation-rename"),
                new OperationContextReference(USER, RENAME), model(), ALICE,
                snapshot("snapshot-before", before), snapshot("snapshot-after", after),
                Map.of(NEW_NAME, new StringValue(newName)),
                result == null ? OperationResultSlot.unavailable() : OperationResultSlot.of(result));
    }

    private static UmlModel model() {
        UmlOperation rename = new UmlOperation(RENAME, "rename", UmlType.BOOLEAN,
                List.of(new UmlParameter(NEW_NAME, "newName", UmlType.STRING)));
        UmlClass user = new UmlClass(USER, "User",
                List.of(new UmlAttribute(USER_NAME, "name", UmlType.STRING)), List.of(rename));
        UmlClass book = new UmlClass(BOOK, "Book",
                List.of(new UmlAttribute(BOOK_TITLE, "title", UmlType.STRING)), List.of());
        return new UmlModel(new UmlModelId("model-contract"), "Contract model",
                List.of(user, book), List.of(), List.of());
    }

    private static List<ObjectInstance> beforeObjects() {
        return List.of(user("Alice"), book("object-old", "Old"));
    }

    private static List<ObjectInstance> afterObjects() {
        return List.of(user("Alicia"), book("object-new", "New"));
    }

    private static ObjectInstance user(String name) {
        return new ObjectInstance(ALICE, "alice", USER,
                List.of(new Slot(new SlotId("slot-user-name"), USER_NAME, SlotValue.ofString(name))));
    }

    private static ObjectInstance book(String id, String title) {
        return new ObjectInstance(new ObjectInstanceId(id), id, BOOK,
                List.of(new Slot(new SlotId("slot-" + id + "-title"), BOOK_TITLE, SlotValue.ofString(title))));
    }

    private static ObjectModel snapshot(String id, List<ObjectInstance> objects) {
        return new ObjectModel(new ObjectModelId(id), id, new ArrayList<>(objects), List.of());
    }
}
