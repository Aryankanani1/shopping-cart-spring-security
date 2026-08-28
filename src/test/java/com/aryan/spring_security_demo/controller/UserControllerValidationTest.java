package com.aryan.spring_security_demo.controller;

import com.aryan.spring_security_demo.Service.user.UserServiceInterface;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.data.firstName").exists())
                .andExpect(jsonPath("$.data.email").exists())
                .andExpect(jsonPath("$.data.password").exists());
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
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.data.email").value("Email must be a valid address"));
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
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.data.password").value("Password must be at least 6 characters"));
    }
}
