package com.aryan.spring_security_demo.cart;

import com.aryan.spring_security_demo.common.config.ModelMapperCustomizer;
import org.modelmapper.ModelMapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Cart's type maps. After catalog's, whose product and image maps the line items use. */
@Component
@Order(20)
public class CartMappings implements ModelMapperCustomizer {

    @Override
    public void customize(ModelMapper modelMapper) {
        // Cart/CartItem id fields don't match the DTOs, so map them explicitly.
        modelMapper.typeMap(Cart.class, CartDto.class).addMappings(mapper ->
                mapper.map(Cart::getId, CartDto::setCartId));
        modelMapper.typeMap(CartItem.class, CartItemDto.class).addMappings(mapper ->
                mapper.map(CartItem::getId, CartItemDto::setItemId));
    }
}
