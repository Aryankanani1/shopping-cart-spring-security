package com.aryan.spring_security_demo.payment;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** A refund as the API shows it (docs/payment-design.md, section 11). */
@JsonPropertyOrder({"id", "paymentId", "kind", "amount", "currency", "reason", "status", "requestedAt",
        "refundedAt", "items"})
public record RefundDto(
        Long id,
        Long paymentId,
        Refund.Kind kind,
        BigDecimal amount,
        String currency,
        String reason,
        Refund.Status status,
        Instant requestedAt,
        Instant refundedAt,
        /** For a refund of items: which, how many, and what each gave back. */
        List<RefundItemDto> items) {

    /** An order item a refund gave money back for. */
    public record RefundItemDto(Long orderItemId, int quantity, BigDecimal amount) {
    }

    static RefundDto from(Refund refund) {
        return new RefundDto(
                refund.getId(),
                refund.getPayment().getId(),
                refund.getKind(),
                refund.getAmount(),
                refund.getCurrency(),
                refund.getReason(),
                refund.getStatus(),
                refund.getRequestedAt(),
                refund.getRefundedAt(),
                refund.getItems().stream()
                        .map(item -> new RefundItemDto(item.getOrderItemId(), item.getQuantity(), item.getAmount()))
                        .toList());
    }
}
