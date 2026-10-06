package com.aryan.spring_security_demo.cart;

import com.aryan.spring_security_demo.identity.User;
import com.aryan.spring_security_demo.identity.UserRepository;
import com.aryan.spring_security_demo.identity.security.AuthUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Every cart operation goes through getCart, which is where the ownership check lives. */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final Long CART_ID = 99L;
    private static final Long OWNER_ID = 42L;

    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ModelMapper modelMapper;
    @Mock private AuthUtils authUtils;
    @Mock private UserRepository userRepository;

    @InjectMocks private CartService cartService;

    @Test
    void getCart_missing_is404BeforeAnyOwnershipCheck() {
        when(cartRepository.findById(CART_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.getCart(CART_ID)).isInstanceOf(CartNotFoundException.class);
        verify(authUtils, never()).requireSelfOrAdmin(any());
    }

    @Test
    void getCart_checksTheCartsOwner() {
        when(cartRepository.findById(CART_ID)).thenReturn(Optional.of(cartOwnedBy(OWNER_ID)));
        doThrow(new AccessDeniedException("nope")).when(authUtils).requireSelfOrAdmin(OWNER_ID);

        assertThatThrownBy(() -> cartService.getCart(CART_ID)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getCart_withNoOwner_isCheckedAsNotYours() {
        Cart orphan = new Cart();
        orphan.setId(CART_ID);
        when(cartRepository.findById(CART_ID)).thenReturn(Optional.of(orphan));

        cartService.getCart(CART_ID);

        verify(authUtils).requireSelfOrAdmin(null);  // which always denies
    }

    @Test
    void clearCart_someoneElses_deletesNothing() {
        when(cartRepository.findById(CART_ID)).thenReturn(Optional.of(cartOwnedBy(OWNER_ID)));
        doThrow(new AccessDeniedException("nope")).when(authUtils).requireSelfOrAdmin(OWNER_ID);

        assertThatThrownBy(() -> cartService.clearCart(CART_ID)).isInstanceOf(AccessDeniedException.class);
        verify(cartItemRepository, never()).deleteAllByCartId(any());
        verify(cartRepository, never()).deleteById(any());
    }

    @Test
    void clearCart_deletesItemsThenTheCart() {
        when(cartRepository.findById(CART_ID)).thenReturn(Optional.of(cartOwnedBy(OWNER_ID)));

        cartService.clearCart(CART_ID);

        verify(cartItemRepository).deleteAllByCartId(CART_ID);
        verify(cartRepository).deleteById(CART_ID);
    }

    @Test
    void initializeNewCart_reusesAnExistingCart() {
        Cart existing = cartOwnedBy(OWNER_ID);
        when(cartRepository.findByUserId(OWNER_ID)).thenReturn(existing);

        assertThat(cartService.initializeNewCart(existing.getUser())).isSameAs(existing);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void initializeNewCart_createsOneForTheUser() {
        User user = new User();
        user.setId(OWNER_ID);
        when(cartRepository.findByUserId(OWNER_ID)).thenReturn(null);
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(cartService.initializeNewCart(user).getUser()).isSameAs(user);
    }

    // Regression: two first add-to-carts at once both found no cart and both
    // inserted one; the second failed the unique user_id and answered 409.
    @Test
    void initializeNewCart_locksTheUserBeforeLookingForTheCart() {
        User user = new User();
        user.setId(OWNER_ID);
        when(cartRepository.findByUserId(OWNER_ID)).thenReturn(cartOwnedBy(OWNER_ID));

        cartService.initializeNewCart(user);

        InOrder order = inOrder(userRepository, cartRepository);
        order.verify(userRepository).lockById(OWNER_ID);
        order.verify(cartRepository).findByUserId(OWNER_ID);
    }

    private static Cart cartOwnedBy(Long ownerId) {
        User owner = new User();
        owner.setId(ownerId);
        Cart cart = new Cart();
        cart.setId(CART_ID);
        cart.setUser(owner);
        return cart;
    }
}
