package de.useweb.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
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
class PackageImportCommandControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ProjectService projectService;
    @Autowired ProjectJsonSerializer serializer;

    @Test
    void createsRenamesAndMovesPackageSubtreeWithReadModelProjection() throws Exception {
        String projectId = createProject("B45 package move");
        createPackage(projectId, "root", "university");
        createPackage(projectId, "people", "university::people");
        command(projectId, "/classes", """
                {"id":"person","name":"Person","packageId":"people",
                 "attributes":[],"operations":[],"superClassIds":[]}
                """, 201);

        String beforeMove = revision(projectId);
        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/packages/root", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commandBody(beforeMove, "{\"id\":\"root\",\"qualifiedName\":\"campus\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.command", equalTo("UPDATE_PACKAGE")))
                .andExpect(jsonPath("$.result.qualifiedName", equalTo("campus")))
                .andExpect(jsonPath("$.affectedElements[0].elementId", equalTo("root")));

        mockMvc.perform(get("/api/v1/projects/{projectId}/read-model", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.explorer[?(@.elementId == 'people')].qualifiedName",
                        equalTo(List.of("campus::people"))))
                .andExpect(jsonPath("$.explorer[?(@.elementId == 'people')].parentNodeId",
                        equalTo(List.of("root"))))
                .andExpect(jsonPath("$.classes[?(@.id == 'person')].qualifiedName",
                        equalTo(List.of("campus::people::Person"))));

        createPackage(projectId, "archive", "archive");
        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/packages/people", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commandBody(revision(projectId),
                                "{\"id\":\"people\",\"qualifiedName\":\"archive::people\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id", equalTo("people")))
                .andExpect(jsonPath("$.result.qualifiedName", equalTo("archive::people")));
        mockMvc.perform(get("/api/v1/projects/{projectId}/read-model", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.explorer[?(@.nodeId == 'people')].parentNodeId",
                        equalTo(List.of("archive"))))
                .andExpect(jsonPath("$.classes[?(@.id == 'person')].qualifiedName",
                        equalTo(List.of("archive::people::Person"))));
    }

    @Test
    void rejectsPackageConflictAndStaleImportUpdateWithoutSideEffects() throws Exception {
        String projectId = createProject("B45 conflicts");
        createPackage(projectId, "one", "one");
        createPackage(projectId, "two", "two");
        String stable = revision(projectId);
        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/packages/two", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commandBody(stable, "{\"id\":\"two\",\"qualifiedName\":\"one\"}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("DUPLICATE_NAMESPACE")))
                .andExpect(jsonPath("$.details.draft.qualifiedName", equalTo("one")));
        assertThat(revision(projectId)).isEqualTo(stable);

        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/packages/one", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commandBody(stable,
                                "{\"id\":\"one\",\"qualifiedName\":\"one::nested\"}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("PACKAGE_CYCLE")))
                .andExpect(jsonPath("$.details.draft.qualifiedName", equalTo("one::nested")));
        assertThat(revision(projectId)).isEqualTo(stable);

        command(projectId, "/imports", """
                {"id":"one-to-two","importingPackageId":"one","importedPackageId":"two",
                 "alias":"shared","source":"two.use","provenance":"WORKSPACE"}
                """, 201);
        String current = revision(projectId);
        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/imports/one-to-two", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commandBody(stable, """
                                {"id":"one-to-two","importingPackageId":"one","importedPackageId":"two",
                                 "alias":"core","source":"core.use","provenance":"WORKSPACE"}
                                """)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("STALE_MODEL_REVISION")))
                .andExpect(jsonPath("$.details.draft.alias", equalTo("core")));
        assertThat(revision(projectId)).isEqualTo(current);
    }

    @Test
    void rejectsImportCycleAndKeepsSubmittedDraft() throws Exception {
        String projectId = createProject("B45 import cycle");
        createPackage(projectId, "one", "one");
        createPackage(projectId, "two", "two");
        createPackage(projectId, "three", "three");
        command(projectId, "/imports", """
                {"id":"one-two","importingPackageId":"one","importedPackageId":"two","alias":"two"}
                """, 201);
        command(projectId, "/imports", """
                {"id":"two-three","importingPackageId":"two","importedPackageId":"three","alias":"three"}
                """, 201);
        String stable = revision(projectId);

        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/imports/two-three", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(stable, """
                                {"id":"two-three","importingPackageId":"two","importedPackageId":"one",
                                 "alias":"one","source":"one.use","provenance":"WORKSPACE"}
                                """)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("IMPORT_CYCLE")))
                .andExpect(jsonPath("$.details.draft.importedPackageId", equalTo("one")))
                .andExpect(jsonPath("$.details.targets[?(@.elementId == 'two-three')]", hasSize(1)));
        assertThat(revision(projectId)).isEqualTo(stable);
    }

    @Test
    void requiresExplicitPackageCascadeAndDeletesAggregateAtomically() throws Exception {
        String projectId = createProject("B45 package delete");
        createPackage(projectId, "root", "university");
        createPackage(projectId, "people", "university::people");
        createPackage(projectId, "external", "external");
        command(projectId, "/classes", """
                {"id":"person","name":"Person","packageId":"people",
                 "attributes":[],"operations":[],"superClassIds":[]}
                """, 201);
        command(projectId, "/imports", """
                {"id":"external-people","importingPackageId":"external","importedPackageId":"people",
                 "alias":"people"}
                """, 201);

        MvcResult impactResult = mockMvc.perform(get(
                        "/api/v1/projects/{projectId}/commands/delete-impact/PACKAGE/root", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.references[?(@.elementType == 'PACKAGE')]", hasSize(1)))
                .andExpect(jsonPath("$.references[?(@.elementType == 'CLASS')]", hasSize(1)))
                .andExpect(jsonPath("$.references[?(@.elementType == 'IMPORT')]", hasSize(1)))
                .andReturn();
        JsonNode impact = objectMapper.readTree(impactResult.getResponse().getContentAsString());
        String deleteRevision = impact.get("revision").asText();
        mockMvc.perform(delete("/api/v1/projects/{projectId}/commands/PACKAGE/root", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteBody(deleteRevision, List.of())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code", equalTo("DELETE_BLOCKED")));
        assertThat(revision(projectId)).isEqualTo(deleteRevision);

        List<String> references = new ArrayList<>();
        impact.get("references").forEach(reference -> references.add(reference.get("referenceId").asText()));
        mockMvc.perform(delete("/api/v1/projects/{projectId}/commands/PACKAGE/root", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteBody(deleteRevision, references)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.command", equalTo("DELETE_PACKAGE")));
        mockMvc.perform(get("/api/v1/projects/{projectId}/uml-model", projectId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.packages[?(@.id == 'root')]", hasSize(0)))
                .andExpect(jsonPath("$.packages[?(@.id == 'people')]", hasSize(0)))
                .andExpect(jsonPath("$.classes[?(@.id == 'person')]", hasSize(0)))
                .andExpect(jsonPath("$.imports[?(@.id == 'external-people')]", hasSize(0)));
    }

    @Test
    void updatesImportDeletesItWithRevisionAndRoundTripsPersistence() throws Exception {
        String projectId = createProject("B45 import lifecycle");
        createPackage(projectId, "one", "one");
        createPackage(projectId, "two", "two");
        command(projectId, "/imports", """
                {"id":"one-two","importingPackageId":"one","importedPackageId":"two",
                 "alias":"shared","source":"two.use","provenance":"WORKSPACE"}
                """, 201);
        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/imports/one-two", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(revision(projectId), """
                                {"id":"one-two","importingPackageId":"one","importedPackageId":"two",
                                 "alias":"core","source":"core.use","provenance":"REPLACED_SOURCE"}
                                """)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.alias", equalTo("core")));
        mockMvc.perform(get("/api/v1/projects/{projectId}/read-model", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.explorer[?(@.nodeId == 'import-root-one-two')].name",
                        equalTo(List.of("core"))))
                .andExpect(jsonPath("$.explorer[?(@.nodeId == 'import-root-one-two')].readOnly",
                        equalTo(List.of(true))))
                .andExpect(jsonPath("$.explorer[?(@.nodeId == 'import-root-one-two')].provenance",
                        equalTo(List.of("REPLACED_SOURCE"))));

        String json = serializer.serialize(projectService.loadProject(new ProjectId(projectId)));
        var restored = serializer.deserialize(json);
        assertThat(restored.umlModel().imports()).singleElement().satisfies(modelImport -> {
            assertThat(modelImport.id().value()).isEqualTo("one-two");
            assertThat(modelImport.alias()).isEqualTo("core");
            assertThat(modelImport.provenance()).isEqualTo("REPLACED_SOURCE");
        });

        MvcResult impactResult = mockMvc.perform(get(
                        "/api/v1/projects/{projectId}/commands/delete-impact/IMPORT/one-two", projectId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.blocked", equalTo(false))).andReturn();
        String deleteRevision = objectMapper.readTree(impactResult.getResponse().getContentAsString())
                .get("revision").asText();
        mockMvc.perform(delete("/api/v1/projects/{projectId}/commands/IMPORT/one-two", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteBody(deleteRevision, List.of())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.command", equalTo("DELETE_IMPORT")));
    }

    @Test
    void reportsOclDefinitionAsImportDeleteBlockerAndHandlesValidationAndNotFound() throws Exception {
        String projectId = createProject("B45 import blockers");
        createPackage(projectId, "one", "one");
        createPackage(projectId, "two", "two");
        command(projectId, "/classes", """
                {"id":"thing","name":"Thing","packageId":"two",
                 "attributes":[],"operations":[],"superClassIds":[]}
                """, 201);
        command(projectId, "/imports", """
                {"id":"one-two","importingPackageId":"one","importedPackageId":"two","alias":"shared"}
                """, 201);
        command(projectId, "/definitions", """
                {"id":"thingCount","kind":"PROPERTY_DEF","ownerKind":"PACKAGE","ownerId":"one",
                 "name":"thingCount","resultType":"Integer","parameters":[],
                 "expression":"shared::Thing.allInstances()->size()"}
                """, 201);

        MvcResult impact = mockMvc.perform(get(
                        "/api/v1/projects/{projectId}/commands/delete-impact/IMPORT/one-two", projectId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.blocked", equalTo(true)))
                .andExpect(jsonPath("$.references[?(@.elementId == 'thingCount')]", hasSize(1))).andReturn();
        String stable = objectMapper.readTree(impact.getResponse().getContentAsString()).get("revision").asText();
        mockMvc.perform(delete("/api/v1/projects/{projectId}/commands/IMPORT/one-two", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteBody(stable, List.of())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code", equalTo("DELETE_BLOCKED")));
        assertThat(revision(projectId)).isEqualTo(stable);

        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/packages/missing", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commandBody(stable, "{\"id\":\"missing\",\"qualifiedName\":\"missing\"}")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code", equalTo("ELEMENT_NOT_FOUND")));
        mockMvc.perform(post("/api/v1/projects/{projectId}/commands/packages", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commandBody(stable, "{\"id\":\"bad\",\"qualifiedName\":\"bad name\"}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code", equalTo("INVALID_PACKAGE")))
                .andExpect(jsonPath("$.details.draft.qualifiedName", equalTo("bad name")));
        assertThat(revision(projectId)).isEqualTo(stable);
    }

    @Test
    void reportsExternalClassifierTypeAsNonCascadePackageDeleteBlocker() throws Exception {
        String projectId = createProject("B45 package type blocker");
        createPackage(projectId, "people", "university::people");
        createPackage(projectId, "courses", "university::courses");
        command(projectId, "/classes", """
                {"id":"person","name":"Person","packageId":"people",
                 "attributes":[],"operations":[],"superClassIds":[]}
                """, 201);
        command(projectId, "/classes", """
                {"id":"course","name":"Course","packageId":"courses",
                 "attributes":[{"id":"teacher","name":"teacher","type":"Person"}],
                 "operations":[],"superClassIds":[]}
                """, 201);

        String stable = revision(projectId);
        mockMvc.perform(get("/api/v1/projects/{projectId}/commands/delete-impact/PACKAGE/people", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked", equalTo(true)))
                .andExpect(jsonPath("$.references[?(@.elementId == 'teacher' && @.relation == 'TYPED_BY_PACKAGE')]",
                        hasSize(1)));
        mockMvc.perform(delete("/api/v1/projects/{projectId}/commands/PACKAGE/people", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteBody(stable, List.of())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code", equalTo("DELETE_BLOCKED")));
        assertThat(revision(projectId)).isEqualTo(stable);
    }

    @Test
    void reportsOclUseOfImportAsPackageDeleteBlocker() throws Exception {
        String projectId = createProject("B45 package import blocker");
        createPackage(projectId, "client", "client");
        createPackage(projectId, "shared", "shared");
        command(projectId, "/classes", """
                {"id":"thing","name":"Thing","packageId":"shared",
                 "attributes":[],"operations":[],"superClassIds":[]}
                """, 201);
        command(projectId, "/imports", """
                {"id":"client-shared","importingPackageId":"client","importedPackageId":"shared",
                 "alias":"lib"}
                """, 201);
        command(projectId, "/definitions", """
                {"id":"thingCount","kind":"PROPERTY_DEF","ownerKind":"PACKAGE","ownerId":"client",
                 "name":"thingCount","resultType":"Integer","parameters":[],
                 "expression":"lib::Thing.allInstances()->size()"}
                """, 201);

        mockMvc.perform(get("/api/v1/projects/{projectId}/commands/delete-impact/PACKAGE/shared", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked", equalTo(true)))
                .andExpect(jsonPath("$.references[?(@.elementId == 'client-shared' && @.cascadeAllowed == true)]",
                        hasSize(1)))
                .andExpect(jsonPath("$.references[?(@.elementId == 'thingCount' && @.relation == 'REFERENCES_IMPORT')]",
                        hasSize(1)));
    }

    private void createPackage(String projectId, String id, String qualifiedName) throws Exception {
        command(projectId, "/packages", "{\"id\":" + quote(id) + ",\"qualifiedName\":"
                + quote(qualifiedName) + "}", 201);
    }

    private MvcResult command(String projectId, String path, String draft, int expectedStatus) throws Exception {
        return mockMvc.perform(post("/api/v1/projects/{projectId}/commands" + path, projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(commandBody(revision(projectId), draft)))
                .andExpect(status().is(expectedStatus)).andReturn();
    }

    private String commandBody(String revision, String draft) throws Exception {
        return "{\"expectedRevision\":" + quote(revision) + ",\"draft\":" + draft + "}";
    }

    private String deleteBody(String revision, List<String> cascadeReferenceIds) throws Exception {
        return objectMapper.writeValueAsString(new DeleteCommandRequestDto(revision, cascadeReferenceIds));
    }

    private String revision(String projectId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/projects/{projectId}", projectId))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/project/updatedAt").asText();
    }

    private String createProject(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProjectRequestDto(name, "B45", null))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/project/id").asText();
    }

    private String quote(String value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
