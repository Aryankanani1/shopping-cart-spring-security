package com.aryan.spring_security_demo.catalog;

import java.math.BigDecimal;

/**
 * Published when an admin changes a product's price, inside the transaction that
 * changes it, so a listener's writes commit or roll back together with the new
 * price. Lets other modules (carts) follow catalog changes without the catalog
 * depending on them.
 */
public record ProductPriceChangedEvent(Long productId, BigDecimal newPrice) {
}
