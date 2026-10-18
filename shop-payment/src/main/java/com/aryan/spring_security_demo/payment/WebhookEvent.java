package com.aryan.spring_security_demo.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * An event the provider reports by webhook. {@code data.idempotencyKey} is the key
 * the shop sent the charge or refund under: {@code pay_<payment id>}, which names
 * the payment, or a refund's own key ({@code rfd_...}, or {@code refund_<payment id>}
 * for one made before partial refunds), which the shop looks the refund up by.
 */
@JsonIgnoreProperties(ignoreUnknown = true)  // providers add fields over time
public record WebhookEvent(String id, String type, long created, Data data) {

    public static final String CHARGE_SUCCEEDED = "charge.succeeded";
    public static final String CHARGE_FAILED = "charge.failed";
    public static final String REFUND_SUCCEEDED = "refund.succeeded";

    /** What the event is about; which fields are set depends on its type. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String idempotencyKey, String providerReference, String cardBrand, String cardLast4,
                       String failureReason, String refundReference) {
    }
}
