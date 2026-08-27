package de.useweb.backend.application.operation;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import de.useweb.backend.api.dto.operation.OperationArgumentDto;
import de.useweb.backend.api.dto.operation.OperationInvocationRequestDto;
import de.useweb.backend.api.dto.snapshot.SlotValueDto;
import de.useweb.backend.application.project.ProjectService;
import de.useweb.backend.domain.layout.LayoutInformation;
import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.project.ProjectId;
import de.useweb.backend.domain.project.ProjectMetadata;
import de.useweb.backend.domain.snapshot.ObjectInstance;
import de.useweb.backend.domain.snapshot.ObjectInstanceId;
import de.useweb.backend.domain.snapshot.ObjectModel;
import de.useweb.backend.domain.snapshot.ObjectModelId;
import de.useweb.backend.domain.snapshot.Slot;
import de.useweb.backend.domain.snapshot.SlotId;
import de.useweb.backend.domain.snapshot.SlotValue;
import de.useweb.backend.domain.uml.ParameterDirection;
import de.useweb.backend.domain.uml.UmlAttribute;
import de.useweb.backend.domain.uml.UmlAttributeId;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlModelId;
import de.useweb.backend.domain.uml.UmlOperation;
import de.useweb.backend.domain.uml.UmlOperationId;
import de.useweb.backend.domain.uml.UmlOperationContract;
import de.useweb.backend.domain.uml.UmlParameter;
import de.useweb.backend.domain.uml.UmlParameterId;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.domain.uml.UmlVisibility;
import de.useweb.backend.validation.service.ValidationService;
import de.useweb.backend.persistence.json.ProjectJsonSerializer;
import de.useweb.backend.persistence.project.InMemoryProjectRepository;

class OperationInvocationServiceTest {
    private static final ProjectId PROJECT_ID = new ProjectId("project-operation");
    private static final UmlClassId ACCOUNT = new UmlClassId("class-account");
    private static final UmlClassId PREMIUM = new UmlClassId("class-premium-account");
    private static final UmlAttributeId BALANCE = new UmlAttributeId("attr-balance");
    private static final UmlParameterId AMOUNT = new UmlParameterId("param-amount");
    private static final UmlOperationId BASE_DEPOSIT = new UmlOperationId("op-base-deposit");
    private static final UmlOperationId PREMIUM_DEPOSIT = new UmlOperationId("op-premium-deposit");

    @Test
    void resolvesRuntimeOverrideAndCommitsCandidateAtomically() {
        Project project = project(false);
        ProjectService projects = projects(project);
        OperationImplementationRegistry registry = new OperationImplementationRegistry();
        registry.register(implementation(PREMIUM_DEPOSIT, context -> {
            ObjectInstance changed = account(120);
            return new OperationExecutionResult(snapshot(List.of(changed)), SlotValue.ofBoolean(true), Map.of());
        }));
        var service = new OperationInvocationService(projects, registry, new ValidationService());

        var result = service.invoke(PROJECT_ID, request(BASE_DEPOSIT));

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.requestedOperationId()).isEqualTo(BASE_DEPOSIT.value());
        assertThat(result.resolvedOperationId()).isEqualTo(PREMIUM_DEPOSIT.value());
        assertThat(result.lifecycle().changedObjects()).extracting(reference -> reference.name()).containsExactly("account1");
        assertThat(result.afterSnapshotId()).startsWith("snapshot-invocation-");
        assertThat(projects.loadProject(PROJECT_ID).objectModel().objects().getFirst().slots().getFirst().value().value())
                .isEqualTo(120);
    }

    @Test
    void queryMutationRollsBackWithoutSaving() {
        Project project = project(true);
        ProjectService projects = projects(project);
        OperationImplementationRegistry registry = new OperationImplementationRegistry();
        registry.register(implementation(PREMIUM_DEPOSIT, context ->
                new OperationExecutionResult(snapshot(List.of(account(120))), SlotValue.ofBoolean(true), Map.of())));
        var service = new OperationInvocationService(projects, registry, new ValidationService());

        var result = service.invoke(PROJECT_ID, request(BASE_DEPOSIT));

        assertThat(result.status()).isEqualTo("ROLLED_BACK");
        assertThat(result.diagnostics()).containsExactly("QUERY_STATE_MUTATION");
        assertThat(result.afterSnapshotId()).isNull();
        assertThat(projects.loadProject(PROJECT_ID).objectModel()).isEqualTo(project.objectModel());
    }

    @Test
    void reportsCreatedAndDeletedObjectsFromCandidateState() {
        Project project = project(false);
        ProjectService projects = projects(project);
        OperationImplementationRegistry registry = new OperationImplementationRegistry();
        registry.register(implementation(PREMIUM_DEPOSIT, context -> {
            ObjectInstance replacement = new ObjectInstance(new ObjectInstanceId("object-replacement"), "account2",
                    PREMIUM, List.of(new Slot(new SlotId("slot-replacement"), BALANCE, SlotValue.ofInteger(100))));
            return new OperationExecutionResult(snapshot(List.of(replacement)), SlotValue.ofBoolean(true), Map.of());
        }));
        var service = new OperationInvocationService(projects, registry, new ValidationService());

        var result = service.invoke(PROJECT_ID, request(BASE_DEPOSIT));

        assertThat(result.lifecycle().createdObjects()).extracting(reference -> reference.name()).containsExactly("account2");
        assertThat(result.lifecycle().deletedObjects()).extracting(reference -> reference.name()).containsExactly("account1");
    }

    @Test
    void violatedPreconditionBlocksExecutionWithoutCandidateState() {
        Project project = project(false, List.of(contract("pre-positive", "PositiveAmount", UmlOperationContract.Kind.PRE,
                "amount > 100", true)));
        ProjectService projects = projects(project);
        OperationImplementationRegistry registry = new OperationImplementationRegistry();
        AtomicBoolean executed = new AtomicBoolean();
        registry.register(implementation(PREMIUM_DEPOSIT, context -> {
            executed.set(true);
            return new OperationExecutionResult(snapshot(List.of(account(120))), SlotValue.ofBoolean(true), Map.of());
        }));

        var result = new OperationInvocationService(projects, registry, new ValidationService())
                .invoke(PROJECT_ID, request(BASE_DEPOSIT));

        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(executed).isFalse();
        assertThat(result.candidateAfterSnapshotId()).isNull();
        assertThat(result.contractResults()).extracting("status")
                .containsExactly("VIOLATED");
        assertThat(result.diagnostics()).contains("PRECONDITION_VIOLATION");
    }

    @Test
    void satisfiedContractsCommitWithResultAtPreAndNewObjectSemantics() {
        Project project = project(false, List.of(
                contract("pre-positive", "PositiveAmount", UmlOperationContract.Kind.PRE, "amount > 0", true),
                contract("post-updated", "Updated", UmlOperationContract.Kind.POST,
                        "self.balance = self.balance@pre + amount and result and "
                                + "PremiumAccount.allInstances()->exists(a | a.oclIsNew())", true)));
        ProjectService projects = projects(project);
        OperationImplementationRegistry registry = new OperationImplementationRegistry();
        registry.register(implementation(PREMIUM_DEPOSIT, context -> {
            ObjectInstance created = new ObjectInstance(new ObjectInstanceId("object-created"), "account2", PREMIUM,
                    List.of(new Slot(new SlotId("slot-created"), BALANCE, SlotValue.ofInteger(0))));
            return new OperationExecutionResult(snapshot(List.of(account(120), created)), SlotValue.ofBoolean(true), Map.of());
        }));

        var result = new OperationInvocationService(projects, registry, new ValidationService())
                .invoke(PROJECT_ID, request(BASE_DEPOSIT));

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.contractResults()).extracting("status")
                .containsExactly("SATISFIED", "SATISFIED");
        assertThat(result.candidateAfterSnapshotId()).isEqualTo(result.afterSnapshotId());
        assertThat(projects.loadProject(PROJECT_ID).objectModel().objects()).hasSize(2);
    }

    @Test
    void violatedPostconditionRollsBackAndKeepsCandidateReferenceForDiagnostics() {
        Project project = project(false, List.of(contract("post-wrong", "WrongBalance",
                UmlOperationContract.Kind.POST, "self.balance = self.balance@pre", true)));
        ProjectService projects = projects(project);
        OperationImplementationRegistry registry = new OperationImplementationRegistry();
        registry.register(implementation(PREMIUM_DEPOSIT, context ->
                new OperationExecutionResult(snapshot(List.of(account(120))), SlotValue.ofBoolean(true), Map.of())));

        var result = new OperationInvocationService(projects, registry, new ValidationService())
                .invoke(PROJECT_ID, request(BASE_DEPOSIT));

        assertThat(result.status()).isEqualTo("ROLLED_BACK");
        assertThat(result.afterSnapshotId()).isNull();
        assertThat(result.candidateAfterSnapshotId()).startsWith("snapshot-invocation-");
        assertThat(result.diagnostics()).contains("POSTCONDITION_VIOLATION");
        assertThat(projects.loadProject(PROJECT_ID).objectModel()).isEqualTo(project.objectModel());
    }

    @Test
    void disabledContractIsReportedWithoutAffectingInvocation() {
        Project project = project(false, List.of(contract("pre-disabled", "Disabled",
                UmlOperationContract.Kind.PRE, "false", false)));
        ProjectService projects = projects(project);
        OperationImplementationRegistry registry = new OperationImplementationRegistry();
        registry.register(implementation(PREMIUM_DEPOSIT, context ->
                new OperationExecutionResult(snapshot(List.of(account(120))), SlotValue.ofBoolean(true), Map.of())));

        var result = new OperationInvocationService(projects, registry, new ValidationService())
                .invoke(PROJECT_ID, request(BASE_DEPOSIT));

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.contractResults()).extracting("status").containsExactly("NOT_EVALUATED");
    }

    @Test
    void executesDeclarativeQueryBodyWithoutImperativeRegistryEntry() {
        Project base = project(true);
        UmlOperation inherited = base.umlModel().findClass(ACCOUNT).orElseThrow().operations().getFirst();
        UmlOperation body = new UmlOperation(PREMIUM_DEPOSIT, "deposit", UmlType.BOOLEAN,
                inherited.parameters(), "self.balance + amount > 100", UmlVisibility.PUBLIC, false, true);
        UmlClass premium = new UmlClass(PREMIUM, "PremiumAccount", List.of(), List.of(body), false, List.of(ACCOUNT));
        UmlModel model = new UmlModel(base.umlModel().id(), base.umlModel().name(),
                List.of(base.umlModel().findClass(ACCOUNT).orElseThrow(), premium), List.of(), List.of());
        Project project = new Project(base.id(), base.metadata(), base.modelText(), model, base.objectModel(), base.layout());
        ProjectService projects = projects(project);

        var result = new OperationInvocationService(projects, new OperationImplementationRegistry(),
                new ValidationService()).invoke(PROJECT_ID, request(BASE_DEPOSIT));

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.result().value()).isEqualTo(true);
        assertThat(projects.loadProject(PROJECT_ID).objectModel().objects()).isEqualTo(project.objectModel().objects());
        assertThat(projects.loadProject(PROJECT_ID).objectModel().links()).isEqualTo(project.objectModel().links());
    }

    private static OperationInvocationRequestDto request(UmlOperationId operationId) {
        return new OperationInvocationRequestDto("object-account", operationId.value(),
                List.of(new OperationArgumentDto(AMOUNT.value(), new SlotValueDto("Integer", 20))), 1000L);
    }

    private static ProjectService projects(Project project) {
        InMemoryProjectRepository repository = new InMemoryProjectRepository();
        repository.save(project);
        return new ProjectService(repository, new ProjectJsonSerializer(),
                Clock.fixed(Instant.ofEpochMilli(2000), ZoneOffset.UTC));
    }

    private static OperationImplementation implementation(UmlOperationId id,
            java.util.function.Function<OperationExecutionContext, OperationExecutionResult> body) {
        return new OperationImplementation() {
            public UmlOperationId operationId() { return id; }
            public OperationExecutionResult execute(OperationExecutionContext context) { return body.apply(context); }
        };
    }

    private static Project project(boolean query) {
        return project(query, List.of());
    }

    private static Project project(boolean query, List<UmlOperationContract> contracts) {
        UmlParameter amount = new UmlParameter(AMOUNT, "amount", UmlType.INTEGER, ParameterDirection.IN, 0);
        UmlOperation base = new UmlOperation(BASE_DEPOSIT, "deposit", UmlType.BOOLEAN, List.of(amount), null,
                UmlVisibility.PUBLIC, false, query);
        UmlOperation override = new UmlOperation(PREMIUM_DEPOSIT, "deposit", UmlType.BOOLEAN, List.of(amount), null,
                UmlVisibility.PUBLIC, false, query, contracts);
        UmlAttribute balance = new UmlAttribute(BALANCE, "balance", UmlType.INTEGER);
        UmlClass account = new UmlClass(ACCOUNT, "Account", List.of(balance), List.of(base));
        UmlClass premium = new UmlClass(PREMIUM, "PremiumAccount", List.of(), List.of(override), false, List.of(ACCOUNT));
        UmlModel model = new UmlModel(new UmlModelId("model-operation"), "Operations",
                List.of(account, premium), List.of(), List.of());
        return new Project(PROJECT_ID,
                new ProjectMetadata("Operations", null, "1", Instant.EPOCH, Instant.ofEpochMilli(1000)),
                model, snapshot(List.of(account(100))), LayoutInformation.empty());
    }

    private static UmlOperationContract contract(String id, String name, UmlOperationContract.Kind kind,
            String expression, boolean enabled) {
        return new UmlOperationContract(id, name, kind, expression, enabled);
    }

    private static ObjectInstance account(int balance) {
        return new ObjectInstance(new ObjectInstanceId("object-account"), "account1", PREMIUM,
                List.of(new Slot(new SlotId("slot-balance"), BALANCE, SlotValue.ofInteger(balance))));
    }

    private static ObjectModel snapshot(List<ObjectInstance> objects) {
        return new ObjectModel(new ObjectModelId("snapshot-before"), "Before", objects, List.of());
    }
}
