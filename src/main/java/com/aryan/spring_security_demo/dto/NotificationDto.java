package com.aryan.spring_security_demo.dto;

import com.aryan.spring_security_demo.enums.NotificationType;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An inbox entry. {@code productId} is null once the product has been deleted;
 * {@code productName} is a snapshot and always present. {@code oldPrice} and
 * {@code newPrice} are set for {@code PRICE_DROP} only.
 */
@Data
@JsonPropertyOrder({"id", "type", "productId", "productName", "oldPrice", "newPrice", "createdAt", "read"})
public class NotificationDto {
    private Long id;
    private NotificationType type;
    private Long productId;
    private String productName;
    private BigDecimal oldPrice;
    private BigDecimal newPrice;
    private Instant createdAt;
    private boolean read;
}
