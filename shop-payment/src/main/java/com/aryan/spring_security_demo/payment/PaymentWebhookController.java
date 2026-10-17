package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.common.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Events from the payment provider. Open at the edge (ShopConfig) because the
 * provider has no user token: what authenticates a request is its signature,
 * checked over the raw body before anything else. So the body comes in as bytes,
 * exactly as signed; parsing and re-serializing the JSON could change them.
 */
@RestController
@RequestMapping("${api.prefix}/payments/webhooks")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentWebhookService webhookService;

    /**
     * 200 once the event is processed, or was already (a repeat); 401 if the
     * signature can't be verified; 400 for a body that isn't an event. Anything
     * else is a 5xx, which a provider answers by delivering the event again.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Void>> receive(
            @RequestHeader(value = WebhookSignature.HEADER, required = false) String signature,
            @RequestHeader(value = StripeWebhookReader.SIGNATURE_HEADER, required = false) String stripeSignature,
            @RequestBody byte[] body) {
        webhookService.receive(new WebhookHeaders(signature, stripeSignature), body);
        return ResponseEntity.ok(new ApiResponse<>("Webhook received", null));
    }
}
