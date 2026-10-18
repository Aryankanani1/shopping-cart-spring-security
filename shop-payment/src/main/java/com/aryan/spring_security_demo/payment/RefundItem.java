package com.aryan.spring_security_demo.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;

/** An order item an {@code ITEMS} refund gives money back for, and how many (V17). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "refund_item")
public class RefundItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refund_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Refund refund;

    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal amount;

    RefundItem(Refund refund, Long orderItemId, int quantity, BigDecimal amount) {
        this.refund = refund;
        this.orderItemId = orderItemId;
        this.quantity = quantity;
        this.amount = amount;
    }
}
