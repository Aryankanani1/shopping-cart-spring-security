package com.aryan.spring_security_demo.controller;

import com.aryan.spring_security_demo.service.order.OrderServiceInterface;
import com.aryan.spring_security_demo.dto.OrderDto;
import com.aryan.spring_security_demo.response.ApiResponse;
import com.aryan.spring_security_demo.response.SlicedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("${api.prefix}/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderServiceInterface orderServiceInterface;

    @PostMapping
    public ResponseEntity<ApiResponse<?>> createOrder(@RequestParam Long userId){
        OrderDto order = orderServiceInterface.placeOrder(userId);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(order.getId()).toUri();
        return ResponseEntity.created(location).body(new ApiResponse<>("Item Order Success!", order));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<?>> getOrderById(@PathVariable Long orderId){
        OrderDto order = orderServiceInterface.getOrder(orderId);
        return ResponseEntity.ok(new ApiResponse<>("Item Order Success!", order));
    }

    /** Largest slice a client may request; a bigger ?size is clamped to this. */
    private static final int MAX_SIZE = 100;
    private static final int DEFAULT_SIZE = 20;

    /**
     * One keyset slice of a user's order history, newest first.
     *
     * <pre>
     *   GET /orders?userId=42                 first slice
     *   GET /orders?userId=42&cursor=eyJ...   next slice (echo back nextCursor)
     * </pre>
     *
     * <p>Cursor-paginated rather than offset: order history is scrolled forward,
     * never jumped to "page 47", so it trades random access for flat cost at any
     * depth and stability under new orders. {@code size} is clamped to {@value
     * #MAX_SIZE} so a single request can never ask for an unbounded slice.
     */
    @GetMapping(params = "userId")
    public ResponseEntity<ApiResponse<SlicedResponse<OrderDto>>> getUserOrders(
            @RequestParam Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "" + DEFAULT_SIZE) int size) {
        int limit = Math.min(Math.max(size, 1), MAX_SIZE);
        SlicedResponse<OrderDto> orders = orderServiceInterface.getUserOrders(userId, cursor, limit);
        return ResponseEntity.ok(new ApiResponse<>("Item Order Success!", orders));
    }
}
