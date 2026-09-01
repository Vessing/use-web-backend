package de.useweb.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.useweb.backend.api.dto.command.DeleteCommandRequestDto;
import de.useweb.backend.api.dto.project.CreateProjectRequestDto;
import de.useweb.backend.application.project.ProjectService;
import de.useweb.backend.domain.project.ProjectId;
import de.useweb.backend.persistence.json.ProjectJsonSerializer;

@SpringBootTest
@AutoConfigureMockMvc
class SnapshotCommandControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ProjectService projectService;
    @Autowired ProjectJsonSerializer serializer;

    @Test
    void executesAllB41CommandsWithSnapshotRevisionsAndStableReferences() throws Exception {
        String projectId = createProject("B41 success");
        createClass(projectId, "person", "Person", "name");
        createClass(projectId, "course", "Course", null);
        createAssociation(projectId);

        JsonNode person = objectCommand(projectId, revision(projectId),
                "{\"id\":\"ada\",\"name\":\"ada\",\"classId\":\"person\",\"slots\":[]}");
        assertThat(person.at("/command").asText()).isEqualTo("CREATE_OBJECT");
        assertThat(person.at("/revisionScope").asText()).isEqualTo("SNAPSHOT");
        assertThat(person.at("/affectedElements/0/elementId").asText()).isEqualTo("ada");

        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/object-model/objects/ada/slots/name-slot", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(revision(projectId),
                                "{\"id\":\"name-slot\",\"attributeId\":\"name\","
                                        + "\"value\":{\"type\":\"Integer\",\"value\":42},\"isUnset\":false}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision", notNullValue()))
                .andExpect(jsonPath("$.result.value.value", equalTo(42)))
                .andExpect(jsonPath("$.affectedElements[2].elementId", equalTo("name")));

        objectCommand(projectId, revision(projectId),
                "{\"id\":\"uml\",\"name\":\"uml\",\"classId\":\"course\",\"slots\":[]}");
        mockMvc.perform(post("/api/v1/projects/{projectId}/commands/object-model/links", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(body(revision(projectId), """
                                {"id":"enrollment-1","associationId":"enrollment","endValues":[
                                  {"associationEndId":"student-end","objectId":"ada","qualifierValues":[]},
                                  {"associationEndId":"course-end","objectId":"uml","qualifierValues":[]}]}
                                """)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.command", equalTo("CREATE_OBJECT_LINK")))
                .andExpect(jsonPath("$.revisionScope", equalTo("SNAPSHOT")))
                .andExpect(jsonPath("$.affectedElements[1].elementId", equalTo("enrollment")));

        mockMvc.perform(get("/api/v1/projects/{projectId}/object-model", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objects.length()", equalTo(2)))
                .andExpect(jsonPath("$.links.length()", equalTo(1)));
        mockMvc.perform(get("/api/v1/projects/{projectId}/export", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectModel.objects[0].id", equalTo("ada")))
                .andExpect(jsonPath("$.objectModel.objects[0].slots[0].value.value", equalTo(42)))
                .andExpect(jsonPath("$.objectModel.links[0].id", equalTo("enrollment-1")));
    }

    @Test
    void rejectsMissingAndStaleRevisionAndPreservesTheCompleteDraft() throws Exception {
        String projectId = createProject("B41 revision");
        createClass(projectId, "person", "Person", null);
        String stale = revision(projectId);

        mockMvc.perform(post("/api/v1/projects/{projectId}/commands/object-model/objects", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"draft":{"id":"ada","name":"ada","classId":"person","slots":[]}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("EXPECTED_REVISION_REQUIRED")))
                .andExpect(jsonPath("$.details.draft.name", equalTo("ada")));

        objectCommand(projectId, stale,
                "{\"id\":\"ada\",\"name\":\"ada\",\"classId\":\"person\",\"slots\":[]}");
        mockMvc.perform(post("/api/v1/projects/{projectId}/commands/object-model/objects", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(body(stale,
                                "{\"id\":\"grace\",\"name\":\"grace\",\"classId\":\"person\",\"slots\":[]}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("STALE_SNAPSHOT_REVISION")))
                .andExpect(jsonPath("$.details.draft.id", equalTo("grace")))
                .andExpect(jsonPath("$.details.targets[1].elementId", equalTo("person")));

        mockMvc.perform(get("/api/v1/projects/{projectId}/object-model", projectId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.objects.length()", equalTo(1)));
    }

    @Test
    void returnsValidationConflictAndNotFoundWithoutPartialSnapshotChanges() throws Exception {
        String projectId = createProject("B41 failures");
        createClass(projectId, "person", "Person", "age");
        objectCommand(projectId, revision(projectId),
                "{\"id\":\"ada\",\"name\":\"ada\",\"classId\":\"person\",\"slots\":[]}");
        String stable = revision(projectId);

        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/object-model/objects/ada/slots/age-slot", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(body(stable, """
                                {"id":"age-slot","attributeId":"age",
                                 "value":{"type":"String","value":"wrong"},"isUnset":false}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("INVALID_SLOT_VALUE")))
                .andExpect(jsonPath("$.details.draft.value.value", equalTo("wrong")))
                .andExpect(jsonPath("$.details.targets[1].elementId", equalTo("age")));
        assertThat(revision(projectId)).isEqualTo(stable);

        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/object-model/objects/missing/slots/age-slot", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(body(stable, """
                                {"id":"age-slot","attributeId":"age",
                                 "value":{"type":"Integer","value":1},"isUnset":false}
                                """)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("INVALID_LINK")))
                .andExpect(jsonPath("$.details.draft.attributeId", equalTo("age")));
        assertThat(revision(projectId)).isEqualTo(stable);
    }

    @Test
    void updatesAndDeletesObjectLinksWithRevisionProtectionAndImpact() throws Exception {
        String projectId = createProject("B42 link lifecycle");
        createClass(projectId, "person", "Person", null);
        createClass(projectId, "course", "Course", null);
        createAssociation(projectId);
        objectCommand(projectId, revision(projectId), "{\"id\":\"ada\",\"name\":\"ada\",\"classId\":\"person\",\"slots\":[]}");
        objectCommand(projectId, revision(projectId), "{\"id\":\"uml\",\"name\":\"uml\",\"classId\":\"course\",\"slots\":[]}");
        objectCommand(projectId, revision(projectId), "{\"id\":\"db\",\"name\":\"db\",\"classId\":\"course\",\"slots\":[]}");
        createLink(projectId, "enrollment-1", "ada", "uml", null);

        String beforeUpdate = revision(projectId);
        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/object-model/links/enrollment-1", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(body(beforeUpdate, linkDraft(
                                "ignored-client-id", "ada", "db", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.command", equalTo("UPDATE_OBJECT_LINK")))
                .andExpect(jsonPath("$.result.id", equalTo("enrollment-1")))
                .andExpect(jsonPath("$.result.endValues[1].objectId", equalTo("db")))
                .andExpect(jsonPath("$.affectedElements[0].relation", equalTo("UPDATED")));
        assertThat(revision(projectId)).isNotEqualTo(beforeUpdate);

        MvcResult impactResult = mockMvc.perform(get(
                        "/api/v1/projects/{projectId}/commands/object-model/links/enrollment-1/delete-impact", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revisionScope", equalTo("SNAPSHOT")))
                .andExpect(jsonPath("$.target.elementId", equalTo("enrollment-1")))
                .andExpect(jsonPath("$.currentLink.endValues[1].objectId", equalTo("db")))
                .andExpect(jsonPath("$.blocked", equalTo(false))).andReturn();
        String impactRevision = objectMapper.readTree(impactResult.getResponse().getContentAsString()).at("/revision").asText();

        mockMvc.perform(delete("/api/v1/projects/{projectId}/commands/object-model/links/enrollment-1", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DeleteCommandRequestDto(impactRevision, List.of(), null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.command", equalTo("DELETE_OBJECT_LINK")))
                .andExpect(jsonPath("$.revisionScope", equalTo("SNAPSHOT")));
        mockMvc.perform(get("/api/v1/projects/{projectId}/object-model", projectId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.links.length()", equalTo(0)));
    }

    @Test
    void preservesUpdateDraftAndSnapshotForValidationNotFoundAndStaleRevision() throws Exception {
        String projectId = createProject("B42 update failures");
        createClass(projectId, "person", "Person", null);
        createClass(projectId, "course", "Course", null);
        createAssociation(projectId);
        objectCommand(projectId, revision(projectId), "{\"id\":\"ada\",\"name\":\"ada\",\"classId\":\"person\",\"slots\":[]}");
        objectCommand(projectId, revision(projectId), "{\"id\":\"uml\",\"name\":\"uml\",\"classId\":\"course\",\"slots\":[]}");
        createLink(projectId, "enrollment-1", "ada", "uml", null);
        String stable = revision(projectId);

        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/object-model/links/enrollment-1", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(stable, linkDraft("enrollment-1", "missing", "uml", null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.details.draft.endValues[0].objectId", equalTo("missing")));
        assertThat(revision(projectId)).isEqualTo(stable);

        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/object-model/links/missing", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(stable, linkDraft("missing", "ada", "uml", null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.details.draft.id", equalTo("missing")));

        objectCommand(projectId, stable, "{\"id\":\"bob\",\"name\":\"bob\",\"classId\":\"person\",\"slots\":[]}");
        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/object-model/links/enrollment-1", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(stable, linkDraft("enrollment-1", "bob", "uml", null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("STALE_SNAPSHOT_REVISION")))
                .andExpect(jsonPath("$.details.draft.endValues[0].objectId", equalTo("bob")));
    }

    @Test
    void associationClassDeleteRequiresItsExplicitCascadeAndReportsBlockingLinks() throws Exception {
        String projectId = createProject("B42 association class");
        createClass(projectId, "person", "Person", null);
        createClass(projectId, "course", "Course", null);
        createClass(projectId, "enrollment-class", "Enrollment", null);
        createAssociation(projectId, "enrollment-class");
        objectCommand(projectId, revision(projectId), "{\"id\":\"ada\",\"name\":\"ada\",\"classId\":\"person\",\"slots\":[]}");
        objectCommand(projectId, revision(projectId), "{\"id\":\"uml\",\"name\":\"uml\",\"classId\":\"course\",\"slots\":[]}");
        objectCommand(projectId, revision(projectId), "{\"id\":\"enrollment-object\",\"name\":\"enrollment\",\"classId\":\"enrollment-class\",\"slots\":[]}");
        createLink(projectId, "enrollment-1", "ada", "uml", "enrollment-object");

        mockMvc.perform(post("/api/v1/projects/{projectId}/commands/associations", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(body(revision(projectId), """
                                {"id":"audit","name":"Audit","ends":[
                                  {"id":"audit-enrollment","roleName":"enrollment","classId":"enrollment-class",
                                   "multiplicity":{"lower":0,"upper":null,"unbounded":true,"raw":"*"}},
                                  {"id":"audit-person","roleName":"auditor","classId":"person",
                                   "multiplicity":{"lower":0,"upper":null,"unbounded":true,"raw":"*"}}]}
                                """)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/projects/{projectId}/commands/object-model/links", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(body(revision(projectId), """
                                {"id":"audit-1","associationId":"audit","endValues":[
                                  {"associationEndId":"audit-enrollment","objectId":"enrollment-object","qualifierValues":[]},
                                  {"associationEndId":"audit-person","objectId":"ada","qualifierValues":[]}]}
                                """)))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get(
                        "/api/v1/projects/{projectId}/commands/object-model/links/enrollment-1/delete-impact", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedCascades[0].elementId", equalTo("enrollment-object")))
                .andExpect(jsonPath("$.allowedCascades[0].cascadeAllowed", equalTo(true)))
                .andExpect(jsonPath("$.blockers[0].elementId", equalTo("audit-1")))
                .andExpect(jsonPath("$.blocked", equalTo(true))).andReturn();
        JsonNode impact = objectMapper.readTree(result.getResponse().getContentAsString());
        String revision = impact.at("/revision").asText();
        String cascadeId = impact.at("/allowedCascades/0/referenceId").asText();

        mockMvc.perform(delete("/api/v1/projects/{projectId}/commands/object-model/links/enrollment-1", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DeleteCommandRequestDto(revision, List.of(cascadeId), null))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code", equalTo("DELETE_BLOCKED")))
                .andExpect(jsonPath("$.details.draft.expectedRevision", equalTo(revision)));
        assertThat(revision(projectId)).isEqualTo(revision);

        mockMvc.perform(delete("/api/v1/projects/{projectId}/commands/object-model/links/audit-1", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DeleteCommandRequestDto(revision, List.of(), null))))
                .andExpect(status().isOk());
        result = mockMvc.perform(get(
                        "/api/v1/projects/{projectId}/commands/object-model/links/enrollment-1/delete-impact", projectId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.blocked", equalTo(false))).andReturn();
        impact = objectMapper.readTree(result.getResponse().getContentAsString());
        revision = impact.at("/revision").asText();
        cascadeId = impact.at("/allowedCascades/0/referenceId").asText();
        mockMvc.perform(delete("/api/v1/projects/{projectId}/commands/object-model/links/enrollment-1", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DeleteCommandRequestDto(revision, List.of(cascadeId), null))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/projects/{projectId}/object-model", projectId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.links.length()", equalTo(0)))
                .andExpect(jsonPath("$.objects.length()", equalTo(2)));
    }

    @Test
    void createsAndUpdatesAssociationClassInstanceAsAtomicAggregate() throws Exception {
        String projectId = createProject("B48 association class instance");
        createClass(projectId, "person", "Person", null);
        createClass(projectId, "course", "Course", null);
        createAssociation(projectId);
        mockMvc.perform(post("/api/v1/projects/{projectId}/commands/associations/enrollment/association-class", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(body(revision(projectId), """
                                {"id":"enrollment-class","name":"EnrollmentRecord","superClassIds":[],
                                 "attributes":[{"id":"grade","name":"grade","type":"Integer"}],
                                 "operations":[]}
                                """)))
                .andExpect(status().isCreated());
        objectCommand(projectId, revision(projectId),
                "{\"id\":\"ada\",\"name\":\"ada\",\"classId\":\"person\",\"slots\":[]}");
        objectCommand(projectId, revision(projectId),
                "{\"id\":\"uml\",\"name\":\"uml\",\"classId\":\"course\",\"slots\":[]}");
        String before = revision(projectId);

        mockMvc.perform(post("/api/v1/projects/{projectId}/commands/object-model/association-class-instances", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(body(before, aggregateDraft(1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.command", equalTo("CREATE_ASSOCIATION_CLASS_INSTANCE")))
                .andExpect(jsonPath("$.result.link.id", equalTo("enrollment-1")))
                .andExpect(jsonPath("$.result.link.associationClassObjectId", equalTo("enrollment-object")))
                .andExpect(jsonPath("$.result.associationClassObject.slots[0].value.value", equalTo(1)))
                .andExpect(jsonPath("$.affectedElements[?(@.elementType == 'OBJECT_LINK')]", hasSize(1)))
                .andExpect(jsonPath("$.affectedElements[?(@.elementType == 'SLOT')]", hasSize(1)));
        assertThat(revision(projectId)).isNotEqualTo(before);

        String createdRevision = revision(projectId);
        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/object-model/association-class-instances/enrollment-1",
                        projectId).contentType(MediaType.APPLICATION_JSON).content(body(createdRevision, aggregateDraft(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.command", equalTo("UPDATE_ASSOCIATION_CLASS_INSTANCE")))
                .andExpect(jsonPath("$.result.associationClassObject.slots[0].value.value", equalTo(2)));

        mockMvc.perform(get("/api/v1/projects/{projectId}/export", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectModel.objects[2].slots[0].value.value", equalTo(2)))
                .andExpect(jsonPath("$.objectModel.links[0].associationClassObjectId", equalTo("enrollment-object")));
        var restored = serializer.deserialize(serializer.serialize(
                projectService.loadProject(new ProjectId(projectId))));
        assertThat(restored.objectModel().links()).hasSize(1);
        assertThat(restored.objectModel().findObject(
                new de.useweb.backend.domain.snapshot.ObjectInstanceId("enrollment-object"))).isPresent();

        String stable = revision(projectId);
        String invalid = aggregateDraft(3).replace("\"type\":\"Integer\",\"value\":3",
                "\"type\":\"String\",\"value\":\"invalid\"");
        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/object-model/association-class-instances/enrollment-1",
                        projectId).contentType(MediaType.APPLICATION_JSON).content(body(stable, invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("INVALID_SLOT_VALUE")))
                .andExpect(jsonPath("$.details.draft.associationClassObject.slots[0].value.value",
                        equalTo("invalid")))
                .andExpect(jsonPath("$.details.targets[?(@.elementType == 'SLOT')]", hasSize(1)))
                .andExpect(jsonPath("$.details.targets[?(@.elementId == 'grade')]", hasSize(1)));
        assertThat(revision(projectId)).isEqualTo(stable);

        String conflictingIdentity = aggregateDraft(3).replace(
                "\"associationClassObjectId\":\"enrollment-object\"",
                "\"associationClassObjectId\":\"other-object\"");
        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/object-model/association-class-instances/enrollment-1",
                        projectId).contentType(MediaType.APPLICATION_JSON).content(body(stable, conflictingIdentity)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("ASSOCIATION_CLASS_IDENTITY_VIOLATION")))
                .andExpect(jsonPath("$.details.draft.link.associationClassObjectId", equalTo("other-object")));
        assertThat(revision(projectId)).isEqualTo(stable);

        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/object-model/association-class-instances/missing",
                        projectId).contentType(MediaType.APPLICATION_JSON).content(body(stable, aggregateDraft(3))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.details.draft.associationClassObject.id", equalTo("enrollment-object")));
        assertThat(revision(projectId)).isEqualTo(stable);

        objectCommand(projectId, stable,
                "{\"id\":\"other\",\"name\":\"other\",\"classId\":\"person\",\"slots\":[]}");
        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/object-model/association-class-instances/enrollment-1",
                        projectId).contentType(MediaType.APPLICATION_JSON).content(body(stable, aggregateDraft(4))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("STALE_SNAPSHOT_REVISION")))
                .andExpect(jsonPath("$.details.draft.link.id", equalTo("enrollment-1")));
    }

    private String aggregateDraft(int grade) {
        return "{\"link\":" + linkDraft("enrollment-1", "ada", "uml", "enrollment-object")
                + ",\"associationClassObject\":{\"id\":\"enrollment-object\",\"name\":\"enrollment\","
                + "\"classId\":\"enrollment-class\",\"slots\":[{\"id\":\"grade-slot\","
                + "\"attributeId\":\"grade\",\"value\":{\"type\":\"Integer\",\"value\":" + grade
                + "},\"isUnset\":false}]}}";
    }

    private JsonNode objectCommand(String projectId, String revision, String draft) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects/{projectId}/commands/object-model/objects", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(body(revision, draft)))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void createClass(String projectId, String id, String name, String attributeId) throws Exception {
        String attributes = attributeId == null ? "[]" : "[{\"id\":\"" + attributeId
                + "\",\"name\":\"" + attributeId + "\",\"type\":\"Integer\"}]";
        mockMvc.perform(post("/api/v1/projects/{projectId}/commands/classes", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(body(revision(projectId),
                                "{\"id\":\"" + id + "\",\"name\":\"" + name
                                        + "\",\"attributes\":" + attributes
                                        + ",\"operations\":[],\"superClassIds\":[]}")))
                .andExpect(status().isCreated());
    }

    private void createAssociation(String projectId) throws Exception {
        createAssociation(projectId, null);
    }

    private void createAssociation(String projectId, String associationClassId) throws Exception {
        String classField = associationClassId == null ? "" : ",\"associationClassId\":\"" + associationClassId + "\"";
        String draft = """
                {"id":"enrollment","name":"Enrollment","ends":[
                  {"id":"student-end","roleName":"student","classId":"person",
                   "multiplicity":{"lower":0,"upper":null,"unbounded":true,"raw":"*"}},
                  {"id":"course-end","roleName":"course","classId":"course",
                   "multiplicity":{"lower":0,"upper":null,"unbounded":true,"raw":"*"}}]
                """ + classField + "}";
        mockMvc.perform(post("/api/v1/projects/{projectId}/commands/associations", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(body(revision(projectId), draft)))
                .andExpect(status().isCreated());
    }

    private void createLink(String projectId, String id, String person, String course,
            String associationClassObjectId) throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/commands/object-model/links", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(body(revision(projectId),
                                linkDraft(id, person, course, associationClassObjectId))))
                .andExpect(status().isCreated());
    }

    private String linkDraft(String id, String person, String course, String associationClassObjectId) {
        String linkObject = associationClassObjectId == null ? "" : ",\"associationClassObjectId\":\""
                + associationClassObjectId + "\"";
        return "{\"id\":\"" + id + "\",\"associationId\":\"enrollment\",\"endValues\":["
                + "{\"associationEndId\":\"student-end\",\"objectId\":\"" + person + "\",\"qualifierValues\":[]},"
                + "{\"associationEndId\":\"course-end\",\"objectId\":\"" + course + "\",\"qualifierValues\":[]}]"
                + linkObject + "}";
    }

    @Test
    void createsAndUpdatesB50StructuredSlotsWithDraftPreservingDiagnostics() throws Exception {
        String projectId = createProject("B50 snapshot commands");
        mockMvc.perform(post("/api/v1/projects/{projectId}/commands/datatypes", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(body(revision(projectId), """
                                {"id":"money","name":"Money","properties":[
                                  {"id":"amount","name":"amount","type":"Real"},
                                  {"id":"currency","name":"currency","type":"String"}]}
                                """)))
                .andExpect(status().isCreated());
        String structuredType = "Sequence(Tuple(label:String,amount:Money))";
        mockMvc.perform(post("/api/v1/projects/{projectId}/commands/classes", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(body(revision(projectId), """
                                {"id":"invoice","name":"Invoice","attributes":[
                                  {"id":"lines","name":"lines","type":"Sequence(Tuple(label:String,amount:Money))"}],
                                 "operations":[],"superClassIds":[]}
                                """)))
                .andExpect(status().isCreated());

        objectCommand(projectId, revision(projectId), """
                {"id":"invoice-1","name":"invoice1","classId":"invoice","slots":[
                  {"id":"slot-lines","attributeId":"lines","isUnset":false,
                   "value":{"type":"Sequence(Tuple(label:String,amount:Money))","value":[
                     {"label":"net","amount":{"amount":19.5,"currency":"EUR"}}]}}]}
                """);
        mockMvc.perform(get("/api/v1/projects/{projectId}/object-model", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objects[0].slots[0].value.type", equalTo(structuredType)))
                .andExpect(jsonPath("$.objects[0].slots[0].value.value[0].amount.currency", equalTo("EUR")));

        String stable = revision(projectId);
        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/object-model/objects/invoice-1/slots/slot-lines", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(body(stable, """
                                {"id":"slot-lines","attributeId":"lines","isUnset":false,
                                 "value":{"type":"Sequence(Tuple(label:String,amount:Money))","value":[
                                   {"label":"bad","amount":{"amount":"not-real","currency":"EUR"}}]}}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("INVALID_SLOT_VALUE")))
                .andExpect(jsonPath("$.details.fieldPath", equalTo("value[0].amount.amount")))
                .andExpect(jsonPath("$.details.draft.value.value[0].amount.amount", equalTo("not-real")))
                .andExpect(jsonPath("$.details.targets[?(@.elementId == 'lines')]", hasSize(1)));
        assertThat(revision(projectId)).isEqualTo(stable);
    }

    private String revision(String projectId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/projects/{projectId}", projectId))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/project/updatedAt").asText();
    }

    private String createProject(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProjectRequestDto(name, "B41", null))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/project/id").asText();
    }

    private String body(String revision, String draft) throws Exception {
        return "{\"expectedRevision\":" + objectMapper.writeValueAsString(revision) + ",\"draft\":" + draft + "}";
    }
}
