package com.aryan.spring_security_demo.cart;

import com.aryan.spring_security_demo.catalog.ProductPriceChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps carts in step with the catalog. Runs synchronously inside the catalog's
 * transaction ({@code MANDATORY}), so a cart change commits only if the price
 * change or delete that caused it does.
 */
@Component
@RequiredArgsConstructor
public class CartCatalogListener {

    private final CartItemRepository cartItemRepository;

    /**
     * Reprice every cart line holding the product, so the bag shows what checkout
     * will charge (checkout always charges the current price).
     */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onPriceChanged(ProductPriceChangedEvent event) {
        for (CartItem item : cartItemRepository.findByProductIdWithCart(event.productId())) {
            item.setUnitPrice(event.newPrice());
            item.setTotalPrice();
            item.getCart().updateTotalAmount();
        }
    }
}
