package de.useweb.backend.api;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class LibraryBackendE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void libraryWorkflowReportsInvariantViolationAndBecomesValidAfterCorrection() throws Exception {
        String projectId = startLibraryProject();

        createUserClass(projectId);
        addUserNameAttribute(projectId);
        addUserBooksAttribute(projectId);
        createBookClass(projectId);
        addBookTitleAttribute(projectId);
        addBookAvailableAttribute(projectId);
        createBorrowsAssociation(projectId);
        createMaxBooksInvariant(projectId);

        createAliceWithTooManyBooks(projectId);
        createMobyDick(projectId);
        createBorrowsLink(projectId);

        mockMvc.perform(post("/api/v1/projects/{projectId}/validate", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "FULL",
                                  "includeWarnings": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("INVALID")))
                .andExpect(jsonPath("$.summary.errorCount", equalTo(1)))
                .andExpect(jsonPath("$.findings[0].code", equalTo("INVARIANT_VIOLATION")))
                .andExpect(jsonPath("$.findings[0].contextObjectId", equalTo("obj-alice")))
                .andExpect(jsonPath("$.findings[0].contextClassId", equalTo("class-user")))
                .andExpect(jsonPath("$.findings[0].invariantId", equalTo("inv-max-books")))
                .andExpect(jsonPath("$.findings[0].expression", equalTo("self.books <= 5")))
                .andExpect(jsonPath("$.findings[0].targets[*].elementId", hasItem("obj-alice")))
                .andExpect(jsonPath("$.findings[0].targets[*].elementId", hasItem("inv-max-books")));

        mockMvc.perform(put("/api/v1/projects/{projectId}/objects/{objectId}/slots/{slotId}", projectId, "obj-alice", "slot-alice-books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "slot-alice-books",
                                  "attributeId": "attr-user-books",
                                  "value": {
                                    "type": "Integer",
                                    "value": 5
                                  },
                                  "isUnset": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", equalTo("slot-alice-books")))
                .andExpect(jsonPath("$.value.value", equalTo(5)));

        mockMvc.perform(post("/api/v1/projects/{projectId}/validate", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "FULL",
                                  "includeWarnings": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", equalTo("VALID")))
                .andExpect(jsonPath("$.summary.errorCount", equalTo(0)))
                .andExpect(jsonPath("$.findings.length()", equalTo(0)));
    }

    private String startLibraryProject() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Library",
                                  "description": "Backend MVP E2E test",
                                  "template": null
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.project.id", notNullValue()))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("project").get("id").asText();
    }

    private void createUserClass(String projectId) throws Exception {
        createClass(projectId, "class-user", "User");
    }

    private void createBookClass(String projectId) throws Exception {
        createClass(projectId, "class-book", "Book");
    }

    private void createClass(String projectId, String classId, String name) throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/classes", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "%s",
                                  "name": "%s",
                                  "attributes": [],
                                  "operations": []
                                }
                                """.formatted(classId, name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", equalTo(classId)))
                .andExpect(jsonPath("$.name", equalTo(name)));
    }

    private void addUserNameAttribute(String projectId) throws Exception {
        addAttribute(projectId, "class-user", "attr-user-name", "name", "String");
    }

    private void addUserBooksAttribute(String projectId) throws Exception {
        addAttribute(projectId, "class-user", "attr-user-books", "books", "Integer");
    }

    private void addBookTitleAttribute(String projectId) throws Exception {
        addAttribute(projectId, "class-book", "attr-book-title", "title", "String");
    }

    private void addBookAvailableAttribute(String projectId) throws Exception {
        addAttribute(projectId, "class-book", "attr-book-available", "available", "Boolean");
    }

    private void addAttribute(String projectId, String classId, String attributeId, String name, String type) throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/classes/{classId}/attributes", projectId, classId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "%s",
                                  "name": "%s",
                                  "type": "%s"
                                }
                                """.formatted(attributeId, name, type)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", equalTo(attributeId)))
                .andExpect(jsonPath("$.type", equalTo(type)));
    }

    private void createBorrowsAssociation(String projectId) throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/associations", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "assoc-borrows",
                                  "name": "Borrows",
                                  "ends": [
                                    {
                                      "id": "end-borrows-user",
                                      "classId": "class-user",
                                      "roleName": "borrower",
                                      "multiplicity": {
                                        "lower": 1,
                                        "upper": 1,
                                        "unbounded": false,
                                        "raw": "1"
                                      },
                                      "navigable": true
                                    },
                                    {
                                      "id": "end-borrows-book",
                                      "classId": "class-book",
                                      "roleName": "borrowedBooks",
                                      "multiplicity": {
                                        "lower": 0,
                                        "upper": 5,
                                        "unbounded": false,
                                        "raw": "0..5"
                                      },
                                      "navigable": true
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", equalTo("assoc-borrows")))
                .andExpect(jsonPath("$.ends.length()", equalTo(2)));
    }

    private void createMaxBooksInvariant(String projectId) throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/invariants", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "inv-max-books",
                                  "name": "maxBooks",
                                  "contextClassId": "class-user",
                                  "expression": {
                                    "id": "expr-max-books",
                                    "text": "self.books <= 5",
                                    "language": "OCL",
                                    "languageVersion": "mvp-ocl"
                                  },
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", equalTo("inv-max-books")))
                .andExpect(jsonPath("$.expression.text", equalTo("self.books <= 5")));
    }

    private void createAliceWithTooManyBooks(String projectId) throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/objects", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "obj-alice",
                                  "name": "alice",
                                  "classId": "class-user",
                                  "slots": [
                                    {
                                      "id": "slot-alice-name",
                                      "attributeId": "attr-user-name",
                                      "value": {
                                        "type": "String",
                                        "value": "Alice"
                                      },
                                      "isUnset": false
                                    },
                                    {
                                      "id": "slot-alice-books",
                                      "attributeId": "attr-user-books",
                                      "value": {
                                        "type": "Integer",
                                        "value": 6
                                      },
                                      "isUnset": false
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", equalTo("obj-alice")))
                .andExpect(jsonPath("$.slots[?(@.id == 'slot-alice-books')].value.value", hasItem(6)));
    }

    private void createMobyDick(String projectId) throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/objects", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "obj-moby-dick",
                                  "name": "mobyDick",
                                  "classId": "class-book",
                                  "slots": [
                                    {
                                      "id": "slot-moby-dick-title",
                                      "attributeId": "attr-book-title",
                                      "value": {
                                        "type": "String",
                                        "value": "Moby Dick"
                                      },
                                      "isUnset": false
                                    },
                                    {
                                      "id": "slot-moby-dick-available",
                                      "attributeId": "attr-book-available",
                                      "value": {
                                        "type": "Boolean",
                                        "value": false
                                      },
                                      "isUnset": false
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", equalTo("obj-moby-dick")));
    }

    private void createBorrowsLink(String projectId) throws Exception {
        mockMvc.perform(post("/api/v1/projects/{projectId}/links", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "link-alice-moby-dick",
                                  "associationId": "assoc-borrows",
                                  "endValues": [
                                    {
                                      "associationEndId": "end-borrows-user",
                                      "objectId": "obj-alice"
                                    },
                                    {
                                      "associationEndId": "end-borrows-book",
                                      "objectId": "obj-moby-dick"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", equalTo("link-alice-moby-dick")));
    }
}
