package com.aryan.spring_security_demo.wishlist;

import com.aryan.spring_security_demo.catalog.Product;
import com.aryan.spring_security_demo.identity.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The alert conditions an item is checked against again in its own transaction.
 * They must agree with the scan queries in {@link WishlistItemRepository}, which
 * {@code WishlistAlertServiceTest} in shop-app runs against MySQL.
 */
class WishlistItemTest {

    private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");

    @Test
    void reminder_isDueAtItsTimeAndAfter_notBefore_andNotWhenCleared() {
        WishlistItem item = item(5);

        item.setRemindAt(NOW);
        assertThat(item.isReminderDue(NOW)).isTrue();
        item.setRemindAt(NOW.minusSeconds(1));
        assertThat(item.isReminderDue(NOW)).isTrue();
        item.setRemindAt(NOW.plusSeconds(1));
        assertThat(item.isReminderDue(NOW)).isFalse();
        item.setRemindAt(null);
        assertThat(item.isReminderDue(NOW)).isFalse();
    }

    @Test
    void priceDrop_onlyBelowTheBaseline_withAlertsOn_andNeverForAMissingPrice() {
        WishlistItem item = item(5);  // baseline 100.00

        item.getProduct().setPrice(new BigDecimal("100.00"));
        assertThat(item.hasPriceDrop()).as("same price").isFalse();
        item.getProduct().setPrice(new BigDecimal("99.99"));
        assertThat(item.hasPriceDrop()).isTrue();
        item.setAlertsEnabled(false);
        assertThat(item.hasPriceDrop()).as("alerts off").isFalse();
        item.setAlertsEnabled(true);
        item.getProduct().setPrice(null);
        assertThat(item.hasPriceDrop()).as("no price").isFalse();
    }

    @Test
    void stock_flipsAreSeenOnlyAgainstTheLastSeenState_withAlertsOn() {
        WishlistItem item = item(5);  // last seen in stock

        assertThat(item.isNewlySoldOut()).isFalse();
        item.getProduct().setInventory(0);
        assertThat(item.isNewlySoldOut()).isTrue();
        assertThat(item.isRestocked()).isFalse();

        item.setWasInStock(false);
        assertThat(item.isNewlySoldOut()).as("sell-out already recorded").isFalse();
        item.getProduct().setInventory(2);
        assertThat(item.isRestocked()).isTrue();
        item.setAlertsEnabled(false);
        assertThat(item.isRestocked()).as("alerts off").isFalse();
    }

    private static WishlistItem item(int inventory) {
        Product product = new Product("Lamp", new BigDecimal("100.00"), "", "Acme", inventory, null);
        return new WishlistItem(new User(), product, NOW);
    }
}
