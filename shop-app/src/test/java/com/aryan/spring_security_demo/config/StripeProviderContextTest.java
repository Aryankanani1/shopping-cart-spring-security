package com.aryan.spring_security_demo.config;

import com.aryan.spring_security_demo.payment.FakePaymentGateway;
import com.aryan.spring_security_demo.payment.FakeWebhookSender;
import com.aryan.spring_security_demo.payment.Installation;
import com.aryan.spring_security_demo.payment.PaymentGateway;
import com.aryan.spring_security_demo.payment.StripePaymentGateway;
import com.aryan.spring_security_demo.payment.StripeWebhookReader;
import com.aryan.spring_security_demo.payment.WebhookReader;
import com.aryan.spring_security_demo.payment.WebhookSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With {@code app.payment.provider=stripe}, the Stripe beans take the fake ones'
 * place, the installation id comes from the migration (V16), and Stripe's signed
 * webhooks reach the shop: the wiring a unit test can't show. Nothing here calls
 * Stripe.
 */
@Tag("integration")
@SpringBootTest(properties = {
        "app.payment.provider=stripe",
        "app.payment.stripe.secret-key=sk_test_context",
        "app.payment.webhook-secret=" + StripeProviderContextTest.SECRET})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StripeProviderContextTest {

    static final String SECRET = "whsec_test-only-context-secret-0000000000000";
    private static final String EVENT_ID = "evt_stripe_context_test";

    @Autowired private ApplicationContext context;
    @Autowired private Installation installation;
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private Clock clock;

    @AfterEach
    void forgetTheEvent() {
        jdbcTemplate.update("delete from payment_webhook_event where event_id = ?", EVENT_ID);
    }

    @Test
    @DisplayName("the Stripe gateway and webhook reader replace the fake provider's beans")
    void theStripeBeansReplaceTheFakeOnes() {
        assertThat(context.getBean(PaymentGateway.class)).isInstanceOf(StripePaymentGateway.class);
        assertThat(context.getBean(WebhookReader.class)).isInstanceOf(StripeWebhookReader.class);
        assertThat(context.getBeanNamesForType(FakePaymentGateway.class)).isEmpty();
        assertThat(context.getBeanNamesForType(FakeWebhookSender.class)).isEmpty();
    }

    @Test
    @DisplayName("the installation id is a UUID the migration gave this database")
    void theInstallationId_comesFromTheMigration() {
        assertThat(installation.id()).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    @DisplayName("a webhook signed in Stripe-Signature is accepted; the same in the fake's header is 401")
    void stripesWebhooks_areReadFromTheirOwnHeader() throws Exception {
        byte[] body = ("{\"id\": \"" + EVENT_ID + "\", \"object\": \"event\", \"type\": \"customer.created\", "
                + "\"created\": 1760616000, \"data\": {\"object\": {\"id\": \"cus_1\"}}}").getBytes(StandardCharsets.UTF_8);
        String signature = WebhookSignature.sign(SECRET, clock.instant(), body);

        mockMvc.perform(post("/api/v1/payments/webhooks").contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSignature.HEADER, signature).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/payments/webhooks").contentType(MediaType.APPLICATION_JSON)
                        .header(StripeWebhookReader.SIGNATURE_HEADER, signature).content(body))
                .andExpect(status().isOk());
    }
}
