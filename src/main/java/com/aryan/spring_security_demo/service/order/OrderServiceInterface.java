package com.aryan.spring_security_demo.service.order;

import com.aryan.spring_security_demo.dto.OrderDto;
import com.aryan.spring_security_demo.dto.OrderSummaryDto;
import com.aryan.spring_security_demo.enums.OrderStatus;
import com.aryan.spring_security_demo.response.SlicedResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderServiceInterface {

    OrderDto placeOrder(Long userId);
    OrderDto getOrder(Long orderId);

    /** A page of all orders (newest first) as summaries — admin order management. */
    Page<OrderSummaryDto> getAllOrders(Pageable pageable);

    /**
     * Move an order to {@code newStatus} along the lifecycle state machine
     * (admin-only fulfillment action). Rejects an illegal transition with a 409.
     * When the target is {@code CANCELLED}, inventory is restocked.
     */
    OrderDto updateStatus(Long orderId, OrderStatus newStatus);

    /**
     * Cancel an order and restock its items. Permitted for the order's owner (or
     * an admin) and only while the order is still {@code PENDING} or
     * {@code PROCESSING} — once shipped it can no longer be cancelled (409).
     */
    OrderDto cancelOrder(Long orderId);

    /**
     * One keyset (cursor) slice of a user's order history, newest first. {@code cursor}
     * is {@code null}/blank for the first slice; {@code size} is the max rows to return.
     */
    SlicedResponse<OrderDto> getUserOrders(Long userId, String cursor, int size);
}
