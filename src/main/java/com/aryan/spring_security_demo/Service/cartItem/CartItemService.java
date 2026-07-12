package com.aryan.spring_security_demo.Service.cartItem;

import com.aryan.spring_security_demo.Service.cart.CartService;
import com.aryan.spring_security_demo.Service.product.ProductService;
import com.aryan.spring_security_demo.exception.ProductNotFoundException;
import com.aryan.spring_security_demo.model.Cart;
import com.aryan.spring_security_demo.model.CartItem;
import com.aryan.spring_security_demo.model.Product;
import com.aryan.spring_security_demo.repository.CartItemRepository;
import com.aryan.spring_security_demo.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

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
    public void updateItemQuantity(Long cartId, Long productId, int quantity) {

        Cart cart = cartService.getCart(cartId);
        cart.getCartItems().stream().filter(item -> item.getProduct().getId().equals(productId))
                .findFirst().ifPresent(item -> {
                            item.setQuantity(quantity);
                            item.setUnitPrice(item.getProduct().getPrice());
                            item.setTotalPrice();
                        });
                    BigDecimal totalAmount = cart.getTotalAmount();
                    cart.setTotalAmount(totalAmount);
                    cartRepository.save(cart);
    }


    @Override
    public CartItem getCartItem(Long cartId, Long productId){
        Cart cart = cartService.getCart(cartId);
        return cart.getCartItems()
                .stream()
                .filter(item -> item.getProduct().getId().equals(productId))
                .findFirst().orElseThrow(() -> new ProductNotFoundException("item not found"));
    }
}
