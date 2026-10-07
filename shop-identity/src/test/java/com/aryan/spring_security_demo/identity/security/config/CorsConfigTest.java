package com.aryan.spring_security_demo.identity.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Which paths get CORS, for whom, and with what. */
class CorsConfigTest {

    @Test
    void noOrigins_noCorsAnywhere() {
        CorsConfigurationSource source = source(List.of());

        assertThat(source.getCorsConfiguration(request("/api/v1/products"))).isNull();
    }

    @Test
    void listedOrigins_getCorsOnTheApiOnly() {
        CorsConfigurationSource source = source(List.of("https://shop.example.com"));

        CorsConfiguration api = source.getCorsConfiguration(request("/api/v1/orders/7"));
        assertThat(api).isNotNull();
        assertThat(api.getAllowedOrigins()).containsExactly("https://shop.example.com");
        assertThat(api.getAllowedHeaders()).containsExactlyInAnyOrder("Authorization", "Content-Type");
        assertThat(api.getAllowCredentials()).as("the token is a header, never a cookie").isNotEqualTo(Boolean.TRUE);
        assertThat(source.getCorsConfiguration(request("/actuator/health"))).isNull();
        assertThat(source.getCorsConfiguration(request("/v3/api-docs"))).isNull();
    }

    private static CorsConfigurationSource source(List<String> origins) {
        CorsProperties properties = new CorsProperties();
        properties.setAllowedOrigins(origins);
        return new CorsConfig().corsConfigurationSource(properties, "/api/v1");
    }

    private static MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        return request;
    }
}
