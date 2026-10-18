package com.aryan.spring_security_demo.payment;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Says in the spec what {@link RefundOrderRequest}'s cross-field checks say: a refund
 * is of items (at least one) or of an amount, not both, and only a refund of items
 * may restock. swagger-core can't read that from {@code @AssertTrue} methods, so the
 * spec allowed bodies with both, or neither, and the staging fuzzing (Schemathesis)
 * spent its requests on ones the API rejects with a 400. As a {@code oneOf} of the
 * two kinds; that each item is there once can't be said in a schema.
 */
@Component
class RefundRequestDocs implements OpenApiCustomizer {

    static final String SCHEMA = RefundOrderRequest.class.getSimpleName();

    @Override
    public void customise(OpenAPI openApi) {
        Schema<?> request = openApi.getComponents() == null || openApi.getComponents().getSchemas() == null
                ? null : openApi.getComponents().getSchemas().get(SCHEMA);
        if (request == null) {
            return;
        }
        JsonSchema ofItems = new JsonSchema();
        ofItems.setRequired(List.of("items"));
        ofItems.addProperty("items", new JsonSchema().minItems(1));

        JsonSchema ofAnAmount = new JsonSchema();
        ofAnAmount.setRequired(List.of("amount"));
        ofAnAmount.addProperty("restock", new JsonSchema()._const(false));

        request.setOneOf(null);
        request.addOneOfItem(ofItems);
        request.addOneOfItem(ofAnAmount);
    }
}
