package com.aryan.spring_security_demo.cart;

import com.aryan.spring_security_demo.identity.User;
import com.aryan.spring_security_demo.identity.UserRepository;
import com.aryan.spring_security_demo.identity.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
     * Loads a cart by id for reading. This and {@link #getCartForUpdate} are the
     * only ways to load a cart by id, and both apply the same ownership check, so
     * a user can never touch another user's cart by guessing its id (IDOR).
     */
    @Override
    @Transactional(readOnly = true)
    public Cart getCart(Long id) {
        Cart cart = cartRepository.findById(id)
                .orElseThrow(() -> new CartNotFoundException("cart not found"));
        requireOwner(cart);
        return cart;
    }

    /**
     * Loads a cart by id to change it: also locks the cart's row until the
     * caller's transaction ends (see CartRepository#findByIdForUpdate), so it
     * must run inside one. Every change a customer makes to a cart starts here,
     * before anything else is read, so it sees what the previous change did.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Cart getCartForUpdate(Long id) {
        Cart cart = cartRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CartNotFoundException("cart not found"));
        requireOwner(cart);
        return cart;
    }

    /** The user's cart, locked like {@link #getCartForUpdate}; null when they have none. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Cart getCartByUserIdForUpdate(Long userId) {
        return cartRepository.findByUserIdForUpdate(userId).orElse(null);
    }

    private void requireOwner(Cart cart) {
        authUtils.requireSelfOrAdmin(cart.getUser() == null ? null : cart.getUser().getId());
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

        Cart cart = getCartForUpdate(id);
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
