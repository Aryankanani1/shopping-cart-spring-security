package com.aryan.spring_security_demo.model;

import com.aryan.spring_security_demo.enums.OrderStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "orders", indexes = {
        // Serves the order-history query end to end:
        //   WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT n
        // Leading user_id satisfies the filter; (created_at, id) gives a stable,
        // unique seek key for keyset pagination without a separate sort step.
        @Index(name = "idx_orders_user_created", columnList = "user_id, created_at, id")
})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orders_seq")
    @SequenceGenerator(name = "orders_seq", sequenceName = "orders_seq", allocationSize = 50)
    private Long Id;

    @Version
    private Long version;

    private LocalDate localDate;

    // High-cardinality, non-updatable ordering key. Paired with the PK in the
    // index above so (created_at, id) is a total order even when many orders
    // share a timestamp — the tiebreaker keyset pagination depends on.
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Enumerated(EnumType.STRING)
    private OrderStatus orderStatus;

    private BigDecimal totalAmount;

    @BatchSize(size = 20)
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL,orphanRemoval = true, fetch = FetchType.LAZY)
    Set<OrderItem> orderItems = new HashSet<>();

    /**
     * Attach an item to this order, keeping both sides of the relationship in
     * sync. {@code OrderItem.order} is the owning side — the one that controls
     * the {@code order_id} foreign key — so it must be set here; touching only
     * {@link #orderItems} would leave the FK null and the link unsaved.
     */
    public void addOrderItem(OrderItem orderItem) {
        this.orderItems.add(orderItem);
        orderItem.setOrder(this);
    }

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order other)) return false;
        return Id != null && Id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
