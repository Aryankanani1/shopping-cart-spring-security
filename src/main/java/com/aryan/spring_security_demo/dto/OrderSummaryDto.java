package com.aryan.spring_security_demo.dto;

import com.aryan.spring_security_demo.enums.OrderStatus;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Row shape for the admin order list. Deliberately lighter than {@link OrderDto}:
 * a management table needs the customer, date, total and status — not the full
 * item breakdown — so this is populated by a JPQL constructor expression that
 * selects scalar columns only, avoiding any lazy collection load.
 *
 * <p>Field order matches the {@code SELECT new OrderSummaryDto(...)} argument
 * order in {@code OrderRepository}; keep the two in sync.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonPropertyOrder({"id", "userId", "userEmail", "orderDate", "totalAmount", "status"})
public class OrderSummaryDto {

    private Long id;
    private Long userId;
    private String userEmail;
    private LocalDate orderDate;
    private BigDecimal totalAmount;
    private OrderStatus status;
}
