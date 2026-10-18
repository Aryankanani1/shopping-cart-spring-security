package com.aryan.spring_security_demo.order;

import com.aryan.spring_security_demo.common.web.SlicedResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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

    /** The orders still unpaid that were placed before {@code placedBefore}, oldest first. */
    List<Long> unpaidOrdersPlacedBefore(Instant placedBefore);

    /**
     * Cancel an order left unpaid since before {@code placedBefore}, as its owner
     * could: the stock goes back on sale, and other modules are told (a payment in
     * progress refuses, with a 409). A transaction of its own.
     *
     * @return false if it has been paid or cancelled in the meantime
     */
    boolean expireIfUnpaid(Long orderId, Instant placedBefore);

    /**
     * Ready {@code userId}'s orders for the account being deleted: cancel and
     * restock the ones that can still be cancelled. Every order is kept.
     */
    void prepareForAccountDeletion(Long userId);

    /**
     * Refund some of a paid order's items (a return; docs/payment-design.md, section
     * 11): check each quantity against what is left of that item, count them as
     * refunded, and put them back in stock if {@code restock}. Inside the caller's
     * transaction, which holds the order's payment row lock already.
     *
     * @param quantities how many of each order item, by order item id
     * @return each item refunded, with what it cost the customer
     * @throws com.aryan.spring_security_demo.common.exception.ResourceNotFoundException
     *         if there is no such order
     * @throws com.aryan.spring_security_demo.common.exception.BadRequestException if
     *         an item isn't in the order
     * @throws InvalidOrderStateException if the order can't have items refunded
     *         (unpaid or cancelled), or an item has fewer left than asked
     */
    List<RefundedItem> refundItems(Long orderId, Map<Long, Integer> quantities, boolean restock);

    /**
     * One keyset (cursor) slice of the signed-in user's order history, newest first.
     * {@code cursor} is {@code null}/blank for the first slice; {@code size} is the
     * max rows to return.
     */
    SlicedResponse<OrderDto> getMyOrders(String cursor, int size);
}
