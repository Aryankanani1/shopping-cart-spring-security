package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.order.OrderCancellingEvent;
import com.aryan.spring_security_demo.order.OrderPlacedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tells the checkout saga what the order module does, inside the order module's
 * transaction ({@code MANDATORY}): what the saga writes commits only with it, and
 * the saga refusing rolls it back. The order module can't call payments (it is the
 * lower module), so it publishes these events instead.
 *
 * <ul>
 *   <li>An order placed starts its checkout saga ({@link CheckoutSaga#orderBooked}).</li>
 *   <li>Orders being cancelled have their payments refunded, and an order being
 *       paid for can't be cancelled ({@link CheckoutSaga#orderCancelling}).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class PaymentOrderListener {

    private final CheckoutSaga checkoutSaga;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onOrderPlaced(OrderPlacedEvent event) {
        checkoutSaga.orderBooked(event.orderId());
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onOrderCancelling(OrderCancellingEvent event) {
        checkoutSaga.orderCancelling(event.orderIds());
    }
}
