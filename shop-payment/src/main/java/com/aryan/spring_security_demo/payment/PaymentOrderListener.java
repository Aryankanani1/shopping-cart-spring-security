package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.order.OrderCancellingEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stops an order being cancelled while it is being paid for: the provider may
 * still take the money, and cancelling would then keep it for a cancelled order.
 * Runs inside the cancellation's transaction ({@code MANDATORY}), so refusing
 * rolls the whole cancellation back.
 *
 * <p>A cancellation that read the order just before a payment started can't slip
 * through with a stale answer: starting a payment raises the order's version
 * ({@code OrderService.startPayment}), so that cancellation fails its version
 * check instead.
 */
@Component
@RequiredArgsConstructor
public class PaymentOrderListener {

    private final PaymentRepository paymentRepository;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onOrderCancelling(OrderCancellingEvent event) {
        if (paymentRepository.countByOrdersAndStatus(event.orderIds(), PaymentStatus.PROCESSING) > 0) {
            throw new PaymentInProgressException("The order can't be cancelled while its payment is being processed");
        }
    }
}
