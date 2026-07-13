package com.aryan.spring_security_demo.Service.order;

import com.aryan.spring_security_demo.model.Order;

public interface OrderServiceInterface {

     Order placeOrder(Long userId);
    Order getOrder(Long orderId);



}
