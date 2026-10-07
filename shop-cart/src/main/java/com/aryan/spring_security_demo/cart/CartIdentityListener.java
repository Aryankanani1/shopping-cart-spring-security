package com.aryan.spring_security_demo.cart;

import com.aryan.spring_security_demo.identity.UserDeletingEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes a customer's cart when their account is deleted. Runs synchronously
 * inside the deletion's transaction ({@code MANDATORY}), so the cart goes only if
 * the account does. The cart's lines go with it (orphan removal).
 */
@Component
@RequiredArgsConstructor
public class CartIdentityListener {

    private final CartRepository cartRepository;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onUserDeleting(UserDeletingEvent event) {
        Cart cart = cartRepository.findByUserId(event.userId());
        if (cart != null) {
            cartRepository.delete(cart);
        }
    }
}
