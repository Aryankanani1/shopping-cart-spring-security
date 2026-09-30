package com.aryan.spring_security_demo.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

/** Body of {@code PUT /wishlist/items/{productId}/reminder}: when to remind (ISO-8601 instant). */
@Data
public class WishlistReminderRequest {

    @NotNull(message = "Reminder time is required")
    @Future(message = "Reminder time must be in the future")
    private Instant remindAt;
}
