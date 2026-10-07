package com.aryan.spring_security_demo.wishlist;

import com.aryan.spring_security_demo.catalog.Product;
import com.aryan.spring_security_demo.catalog.ProductRepository;
import com.aryan.spring_security_demo.catalog.ProductServiceInterface;
import com.aryan.spring_security_demo.identity.User;
import com.aryan.spring_security_demo.identity.UserRepository;
import com.aryan.spring_security_demo.identity.security.AuthUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Saving to the wishlist. Repositories, the product mapper and the auth helper are mocked. */
@ExtendWith(MockitoExtension.class)
class WishlistServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long PRODUCT_ID = 3L;
    private static final Instant NOW = Instant.parse("2026-03-14T12:00:00Z");

    @Mock private WishlistItemRepository wishlistItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProductServiceInterface productService;
    @Mock private AuthUtils authUtils;
    @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @InjectMocks private WishlistService wishlistService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product("Desk Lamp", new BigDecimal("40.00"), "", "Acme", 3, null);
        product.setId(PRODUCT_ID);
        when(authUtils.currentUserId()).thenReturn(USER_ID);
    }

    // Regression: two adds of the same product at once both found it unsaved and
    // both inserted it; the second failed the unique constraint and answered 409.
    @Test
    void add_locksTheUserBeforeCheckingWhetherTheProductIsSaved() {
        when(wishlistItemRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.empty());
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(wishlistItemRepository.save(any(WishlistItem.class))).thenAnswer(inv -> inv.getArgument(0));

        WishlistServiceInterface.AddResult result = wishlistService.add(PRODUCT_ID);

        assertThat(result.created()).isTrue();
        InOrder order = inOrder(userRepository, wishlistItemRepository);
        order.verify(userRepository).lockById(USER_ID);
        order.verify(wishlistItemRepository).findByUserIdAndProductId(USER_ID, PRODUCT_ID);
    }

    @Test
    void add_alreadySaved_returnsTheItemWithoutSavingAgain() {
        WishlistItem saved = new WishlistItem(new User(), product, NOW);
        when(wishlistItemRepository.findByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(Optional.of(saved));

        WishlistServiceInterface.AddResult result = wishlistService.add(PRODUCT_ID);

        assertThat(result.created()).isFalse();
        verify(wishlistItemRepository, never()).save(any());
    }
}
