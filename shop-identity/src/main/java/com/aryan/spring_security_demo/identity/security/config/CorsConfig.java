package com.aryan.spring_security_demo.identity.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * CORS for a frontend hosted on another origin, applied by ShopConfig's filter
 * chain ({@code http.cors(...)}). Only the origins in {@link CorsProperties} get
 * CORS headers, and only on the API paths; the actuator and API docs never do.
 *
 * <p>The JWT travels in the {@code Authorization} header, never in a cookie, so
 * credentials stay disallowed: a listed origin can call the API only with a token
 * its own page already holds, and no other site can borrow a user's session.
 */
@Configuration(proxyBeanMethods = false)
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties,
                                                           @Value("${api.prefix}") String apiPrefix) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (properties.getAllowedOrigins().isEmpty()) {
            return source;  // no CORS headers anywhere: same-origin only
        }
        CorsConfiguration api = new CorsConfiguration();
        api.setAllowedOrigins(properties.getAllowedOrigins());
        api.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        api.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        // Response headers the frontend may read: where a created resource lives,
        // when to retry after a 429, and a downloaded image's file name.
        api.setExposedHeaders(List.of(HttpHeaders.LOCATION, HttpHeaders.RETRY_AFTER, HttpHeaders.CONTENT_DISPOSITION));
        api.setMaxAge(Duration.ofHours(1));
        source.registerCorsConfiguration(apiPrefix + "/**", api);
        return source;
    }
}
