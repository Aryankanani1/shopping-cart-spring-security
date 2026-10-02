package com.aryan.spring_security_demo.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The actuator access rules follow the endpoints, not the "/actuator" string, so
 * moving the base path keeps metrics admin-only. A path-based rule would have
 * stopped matching and let any signed-in customer in.
 */
@SpringBootTest(properties = "management.endpoints.web.base-path=/manage")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorBasePathTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("anonymous: health is public at the new path, metrics need sign-in")
    void anonymous() throws Exception {
        mockMvc.perform(get("/manage/health")).andExpect(status().isOk());
        mockMvc.perform(get("/manage/metrics")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("customer: metrics and info at the new path are forbidden")
    void customer() throws Exception {
        mockMvc.perform(get("/manage/metrics")).andExpect(status().isForbidden());
        mockMvc.perform(get("/manage/info")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("admin: metrics at the new path work")
    void admin() throws Exception {
        mockMvc.perform(get("/manage/metrics")).andExpect(status().isOk());
    }
}
