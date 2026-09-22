package com.aryan.spring_security_demo.controller;

import com.aryan.spring_security_demo.service.order.OrderServiceInterface;
import com.aryan.spring_security_demo.dto.OrderDto;
import com.aryan.spring_security_demo.dto.OrderSummaryDto;
import com.aryan.spring_security_demo.request.UpdateOrderStatusRequest;
import com.aryan.spring_security_demo.response.ApiResponse;
import com.aryan.spring_security_demo.response.PagedResponse;
import com.aryan.spring_security_demo.response.SlicedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

    @GetMapping("/{orderId:\\d+}")
    public ResponseEntity<ApiResponse<?>> getOrderById(@PathVariable Long orderId){
        OrderDto order = orderServiceInterface.getOrder(orderId);
        return ResponseEntity.ok(new ApiResponse<>("Item Order Success!", order));
    }

    /**
     * Admin order list — every order, newest first, paginated. Admin-only: the
     * {@code GET /orders/admin} ROLE_ADMIN rule lives at the edge in ShopConfig,
     * so a normal user can never enumerate other customers' orders here. Returns
     * {@link OrderSummaryDto} (no item breakdown) — enough to drive the table.
     */
    @GetMapping("/admin")
    public ResponseEntity<ApiResponse<PagedResponse<OrderSummaryDto>>> getAllOrders(
            @PageableDefault(size = DEFAULT_SIZE) Pageable pageable) {
        Page<OrderSummaryDto> page = orderServiceInterface.getAllOrders(pageable);
        return ResponseEntity.ok(new ApiResponse<>("Success!", PagedResponse.from(page)));
    }

    /**
     * Advance an order along its lifecycle (e.g. PROCESSING → SHIPPED). Admin-only:
     * the ROLE_ADMIN rule for {@code PATCH /orders/*&#47;status} lives at the edge in
     * ShopConfig, so this stays free of security wiring. An illegal transition is
     * rejected as 409 by the service's state-machine check.
     */
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<ApiResponse<?>> updateStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request){
        OrderDto order = orderServiceInterface.updateStatus(orderId, request.getStatus());
        return ResponseEntity.ok(new ApiResponse<>("Order status updated", order));
    }

    /**
     * Cancel an order and restock its items. Allowed for the order's owner (or an
     * admin) and only while still PENDING/PROCESSING — the service enforces both,
     * returning 403 for a non-owner and 409 once the order has shipped.
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<?>> cancelOrder(@PathVariable Long orderId){
        OrderDto order = orderServiceInterface.cancelOrder(orderId);
        return ResponseEntity.ok(new ApiResponse<>("Order cancelled", order));
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
