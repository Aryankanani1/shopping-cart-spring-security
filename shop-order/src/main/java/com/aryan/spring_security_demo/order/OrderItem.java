package com.aryan.spring_security_demo.order;

import com.aryan.spring_security_demo.catalog.Product;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "orderitems")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    private int quantity;
    private BigDecimal price;

    /**
     * How many have been refunded (returned), at most {@link #quantity} (V17;
     * docs/payment-design.md, section 11). A cancellation restocks only the rest.
     */
    @Column(name = "refunded_quantity", nullable = false)
    private int refundedQuantity;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    public OrderItem(Product product, int quantity, BigDecimal price) {
        this.quantity = quantity;
        this.price = price;
        this.product = product;
    }

    /** How many can still be refunded. */
    public int refundableQuantity() {
        return quantity - refundedQuantity;
    }

    /** Count {@code count} more of this item as refunded; never more than were bought. */
    void refund(int count) {
        if (count < 1 || count > refundableQuantity()) {
            throw new InvalidOrderStateException("Only " + refundableQuantity()
                    + " of order item " + id + " can still be refunded, not " + count);
        }
        refundedQuantity += count;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderItem other)) return false;
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
