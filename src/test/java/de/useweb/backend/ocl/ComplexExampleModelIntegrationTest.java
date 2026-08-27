package de.useweb.backend.ocl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.useweb.backend.domain.snapshot.ObjectInstance;
import de.useweb.backend.domain.snapshot.ObjectInstanceId;
import de.useweb.backend.domain.snapshot.ObjectLink;
import de.useweb.backend.domain.snapshot.ObjectLinkEnd;
import de.useweb.backend.domain.snapshot.ObjectLinkId;
import de.useweb.backend.domain.snapshot.ObjectModel;
import de.useweb.backend.domain.snapshot.ObjectModelId;
import de.useweb.backend.domain.snapshot.Slot;
import de.useweb.backend.domain.snapshot.SlotId;
import de.useweb.backend.domain.snapshot.SlotValue;
import de.useweb.backend.domain.uml.Multiplicity;
import de.useweb.backend.domain.uml.UmlAssociation;
import de.useweb.backend.domain.uml.UmlAssociationEnd;
import de.useweb.backend.domain.uml.UmlAssociationEndId;
import de.useweb.backend.domain.uml.UmlAssociationId;
import de.useweb.backend.domain.uml.UmlAttribute;
import de.useweb.backend.domain.uml.UmlAttributeId;
import de.useweb.backend.domain.uml.UmlClass;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.domain.uml.UmlEnumeration;
import de.useweb.backend.domain.uml.UmlEnumerationId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlModelId;
import de.useweb.backend.domain.uml.UmlOperation;
import de.useweb.backend.domain.uml.UmlOperationId;
import de.useweb.backend.domain.uml.UmlParameter;
import de.useweb.backend.domain.uml.UmlParameterId;
import de.useweb.backend.domain.uml.UmlType;
import de.useweb.backend.ocl.contract.OperationConstraintKind;
import de.useweb.backend.ocl.contract.OperationContext;
import de.useweb.backend.ocl.contract.OperationContextReference;
import de.useweb.backend.ocl.contract.OperationContractId;
import de.useweb.backend.ocl.contract.OperationContractParser;
import de.useweb.backend.ocl.contract.OperationContractResult;
import de.useweb.backend.ocl.contract.OperationContractService;
import de.useweb.backend.ocl.contract.OperationInvocationId;
import de.useweb.backend.ocl.contract.OperationResultSlot;
import de.useweb.backend.ocl.definition.OclDefinitionService;
import de.useweb.backend.ocl.definition.OclModelDefinitionFactory;
import de.useweb.backend.ocl.evaluation.EvaluationContext;
import de.useweb.backend.ocl.evaluation.OclEvaluator;
import de.useweb.backend.ocl.parser.OclParser;
import de.useweb.backend.ocl.typecheck.OclTypeChecker;
import de.useweb.backend.ocl.typecheck.TypeEnvironment;
import de.useweb.backend.ocl.value.BooleanValue;
import de.useweb.backend.ocl.value.RealValue;
import de.useweb.backend.ocl.value.SetValue;

class ComplexExampleModelIntegrationTest {
    private final OclParser parser = new OclParser();
    private final OclTypeChecker typeChecker = new OclTypeChecker();
    private final OclEvaluator evaluator = new OclEvaluator();

    @Test
    void demoProfileEvaluatesModelWideNavigationAndQuantifiers() {
        UmlClass employee = clazz("Employee", List.of(attr("salary", UmlType.INTEGER)), List.of());
        UmlClass department = clazz("Department", List.of(attr("budget", UmlType.INTEGER)), List.of());
        UmlClass project = clazz("Project", List.of(attr("budget", UmlType.INTEGER)), List.of());
        UmlAssociation worksIn = association("WorksIn", employee, "employee", department, "department");
        UmlAssociation worksOn = association("WorksOn", employee, "employee", project, "project");
        UmlAssociation controls = new UmlAssociation(new UmlAssociationId("association-Controls"), "Controls", List.of(
                new UmlAssociationEnd(new UmlAssociationEndId("end-Controls-left"), department.id(), "department",
                        Multiplicity.exactlyOne(), true),
                new UmlAssociationEnd(new UmlAssociationEndId("end-Controls-right"), project.id(), "project",
                        Multiplicity.zeroToMany(), true)));
        UmlModel model = model("Demo", List.of(employee, department, project),
                List.of(worksIn, worksOn, controls), List.of());
        ObjectInstance e1 = object(employee, "e1", Map.of("salary", 100));
        ObjectInstance e2 = object(employee, "e2", Map.of("salary", 50));
        ObjectInstance d = object(department, "d", Map.of("budget", 200));
        ObjectInstance p = object(project, "p", Map.of("budget", 150));
        ObjectModel snapshot = snapshot(List.of(e1, e2, d, p), List.of(
                link(worksIn, e1, d), link(worksIn, e2, d), link(worksOn, e1, p), link(controls, d, p)));

        assertBoolean(model, snapshot, d, "self.employee->size() >= self.project->size()", true, null);
        assertBoolean(model, snapshot, e1, "Employee.allInstances()->forAll(e1, e2 | "
                + "e1.project->size() > e2.project->size() implies e1.salary > e2.salary)", true, null);
        assertBoolean(model, snapshot, p, "self.budget <= self.department.budget", true, null);
        assertBoolean(model, snapshot, p, "self.department.employee->includesAll(self.employee)", true, null);
    }

    @Test
    void carRentalProfileExecutesCollectionReturningQueryBodyAcrossInheritance() {
        UmlClass vehicle = clazz("Vehicle", List.of(attr("registration", UmlType.STRING)), List.of());
        UmlOperation select = new UmlOperation(idOp("selectR24"), "selectR24",
                new UmlType("Set(Vehicle)"), List.of(),
                "self.vehicle->select(v | v.registration.substring(1,3) = 'R24')");
        UmlClass company = clazz("Company", List.of(), List.of(select));
        UmlAssociation owns = association("Owns", company, "company", vehicle, "vehicle");
        UmlModel model = model("CarRental", List.of(company, vehicle), List.of(owns), List.of());
        ObjectInstance c = object(company, "company", Map.of());
        ObjectInstance matching = object(vehicle, "r24", Map.of("registration", "R24-1"));
        ObjectInstance other = object(vehicle, "x", Map.of("registration", "X99-1"));
        ObjectModel snapshot = snapshot(List.of(c, matching, other),
                List.of(link(owns, c, matching), link(owns, c, other)));
        OclDefinitionService definitions = definitions(model);

        var result = evaluator.evaluate(parser.parse("self.selectR24()").ast(),
                new EvaluationContext(model, snapshot, c, Map.of(), snapshot, definitions));

        assertThat(result.success()).isTrue();
        assertThat(result.value()).isInstanceOf(SetValue.class);
        assertThat(((SetValue) result.value()).values()).hasSize(1);
    }

    @Test
    void civstatProfileCombinesEnumsLetStringsAndGlobalUniqueness() {
        UmlEnumeration civilStatus = new UmlEnumeration(new UmlEnumerationId("enum-civil-status"),
                "CivilStatus", List.of("single", "married", "divorced", "widowed"));
        UmlClass person = clazz("Person", List.of(attr("name", UmlType.STRING),
                attr("civstat", new UmlType("CivilStatus"))), List.of());
        UmlModel model = model("CivilStatusWorld", List.of(person), List.of(), List.of(civilStatus));
        ObjectInstance alice = object(person, "alice", Map.of("name", "Alice", "civstat", "single"));
        ObjectInstance bob = object(person, "bob", Map.of("name", "Bob", "civstat", "single"));
        ObjectModel snapshot = snapshot(List.of(alice, bob), List.of());

        assertBoolean(model, snapshot, alice,
                "let capital : Set(String) = Set{'A','B'} in capital->includes(self.name.substring(1,1))", true, null);
        assertBoolean(model, snapshot, alice, "self.civstat = CivilStatus::single", true, null);
        assertBoolean(model, snapshot, alice, "Person.allInstances()->forAll(p | "
                + "self = p or self.name <> p.name)", true, null);
    }

    @Test
    void treeProfileExecutesRecursiveQueryOnAcyclicSnapshot() {
        UmlOperation descendants = new UmlOperation(idOp("descendants"), "descendants",
                new UmlType("Set(TreeNode)"), List.of(),
                "Set{self}->union(self.child)->union(self.child->collect(tn | tn.descendants())->flatten()->asSet())");
        UmlClass node = clazz("TreeNode", List.of(), List.of(descendants));
        UmlAssociation parentship = association("Parentship", node, "parent", node, "child");
        UmlModel model = model("Tree", List.of(node), List.of(parentship), List.of());
        ObjectInstance root = object(node, "root", Map.of());
        ObjectInstance child = object(node, "child", Map.of());
        ObjectInstance leaf = object(node, "leaf", Map.of());
        ObjectModel snapshot = snapshot(List.of(root, child, leaf),
                List.of(link(parentship, root, child), link(parentship, child, leaf)));
        OclDefinitionService definitions = definitions(model);

        var result = evaluator.evaluate(parser.parse("self.descendants()").ast(),
                new EvaluationContext(model, snapshot, root, Map.of(), snapshot, definitions));

        assertThat(result.success()).isTrue();
        assertThat(((SetValue) result.value()).values()).hasSize(3);
    }

    @Test
    void employeeProfileEvaluatesResultAndAtPrePostcondition() {
        UmlOperation raise = new UmlOperation(idOp("raiseSalary"), "raiseSalary", UmlType.REAL,
                List.of(new UmlParameter(new UmlParameterId("parameter-rate"), "rate", UmlType.REAL)));
        UmlClass person = clazz("Person", List.of(attr("salary", UmlType.REAL)), List.of(raise));
        UmlModel model = model("Employee", List.of(person), List.of(), List.of());
        ObjectInstance before = object(person, "person", Map.of("salary", 100.0));
        ObjectInstance after = object(person, "person", Map.of("salary", 150.0));
        ObjectModel pre = snapshot("employee-pre", List.of(before), List.of());
        ObjectModel post = snapshot("employee-post", List.of(after), List.of());
        String source = "context Person::raiseSalary(rate : Real) post SalaryRaised: "
                + "self.salary = self.salary@pre * (1.0 + rate) and result = self.salary";
        var contract = new OperationContractParser().parse(new OperationContractId("employee-post"), source, model)
                .optionalContract().orElseThrow();
        OperationContext context = new OperationContext(new OperationInvocationId("raise-invocation"),
                new OperationContextReference(person.id(), raise.id()), model, before.id(), pre, post,
                Map.of(raise.parameters().getFirst().id(), new RealValue(0.5)),
                OperationResultSlot.of(new RealValue(150.0)));

        assertThat(new OperationContractService().evaluate(context, contract).status())
                .isEqualTo(OperationContractResult.Status.SATISFIED);
        assertThat(contract.kind()).isEqualTo(OperationConstraintKind.POSTCONDITION);
    }

    @Test
    void derivedPropertiesProfileAdaptsDerivedAssociationEndToLazyDerivedAttribute() {
        UmlClass b = clazz("B", List.of(attr("value", UmlType.INTEGER)), List.of());
        UmlAttribute smallCount = new UmlAttribute(idAttr("smallCount"), "smallCount", UmlType.INTEGER,
                true, "self.allBs->select(b | b.value < 10)->size()", null);
        UmlClass a = clazz("A", List.of(smallCount), List.of());
        UmlAssociation allLinks = association("AllLinks", a, "allAs", b, "allBs");
        UmlModel model = model("Derived", List.of(a, b), List.of(allLinks), List.of());
        ObjectInstance owner = object(a, "a", Map.of());
        ObjectInstance small = object(b, "small", Map.of("value", 5));
        ObjectInstance large = object(b, "large", Map.of("value", 20));
        ObjectModel snapshot = snapshot(List.of(owner, small, large),
                List.of(link(allLinks, owner, small), link(allLinks, owner, large)));

        assertBoolean(model, snapshot, owner, "self.smallCount = 1", true, definitions(model));
    }

    private void assertBoolean(UmlModel model, ObjectModel snapshot, ObjectInstance self, String expression,
            boolean expected, OclDefinitionService definitions) {
        var parsed = parser.parse(expression);
        assertThat(parsed.success()).as(expression).isTrue();
        assertThat(typeChecker.checkExpression(new TypeEnvironment(model,
                model.findClass(self.classId()).orElseThrow()), parsed.ast()).success()).as(expression).isTrue();
        var evaluated = evaluator.evaluate(parsed.ast(), definitions == null
                ? new EvaluationContext(model, snapshot, self)
                : new EvaluationContext(model, snapshot, self, Map.of(), snapshot, definitions));
        assertThat(evaluated.success()).as(expression).isTrue();
        assertThat(evaluated.value()).isEqualTo(new BooleanValue(expected));
    }

    private OclDefinitionService definitions(UmlModel model) {
        return new OclDefinitionService(model, new OclModelDefinitionFactory().definitions(model));
    }

    private static UmlModel model(String name, List<UmlClass> classes, List<UmlAssociation> associations,
            List<UmlEnumeration> enumerations) {
        return new UmlModel(new UmlModelId("model-" + name), name, classes, associations, List.of(), enumerations);
    }

    private static UmlClass clazz(String name, List<UmlAttribute> attributes, List<UmlOperation> operations) {
        return new UmlClass(new UmlClassId("class-" + name), name, attributes, operations);
    }

    private static UmlAttribute attr(String name, UmlType type) {
        return new UmlAttribute(idAttr(name), name, type);
    }

    private static UmlAttributeId idAttr(String name) {
        return new UmlAttributeId("attribute-" + name);
    }

    private static UmlOperationId idOp(String name) {
        return new UmlOperationId("operation-" + name);
    }

    private static UmlAssociation association(String name, UmlClass left, String leftRole,
            UmlClass right, String rightRole) {
        return new UmlAssociation(new UmlAssociationId("association-" + name), name, List.of(
                new UmlAssociationEnd(new UmlAssociationEndId("end-" + name + "-left"), left.id(), leftRole,
                        Multiplicity.zeroToMany(), true),
                new UmlAssociationEnd(new UmlAssociationEndId("end-" + name + "-right"), right.id(), rightRole,
                        Multiplicity.zeroToMany(), true)));
    }

    private static ObjectInstance object(UmlClass type, String name, Map<String, Object> values) {
        List<Slot> slots = new ArrayList<>();
        for (UmlAttribute attribute : type.attributes()) {
            if (attribute.derived()) continue;
            Object value = values.get(attribute.name());
            slots.add(new Slot(new SlotId("slot-" + name + "-" + attribute.name()), attribute.id(),
                    new SlotValue(value, attribute.type())));
        }
        return new ObjectInstance(new ObjectInstanceId("object-" + name), name, type.id(), slots);
    }

    private static ObjectModel snapshot(List<ObjectInstance> objects, List<ObjectLink> links) {
        return snapshot("complex", objects, links);
    }

    private static ObjectModel snapshot(String id, List<ObjectInstance> objects, List<ObjectLink> links) {
        return new ObjectModel(new ObjectModelId("snapshot-" + id), "Complex example", objects, links);
    }

    private static ObjectLink link(UmlAssociation association, ObjectInstance left, ObjectInstance right) {
        return new ObjectLink(new ObjectLinkId("link-" + association.name() + "-" + left.name() + "-" + right.name()),
                association.id(), List.of(new ObjectLinkEnd(association.ends().get(0).id(), left.id()),
                        new ObjectLinkEnd(association.ends().get(1).id(), right.id())));
    }
}
