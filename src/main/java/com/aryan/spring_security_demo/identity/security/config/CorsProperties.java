package com.aryan.spring_security_demo.identity.security.config;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Browser origins allowed to call the API cross-origin ({@code app.cors.*}, set with
 * the comma-separated {@code APP_CORS_ALLOWED_ORIGINS} env var). Empty by default:
 * the API then sends no CORS headers, and the frontend has to be served from the
 * same host as {@code /api}. See {@link CorsConfig}.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /** Exact origins such as {@code https://shop.example.com}: scheme, host and an optional port. */
    private List<String> allowedOrigins = new ArrayList<>();

    /**
     * A browser's Origin header is exactly scheme, host and port, so anything else
     * (a {@code *}, a path, a trailing slash) would never match, or would match far
     * more than intended. Rejecting it at startup beats a frontend that silently
     * can't reach the API.
     */
    @AssertTrue(message = "APP_CORS_ALLOWED_ORIGINS must list exact origins such as https://shop.example.com "
            + "(scheme and host, optional port; no *, path or trailing slash)")
    public boolean isEveryOriginExact() {
        return allowedOrigins.stream().allMatch(CorsProperties::isOrigin);
    }

    private static boolean isOrigin(String value) {
        try {
            URI uri = new URI(value);
            return ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    && uri.getHost() != null
                    && uri.getRawUserInfo() == null
                    && uri.getRawPath().isEmpty()
                    && uri.getRawQuery() == null
                    && uri.getRawFragment() == null;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
