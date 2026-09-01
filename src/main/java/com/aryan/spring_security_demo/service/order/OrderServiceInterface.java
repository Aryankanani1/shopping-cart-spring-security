package com.aryan.spring_security_demo.service.order;

import com.aryan.spring_security_demo.dto.OrderDto;
import com.aryan.spring_security_demo.response.SlicedResponse;

public interface OrderServiceInterface {

    OrderDto placeOrder(Long userId);
    OrderDto getOrder(Long orderId);

    /**
     * One keyset (cursor) slice of a user's order history, newest first. {@code cursor}
     * is {@code null}/blank for the first slice; {@code size} is the max rows to return.
     */
    SlicedResponse<OrderDto> getUserOrders(Long userId, String cursor, int size);
}
