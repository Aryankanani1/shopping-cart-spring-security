package com.aryan.spring_security_demo;

import com.aryan.spring_security_demo.cart.Cart;
import com.aryan.spring_security_demo.cart.CartDto;
import com.aryan.spring_security_demo.cart.CartItem;
import com.aryan.spring_security_demo.cart.CartItemDto;
import com.aryan.spring_security_demo.cart.CartMappings;
import com.aryan.spring_security_demo.catalog.CatalogMappings;
import com.aryan.spring_security_demo.catalog.Category;
import com.aryan.spring_security_demo.catalog.Image;
import com.aryan.spring_security_demo.catalog.ImageDto;
import com.aryan.spring_security_demo.catalog.Product;
import com.aryan.spring_security_demo.common.config.ModelMapperConfig;
import com.aryan.spring_security_demo.order.Order;
import com.aryan.spring_security_demo.order.OrderDto;
import com.aryan.spring_security_demo.order.OrderItem;
import com.aryan.spring_security_demo.order.OrderMappings;
import com.aryan.spring_security_demo.order.OrderStatus;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The entity-to-DTO mappings the services rely on, including the fields whose
 * names differ (cartId, itemId, imageId, orderDate, status, ...) and the cart's
 * nested product and images. Each module contributes its own type maps.
 */
class ModelMapperMappingsTest {

    // The modules' type maps in their @Order, as Spring injects them.
    private final ModelMapper modelMapper = new ModelMapperConfig().modelMapper(
            List.of(new CatalogMappings(), new CartMappings(), new OrderMappings()));

    @Test
    void cart_mapsItsIdsItemsAndTheirProductsImages() {
        Product product = product();
        CartItem item = new CartItem();
        item.setId(9L);
        item.setProduct(product);
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("25.00"));
        item.setTotalPrice();
        Cart cart = new Cart();
        cart.setId(5L);
        cart.addItem(item);

        CartDto dto = modelMapper.map(cart, CartDto.class);

        assertThat(dto.getCartId()).isEqualTo(5L);
        assertThat(dto.getTotalAmount()).isEqualByComparingTo("50.00");
        CartItemDto line = dto.getCartItems().iterator().next();
        assertThat(line.getItemId()).isEqualTo(9L);
        assertThat(line.getQuantity()).isEqualTo(2);
        assertThat(line.getProduct().getName()).isEqualTo("Kettle");
        ImageDto image = line.getProduct().getImages().get(0);
        assertThat(image.getImageId()).isEqualTo(3L);
        assertThat(image.getImageName()).isEqualTo("kettle.png");
        assertThat(image.getDownloadUrl()).isEqualTo("/api/v1/images/3");
    }

    @Test
    void order_mapsItsDateStatusAndItems() {
        Order order = new Order();
        order.setId(7L);
        order.setLocalDate(LocalDate.of(2026, 3, 14));
        order.setOrderStatus(OrderStatus.SHIPPED);
        order.addOrderItem(new OrderItem(product(), 2, new BigDecimal("25.00")));

        OrderDto dto = modelMapper.map(order, OrderDto.class);

        assertThat(dto.getId()).isEqualTo(7L);
        assertThat(dto.getOrderDate()).isEqualTo(LocalDate.of(2026, 3, 14));
        assertThat(dto.getStatus()).isEqualTo("SHIPPED");
        assertThat(dto.getItems()).singleElement().satisfies(line -> {
            assertThat(line.getProductId()).isEqualTo(11L);
            assertThat(line.getProductName()).isEqualTo("Kettle");
            assertThat(line.getProductBrand()).isEqualTo("Acme");
            assertThat(line.getQuantity()).isEqualTo(2);
        });
    }

    private static Product product() {
        Product product = new Product("Kettle", new BigDecimal("25.00"), "", "Acme", 4, new Category("Home"));
        product.setId(11L);
        Image image = new Image();
        image.setId(3L);
        image.setFileName("kettle.png");
        image.setURL("/api/v1/images/3");
        image.setProduct(product);
        product.setImageList(new ArrayList<>(List.of(image)));
        return product;
    }
}
