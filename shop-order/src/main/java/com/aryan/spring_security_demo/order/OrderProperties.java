package com.aryan.spring_security_demo.order;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Order settings ({@code app.orders.*}). */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.orders")
public class OrderProperties {

    /**
     * How long an order may stay unpaid. Checkout reserves its stock, so once this
     * has passed, {@link UnpaidOrderExpiryJob} cancels it and the items go back on
     * sale; the order's {@code payBy} tells the customer when.
     */
    private Duration unpaidExpiry = Duration.ofMinutes(30);
}
