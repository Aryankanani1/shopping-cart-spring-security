package com.aryan.spring_security_demo.service.cartItem;

import com.aryan.spring_security_demo.service.cart.CartService;
import com.aryan.spring_security_demo.service.product.ProductService;
import com.aryan.spring_security_demo.exception.InsufficientStockException;
import com.aryan.spring_security_demo.exception.ProductNotFoundException;
import com.aryan.spring_security_demo.exception.ResourceNotFoundException;
import com.aryan.spring_security_demo.model.Cart;
import com.aryan.spring_security_demo.model.CartItem;
import com.aryan.spring_security_demo.model.Product;
import com.aryan.spring_security_demo.repository.CartItemRepository;
import com.aryan.spring_security_demo.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartItemService implements CartItemServiceInterface {

    private final CartItemRepository cartItemRepository;
    private final CartRepository cartRepository;
    private final ProductService productService;
    private final CartService cartService;


    @Override
    @Transactional
    public void addItemToCart(Long cartId, Long productId, Integer quantity) {
        // get the cart
        Cart cart = cartService.getCart(cartId);
        // get the item
        Product product = productService.getProductById(productId);
        // check if the product already in the cart
        // if yes, then increase the quantity with the requested quantity
        // if no initiates the new cart entry
        CartItem cartItem = cart.getCartItems()
                .stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst().orElse(new CartItem());
        // Check the combined quantity: the cart may already hold some of this
        // product (a new CartItem starts at 0).
        requireStock(product, cartItem.getQuantity() + quantity);
        if(cartItem.getId() == null){
           cartItem.setCart(cart);
           cartItem.setProduct(product);
           cartItem.setQuantity(quantity);
           cartItem.setUnitPrice(product.getPrice());
        }
        else {
            cartItem.setQuantity(cartItem.getQuantity() + quantity);
        }
        cartItem.setTotalPrice();
        cart.addItem(cartItem);
        cartItemRepository.save(cartItem);
        cartRepository.save(cart);
    }

    @Override
    @Transactional
    public void removeItemFromCart(Long cartId, Long productId) {

        Cart cart = cartService.getCart(cartId);
        CartItem itemToRemove = getCartItem(cartId,productId);
        cart.removeItem(itemToRemove);
        cartRepository.save(cart);
    }

    @Override
    @Transactional
    public void updateItemQuantity(Long cartId, Long itemId, int quantity) {

        Cart cart = cartService.getCart(cartId);
        // Match on the cart line's own id — the id the API path carries — not the
        // product id.
        CartItem item = cart.getCartItems().stream()
                .filter(line -> line.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("cart item not found"));
        Product product = item.getProduct();
        requireStock(product, quantity);
        item.setQuantity(quantity);
        item.setUnitPrice(product.getPrice());
        item.setTotalPrice();
        cart.updateTotalAmount();
        cartRepository.save(cart);
    }

    private void requireStock(Product product, int quantity) {
        if (quantity > product.getInventory()) {
            throw new InsufficientStockException(product.getName(), product.getInventory());
        }
    }


    @Override
    @Transactional(readOnly = true)
    public CartItem getCartItem(Long cartId, Long productId){
        Cart cart = cartService.getCart(cartId);
        return cart.getCartItems()
                .stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst().orElseThrow(() -> new ProductNotFoundException("item not found"));
    }
}
