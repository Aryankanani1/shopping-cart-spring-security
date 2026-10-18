package com.aryan.spring_security_demo.order;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonPropertyOrder({"id", "productId", "productName", "quantity", "refundedQuantity", "price"})
public class OrderItemDto {

    /** The order item's own id: what a refund of items names (docs/payment-design.md, section 11). */
    private Long id;
    private Long productId;
    private String productName;
    private String productBrand;
    private int quantity;
    /** How many have been refunded so far. */
    private int refundedQuantity;
    private BigDecimal price;
}

