package de.useweb.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
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

@SpringBootTest
@AutoConfigureMockMvc
class EnumerationCommandControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void createsUpdatesReordersAndPersistsStableLiteralIds() throws Exception {
        String projectId = createProject("B43 enumeration lifecycle");
        JsonNode created = mutate(post("/api/v1/projects/{projectId}/commands/enumerations", projectId),
                revision(projectId), """
                {"id":"status","name":"Status","visibility":"PUBLIC","literalDefinitions":[
                  {"id":"lit-open","name":"OPEN"},{"id":"lit-closed","name":"CLOSED"}]}
                """, 201);
        String afterCreate = created.get("revision").asText();
        assertThat(created.at("/result/literalDefinitions/0/id").asText()).isEqualTo("lit-open");

        JsonNode updated = mutate(put("/api/v1/projects/{projectId}/commands/enumerations/{id}", projectId, "status"),
                afterCreate, """
                {"id":"status","name":"WorkflowStatus","visibility":"PRIVATE","literalDefinitions":[
                  {"id":"lit-closed","name":"CLOSED"},{"id":"lit-open","name":"READY"}]}
                """, 200);
        assertThat(updated.at("/result/literalDefinitions/0/id").asText()).isEqualTo("lit-closed");
        assertThat(updated.at("/result/literalDefinitions/1/id").asText()).isEqualTo("lit-open");
        assertThat(updated.at("/result/visibility").asText()).isEqualTo("PRIVATE");

        MvcResult literalImpact = mockMvc.perform(get(
                        "/api/v1/projects/{projectId}/commands/delete-impact/ENUMERATION_LITERAL/lit-open", projectId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.blocked", equalTo(false))).andReturn();
        String deleteRevision = objectMapper.readTree(literalImpact.getResponse().getContentAsString()).get("revision").asText();
        mockMvc.perform(delete("/api/v1/projects/{projectId}/commands/ENUMERATION_LITERAL/lit-open", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(deleteBody(deleteRevision, List.of(), "status")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.literalDefinitions.length()", equalTo(1)));

        mockMvc.perform(get("/api/v1/projects/{projectId}/uml-model", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enumerations[0].literals[0]", equalTo("CLOSED")))
                .andExpect(jsonPath("$.enumerations[0].literalDefinitions[0].id", equalTo("lit-closed")));
        mockMvc.perform(get("/api/v1/projects/{projectId}/read-model", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enumerations[0].literals[0].id", equalTo("lit-closed")))
                .andExpect(jsonPath("$.enumerations[0].literals[0].order", equalTo(0)));
        MvcResult enumImpact = mockMvc.perform(get(
                        "/api/v1/projects/{projectId}/commands/delete-impact/ENUMERATION/status", projectId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.blocked", equalTo(false))).andReturn();
        String enumRevision = objectMapper.readTree(enumImpact.getResponse().getContentAsString()).get("revision").asText();
        mockMvc.perform(delete("/api/v1/projects/{projectId}/commands/ENUMERATION/status", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(deleteBody(enumRevision, List.of(), null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.command", equalTo("DELETE_ENUMERATION")));
    }

    @Test
    void acceptsLegacyLiteralListAndKeepsIdsDuringReorder() throws Exception {
        String projectId = createProject("B43 v1 compatibility");
        JsonNode created = mutate(post("/api/v1/projects/{projectId}/commands/enumerations", projectId),
                revision(projectId), "{\"id\":\"priority\",\"name\":\"Priority\",\"literals\":[\"LOW\",\"HIGH\"]}", 201);
        String lowId = created.at("/result/literalDefinitions/0/id").asText();
        String highId = created.at("/result/literalDefinitions/1/id").asText();
        JsonNode reordered = mutate(put("/api/v1/projects/{projectId}/commands/enumerations/{id}", projectId, "priority"),
                created.get("revision").asText(),
                "{\"id\":\"priority\",\"name\":\"Priority\",\"literals\":[\"HIGH\",\"LOW\"]}", 200);
        assertThat(reordered.at("/result/literalDefinitions/0/id").asText()).isEqualTo(highId);
        assertThat(reordered.at("/result/literalDefinitions/1/id").asText()).isEqualTo(lowId);
    }

    @Test
    void rejectsDuplicateLiteralAndStaleRevisionWithoutSideEffects() throws Exception {
        String projectId = createProject("B43 atomic validation");
        String stable = revision(projectId);
        mockMvc.perform(post("/api/v1/projects/{projectId}/commands/enumerations", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(body(stable, """
                                {"id":"broken","name":"Broken","literalDefinitions":[
                                  {"id":"one","name":"SAME"},{"id":"two","name":"SAME"}]}
                                """)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code", equalTo("INVALID_ENUMERATION")))
                .andExpect(jsonPath("$.details.draft.name", equalTo("Broken")));
        assertThat(revision(projectId)).isEqualTo(stable);

        JsonNode created = mutate(post("/api/v1/projects/{projectId}/commands/enumerations", projectId), stable,
                "{\"id\":\"state\",\"name\":\"State\",\"literals\":[\"ON\"]}", 201);
        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/enumerations/state", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(body(stable,
                                "{\"id\":\"state\",\"name\":\"Changed\",\"literals\":[\"ON\"]}")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code", equalTo("STALE_MODEL_REVISION")));
        assertThat(revision(projectId)).isEqualTo(created.get("revision").asText());
    }

    @Test
    void reportsTypedAndOclReferencesForEnumerationAndLiteralDeletion() throws Exception {
        String projectId = createProject("B43 delete impact");
        JsonNode enumeration = mutate(post("/api/v1/projects/{projectId}/commands/enumerations", projectId),
                revision(projectId), """
                {"id":"status","name":"Status","literalDefinitions":[{"id":"lit-open","name":"OPEN"}]}
                """, 201);
        JsonNode type = mutate(post("/api/v1/projects/{projectId}/commands/classes", projectId),
                enumeration.get("revision").asText(), """
                {"id":"ticket","name":"Ticket","attributes":[{"id":"ticket-status","name":"status","type":"Status"}],
                 "operations":[],"superClassIds":[]}
                """, 201);
        mutate(post("/api/v1/projects/{projectId}/commands/invariants", projectId), type.get("revision").asText(), """
                {"id":"open-ticket","name":"OpenTicket","contextClassId":"ticket","enabled":true,
                 "expression":{"id":"expr-open","text":"self.status = Status::OPEN","language":"OCL","languageVersion":"2.4"}}
                """, 201);

        String stable = revision(projectId);
        mockMvc.perform(put("/api/v1/projects/{projectId}/commands/enumerations/status", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(body(stable, """
                                {"id":"status","name":"RenamedStatus","literalDefinitions":[
                                  {"id":"lit-open","name":"OPEN"}]}
                                """)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("ENUMERATION_REFERENCED")))
                .andExpect(jsonPath("$.details.blockers[0].referenceId").exists());
        assertThat(revision(projectId)).isEqualTo(stable);

        mockMvc.perform(get("/api/v1/projects/{projectId}/commands/delete-impact/ENUMERATION/status", projectId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.blocked", equalTo(true)))
                .andExpect(jsonPath("$.references[?(@.elementType == 'ATTRIBUTE')].elementId",
                        equalTo(java.util.List.of("ticket-status"))));
        MvcResult literalImpact = mockMvc.perform(get(
                        "/api/v1/projects/{projectId}/commands/delete-impact/ENUMERATION_LITERAL/lit-open", projectId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.blocked", equalTo(true))).andReturn();
        String current = objectMapper.readTree(literalImpact.getResponse().getContentAsString()).get("revision").asText();
        mockMvc.perform(delete("/api/v1/projects/{projectId}/commands/ENUMERATION_LITERAL/lit-open", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content(deleteBody(current, List.of(), "status")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code", equalTo("DELETE_BLOCKED")));
    }

    private JsonNode mutate(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String revision, String draft, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body(revision, draft)))
                .andExpect(status().is(expectedStatus)).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String body(String revision, String draft) throws Exception {
        return "{\"expectedRevision\":" + quote(revision) + ",\"draft\":" + draft + "}";
    }

    private String deleteBody(String revision, List<String> cascadeReferenceIds, String enumerationId) throws Exception {
        return objectMapper.writeValueAsString(
                new DeleteCommandRequestDto(revision, cascadeReferenceIds, enumerationId));
    }

    private String revision(String projectId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/projects/{projectId}", projectId)).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/project/updatedAt").asText();
    }

    private String createProject(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateProjectRequestDto(name, "B43", null))))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).at("/project/id").asText();
    }

    private String quote(String value) throws Exception { return objectMapper.writeValueAsString(value); }
}
