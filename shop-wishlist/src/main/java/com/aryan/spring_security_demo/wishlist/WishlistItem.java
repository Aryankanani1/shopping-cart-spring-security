package com.aryan.spring_security_demo.wishlist;

import com.aryan.spring_security_demo.catalog.Product;
import com.aryan.spring_security_demo.identity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A product a user has saved to their wishlist, plus what they want to hear about
 * it: an optional one-shot dated reminder, and price-drop / back-in-stock alerts.
 *
 * <p>The alert fields are a <em>baseline</em> the scheduled scan compares the
 * live product against. When it announces a change it moves the baseline to the
 * new state, so every price drop or restock is announced exactly once.
 *
 * <p>Both foreign keys cascade in the database ({@link OnDelete}), so deleting the
 * user or the product never trips over a wishlist row.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "wishlist_item",
        uniqueConstraints = @UniqueConstraint(name = "uk_wishlist_user_product",
                columnNames = {"user_id", "product_id"}),
        indexes = @Index(name = "idx_wishlist_remind_at", columnList = "remind_at"))
public class WishlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Product product;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** The price when saved. Never changes; lets the UI show "was $X". */
    @Column(name = "price_when_added", nullable = false, updatable = false)
    private BigDecimal priceWhenAdded;

    /** When to remind the user about this item; cleared once the reminder fires. */
    @Column(name = "remind_at")
    private Instant remindAt;

    @Column(name = "alerts_enabled", nullable = false)
    private boolean alertsEnabled = true;

    /**
     * The lowest price the user has been told about (initially the price when
     * saved). A price drop is announced when the live price goes below it.
     */
    @Column(name = "alert_price", nullable = false)
    private BigDecimal alertPrice;

    /** Stock state as last seen by the scan; a false → true flip is a restock. */
    @Column(name = "was_in_stock", nullable = false)
    private boolean wasInStock;

    public WishlistItem(User user, Product product, Instant now) {
        this.user = user;
        this.product = product;
        this.createdAt = now;
        this.priceWhenAdded = product.getPrice();
        resetAlertBaseline();
    }

    /**
     * Take the product's current price and stock as the new baseline, so only
     * changes from here on are announced. Used when saving the item and when
     * alerts are switched back on (nothing that happened while off is replayed).
     */
    public void resetAlertBaseline() {
        this.alertPrice = product.getPrice();
        this.wasInStock = product.getInventory() > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WishlistItem other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
