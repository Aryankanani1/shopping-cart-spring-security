package com.aryan.spring_security_demo.dto;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@JsonPropertyOrder({"id", "userId", "orderDate", "status", "totalAmount",
        "recipientName", "addressLine1", "addressLine2", "city", "state", "postalCode", "country",
        "items"})
public class OrderDto {

    private Long id;
    private Long userId;
    private LocalDate orderDate;
    private BigDecimal totalAmount;
    private String status;

    // Shipping address (matches Order field names so ModelMapper maps them directly).
    private String recipientName;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String postalCode;
    private String country;

    private List<OrderItemDto> items;

}
