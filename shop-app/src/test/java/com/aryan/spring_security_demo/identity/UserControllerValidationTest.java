package com.aryan.spring_security_demo.identity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Validation slice for {@link UserController}. Security filters are disabled so
 * a malformed create request is rejected by Bean Validation before the service.
 */
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserServiceInterface userService;

    @Test
    void createUser_withBlankFields_returns400() throws Exception {
        String body = "{}";

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.firstName").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void createUser_withInvalidEmail_returns400() throws Exception {
        String body = """
                {
                  "firstName": "Ada",
                  "lastName": "Lovelace",
                  "email": "not-an-email",
                  "password": "123456"
                }
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.email").value("Email must be a valid address"));
    }

    @Test
    void createUser_withShortPassword_returns400() throws Exception {
        String body = """
                {
                  "firstName": "Ada",
                  "lastName": "Lovelace",
                  "email": "ada@example.com",
                  "password": "123"
                }
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.password").value("Password must be 6-72 characters"));
    }

    @Test
    void createUser_withPasswordOver72Characters_returns400() throws Exception {
        String body = """
                {
                  "firstName": "Ada",
                  "lastName": "Lovelace",
                  "email": "ada@example.com",
                  "password": "%s"
                }
                """.formatted("a".repeat(73));

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("Password must be 6-72 characters"));
    }

    // Regression: the columns are varchar(255), so a longer value got past
    // validation and came back from the database as a 409 "Data conflict".
    @Test
    void createUser_withTextLongerThanItsColumn_returns400() throws Exception {
        // A well-formed address of 260 characters (local part 64, domain 195).
        String longEmail = "a".repeat(64) + "@" + ("b".repeat(63) + ".").repeat(3) + "com";
        String body = """
                {
                  "firstName": "%1$s",
                  "lastName": "%1$s",
                  "email": "%2$s",
                  "password": "123456"
                }
                """.formatted("a".repeat(256), longEmail);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.firstName").value("First name must be at most 255 characters"))
                .andExpect(jsonPath("$.errors.lastName").value("Last name must be at most 255 characters"))
                .andExpect(jsonPath("$.errors.email").value("Email must be at most 255 characters"));
    }

    @Test
    void updateUser_withNamesLongerThanTheirColumns_returns400() throws Exception {
        String body = """
                {
                  "firstName": "%1$s",
                  "lastName": "%1$s"
                }
                """.formatted("a".repeat(256));

        mockMvc.perform(put("/api/v1/users/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.firstName").value("First name must be at most 255 characters"))
                .andExpect(jsonPath("$.errors.lastName").value("Last name must be at most 255 characters"));
    }
}
