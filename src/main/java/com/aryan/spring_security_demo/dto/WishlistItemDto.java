package com.aryan.spring_security_demo.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A wishlist entry as the client sees it. The alert baseline (the lowest
 * announced price, the last-seen stock state) is internal bookkeeping for the
 * scan and deliberately not exposed.
 */
@Data
@JsonPropertyOrder({"product", "addedAt", "priceWhenAdded", "remindAt", "alertsEnabled"})
public class WishlistItemDto {
    private ProductDto product;
    private Instant addedAt;
    private BigDecimal priceWhenAdded;
    private Instant remindAt;
    private boolean alertsEnabled;
}
