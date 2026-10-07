package com.aryan.spring_security_demo.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
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
 * Exposing every endpoint ("*") still brings up only health, info and metrics:
 * the others are switched off ({@code management.endpoints.access.default: none}),
 * so a careless exposure change can't leak the environment or a heap dump.
 */
@Tag("integration")
@SpringBootTest(properties = "management.endpoints.web.exposure.include=*")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "ADMIN")
class ActuatorLockdownTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("with exposure '*', even an admin can't reach env, heapdump, loggers, beans or threaddump")
    void unlistedEndpoints_stayOff() throws Exception {
        for (String endpoint : new String[]{"env", "heapdump", "loggers", "beans", "threaddump", "configprops", "mappings"}) {
            mockMvc.perform(get("/actuator/" + endpoint)).andExpect(status().isNotFound());
        }
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isOk());
    }
}
