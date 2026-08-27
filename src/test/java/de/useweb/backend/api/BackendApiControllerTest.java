package de.useweb.backend.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@SpringBootTest
@AutoConfigureMockMvc
class BackendApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exposesTheVersionedOclComplianceProfile() throws Exception {
        mockMvc.perform(get("/api/v1/ocl/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId", equalTo("use-web-ocl-2.4-subset-v3")))
                .andExpect(jsonPath("$.oclVersion", equalTo("2.4")))
                .andExpect(jsonPath("$.complianceClaim", containsString("subset")))
                .andExpect(jsonPath("$.apiVersion", equalTo("v1")))
                .andExpect(jsonPath("$.runtimeLimits.maxIteratorBindings", equalTo(100000)))
                .andExpect(jsonPath("$.runtimeLimits.maxSourceCharacters", equalTo(100000)))
                .andExpect(jsonPath("$.runtimeLimits.maxDiagnostics", equalTo(32)))
                .andExpect(jsonPath("$.runtimeLimits.maxResultElements", equalTo(1000000)))
                .andExpect(jsonPath("$.features.length()", equalTo(16)))
                .andExpect(jsonPath("$.features[?(@.id == 'OCL-PROFILE-012')].status",
                        contains("NOT_SUPPORTED")))
                .andExpect(jsonPath("$.features[?(@.id == 'OCL-PROFILE-013')].status",
                        contains("NOT_SUPPORTED")))
                .andExpect(jsonPath("$.features[?(@.id == 'OCL-PROFILE-014')].status",
                        contains("OUT_OF_SCOPE")))
                .andExpect(jsonPath("$.features[?(@.id == 'OCL-PROFILE-015')].status",
                        contains("NOT_SUPPORTED")))
                .andExpect(jsonPath("$.features[?(@.id == 'OCL-PROFILE-016')].status",
                        contains("OUT_OF_SCOPE")));
    }

    @Test
    void createsProjectFromDashboardStartProjectFlow() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Library Demo",
                                  "description": "MVP project from dashboard",
                                  "template": null
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", notNullValue()))
                .andExpect(jsonPath("$.project.id", notNullValue()))
                .andExpect(jsonPath("$.project.name", equalTo("Library Demo")))
                .andExpect(jsonPath("$.umlModel.classes.length()", equalTo(0)))
                .andExpect(jsonPath("$.objectModel.objects.length()", equalTo(0)));
    }

    @Test
    void trimsProjectNameFromDashboardStartProjectFlow() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "  Library Demo  ",
                                  "description": "MVP project from dashboard",
                                  "template": null
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.project.name", equalTo("Library Demo")));
    }

    @Test
    void rejectsBlankProjectNameFromDashboardStartProjectFlow() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "   ",
                                  "description": "MVP project from dashboard",
                                  "template": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.kind", equalTo("API_ERROR")))
                .andExpect(jsonPath("$.code", equalTo("INVALID_PROJECT_NAME")))
                .andExpect(jsonPath("$.severity", equalTo("ERROR")))
                .andExpect(jsonPath("$.userMessage", equalTo("Bitte gib einen Projektnamen ein.")))
                .andExpect(jsonPath("$.path", equalTo("/api/v1/projects")))
                .andExpect(jsonPath("$.details.field", equalTo("name")));
    }

    @Test
    void rejectsMissingProjectBodyFromDashboardStartProjectFlow() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", equalTo("INVALID_PROJECT_NAME")))
                .andExpect(jsonPath("$.details.field", equalTo("name")));
    }

    @Test
    void loadsPreviouslyCreatedProject() throws Exception {
        String projectId = createProject("Loadable Project");

        mockMvc.perform(get("/api/v1/projects/{projectId}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.id", equalTo(projectId)))
                .andExpect(jsonPath("$.project.name", equalTo("Loadable Project")));
    }

    @Test
    void listsProjectSummariesForAllProjectsPage() throws Exception {
        String firstProjectId = createProject("ApiListStep6b Alpha");
        String secondProjectId = createProject("ApiListStep6b Beta");

        MvcResult listResult = mockMvc.perform(get("/api/v1/projects")
                        .queryParam("search", "ApiListStep6b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", notNullValue()))
                .andExpect(jsonPath("$[0].name", notNullValue()))
                .andExpect(jsonPath("$[0].updatedAt", notNullValue()))
                .andReturn();

        JsonNode projects = objectMapper.readTree(listResult.getResponse().getContentAsString());
        assertThat(projects).hasSize(2);
        assertThat(projects.findValuesAsText("id")).contains(firstProjectId, secondProjectId);

        for (JsonNode project : projects) {
            mockMvc.perform(get("/api/v1/projects/{projectId}", project.get("id").asText()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.project.id", equalTo(project.get("id").asText())));
        }
    }

    @Test
    void savesProjectThroughPutEndpoint() throws Exception {
        String projectId = createProject("Before Save");
        MvcResult loadResult = mockMvc.perform(get("/api/v1/projects/{projectId}", projectId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode project = objectMapper.readTree(loadResult.getResponse().getContentAsString());
        ((ObjectNode) project.get("project")).put("name", "After Save");

        mockMvc.perform(put("/api/v1/projects/{projectId}", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(project)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.project.id", equalTo(projectId)))
                .andExpect(jsonPath("$.project.name", equalTo("After Save")));
    }

    @Test
    void validatesProjectAndReturnsValidationResultWithHttpOk() throws Exception {
        String projectId = createProject("Validation Project");

        mockMvc.perform(post("/api/v1/projects/{projectId}/validate", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "FULL",
                                  "includeWarnings": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId", equalTo(projectId)))
                .andExpect(jsonPath("$.status", equalTo("VALID")))
                .andExpect(jsonPath("$.summary.errorCount", equalTo(0)));
    }

    @Test
    void returnsOclParseDiagnosticsForInvalidExpression() throws Exception {
        String projectId = createProject("OCL Project");

        mockMvc.perform(post("/api/v1/projects/{projectId}/ocl/parse", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expression": "self.books <=",
                                  "sourceId": "invariant-max-books",
                                  "sourceKind": "INVARIANT_EXPRESSION",
                                  "documentVersion": 7
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.diagnostics[0].code", equalTo("UNEXPECTED_TOKEN")))
                .andExpect(jsonPath("$.diagnostics[0].phase", equalTo("PARSER")))
                .andExpect(jsonPath("$.diagnostics[0].source.sourceId", equalTo("invariant-max-books")))
                .andExpect(jsonPath("$.diagnostics[0].source.sourceKind", equalTo("INVARIANT_EXPRESSION")))
                .andExpect(jsonPath("$.diagnostics[0].source.documentVersion", equalTo(7)))
                .andExpect(jsonPath("$.diagnostics[0].source.range.startOffset", equalTo(13)));
    }

    @Test
    void appliesAndReturnsModelTextThroughProjectApi() throws Exception {
        String projectId = createProject("Model Text Project");

        mockMvc.perform(post("/api/v1/projects/{projectId}/model-text/apply", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "modelText": "model Library\\nclass User\\nattributes\\nbooks : Integer\\nend\\nconstraints\\ncontext User\\ninv maxBooks:\\nself.books <= 5\\n",
                                  "format": "USE_MODEL_TEXT",
                                  "mode": "REPLACE_UML_MODEL",
                                  "includeDiagnostics": true,
                                  "sourceName": "library.use",
                                  "sourceFormat": "use",
                                  "sourceOrigin": "open-existing"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.status", equalTo("APPLIED")))
                .andExpect(jsonPath("$.project.modelText.modelText", notNullValue()))
                .andExpect(jsonPath("$.project.umlModel.classes[0].name", equalTo("User")))
                .andExpect(jsonPath("$.project.umlModel.invariants[0].name", equalTo("maxBooks")));

        mockMvc.perform(get("/api/v1/projects/{projectId}/model-text", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId", equalTo(projectId)))
                .andExpect(jsonPath("$.modelText", notNullValue()))
                .andExpect(jsonPath("$.sourceName", equalTo("library.use")));
    }

    @Test
    void returnsApiErrorForMissingProject() throws Exception {
        mockMvc.perform(get("/api/v1/projects/project-does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.kind", equalTo("API_ERROR")))
                .andExpect(jsonPath("$.code", equalTo("PROJECT_NOT_FOUND")))
                .andExpect(jsonPath("$.severity", equalTo("ERROR")))
                .andExpect(jsonPath("$.technicalMessage", equalTo("Project not found: project-does-not-exist")))
                .andExpect(jsonPath("$.path", equalTo("/api/v1/projects/project-does-not-exist")))
                .andExpect(jsonPath("$.details.projectId", equalTo("project-does-not-exist")));
    }

    private String createProject(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": null,
                                  "template": null
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("project").get("id").asText();
    }
}
