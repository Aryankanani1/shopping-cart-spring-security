package com.aryan.spring_security_demo.Service.order;

import com.aryan.spring_security_demo.model.Order;

import java.util.List;

public interface OrderServiceInterface {

     Order placeOrder(Long userId);
    Order getOrder(Long orderId);


    List<Order> getUserOrders(Long userId);
}
