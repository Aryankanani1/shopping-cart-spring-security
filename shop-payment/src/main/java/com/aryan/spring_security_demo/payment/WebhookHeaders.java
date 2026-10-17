package com.aryan.spring_security_demo.payment;

/**
 * The signature headers a webhook request may carry. Each provider signs in its
 * own header, and its {@link WebhookReader} reads that one.
 */
public record WebhookHeaders(String paymentSignature, String stripeSignature) {
}
