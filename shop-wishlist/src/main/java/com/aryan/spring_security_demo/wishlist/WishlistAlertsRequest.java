package com.aryan.spring_security_demo.wishlist;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Body of {@code PUT /wishlist/items/{productId}/alerts}: turn price/stock alerts on or off. */
@Data
public class WishlistAlertsRequest {

    @NotNull(message = "enabled is required")
    private Boolean enabled;
}
