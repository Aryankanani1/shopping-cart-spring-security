package com.aryan.spring_security_demo.payment;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.math.BigDecimal;
import java.time.Instant;

/** A payment attempt as the API shows it. */
@JsonPropertyOrder({"id", "orderId", "status", "amount", "currency", "paymentMethod",
        "cardBrand", "cardLast4", "failureReason", "providerReference", "createdAt", "completedAt",
        "refundReference", "refundRequestedAt", "refundedAt"})
public record PaymentDto(
        Long id,
        Long orderId,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        String cardBrand,
        String cardLast4,
        String failureReason,
        String providerReference,
        Instant createdAt,
        Instant completedAt,
        String refundReference,
        Instant refundRequestedAt,
        Instant refundedAt) {

    static PaymentDto from(Payment payment) {
        return new PaymentDto(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaymentMethod(),
                payment.getCardBrand(),
                payment.getCardLast4(),
                payment.getFailureReason(),
                payment.getProviderReference(),
                payment.getCreatedAt(),
                payment.getCompletedAt(),
                payment.getRefundReference(),
                payment.getRefundRequestedAt(),
                payment.getRefundedAt());
    }
}
