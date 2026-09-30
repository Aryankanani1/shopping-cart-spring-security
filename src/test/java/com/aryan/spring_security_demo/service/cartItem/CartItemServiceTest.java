package com.aryan.spring_security_demo.service.cartItem;

import com.aryan.spring_security_demo.exception.InsufficientStockException;
import com.aryan.spring_security_demo.exception.ResourceNotFoundException;
import com.aryan.spring_security_demo.model.Cart;
import com.aryan.spring_security_demo.model.CartItem;
import com.aryan.spring_security_demo.model.Product;
import com.aryan.spring_security_demo.repository.CartItemRepository;
import com.aryan.spring_security_demo.repository.CartRepository;
import com.aryan.spring_security_demo.service.cart.CartService;
import com.aryan.spring_security_demo.service.product.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Cart-line mutations: quantity updates address a line by its item id, and every
 * add/update is checked against the product's inventory. The cart service and
 * repositories are mocked; the cart and product are plain objects, so the
 * assertions read the state the service left behind.
 */
@ExtendWith(MockitoExtension.class)
class CartItemServiceTest {

    private static final Long CART_ID = 99L;
    private static final Long PRODUCT_ID = 7L;
    // Deliberately different from PRODUCT_ID, so a lookup on the wrong id misses.
    private static final Long ITEM_ID = 500L;

    @Mock private CartItemRepository cartItemRepository;
    @Mock private CartRepository cartRepository;
    @Mock private ProductService productService;
    @Mock private CartService cartService;

    @InjectMocks private CartItemService cartItemService;

    private Product product;
    private Cart cart;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(PRODUCT_ID);
        product.setName("Wireless Mouse");
        product.setPrice(BigDecimal.TEN);
        product.setInventory(5);

        cart = new Cart();
        cart.setId(CART_ID);

        lenient().when(cartService.getCart(CART_ID)).thenReturn(cart);
        lenient().when(productService.getProductById(PRODUCT_ID)).thenReturn(product);
    }

    @Test
    void updateItemQuantity_matchesLineByItemId_andRecomputesTotals() {
        CartItem line = addLine(1);

        cartItemService.updateItemQuantity(CART_ID, ITEM_ID, 3);

        assertThat(line.getQuantity()).isEqualTo(3);
        assertThat(line.getTotalPrice()).isEqualByComparingTo("30");
        assertThat(cart.getTotalAmount()).isEqualByComparingTo("30");
        verify(cartRepository).save(cart);
    }

    @Test
    void updateItemQuantity_withProductIdInsteadOfItemId_is404() {
        addLine(1);

        assertThatThrownBy(() -> cartItemService.updateItemQuantity(CART_ID, PRODUCT_ID, 3))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateItemQuantity_beyondStock_isRejectedAndLineUntouched() {
        CartItem line = addLine(1);

        assertThatThrownBy(() -> cartItemService.updateItemQuantity(CART_ID, ITEM_ID, 6))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Only 5 of Wireless Mouse left in stock");

        assertThat(line.getQuantity()).isEqualTo(1);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void addItemToCart_newLineWithinStock_isAdded() {
        cartItemService.addItemToCart(CART_ID, PRODUCT_ID, 5);

        assertThat(cart.getCartItems()).singleElement()
                .satisfies(item -> assertThat(item.getQuantity()).isEqualTo(5));
        assertThat(cart.getTotalAmount()).isEqualByComparingTo("50");
    }

    @Test
    void addItemToCart_checksCombinedQuantityAgainstStock() {
        CartItem line = addLine(3);

        // 3 already in the cart + 3 more exceeds the 5 in stock.
        assertThatThrownBy(() -> cartItemService.addItemToCart(CART_ID, PRODUCT_ID, 3))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(line.getQuantity()).isEqualTo(3);
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    void addItemToCart_soldOutProduct_isRejected() {
        product.setInventory(0);

        assertThatThrownBy(() -> cartItemService.addItemToCart(CART_ID, PRODUCT_ID, 1))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessage("Wireless Mouse is out of stock");

        assertThat(cart.getCartItems()).isEmpty();
    }

    /** Put a persisted-looking line for {@link #product} into the cart. */
    private CartItem addLine(int quantity) {
        CartItem item = new CartItem();
        item.setId(ITEM_ID);
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setUnitPrice(product.getPrice());
        item.setTotalPrice();
        cart.addItem(item);
        return item;
    }
}
