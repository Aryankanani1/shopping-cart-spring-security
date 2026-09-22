package com.aryan.spring_security_demo.request;

import com.aryan.spring_security_demo.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Body of {@code PATCH /orders/{id}/status}: the target lifecycle state an admin
 * wants the order to move to. Binding to the {@link OrderStatus} enum means an
 * unknown value is rejected as a 400 by Jackson before it ever reaches the
 * service; {@link NotNull} rejects a missing/blank status the same way.
 */
@Data
public class UpdateOrderStatusRequest {

    @NotNull(message = "status is required")
    private OrderStatus status;
}
