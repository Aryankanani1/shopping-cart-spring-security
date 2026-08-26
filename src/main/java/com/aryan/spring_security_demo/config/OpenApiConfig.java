package com.aryan.spring_security_demo.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 metadata for the generated spec and Swagger UI.
 *
 * <p>Declares a single {@code bearerAuth} HTTP-bearer (JWT) scheme and applies it
 * globally, so the Swagger UI "Authorize" dialog lets you paste a token once and
 * have it sent on every request. Endpoints that are actually {@code permitAll()}
 * simply ignore the header; the secured cart/order paths require it.
 *
 * <p>The spec is served at {@code /v3/api-docs} and the UI at
 * {@code /swagger-ui.html}. Both sit outside {@code api.prefix}, so the existing
 * "everything not under the secured paths is permitAll" rule already exposes them.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI shoppingCartOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Shopping Cart API")
                        .version("v1")
                        .description("E-commerce REST API: JWT-authenticated, role-based catalog, "
                                + "cart and order flow."))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the token returned by POST /api/v1/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
