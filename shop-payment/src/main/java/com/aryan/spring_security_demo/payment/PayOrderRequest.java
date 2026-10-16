package com.aryan.spring_security_demo.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * The body of a payment request. There is no amount: the server charges the
 * order's total, so a client can't choose what it pays.
 */
@Data
public class PayOrderRequest {

    /**
     * What to charge: one of the fake provider's test methods (such as
     * {@code test_visa}), standing in for the token a provider's card form returns.
     */
    @NotBlank(message = "paymentMethod is required")
    @Size(max = 64, message = "paymentMethod must be at most 64 characters")
    private String paymentMethod;
}
