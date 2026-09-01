package de.useweb.backend.application.uml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.useweb.backend.api.dto.snapshot.ObjectInstanceDto;
import de.useweb.backend.api.dto.snapshot.SlotDto;
import de.useweb.backend.api.dto.snapshot.SlotValueDto;
import de.useweb.backend.api.dto.uml.UmlAttributeDto;
import de.useweb.backend.api.dto.uml.UmlClassDto;
import de.useweb.backend.api.dto.uml.UmlDataTypeDto;
import de.useweb.backend.api.dto.uml.UmlDataTypePropertyDto;
import de.useweb.backend.application.project.ProjectService;
import de.useweb.backend.application.snapshot.ObjectModelService;
import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.snapshot.ObjectInstanceId;
import de.useweb.backend.domain.uml.UmlClassId;
import de.useweb.backend.error.ObjectModelException;
import de.useweb.backend.persistence.json.ProjectJsonSerializer;
import de.useweb.backend.persistence.project.InMemoryProjectRepository;

class StructuredUmlTypeServiceTest {

    private final InMemoryProjectRepository repository = new InMemoryProjectRepository();
    private final ProjectJsonSerializer serializer = new ProjectJsonSerializer();
    private final ProjectService projects = new ProjectService(repository, serializer,
            Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC));
    private final UmlModelService uml = new UmlModelService(projects);
    private final ObjectModelService objects = new ObjectModelService(projects);

    @Test
    void resolvesAndPersistsNestedDataTypeTupleAndCollectionValues() {
        Project project = projects.createProject("B50", "Structured values");
        uml.createDataType(project.id(), new UmlDataTypeDto("money", "Money", List.of(
                new UmlDataTypePropertyDto("amount", "amount", "Real"),
                new UmlDataTypePropertyDto("currency", "currency", "String")), null, "Money"));
        UmlClassDto invoice = uml.createClass(project.id(),
                new UmlClassDto("invoice", "Invoice", List.of(), List.of()));
        String type = "Sequence(Tuple(label:String,amount:Money))";
        UmlAttributeDto lines = uml.addAttribute(project.id(), new UmlClassId(invoice.id()),
                new UmlAttributeDto("lines", "lines", type));
        UmlAttributeDto labels = uml.addAttribute(project.id(), new UmlClassId(invoice.id()),
                new UmlAttributeDto("labels", "labels", "Set(String)"));
        UmlAttributeDto amounts = uml.addAttribute(project.id(), new UmlClassId(invoice.id()),
                new UmlAttributeDto("amounts", "amounts", "Bag(Money)"));
        UmlAttributeDto stages = uml.addAttribute(project.id(), new UmlClassId(invoice.id()),
                new UmlAttributeDto("stages", "stages", "OrderedSet(Integer)"));

        List<Map<String, Object>> value = List.of(
                Map.of("label", "net", "amount", Map.of("amount", 19.5, "currency", "EUR")),
                Map.of("label", "tax", "amount", Map.of("amount", 3.7, "currency", "EUR")));
        List<String> setValue = List.of("net", "tax");
        List<Map<String, Object>> bagValue = List.of(
                Map.of("amount", 12.5, "currency", "EUR"),
                Map.of("amount", 12.5, "currency", "EUR"));
        List<Integer> orderedSetValue = List.of(2, 1);
        ObjectInstanceDto created = objects.createObject(project.id(), new ObjectInstanceDto(
                "invoice-1", "invoice1", invoice.id(), List.of(
                        new SlotDto("slot-lines", lines.id(), new SlotValueDto(type, value), false),
                        new SlotDto("slot-labels", labels.id(), new SlotValueDto("Set(String)", setValue), false),
                        new SlotDto("slot-amounts", amounts.id(), new SlotValueDto("Bag(Money)", bagValue), false),
                        new SlotDto("slot-stages", stages.id(),
                                new SlotValueDto("OrderedSet(Integer)", orderedSetValue), false))));

        Project restored = serializer.deserialize(serializer.serialize(projects.loadProject(project.id())));
        var restoredObject = restored.objectModel().findObject(new ObjectInstanceId(created.id())).orElseThrow();
        assertThat(restoredObject.findSlot(new de.useweb.backend.domain.uml.UmlAttributeId(lines.id()))
                .orElseThrow().value().value()).isEqualTo(value);
        assertThat(restoredObject.findSlot(new de.useweb.backend.domain.uml.UmlAttributeId(labels.id()))
                .orElseThrow().value().value()).isEqualTo(setValue);
        assertThat(restoredObject.findSlot(new de.useweb.backend.domain.uml.UmlAttributeId(amounts.id()))
                .orElseThrow().value().value()).isEqualTo(bagValue);
        assertThat(restoredObject.findSlot(new de.useweb.backend.domain.uml.UmlAttributeId(stages.id()))
                .orElseThrow().value().value()).isEqualTo(orderedSetValue);
        assertThat(restored.umlModel().findAttribute(new de.useweb.backend.domain.uml.UmlAttributeId(lines.id())))
                .get().extracting(attribute -> attribute.type().name()).isEqualTo(type);
    }

    @Test
    void validatesUniqueCollectionsAndReportsTheNestedFieldPathWithoutPersisting() {
        Project project = projects.createProject("B50 invalid", "Atomic structured validation");
        UmlClassDto holder = uml.createClass(project.id(),
                new UmlClassDto("holder", "Holder", List.of(), List.of()));
        UmlAttributeDto tags = uml.addAttribute(project.id(), new UmlClassId(holder.id()),
                new UmlAttributeDto("tags", "tags", "OrderedSet(Tuple(label:String,rank:Integer))"));
        ObjectInstanceDto created = objects.createObject(project.id(),
                new ObjectInstanceDto("holder-1", "holder1", holder.id(), List.of()));
        List<Map<String, Object>> duplicate = List.of(
                Map.of("label", "A", "rank", 1), Map.of("label", "A", "rank", 1));

        assertThatThrownBy(() -> objects.setSlotValue(project.id(), new ObjectInstanceId(created.id()),
                new SlotDto("slot-tags", tags.id(),
                        new SlotValueDto("OrderedSet(Tuple(label:String,rank:Integer))", duplicate), false)))
                .isInstanceOf(ObjectModelException.class)
                .satisfies(error -> {
                    ObjectModelException exception = (ObjectModelException) error;
                    assertThat(exception.error().code()).isEqualTo("INVALID_SLOT_VALUE");
                    assertThat(exception.error().details()).containsEntry("fieldPath", "value");
                });
        assertThat(projects.loadProject(project.id()).objectModel()
                .findObject(new ObjectInstanceId(created.id())).orElseThrow()
                .findSlot(new de.useweb.backend.domain.uml.UmlAttributeId(tags.id())).orElseThrow().value().value())
                .isNull();
    }

    @Test
    void recursivelyValidatesStaticClassifierValues() {
        Project project = projects.createProject("B50 static", "Structured classifier value");
        uml.createDataType(project.id(), new UmlDataTypeDto("money", "Money", List.of(
                new UmlDataTypePropertyDto("amount", "amount", "Real")), null, "Money"));
        UmlClassDto ledger = uml.createClass(project.id(),
                new UmlClassDto("ledger", "Ledger", List.of(), List.of()));

        UmlAttributeDto attribute = uml.addAttribute(project.id(), new UmlClassId(ledger.id()),
                new UmlAttributeDto("totals", "totals", "Bag(Money)", false, null, null, "PUBLIC", List.of(), true,
                        new SlotValueDto("Bag(Money)", List.of(Map.of("amount", 12.5), Map.of("amount", 12.5)))));

        assertThat(attribute.classifierValue().value()).isEqualTo(List.of(Map.of("amount", 12.5), Map.of("amount", 12.5)));
        assertThat(objects.createObject(project.id(),
                new ObjectInstanceDto("ledger-1", "ledger1", ledger.id(), List.of())).slots()).isEmpty();
    }
}
