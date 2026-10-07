package com.aryan.spring_security_demo.notification;

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
 * One entry in a user's in-app inbox, raised from their wishlist (see
 * {@link NotificationType}). It stores structured facts — type, product name,
 * prices — rather than a rendered sentence, so the client owns wording and money
 * formatting, and the product name/prices are snapshots that stay correct after
 * the catalogue changes.
 *
 * <p>No {@code @Version}: an inbox entry is written once and only ever gets its
 * read time set, which is idempotent. If the product is deleted the link is set
 * to null in the database ({@link OnDelete}) and the entry itself is kept.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notification", indexes = {
        // The inbox query: WHERE user_id = ? ORDER BY created_at DESC.
        @Index(name = "idx_notification_user_created", columnList = "user_id, created_at")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(name = "product_name", nullable = false)
    private String productName;

    /** PRICE_DROP only: the previously announced price and the new one. */
    @Column(name = "old_price")
    private BigDecimal oldPrice;

    @Column(name = "new_price")
    private BigDecimal newPrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Null until the user reads it. */
    @Column(name = "read_at")
    private Instant readAt;

    // The factories take the user and product rather than the wishlist item that
    // prompted them, so this module needs nothing from the wishlist module.
    public static Notification reminder(User user, Product product, Instant now) {
        return about(user, product, NotificationType.WISHLIST_REMINDER, now);
    }

    public static Notification priceDrop(User user, Product product, BigDecimal oldPrice, BigDecimal newPrice,
                                         Instant now) {
        Notification n = about(user, product, NotificationType.PRICE_DROP, now);
        n.oldPrice = oldPrice;
        n.newPrice = newPrice;
        return n;
    }

    public static Notification backInStock(User user, Product product, Instant now) {
        return about(user, product, NotificationType.BACK_IN_STOCK, now);
    }

    private static Notification about(User user, Product product, NotificationType type, Instant now) {
        Notification n = new Notification();
        n.user = user;
        n.product = product;
        n.productName = product.getName();
        n.type = type;
        n.createdAt = now;
        return n;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Notification other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
