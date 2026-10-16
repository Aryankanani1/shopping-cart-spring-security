package com.aryan.spring_security_demo.order;

import com.aryan.spring_security_demo.common.web.SlicedResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderServiceInterface {

    /** Turn the signed-in user's cart into an order shipped to the given address. */
    OrderDto placeOrder(PlaceOrderRequest shippingAddress);
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
     * Cancel an order and restock its items; the payment module refunds a paid
     * one. Permitted for the order's owner (or an admin) until the order ships;
     * after that it can no longer be cancelled (409).
     */
    OrderDto cancelOrder(Long orderId);

    /**
     * Load an order to pay for, locked until the caller's transaction ends, and
     * check the caller owns it (or is an admin). Its status is not checked: a
     * payment request that is repeated finds its first result even once the order
     * is paid. Requires a transaction.
     */
    Order lockForPayment(Long orderId);

    /**
     * Note that a payment is starting for a locked order: raises its version, so a
     * cancellation that read the order before fails its version check at commit,
     * instead of cancelling an order that is being paid for. Requires a transaction.
     */
    void startPayment(Order order);

    /** Record that the order is paid ({@code PENDING} to {@code PAID}). Requires a transaction. */
    void markPaid(Long orderId);

    /**
     * Ready {@code userId}'s orders for the account being deleted: cancel and
     * restock the ones that can still be cancelled. Every order is kept.
     */
    void prepareForAccountDeletion(Long userId);

    /**
     * One keyset (cursor) slice of the signed-in user's order history, newest first.
     * {@code cursor} is {@code null}/blank for the first slice; {@code size} is the
     * max rows to return.
     */
    SlicedResponse<OrderDto> getMyOrders(String cursor, int size);
}
