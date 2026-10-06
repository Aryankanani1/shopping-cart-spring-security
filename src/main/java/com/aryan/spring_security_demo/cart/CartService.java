package com.aryan.spring_security_demo.cart;

import com.aryan.spring_security_demo.identity.User;
import com.aryan.spring_security_demo.identity.UserRepository;
import com.aryan.spring_security_demo.identity.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartService implements CartServiceInterface{

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ModelMapper modelMapper;
    private final AuthUtils authUtils;
    private final UserRepository userRepository;

    /**
     * Sole entry point for loading a cart by id, so the ownership check lives in
     * one place: every cart-scoped operation (view, clear, total, and each
     * cart-item mutation in {@code CartItemService}) funnels through here, so a
     * user can never touch another user's cart by guessing its id (IDOR).
     */
    @Override
    @Transactional(readOnly = true)
    public Cart getCart(Long id) {
        Cart cart = cartRepository.findById(id)
                .orElseThrow(() -> new CartNotFoundException("cart not found"));
        authUtils.requireSelfOrAdmin(cart.getUser() == null ? null : cart.getUser().getId());
        return cart;
    }

    /**
     * Map the cart to a DTO <em>inside</em> this transaction, so the lazy
     * {@code cartItems} (and their nested product/images) are loaded while the
     * persistence context is still open. The controller then serializes a plain
     * DTO — no {@code LazyInitializationException} even with open-in-view off.
     */
    @Override
    @Transactional(readOnly = true)
    public CartDto getCartDto(Long id) {
        return modelMapper.map(getCart(id), CartDto.class);
    }

    @Override
    @Transactional
    public void clearCart(Long id) {

        Cart cart = getCart(id);
        cartItemRepository.deleteAllByCartId(id);
        cart.getCartItems().clear();
        cartRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalPrice(Long id) {
        Cart cart = getCart(id);
        return cart.getTotalAmount();
    }

    @Override
    @Transactional
    public Cart initializeNewCart(User user){
        // Two first add-to-carts at once must not both create a cart (the second
        // would fail the unique user_id): the second waits here, then finds it.
        userRepository.lockById(user.getId());
        return Optional.ofNullable(getCartByUserId(user.getId()))
                .orElseGet(() -> {
                    Cart cart = new Cart();
                    cart.setUser(user);
                    return cartRepository.save(cart);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Cart getCartByUserId(Long userId) {
        return cartRepository.findByUserId(userId);
    }

}
