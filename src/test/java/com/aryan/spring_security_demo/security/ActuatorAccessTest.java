package com.aryan.spring_security_demo.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who can see what under {@code /actuator}: the health probes are public but
 * show only UP/DOWN, and everything else (details, info, metrics) is for admins.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorAccessTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("anonymous: health probes answer UP without details; info and metrics need sign-in")
    void anonymous() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());

        mockMvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("customer: no health details, and info, metrics and the index are forbidden")
    void customer() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());

        mockMvc.perform(get("/actuator")).andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/info")).andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/metrics/jvm.memory.used")).andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("admin: health details, build/Java/process info, and metrics")
    void admin() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db.status").value("UP"));

        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.build.version").isNotEmpty())
                .andExpect(jsonPath("$.build.time").isNotEmpty())
                .andExpect(jsonPath("$.java.version").isNotEmpty())
                .andExpect(jsonPath("$.process.pid").isNumber());

        mockMvc.perform(get("/actuator/metrics/jvm.memory.used"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value").isNumber());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("admin: the Prometheus endpoint (scrape account off) serves labelled metrics")
    void admin_prometheus() throws Exception {
        mockMvc.perform(get("/api/v1/products")).andExpect(status().isOk());

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_memory_used_bytes")))
                // Every series carries the application tag.
                .andExpect(content().string(containsString("application=\"spring_security_demo\"")))
                // HTTP latency buckets, for percentiles.
                .andExpect(content().string(containsString("http_server_requests_seconds_bucket")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("request metrics can be filtered by endpoint, as the README shows for orders")
    void requestMetrics_byUri() throws Exception {
        mockMvc.perform(get("/api/v1/products")).andExpect(status().isOk());

        mockMvc.perform(get("/actuator/metrics/http.server.requests")
                        .param("tag", "uri:/api/v1/products")
                        .param("tag", "method:GET"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[?(@.statistic == 'COUNT')].value").isNotEmpty());
    }
}
