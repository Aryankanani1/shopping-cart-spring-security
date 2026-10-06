package com.aryan.spring_security_demo.order;

import com.aryan.spring_security_demo.identity.UserDeletingEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settles a customer's orders when their account is deleted. Runs synchronously
 * inside the deletion's transaction ({@code MANDATORY}), so the cancellations
 * commit only if the account delete does.
 *
 * <p>Orders still open are cancelled and their stock returned, exactly as if the
 * customer had cancelled each one. Every order is kept: the database sets its
 * {@code user_id} to null when the account row goes.
 */
@Component
@RequiredArgsConstructor
public class OrderIdentityListener {

    private final OrderServiceInterface orderService;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onUserDeleting(UserDeletingEvent event) {
        orderService.prepareForAccountDeletion(event.userId());
    }
}
