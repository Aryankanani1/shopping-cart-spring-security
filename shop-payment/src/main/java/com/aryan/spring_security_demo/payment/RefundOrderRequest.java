package com.aryan.spring_security_demo.payment;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * An admin's refund of part of a paid order (docs/payment-design.md, section 11):
 * either order items and quantities, which the server prices at what the customer
 * paid for them, or an amount; and a reason, which the customer sees.
 */
@Data
public class RefundOrderRequest {

    /** The order items given back, and how many of each. */
    @Valid
    @Size(max = 100, message = "at most 100 items")
    private List<@NotNull(message = "an item can't be null") RefundLine> items;

    /** Put the items back in stock (only with items). */
    private boolean restock;

    /** An amount to give back, instead of items. */
    @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
    @Digits(integer = 12, fraction = 2, message = "amount has at most 2 decimals")
    private BigDecimal amount;

    @NotBlank(message = "reason is required")
    @Size(max = Refund.MAX_REASON, message = "reason must be at most 255 characters")
    private String reason;

    @JsonIgnore
    @AssertTrue(message = "give either items or an amount, not both")
    public boolean isItemsOrAmount() {
        return hasItems() != (amount != null);
    }

    @JsonIgnore
    @AssertTrue(message = "restock is only for a refund of items")
    public boolean isRestockOnlyWithItems() {
        return !restock || hasItems();
    }

    @JsonIgnore
    @AssertTrue(message = "each order item at most once")
    public boolean isEachItemOnce() {
        if (!hasItems()) {
            return true;
        }
        List<Long> ids = items.stream().filter(Objects::nonNull).map(RefundLine::getOrderItemId)
                .filter(Objects::nonNull).toList();
        return ids.stream().distinct().count() == ids.size();
    }

    boolean hasItems() {
        return items != null && !items.isEmpty();
    }

    /** One order item given back. */
    @Data
    public static class RefundLine {

        @NotNull(message = "orderItemId is required")
        private Long orderItemId;

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        @Max(value = 10_000, message = "quantity must be at most 10000")
        private Integer quantity;
    }
}
