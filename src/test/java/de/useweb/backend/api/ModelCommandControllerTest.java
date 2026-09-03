package de.useweb.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import de.useweb.backend.domain.project.Project;
import de.useweb.backend.domain.project.ProjectId;
import de.useweb.backend.domain.uml.UmlModel;
import de.useweb.backend.domain.uml.UmlPackage;
import de.useweb.backend.domain.uml.UmlPackageId;
import de.useweb.backend.persistence.json.ProjectJsonSerializer;

@SpringBootTest
@AutoConfigureMockMvc
class ModelCommandControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ProjectService projectService;
    @Autowired ProjectJsonSerializer projectJsonSerializer;

    @Test
    void createsClassWithInitialFeaturesAtomicallyAndReturnsNewRevision() throws Exception {
        String projectId = createProject("B36 atomic class");
        String before = revision(projectId);
        MvcResult result = command(projectId, "/classes", before, """
                {"id":"student","name":"Student","abstractClass":false,"superClassIds":[],
                 "attributes":[{"id":"name","name":"name","type":"String"}],
                 "operations":[{"id":"display","name":"displayName","returnType":"String","parameters":[]}]}
                """, 201);
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("revision").asText()).isNotEqualTo(before);
        assertThat(response.at("/result/attributes/0/name").asText()).isEqualTo("name");
        assertThat(response.at("/result/operations/0/name").asText()).isEqualTo("displayName");
    }

    @Test
    void movesClassToNamespaceAndProjectsTheNewExplorerParent() throws Exception {
        String projectId = createProject("Class namespace");
        command(projectId, "/packages", revision(projectId),
                "{\"id\":\"package-people\",\"name\":\"people\",\"qualifiedName\":\"university::people\"}", 201);
        command(projectId, "/classes", revision(projectId),
                "{\"id\":\"person\",\"name\":\"Person\",\"attributes\":[],\"operations\":[],\"superClassIds\":[]}", 201);

        String expectedRevision = revision(projectId);
        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/classes/{classId}", projectId, "person")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commandBody(expectedRevision, """
                                {"id":"person","name":"Person","attributes":[],"operations":[],
                                 "abstractClass":false,"superClassIds":[],"visibility":"PUBLIC",
                                 "packageId":"package-people"}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.packageId", equalTo("package-people")));

        mockMvc.perform(get("/api/v1/projects/{projectId}/read-model", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.explorer[?(@.elementId == 'person')].parentNodeId",
                        equalTo(java.util.List.of("package-people"))))
                .andExpect(jsonPath("$.explorer[?(@.elementId == 'person')].qualifiedName",
                        equalTo(java.util.List.of("university::people::Person"))));
    }

    @Test
    void rejectsInvalidAssociationWithFieldDiagnosticAndKeepsDraft() throws Exception {
        String projectId = createProject("B36 association diagnostic");
        String revision = revision(projectId);
        mockMvc.perform(post("/api/v1/projects/{projectId}/commands/associations", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commandBody(revision, "{\"id\":\"a\",\"name\":\"Enrollment\",\"ends\":[]}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("NARY_END_REQUIRED")))
                .andExpect(jsonPath("$.details.field", equalTo("ends")))
                .andExpect(jsonPath("$.details.draft.name", equalTo("Enrollment")))
                .andExpect(jsonPath("$.details.targets[0].elementId", equalTo("a")))
                .andExpect(jsonPath("$.details.targets[0].elementType", equalTo("ASSOCIATION")));
        assertThat(revision(projectId)).isEqualTo(revision);
    }

    @Test
    void rejectsStaleRevisionWithoutSideEffects() throws Exception {
        String projectId = createProject("B36 stale");
        String revision = revision(projectId);
        command(projectId, "/classes", revision,
                "{\"id\":\"person\",\"name\":\"Person\",\"attributes\":[],\"operations\":[],\"superClassIds\":[]}", 201);
        mockMvc.perform(post("/api/v1/projects/{projectId}/commands/classes", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commandBody(revision,
                                "{\"id\":\"ghost\",\"name\":\"Ghost\",\"attributes\":[],\"operations\":[],\"superClassIds\":[]}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("STALE_MODEL_REVISION")))
                .andExpect(jsonPath("$.details.draft.name", equalTo("Ghost")));
        mockMvc.perform(get("/api/v1/projects/{projectId}/uml-model", projectId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.classes.length()", equalTo(1)));
    }

    @Test
    void returnsNotFoundForMissingGeneralizationOwner() throws Exception {
        String projectId = createProject("B36 not found");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/v1/projects/{projectId}/commands/classes/{classId}/generalizations", projectId, "missing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commandBody(revision(projectId), "{\"supertypeIds\":[]}")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("ELEMENT_NOT_FOUND")))
                .andExpect(jsonPath("$.details.elementId", equalTo("missing")));
    }

    @Test
    void previewsDeleteAndRequiresExplicitCascadeSelection() throws Exception {
        String projectId = createProject("B36 delete");
        String revision = revision(projectId);
        command(projectId, "/classes", revision,
                "{\"id\":\"person\",\"name\":\"Person\",\"attributes\":[],\"operations\":[],\"superClassIds\":[]}", 201);
        String current = revision(projectId);
        command(projectId, "/classes", current,
                "{\"id\":\"course\",\"name\":\"Course\",\"attributes\":[],\"operations\":[],\"superClassIds\":[]}", 201);
        String withClasses = revision(projectId);
        command(projectId, "/associations", withClasses, """
                {"id":"enrollment","name":"Enrollment","ends":[
                  {"id":"studentEnd","roleName":"students","classId":"person","multiplicity":{"lower":0,"upper":null,"unbounded":true,"raw":"*"}},
                  {"id":"courseEnd","roleName":"courses","classId":"course","multiplicity":{"lower":0,"upper":null,"unbounded":true,"raw":"*"}}]}
                """, 201);
        MvcResult impactResult = mockMvc.perform(get(
                        "/api/v1/projects/{projectId}/commands/delete-impact/CLASS/{classId}", projectId, "person"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.references[0].elementType", equalTo("ASSOCIATION")))
                .andReturn();
        JsonNode impact = objectMapper.readTree(impactResult.getResponse().getContentAsString());
        String deleteRevision = impact.get("revision").asText();
        String referenceId = impact.at("/references/0/referenceId").asText();
        mockMvc.perform(delete("/api/v1/projects/{projectId}/commands/CLASS/{classId}", projectId, "person")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteBody(deleteRevision, List.of())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code", equalTo("DELETE_BLOCKED")))
                .andExpect(jsonPath("$.details.blockers[0].elementId", equalTo("enrollment")));
        mockMvc.perform(delete("/api/v1/projects/{projectId}/commands/CLASS/{classId}", projectId, "person")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteBody(deleteRevision, List.of(referenceId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision", notNullValue()));
    }

    @Test
    void compilesInvariantBeforePersistingIt() throws Exception {
        String projectId = createProject("B36 invariant compile");
        command(projectId, "/classes", revision(projectId),
                "{\"id\":\"person\",\"name\":\"Person\",\"attributes\":[],\"operations\":[],\"superClassIds\":[]}", 201);
        String revision = revision(projectId);
        mockMvc.perform(post("/api/v1/projects/{projectId}/commands/invariants", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commandBody(revision, """
                                {"id":"broken","name":"Broken","contextClassId":"person","enabled":true,
                                 "expression":{"id":"broken-expr","text":"self.","language":"OCL","languageVersion":"2.4"}}
                                """)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code", equalTo("OCL_COMPILE_FAILED")))
                .andExpect(jsonPath("$.details.diagnostics[0].sourceRange", notNullValue()));
        assertThat(revision(projectId)).isEqualTo(revision);
    }

    @Test
    void supportsDataTypeGeneralizationOperationAndInvariantUpdates() throws Exception {
        String projectId = createProject("B36 update commands");
        command(projectId, "/classes", revision(projectId),
                "{\"id\":\"person\",\"name\":\"Person\",\"attributes\":[],\"operations\":[],\"superClassIds\":[]}", 201);
        command(projectId, "/classes", revision(projectId), """
                {"id":"student","name":"Student","attributes":[],"superClassIds":[],
                 "operations":[{"id":"label","name":"label","returnType":"String","parameters":[]}]}
                """, 201);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/v1/projects/{projectId}/commands/classes/student/generalizations", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commandBody(revision(projectId), "{\"supertypeIds\":[\"person\"]}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.superClassIds[0]", equalTo("person")));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/v1/projects/{projectId}/commands/classes/student/operations/label", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(revision(projectId), """
                                {"id":"label","name":"label","returnType":"String","parameters":[],"bodyExpression":"'Student'"}
                                """)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.bodyExpression", equalTo("'Student'")));
        command(projectId, "/datatypes", revision(projectId), """
                {"id":"email","name":"Email","properties":[{"id":"local","name":"local","type":"String"}]}
                """, 201);
        command(projectId, "/invariants", revision(projectId), """
                {"id":"self-equal","name":"SelfEqual","contextClassId":"student","enabled":true,
                 "expression":{"id":"self-equal-expr","text":"self = self","language":"OCL","languageVersion":"2.4"}}
                """, 201);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/v1/projects/{projectId}/commands/invariants/self-equal", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(revision(projectId), """
                                {"id":"self-equal","name":"SelfEqual","contextClassId":"student","enabled":false,
                                 "expression":{"id":"self-equal-expr","text":"true","language":"OCL","languageVersion":"2.4"}}
                                """)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.enabled", equalTo(false)));
    }

    @Test
    void rejectsUnknownDataTypePropertyTypeWithoutMutation() throws Exception {
        String projectId = createProject("B36 datatype validation");
        String revision = revision(projectId);
        mockMvc.perform(post("/api/v1/projects/{projectId}/commands/datatypes", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(revision, """
                                {"id":"broken","name":"Broken","properties":[{"id":"x","name":"x","type":"Missing"}]}
                                """)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code", equalTo("TYPE_ERROR")))
                .andExpect(jsonPath("$.details.draft.name", equalTo("Broken")));
        assertThat(revision(projectId)).isEqualTo(revision);
    }

    @Test
    void atomicallyCreatesFeatureRedefinitionAndProjectsStableTargets() throws Exception {
        String projectId = createProject("B38 feature redefinition");
        command(projectId, "/classes", revision(projectId), classWithOperation("left", "Left", "left-display"), 201);
        command(projectId, "/classes", revision(projectId), classWithOperation("right", "Right", "right-display"), 201);
        command(projectId, "/classes", revision(projectId), classWithOperation("child", "Child", "local-display"), 201);

        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/classes/child/redefinitions", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(revision(projectId), """
                                {"featureKind":"OPERATION","localFeatureId":"local-display",
                                 "redefinedFeatureIds":["left-display","right-display"],
                                 "supertypeIds":["left","right"]}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.superClassIds.length()", equalTo(2)))
                .andExpect(jsonPath("$.result.operations[0].redefinedOperationIds[0]", equalTo("left-display")))
                .andExpect(jsonPath("$.revision", notNullValue()));

        mockMvc.perform(get("/api/v1/projects/{projectId}/read-model", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capabilities.featureRedefinition", equalTo(true)))
                .andExpect(jsonPath("$.classes[2].operations[0].redefinedFeatures[0].id", equalTo("left-display")))
                .andExpect(jsonPath("$.classes[2].operations[0].redefinedFeatures[0].name", equalTo("displayName")));

        mockMvc.perform(get("/api/v1/projects/{projectId}/commands/delete-impact/OPERATION/left-display", projectId)
                        .param("classId", "left"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.references[0].relation", equalTo("REDEFINES")))
                .andExpect(jsonPath("$.references[0].elementId", equalTo("local-display")));

        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/classes/child/redefinitions", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(revision(projectId), """
                                {"featureKind":"OPERATION","localFeatureId":"local-display",
                                 "redefinedFeatureIds":[],"supertypeIds":["left"]}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.superClassIds.length()", equalTo(1)))
                .andExpect(jsonPath("$.result.operations[0].redefinedOperationIds.length()", equalTo(0)));
    }

    @Test
    void rejectsInvalidConflictNotFoundAndStaleRedefinitionWithoutSideEffects() throws Exception {
        String projectId = createProject("B38 failures");
        command(projectId, "/classes", revision(projectId), classWithOperation("left", "Left", "left-display"), 201);
        command(projectId, "/classes", revision(projectId), classWithOperation("right", "Right", "right-display"), 201);
        command(projectId, "/classes", revision(projectId), classWithOperation("child", "Child", "local-display"), 201);
        String stable = revision(projectId);

        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/classes/child/redefinitions", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(stable, """
                                {"featureKind":"OPERATION","localFeatureId":"local-display",
                                 "redefinedFeatureIds":[],"supertypeIds":["left","right"]}
                                """)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("AMBIGUOUS_INHERITED_FEATURE")))
                .andExpect(jsonPath("$.details.draft.localFeatureId", equalTo("local-display")))
                .andExpect(jsonPath("$.details.targets.length()", equalTo(5)));
        assertThat(revision(projectId)).isEqualTo(stable);

        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/classes/child/redefinitions", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(stable, """
                                {"featureKind":"OPERATION","localFeatureId":"missing","redefinedFeatureIds":[]}
                                """)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code", equalTo("ELEMENT_NOT_FOUND")));

        command(projectId, "/classes", stable, classWithOperation("other", "Other", "other-display"), 201);
        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/classes/child/redefinitions", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(stable, """
                                {"featureKind":"OPERATION","localFeatureId":"local-display","redefinedFeatureIds":[]}
                                """)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code", equalTo("STALE_MODEL_REVISION")));
    }

    @Test
    void updatesAndProjectsStaticClassifierValueWithoutCreatingObjectSlots() throws Exception {
        String projectId = createProject("B39 static classifier value");
        command(projectId, "/classes", revision(projectId), """
                {"id":"student","name":"Student","superClassIds":[],"operations":[],
                 "attributes":[{"id":"next-number","name":"nextStudentNumber","type":"Integer"},
                  {"id":"max-number","name":"maxStudentNumber","type":"Integer",
                   "staticAttribute":true,"derived":true,"deriveExpression":"9999"}]}
                """, 201);

        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/classes/student/attributes/next-number", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(revision(projectId), """
                                {"id":"next-number","name":"nextStudentNumber","type":"Integer",
                                 "staticAttribute":true,"classifierValue":{"type":"Integer","value":1043}}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.staticAttribute", equalTo(true)))
                .andExpect(jsonPath("$.result.classifierValue.value", equalTo(1043)))
                .andExpect(jsonPath("$.revision", notNullValue()));

        mockMvc.perform(get("/api/v1/projects/{projectId}/read-model", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capabilities.staticFeatures", equalTo(true)))
                .andExpect(jsonPath("$.classes[0].attributes[0].staticFeature", equalTo(true)))
                .andExpect(jsonPath("$.classes[0].attributes[0].classifierValue.status", equalTo("VALUE")))
                .andExpect(jsonPath("$.classes[0].attributes[0].classifierValue.scalar", equalTo(1043)))
                .andExpect(jsonPath("$.classes[0].attributes[1].classifierValue.status", equalTo("VALUE")))
                .andExpect(jsonPath("$.classes[0].attributes[1].classifierValue.scalar", equalTo(9999)));
    }

    @Test
    void rejectsInvalidStaticAttributeDraftsAndKeepsRevision() throws Exception {
        String projectId = createProject("B39 static validation");
        command(projectId, "/classes", revision(projectId), """
                {"id":"student","name":"Student","superClassIds":[],"operations":[],
                 "attributes":[{"id":"next-number","name":"nextStudentNumber","type":"Integer"}]}
                """, 201);
        String stable = revision(projectId);

        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/classes/student/attributes/next-number", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(stable, """
                                {"id":"next-number","name":"nextStudentNumber","type":"Integer",
                                 "staticAttribute":true,"classifierValue":{"type":"String","value":"wrong"}}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("STATIC_VALUE_TYPE_MISMATCH")))
                .andExpect(jsonPath("$.details.draft.classifierValue.value", equalTo("wrong")));
        assertThat(revision(projectId)).isEqualTo(stable);

        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/classes/student/attributes/missing", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(stable, """
                                {"id":"missing","name":"missing","type":"Integer","staticAttribute":true}
                                """)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("ELEMENT_NOT_FOUND")));
    }

    @Test
    void createsUpdatesProjectsAndDeletesPersistentClassDefinition() throws Exception {
        String projectId = createProject("B40 class definition");
        command(projectId, "/classes", revision(projectId), """
                {"id":"person","name":"Person","superClassIds":[],"operations":[],
                 "attributes":[{"id":"name","name":"name","type":"String"}]}
                """, 201);
        MvcResult created = command(projectId, "/definitions", revision(projectId), """
                {"id":"display-name","kind":"PROPERTY_DEF","ownerKind":"CLASS","ownerId":"person",
                 "name":"displayName","resultType":"String","parameters":[],"expression":"self.name"}
                """, 201);
        JsonNode result = objectMapper.readTree(created.getResponse().getContentAsString());
        assertThat(result.at("/result/qualifiedName").asText()).isEqualTo("Person::displayName");
        assertThat(result.at("/result/sourceRange/startLine").asInt()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/projects/{projectId}/read-model", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.definitions[?(@.id == 'display-name')].readOnly", equalTo(java.util.List.of(false))));

        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/definitions/display-name", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(revision(projectId), """
                                {"kind":"PROPERTY_DEF","ownerKind":"CLASS","ownerId":"person",
                                 "name":"displayName","resultType":"String","parameters":[],
                                 "expression":"self.name.concat('!')"}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id", equalTo("display-name")));

        String deleteRevision = revision(projectId);
        mockMvc.perform(delete("/api/v1/projects/{projectId}/commands/DEFINITION/display-name", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteBody(deleteRevision, List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id", equalTo("display-name")));
        mockMvc.perform(get("/api/v1/projects/{projectId}/commands/definitions", projectId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()", equalTo(0)));
    }

    @Test
    void rejectsInvalidDefinitionWithoutChangingRevisionAndReturnsDraft() throws Exception {
        String projectId = createProject("B40 definition diagnostic");
        command(projectId, "/classes", revision(projectId),
                "{\"id\":\"person\",\"name\":\"Person\",\"attributes\":[],\"operations\":[],\"superClassIds\":[]}", 201);
        String stable = revision(projectId);
        mockMvc.perform(post("/api/v1/projects/{projectId}/commands/definitions", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(stable, """
                                {"id":"broken","kind":"PROPERTY_DEF","ownerKind":"CLASS","ownerId":"person",
                                 "name":"broken","resultType":"String","parameters":[],"expression":"1 +"}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("OCL_DEFINITION_COMPILE_FAILED")))
                .andExpect(jsonPath("$.details.draft.name", equalTo("broken")))
                .andExpect(jsonPath("$.details.definitionId", equalTo("broken")));
        assertThat(revision(projectId)).isEqualTo(stable);
    }

    @Test
    void persistsPackageDefinitionAndRejectsImplicitSelf() throws Exception {
        String projectId = createProject("B40 package definition");
        Project project = projectService.loadProject(new ProjectId(projectId));
        UmlModel model = project.umlModel();
        projectService.saveProject(new Project(project.id(), project.metadata(), project.modelText(),
                new UmlModel(model.id(), model.name(), model.classes(), model.associations(), model.invariants(),
                        model.enumerations(), java.util.List.of(new UmlPackage(new UmlPackageId("pkg-utils"), "utils")),
                        model.imports(), model.dataTypes()), project.objectModel(), project.layout(), project.definitions()));

        command(projectId, "/definitions", revision(projectId), """
                {"id":"answer","kind":"PROPERTY_DEF","ownerKind":"PACKAGE","ownerId":"pkg-utils",
                 "name":"answer","resultType":"Integer","parameters":[],"expression":"42"}
                """, 201);
        mockMvc.perform(get("/api/v1/projects/{projectId}/read-model", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.definitions[?(@.id == 'answer')].owner.kind", equalTo(java.util.List.of("PACKAGE"))))
                .andExpect(jsonPath("$.definitions[?(@.id == 'answer')].qualifiedName", equalTo(java.util.List.of("utils::answer"))));

        String stable = revision(projectId);
        mockMvc.perform(post("/api/v1/projects/{projectId}/commands/definitions", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(stable, """
                                {"id":"bad-self","kind":"PROPERTY_DEF","ownerKind":"PACKAGE","ownerId":"pkg-utils",
                                 "name":"badSelf","resultType":"Integer","parameters":[],"expression":"self.age"}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("PACKAGE_DEFINITION_SELF_NOT_ALLOWED")))
                .andExpect(jsonPath("$.details.draft.name", equalTo("badSelf")));
        assertThat(revision(projectId)).isEqualTo(stable);
    }

    @Test
    void createsAttributeThroughRevisionProtectedCommandAndRejectsInvalidDeriveDraft() throws Exception {
        String projectId = createProject("B44 attribute command");
        command(projectId, "/classes", revision(projectId),
                "{\"id\":\"person\",\"name\":\"Person\",\"attributes\":[],\"operations\":[],\"superClassIds\":[]}", 201);
        MvcResult created = command(projectId, "/classes/person/attributes", revision(projectId), """
                {"id":"age","name":"age","type":"Integer","derived":true,"deriveExpression":"1",
                 "staticAttribute":true,"visibility":"PUBLIC"}
                """, 201);
        JsonNode response = objectMapper.readTree(created.getResponse().getContentAsString());
        assertThat(response.get("command").asText()).isEqualTo("CREATE_ATTRIBUTE");
        assertThat(response.at("/result/staticAttribute").asBoolean()).isTrue();
        assertThat(response.at("/result/deriveExpression").asText()).isEqualTo("1");
        assertThat(response.at("/affectedElements/0/elementId").asText()).isEqualTo("age");

        String stable = revision(projectId);
        mockMvc.perform(post("/api/v1/projects/{projectId}/commands/classes/person/attributes", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(stable, """
                                {"id":"bad","name":"bad","type":"String","derived":true,"deriveExpression":"1"}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("OCL_FEATURE_COMPILE_FAILED")))
                .andExpect(jsonPath("$.details.draft.name", equalTo("bad")))
                .andExpect(jsonPath("$.details.diagnostics[0].code", equalTo("OCL_FEATURE_TYPE_MISMATCH")))
                .andExpect(jsonPath("$.details.targets[0].elementType", equalTo("ATTRIBUTE")));
        assertThat(revision(projectId)).isEqualTo(stable);
    }

    @Test
    void createsOperationWithContractsAndProjectsStaticMetadata() throws Exception {
        String projectId = createProject("B44 operation command");
        command(projectId, "/classes", revision(projectId),
                "{\"id\":\"person\",\"name\":\"Person\",\"attributes\":[],\"operations\":[],\"superClassIds\":[]}", 201);
        MvcResult created = command(projectId, "/classes/person/operations", revision(projectId), """
                {"id":"label","name":"label","returnType":"String","parameters":[],"bodyExpression":"'Person'",
                 "staticOperation":true,"query":true,"contracts":[{"id":"pre-label","name":"Always",
                 "kind":"PRE","expression":"true","enabled":true}]}
                """, 201);
        JsonNode response = objectMapper.readTree(created.getResponse().getContentAsString());
        assertThat(response.get("command").asText()).isEqualTo("CREATE_OPERATION");
        assertThat(response.at("/result/staticOperation").asBoolean()).isTrue();
        assertThat(response.at("/result/contracts/0/kind").asText()).isEqualTo("PRE");

        mockMvc.perform(get("/api/v1/projects/{projectId}/read-model", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classes[0].operations[0].staticFeature", equalTo(true)));

        String stable = revision(projectId);
        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/classes/person/operations/label", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(stable, """
                                {"id":"label","name":"label","returnType":"String","parameters":[],"bodyExpression":"1",
                                 "staticOperation":true,"query":true,"contracts":[]}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("OCL_FEATURE_COMPILE_FAILED")))
                .andExpect(jsonPath("$.details.diagnostics[0].source.range.startLine", equalTo(1)));
        assertThat(revision(projectId)).isEqualTo(stable);

        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/classes/person/operations/label", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(stable, """
                                {"id":"label","name":"label","returnType":"String","parameters":[
                                  {"id":"left","name":"value","type":"String"},
                                  {"id":"right","name":"value","type":"String"}],
                                 "bodyExpression":"'Person'","staticOperation":true,"contracts":[]}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("OPERATION_PARAMETER_CONFLICT")))
                .andExpect(jsonPath("$.details.draft.parameters[1].id", equalTo("right")));
        assertThat(revision(projectId)).isEqualTo(stable);

        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/classes/person/operations/label", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(stable, """
                                {"id":"label","name":"label","returnType":"String","parameters":[],
                                 "bodyExpression":"'Person'","staticOperation":true,
                                 "contracts":[{"id":"around","name":"Around","kind":"AROUND",
                                   "expression":"true","enabled":true}]}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("INVALID_OPERATION_CONTRACT_KIND")))
                .andExpect(jsonPath("$.details.field", equalTo("contracts.around.kind")));
        assertThat(revision(projectId)).isEqualTo(stable);
    }

    @Test
    void layoutSaveDoesNotMakeSubsequentOperationCommandStale() throws Exception {
        String projectId = createProject("Layout and operation command");
        command(projectId, "/classes", revision(projectId),
                "{\"id\":\"person\",\"name\":\"Person\",\"attributes\":[],\"operations\":[],\"superClassIds\":[]}", 201);
        String modelRevision = revision(projectId);

        mockMvc.perform(put("/api/v1/projects/{projectId}/layout", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "classDiagram": {"nodes": [], "edges": [], "viewport": null},
                                  "objectDiagram": {"nodes": [], "edges": [], "viewport": null}
                                }
                                """))
                .andExpect(status().isOk());

        command(projectId, "/classes/person/operations", modelRevision, """
                {"id":"label","name":"label","returnType":"String","parameters":[],
                 "bodyExpression":"'Person'","query":true,"contracts":[]}
                """, 201);
    }

    @Test
    void deletesOperationByStableIdWithoutOwnerInRequestAndPersistsRoundTrip() throws Exception {
        String projectId = createProject("B49 operation delete");
        command(projectId, "/classes", revision(projectId),
                "{\"id\":\"person\",\"name\":\"Person\",\"visibility\":\"PRIVATE\",\"attributes\":[],\"operations\":[],\"superClassIds\":[]}", 201);
        command(projectId, "/classes/person/operations", revision(projectId), """
                {"id":"label","name":"label","returnType":"String","parameters":[],
                 "bodyExpression":"'Person'","query":true,"contracts":[]}
                """, 201);

        MvcResult impactResult = mockMvc.perform(get(
                        "/api/v1/projects/{projectId}/commands/delete-impact/OPERATION/{operationId}",
                        projectId, "label"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target.elementId", equalTo("label")))
                .andExpect(jsonPath("$.target.elementName", equalTo("label")))
                .andExpect(jsonPath("$.blocked", equalTo(false)))
                .andReturn();
        String deleteRevision = objectMapper.readTree(impactResult.getResponse().getContentAsString())
                .get("revision").asText();

        mockMvc.perform(delete("/api/v1/projects/{projectId}/commands/OPERATION/{operationId}",
                        projectId, "label")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteBody(deleteRevision, List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.command", equalTo("DELETE_OPERATION")))
                .andExpect(jsonPath("$.revision", notNullValue()))
                .andExpect(jsonPath("$.affectedElements[0].elementId", equalTo("label")))
                .andExpect(jsonPath("$.affectedElements[1].elementType", equalTo("CLASS")))
                .andExpect(jsonPath("$.affectedElements[1].elementId", equalTo("person")));

        mockMvc.perform(get("/api/v1/projects/{projectId}/read-model", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classes[0].operations", hasSize(0)));
        ProjectJsonSerializer serializer = new ProjectJsonSerializer();
        Project persisted = projectService.loadProject(new ProjectId(projectId));
        assertThat(persisted.umlModel().findClass(new de.useweb.backend.domain.uml.UmlClassId("person"))
                .orElseThrow().visibility().name()).isEqualTo("PRIVATE");
        Project restored = serializer.deserialize(serializer.serialize(persisted));
        assertThat(restored.umlModel().findClass(new de.useweb.backend.domain.uml.UmlClassId("person"))
                .orElseThrow()).satisfies(owner -> {
                    assertThat(owner.visibility().name()).isEqualTo("PRIVATE");
                    assertThat(owner.operations()).isEmpty();
                });
    }

    @Test
    void returnsStructuredNotFoundForUnknownOperationDelete() throws Exception {
        String projectId = createProject("B49 operation not found");

        mockMvc.perform(get("/api/v1/projects/{projectId}/commands/delete-impact/OPERATION/{operationId}",
                        projectId, "missing-operation"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("ELEMENT_NOT_FOUND")))
                .andExpect(jsonPath("$.details.elementType", equalTo("OPERATION")))
                .andExpect(jsonPath("$.details.elementId", equalTo("missing-operation")))
                .andExpect(jsonPath("$.details.target.elementType", equalTo("OPERATION")))
                .andExpect(jsonPath("$.details.target.elementId", equalTo("missing-operation")));

        mockMvc.perform(delete("/api/v1/projects/{projectId}/commands/OPERATION/{operationId}",
                        projectId, "missing-operation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteBody(revision(projectId), List.of())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("ELEMENT_NOT_FOUND")))
                .andExpect(jsonPath("$.details.elementType", equalTo("OPERATION")))
                .andExpect(jsonPath("$.details.elementId", equalTo("missing-operation")))
                .andExpect(jsonPath("$.details.target.elementType", equalTo("OPERATION")))
                .andExpect(jsonPath("$.details.target.elementId", equalTo("missing-operation")));
    }

    @Test
    void returnsCurrentImpactAndCompleteDraftForStaleOperationDelete() throws Exception {
        String projectId = createProject("B49 stale operation delete");
        command(projectId, "/classes", revision(projectId),
                "{\"id\":\"person\",\"name\":\"Person\",\"attributes\":[],\"operations\":[],\"superClassIds\":[]}", 201);
        command(projectId, "/classes/person/operations", revision(projectId),
                "{\"id\":\"label\",\"name\":\"label\",\"returnType\":\"String\",\"parameters\":[]}", 201);
        String stale = revision(projectId);
        command(projectId, "/classes", stale,
                "{\"id\":\"book\",\"name\":\"Book\",\"attributes\":[],\"operations\":[],\"superClassIds\":[]}", 201);

        mockMvc.perform(delete("/api/v1/projects/{projectId}/commands/OPERATION/{operationId}",
                        projectId, "label")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteBody(stale, List.of())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("STALE_MODEL_REVISION")))
                .andExpect(jsonPath("$.details.draft.expectedRevision", equalTo(stale)))
                .andExpect(jsonPath("$.details.currentImpact.target.elementId", equalTo("label")))
                .andExpect(jsonPath("$.details.actualRevision", equalTo(revision(projectId))));
        assertThat(projectService.loadProject(new ProjectId(projectId)).umlModel().classes().stream()
                .flatMap(owner -> owner.operations().stream()).map(operation -> operation.id().value()))
                .contains("label");
    }

    @Test
    void blocksReferencedOperationDeleteAndRejectsUnknownCascade() throws Exception {
        String projectId = createProject("B49 operation blockers");
        command(projectId, "/classes", revision(projectId),
                "{\"id\":\"person\",\"name\":\"Person\",\"attributes\":[],\"operations\":[],\"superClassIds\":[]}", 201);
        command(projectId, "/classes/person/operations", revision(projectId), """
                {"id":"label","name":"label","returnType":"String","parameters":[],
                 "bodyExpression":"'Person'","query":true,"contracts":[]}
                """, 201);
        command(projectId, "/classes/person/operations", revision(projectId), """
                {"id":"caller","name":"caller","returnType":"String","parameters":[],
                 "bodyExpression":"self.label()","query":true,
                 "contracts":[{"id":"pre-caller","name":"CanCall","kind":"PRE",
                   "expression":"self.label() = 'Person'","enabled":true}]}
                """, 201);
        command(projectId, "/invariants", revision(projectId), """
                {"id":"uses-label","name":"UsesLabel","contextClassId":"person","enabled":true,
                 "expression":{"id":"uses-label-expression","text":"self.label() = 'Person'",
                   "language":"OCL","languageVersion":"2.4"}}
                """, 201);

        MvcResult impactResult = mockMvc.perform(get(
                        "/api/v1/projects/{projectId}/commands/delete-impact/OPERATION/{operationId}",
                        projectId, "label"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked", equalTo(true)))
                .andExpect(jsonPath("$.references[?(@.elementId == 'caller')]", hasSize(1)))
                .andExpect(jsonPath("$.references[?(@.elementId == 'uses-label')]", hasSize(1)))
                .andReturn();
        String deleteRevision = objectMapper.readTree(impactResult.getResponse().getContentAsString())
                .get("revision").asText();

        mockMvc.perform(delete("/api/v1/projects/{projectId}/commands/OPERATION/{operationId}",
                        projectId, "label").contentType(MediaType.APPLICATION_JSON)
                        .content(deleteBody(deleteRevision, List.of())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("DELETE_BLOCKED")))
                .andExpect(jsonPath("$.details.draft.expectedRevision", equalTo(deleteRevision)))
                .andExpect(jsonPath("$.details.target.elementId", equalTo("label")))
                .andExpect(jsonPath("$.details.blockers[?(@.elementId == 'caller')]", hasSize(1)));

        mockMvc.perform(delete("/api/v1/projects/{projectId}/commands/OPERATION/{operationId}",
                        projectId, "label").contentType(MediaType.APPLICATION_JSON)
                        .content(deleteBody(deleteRevision, List.of("not-allowed"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("INVALID_CASCADE_SELECTION")));
        assertThat(revision(projectId)).isEqualTo(deleteRevision);
    }

    @Test
    void updatesAssociationEndsQualifiersAndAssociationClassThroughCommand() throws Exception {
        String projectId = createProject("B44 association command");
        command(projectId, "/classes", revision(projectId),
                "{\"id\":\"person\",\"name\":\"Person\",\"attributes\":[],\"operations\":[],\"superClassIds\":[]}", 201);
        command(projectId, "/classes", revision(projectId),
                "{\"id\":\"course\",\"name\":\"Course\",\"attributes\":[],\"operations\":[],\"superClassIds\":[]}", 201);
        command(projectId, "/classes", revision(projectId),
                "{\"id\":\"enrollment\",\"name\":\"Enrollment\",\"attributes\":[],\"operations\":[],\"superClassIds\":[]}", 201);
        command(projectId, "/associations", revision(projectId), """
                {"id":"attends","name":"Attends","ends":[
                  {"id":"personEnd","classId":"person","roleName":"students","navigable":true,
                   "multiplicity":{"lower":0,"unbounded":true,"raw":"*"}},
                  {"id":"courseEnd","classId":"course","roleName":"courses","navigable":true,
                   "multiplicity":{"lower":0,"unbounded":true,"raw":"*"}}]}
                """, 201);

        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/associations/attends", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(revision(projectId), """
                                {"id":"attends","name":"Enrollment","associationClassId":"enrollment","ends":[
                                  {"id":"personEnd","classId":"person","roleName":"students","navigable":true,
                                   "ordered":true,"unique":true,"aggregationKind":"NONE",
                                   "multiplicity":{"lower":0,"unbounded":true,"raw":"*"}},
                                  {"id":"courseEnd","classId":"course","roleName":"courses","navigable":true,
                                   "ordered":false,"unique":true,"aggregationKind":"SHARED",
                                   "qualifiers":[{"id":"semester","name":"semester","type":"String","order":0}],
                                   "multiplicity":{"lower":0,"unbounded":true,"raw":"*"}}]}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.command", equalTo("UPDATE_ASSOCIATION")))
                .andExpect(jsonPath("$.result.associationClassId", equalTo("enrollment")))
                .andExpect(jsonPath("$.result.ends[0].ordered", equalTo(true)))
                .andExpect(jsonPath("$.result.ends[1].aggregationKind", equalTo("SHARED")))
                .andExpect(jsonPath("$.result.ends[1].qualifiers[0].id", equalTo("semester")))
                .andExpect(jsonPath("$.affectedElements[?(@.elementType == 'ASSOCIATION_END')]", hasSize(2)))
                .andExpect(jsonPath("$.affectedElements[?(@.elementType == 'QUALIFIER')]", hasSize(1)));

        String stable = revision(projectId);
        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/associations/missing", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(stable, """
                                {"id":"missing","name":"Missing","ends":[]}
                                """)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("ELEMENT_NOT_FOUND")))
                .andExpect(jsonPath("$.details.draft.id", equalTo("missing")));
        assertThat(revision(projectId)).isEqualTo(stable);
    }

    @Test
    void createsAssociationClassFeaturesAndBindingAsOneAtomicCommand() throws Exception {
        String projectId = createProject("B48 association class aggregate");
        command(projectId, "/classes", revision(projectId),
                "{\"id\":\"person\",\"name\":\"Person\",\"attributes\":[],\"operations\":[],\"superClassIds\":[]}", 201);
        command(projectId, "/classes", revision(projectId),
                "{\"id\":\"course\",\"name\":\"Course\",\"attributes\":[],\"operations\":[],\"superClassIds\":[]}", 201);
        command(projectId, "/associations", revision(projectId), """
                {"id":"enrollment","name":"Enrollment","ends":[
                  {"id":"student-end","classId":"person","roleName":"student",
                   "multiplicity":{"lower":0,"unbounded":true,"raw":"*"}},
                  {"id":"course-end","classId":"course","roleName":"course",
                   "multiplicity":{"lower":0,"unbounded":true,"raw":"*"}}]}
                """, 201);
        String before = revision(projectId);

        mockMvc.perform(post("/api/v1/projects/{projectId}/commands/associations/enrollment/association-class", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(before, """
                                {"id":"enrollment-class","name":"EnrollmentRecord","superClassIds":[],
                                 "attributes":[{"id":"grade","name":"grade","type":"Integer"}],
                                 "operations":[{"id":"summary","name":"summary","returnType":"String",
                                                "parameters":[]}]}
                                """)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.command", equalTo("CREATE_ASSOCIATION_CLASS")))
                .andExpect(jsonPath("$.revision", notNullValue()))
                .andExpect(jsonPath("$.result.association.associationClassId", equalTo("enrollment-class")))
                .andExpect(jsonPath("$.result.associationClass.attributes[0].id", equalTo("grade")))
                .andExpect(jsonPath("$.result.associationClass.operations[0].id", equalTo("summary")))
                .andExpect(jsonPath("$.affectedElements[?(@.elementType == 'CLASS')]", hasSize(1)))
                .andExpect(jsonPath("$.affectedElements[?(@.elementType == 'ATTRIBUTE')]", hasSize(1)))
                .andExpect(jsonPath("$.affectedElements[?(@.elementType == 'OPERATION')]", hasSize(1)));
        assertThat(revision(projectId)).isNotEqualTo(before);

        String stable = revision(projectId);
        mockMvc.perform(post("/api/v1/projects/{projectId}/commands/associations/enrollment/association-class", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(stable, """
                                {"id":"duplicate","name":"Duplicate","attributes":[],"operations":[],
                                 "superClassIds":[]}
                                """)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("ASSOCIATION_CLASS_ALREADY_BOUND")))
                .andExpect(jsonPath("$.details.draft.id", equalTo("duplicate")));
        assertThat(revision(projectId)).isEqualTo(stable);

        mockMvc.perform(post("/api/v1/projects/{projectId}/commands/associations/missing/association-class", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(stable, """
                                {"id":"missing-class","name":"Missing","attributes":[],"operations":[],
                                 "superClassIds":[]}
                                """)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.details.draft.id", equalTo("missing-class")));
        assertThat(revision(projectId)).isEqualTo(stable);
    }

    private String classWithOperation(String id, String name, String operationId) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + name
                + "\",\"attributes\":[],\"operations\":[{\"id\":\"" + operationId
                + "\",\"name\":\"displayName\",\"returnType\":\"String\",\"parameters\":[]}],\"superClassIds\":[]}";
    }

    @Test
    void acceptsB50StructuredAttributeTypesAndClassifierValuesThroughCommands() throws Exception {
        String projectId = createProject("B50 model commands");
        command(projectId, "/datatypes", revision(projectId), """
                {"id":"money","name":"Money","properties":[
                  {"id":"amount","name":"amount","type":"Real"},
                  {"id":"currency","name":"currency","type":"String"}]}
                """, 201);
        command(projectId, "/classes", revision(projectId),
                "{\"id\":\"ledger\",\"name\":\"Ledger\",\"attributes\":[],\"operations\":[],\"superClassIds\":[]}", 201);

        MvcResult created = command(projectId, "/classes/ledger/attributes", revision(projectId), """
                {"id":"totals","name":"totals","type":"Sequence(Tuple(label:String,amount:Money))",
                 "staticAttribute":true,"classifierValue":{
                   "type":"Sequence(Tuple(label:String,amount:Money))",
                   "value":[{"label":"net","amount":{"amount":12.5,"currency":"EUR"}}]}}
                """, 201);
        JsonNode response = objectMapper.readTree(created.getResponse().getContentAsString());
        assertThat(response.at("/result/type").asText())
                .isEqualTo("Sequence(Tuple(label:String,amount:Money))");
        assertThat(response.at("/result/classifierValue/value/0/amount/currency").asText()).isEqualTo("EUR");

        mockMvc.perform(get("/api/v1/projects/{projectId}/commands/delete-impact/DATATYPE/money", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked", equalTo(true)))
                .andExpect(jsonPath("$.target.elementId", equalTo("money")))
                .andExpect(jsonPath("$.references[0].elementId", equalTo("totals")));

        String stable = revision(projectId);
        mockMvc.perform(post("/api/v1/projects/{projectId}/commands/classes/ledger/attributes", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(stable, """
                                {"id":"broken","name":"broken","type":"Sequence(UnknownType)"}
                                """)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("TYPE_ERROR")))
                .andExpect(jsonPath("$.details.draft.type", equalTo("Sequence(UnknownType)")))
                .andExpect(jsonPath("$.details.fieldPath", equalTo("type.elementType")));
        assertThat(revision(projectId)).isEqualTo(stable);
    }

    @Test
    void reportsRecursivePersistedAndOclBlockersForDataTypePropertyDeletion() throws Exception {
        String projectId = createProject("B51 property blockers");
        command(projectId, "/datatypes", revision(projectId), """
                {"id":"money","name":"Money","properties":[
                  {"id":"currency","name":"currency","type":"String"},
                  {"id":"amount","name":"amount","type":"Real"}]}
                """, 201);
        command(projectId, "/datatypes", revision(projectId), """
                {"id":"envelope","name":"Envelope","properties":[
                  {"id":"primary","name":"primary","type":"Money"},
                  {"id":"tupleValue","name":"tupleValue","type":"Tuple(money:Money)"},
                  {"id":"setValue","name":"setValue","type":"Set(Money)"},
                  {"id":"bagValue","name":"bagValue","type":"Bag(Money)"},
                  {"id":"sequenceValue","name":"sequenceValue","type":"Sequence(Money)"},
                  {"id":"orderedValue","name":"orderedValue","type":"OrderedSet(Money)"}]}
                """, 201);
        String envelopeValue = """
                {"primary":{"currency":"EUR","amount":1.0},
                 "tupleValue":{"money":{"currency":"EUR","amount":2.0}},
                 "setValue":[{"currency":"EUR","amount":3.0}],
                 "bagValue":[{"currency":"EUR","amount":4.0},{"currency":"EUR","amount":4.0}],
                 "sequenceValue":[{"currency":"EUR","amount":5.0}],
                 "orderedValue":[{"currency":"EUR","amount":6.0}]}
                """;
        command(projectId, "/classes", revision(projectId), """
                {"id":"ledger","name":"Ledger","operations":[],"superClassIds":[],"attributes":[
                  {"id":"directStored","name":"directStored","type":"Money","staticAttribute":true,
                   "classifierValue":{"type":"Money","value":{"currency":"EUR","amount":0.5}}},
                  {"id":"stored","name":"stored","type":"Envelope","staticAttribute":true,
                   "classifierValue":{"type":"Envelope","value":%s}},
                  {"id":"instanceStored","name":"instanceStored","type":"Envelope"},
                  {"id":"moneyValue","name":"moneyValue","type":"Money"}]}
                """.formatted(envelopeValue), 201);
        mockMvc.perform(post("/api/v1/projects/{projectId}/commands/object-model/objects", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(revision(projectId), """
                                {"id":"ledger-1","name":"ledger1","classId":"ledger","slots":[
                                  {"id":"slot-envelope","attributeId":"instanceStored","isUnset":false,
                                   "value":{"type":"Envelope","value":%s}}]}
                                """.formatted(envelopeValue))))
                .andExpect(status().isCreated());
        command(projectId, "/invariants", revision(projectId), """
                {"id":"currency-check","name":"CurrencyCheck","contextClassId":"ledger","enabled":true,
                 "expression":{"id":"currency-check-expression","text":"self.moneyValue.currency = 'EUR'",
                               "language":"OCL","languageVersion":"2.4"}}
                """, 201);

        MvcResult impactResult = mockMvc.perform(get(
                        "/api/v1/projects/{projectId}/commands/datatypes/money/properties/currency/delete-impact",
                        projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked", equalTo(true)))
                .andExpect(jsonPath("$.target.elementType", equalTo("DATATYPE_PROPERTY")))
                .andReturn();
        JsonNode impact = objectMapper.readTree(impactResult.getResponse().getContentAsString());
        List<String> paths = new java.util.ArrayList<>();
        impact.withArray("references").forEach(reference -> paths.add(reference.path("path").asText()));
        assertThat(paths).anyMatch(path -> path.contains(".primary.currency"))
                .anyMatch(path -> path.endsWith("classifierValue.value.currency"))
                .anyMatch(path -> path.contains(".tupleValue.money.currency"))
                .anyMatch(path -> path.contains(".setValue[0].currency"))
                .anyMatch(path -> path.contains(".bagValue[1].currency"))
                .anyMatch(path -> path.contains(".sequenceValue[0].currency"))
                .anyMatch(path -> path.contains(".orderedValue[0].currency"));
        JsonNode oclReference = impact.withArray("references").findValuesAsText("elementId").contains("currency-check")
                ? java.util.stream.StreamSupport.stream(impact.withArray("references").spliterator(), false)
                        .filter(reference -> "currency-check".equals(reference.path("elementId").asText()))
                        .findFirst().orElseThrow()
                : null;
        assertThat(oclReference).isNotNull();
        assertThat(oclReference.path("sourceRange").path("startOffset").asInt()).isGreaterThanOrEqualTo(0);

        String stable = revision(projectId);
        mockMvc.perform(delete("/api/v1/projects/{projectId}/commands/datatypes/money/properties/currency", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteBody(stable, List.of())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("DELETE_BLOCKED")))
                .andExpect(jsonPath("$.details.draft.expectedRevision", equalTo(stable)))
                .andExpect(jsonPath("$.details.currentImpact.blocked", equalTo(true)));
        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/datatypes/money", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(stable, """
                                {"id":"money","name":"Money","properties":[
                                  {"id":"amount","name":"amount","type":"Real"}]}
                                """)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("DELETE_BLOCKED")))
                .andExpect(jsonPath("$.details.draft.properties[0].id", equalTo("amount")));
        assertThat(revision(projectId)).isEqualTo(stable);
    }

    @Test
    void deletesUnreferencedDataTypePropertyWithRevisionAndStableRemainingOrder() throws Exception {
        String projectId = createProject("B51 property lifecycle");
        command(projectId, "/datatypes", revision(projectId), """
                {"id":"address","name":"Address","properties":[
                  {"id":"street","name":"street","type":"String"},
                  {"id":"postalCode","name":"postalCode","type":"String"},
                  {"id":"city","name":"city","type":"String"}]}
                """, 201);
        mockMvc.perform(get("/api/v1/projects/{projectId}/commands/datatypes/missing/properties/postalCode/delete-impact",
                        projectId)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("ELEMENT_NOT_FOUND")));
        mockMvc.perform(get("/api/v1/projects/{projectId}/commands/datatypes/address/properties/missing/delete-impact",
                        projectId)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.details.elementType", equalTo("DATATYPE_PROPERTY")));

        String previewRevision = revision(projectId);
        mockMvc.perform(delete("/api/v1/projects/{projectId}/commands/datatypes/address/properties/postalCode", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(deleteBody(previewRevision, List.of("not-allowed"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("INVALID_CASCADE_SELECTION")));
        command(projectId, "/classes", previewRevision,
                "{\"id\":\"marker\",\"name\":\"Marker\",\"attributes\":[],\"operations\":[],\"superClassIds\":[]}",
                201);
        mockMvc.perform(delete("/api/v1/projects/{projectId}/commands/datatypes/address/properties/postalCode", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(deleteBody(previewRevision, List.of())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("STALE_MODEL_REVISION")))
                .andExpect(jsonPath("$.details.currentImpact.target.elementId", equalTo("postalCode")));

        String current = revision(projectId);
        MvcResult deleted = mockMvc.perform(delete(
                        "/api/v1/projects/{projectId}/commands/datatypes/address/properties/postalCode", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(deleteBody(current, List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.command", equalTo("DELETE_DATATYPE_PROPERTY")))
                .andExpect(jsonPath("$.result.properties[0].id", equalTo("street")))
                .andExpect(jsonPath("$.result.properties[1].id", equalTo("city")))
                .andReturn();
        assertThat(objectMapper.readTree(deleted.getResponse().getContentAsString()).path("revision").asText())
                .isNotEqualTo(current);
        Project restored = projectJsonSerializer.deserialize(
                projectJsonSerializer.serialize(projectService.loadProject(new ProjectId(projectId))));
        assertThat(restored.umlModel().dataTypes().stream().filter(type -> type.id().value().equals("address"))
                .findFirst().orElseThrow().properties()).extracting("id").containsExactly("street", "city");

        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/datatypes/address", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(revision(projectId), """
                                {"id":"address","name":"Address","properties":[
                                  {"id":"street","name":"street","type":"String"}]}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.properties.length()", equalTo(1)))
                .andExpect(jsonPath("$.result.properties[0].id", equalTo("street")));
    }

    private MvcResult command(String projectId, String path, String revision, String draft, int status) throws Exception {
        return mockMvc.perform(post("/api/v1/projects/{projectId}/commands" + path, projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(revision, draft)))
                .andExpect(status().is(status)).andReturn();
    }

    private String commandBody(String revision, String draft) throws Exception {
        return "{\"expectedRevision\":" + quote(revision) + ",\"draft\":" + draft + "}";
    }

    private String deleteBody(String revision, List<String> cascadeReferenceIds) throws Exception {
        return objectMapper.writeValueAsString(new DeleteCommandRequestDto(revision, cascadeReferenceIds));
    }

    private String revision(String projectId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/projects/{projectId}", projectId)).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/project/updatedAt").asText();
    }

    private String createProject(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProjectRequestDto(name, "B36", null))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/project/id").asText();
    }

    private String quote(String value) throws Exception { return objectMapper.writeValueAsString(value); }
}
