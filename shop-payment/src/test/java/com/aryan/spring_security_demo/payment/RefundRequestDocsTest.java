package com.aryan.spring_security_demo.payment;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A refund's body is documented as one of two kinds: items, or an amount without restocking. */
class RefundRequestDocsTest {

    private final RefundRequestDocs docs = new RefundRequestDocs();

    @Test
    void aRefund_isOfItemsOrOfAnAmount() {
        Schema<?> request = new JsonSchema();
        OpenAPI openApi = new OpenAPI().components(new Components().addSchemas(RefundRequestDocs.SCHEMA, request));

        docs.customise(openApi);

        assertThat(request.getOneOf()).hasSize(2);
        Schema<?> ofItems = request.getOneOf().get(0);
        Schema<?> ofAnAmount = request.getOneOf().get(1);
        assertThat(ofItems.getRequired()).containsExactly("items");
        assertThat(ofItems.getProperties().get("items").getMinItems()).isEqualTo(1);
        assertThat(ofAnAmount.getRequired()).containsExactly("amount");
        assertThat(ofAnAmount.getProperties().get("restock").getConst()).isEqualTo(false);
    }

    @Test
    void customisingTwice_givesTheSameTwoKinds() {
        Schema<?> request = new JsonSchema();
        OpenAPI openApi = new OpenAPI().components(new Components().addSchemas(RefundRequestDocs.SCHEMA, request));

        docs.customise(openApi);
        docs.customise(openApi);

        assertThat(request.getOneOf()).hasSize(2);
    }

    @Test
    void aSpecWithoutTheRequest_isLeftAlone() {
        OpenAPI openApi = new OpenAPI().components(new Components());

        docs.customise(openApi);

        assertThat(openApi.getComponents().getSchemas()).isNull();
    }
}
