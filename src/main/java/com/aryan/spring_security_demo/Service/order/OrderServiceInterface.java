package com.aryan.spring_security_demo.Service.order;

import com.aryan.spring_security_demo.dto.OrderDto;
import com.aryan.spring_security_demo.model.Order;

import java.util.List;
public interface OrderServiceInterface {

     OrderDto placeOrder(Long userId);
    OrderDto getOrder(Long orderId);

    List<OrderDto> getUserOrders(Long userId);
}
