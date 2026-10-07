package com.aryan.spring_security_demo.order;

import com.aryan.spring_security_demo.common.config.ModelMapperCustomizer;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;

/** Order's type maps. ({@code @Order} is written out in full: {@link Order} here is the entity.) */
@Component
@org.springframework.core.annotation.Order(30)
public class OrderMappings implements ModelMapperCustomizer {

    @Override
    public void customize(ModelMapper modelMapper) {
        // Order field names don't match OrderDto, so map them explicitly.
        modelMapper.typeMap(Order.class, OrderDto.class).addMappings(mapper -> {
            mapper.map(Order::getLocalDate, OrderDto::setOrderDate);
            mapper.map(Order::getOrderStatus, OrderDto::setStatus);
            mapper.map(Order::getOrderItems, OrderDto::setItems);
        });
    }
}
