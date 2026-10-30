
package com.student.studentmanagement;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class StudentManagementApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StudentRepository studentRepository;

    @Test
    void contextLoads() {
    }

    @Test
    void searchReturnsPaginatedStudents() throws Exception {

        Student student = new Student(
                "Priya Sharma Updated",
                "priya.updated@example.com",
                "Computer Science",
                23
        );
        student.setId(3L);

        PageRequest pageable = PageRequest.of(
                0, 5, Sort.by("id").ascending()
        );

        PageImpl<Student> page = new PageImpl<>(
                List.of(student),
                pageable,
                1
        );

        when(studentRepository.searchStudents(
                eq("Priya"),
                any()
        )).thenReturn(page);

        mockMvc.perform(get("/api/students/search")
                        .param("keyword", "Priya")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(3))
                .andExpect(jsonPath("$.content[0].name")
                        .value("Priya Sharma Updated"));

        verify(studentRepository).searchStudents(
                eq("Priya"),
                any()
        );
    }

    @Test
    void rejectsPageSizeZero() throws Exception {

        mockMvc.perform(get("/api/students/search")
                        .param("keyword", "Priya")
                        .param("page", "0")
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(
                        "Page must be non-negative and size must be between 1 and 100."
                ))
                .andExpect(jsonPath("$.path")
                        .value("/api/students/search"));
    }

    @Test
    void rejectsUnknownSortField() throws Exception {

        mockMvc.perform(get("/api/students/search")
                        .param("page", "0")
                        .param("size", "5")
                        .param("sortBy", "password")
                        .param("direction", "desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(
                        "Invalid sortBy field. Allowed fields: id, name, email, course, age."
                ))
                .andExpect(jsonPath("$.path")
                        .value("/api/students/search"));
    }

    @Test
    void rejectsInvalidSortDirection() throws Exception {

        mockMvc.perform(get("/api/students/search")
                        .param("page", "0")
                        .param("size", "5")
                        .param("sortBy", "name")
                        .param("direction", "sideways"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(
                        "Invalid sort direction. Use asc or desc."
                ))
                .andExpect(jsonPath("$.path")
                        .value("/api/students/search"));
    }

    @Test
    void rejectsInvalidStudentFields() throws Exception {

        String invalidStudent = """
                {
                    "name": "",
                    "email": "invalid-email",
                    "course": "",
                    "age": -5
                }
                """;

        mockMvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidStudent))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(
                        containsString("email: Please enter a valid email address")
                ))
                .andExpect(jsonPath("$.path").value("/api/students"));
    }

    @Test
    void returnsNotFoundForMissingStudent() throws Exception {

        when(studentRepository.findById(99999L))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/students/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message")
                        .value("Student not found with ID: 99999"))
                .andExpect(jsonPath("$.path")
                        .value("/api/students/99999"));
    }

    @Test
    void rejectsMalformedJson() throws Exception {

        String malformedJson = """
                {
                    "name": "Madhurima",
                    "email": "madhu@example.com",
                    "course": "CSE",
                    "age":
                }
                """;

        mockMvc.perform(post("/api/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message")
                        .value("Invalid or malformed JSON request body."))
                .andExpect(jsonPath("$.path").value("/api/students"));
    }
}

