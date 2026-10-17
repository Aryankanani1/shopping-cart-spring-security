package com.aryan.spring_security_demo.payment;

/**
 * Reads a webhook in its provider's format: checks the request's signature over the
 * raw body, then turns the body into the shop's {@link WebhookEvent}. One per
 * provider ({@link FakeWebhookReader}, {@link StripeWebhookReader}); everything after
 * it, from {@link PaymentWebhookProcessor} on, is the same for both.
 */
public interface WebhookReader {

    /**
     * @throws InvalidWebhookSignatureException if the signature is missing, too far
     *         from now, or doesn't match
     * @throws InvalidWebhookException if the body isn't an event
     */
    WebhookEvent read(WebhookHeaders headers, byte[] body);
}
