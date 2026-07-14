package com.aryan.spring_security_demo.controller;

import com.aryan.spring_security_demo.Service.order.OrderServiceInterface;
import com.aryan.spring_security_demo.dto.OrderDto;
import com.aryan.spring_security_demo.exception.ResourceNotFoundException;
import com.aryan.spring_security_demo.model.Order;
import com.aryan.spring_security_demo.response.ApiResponse;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static ch.qos.logback.core.util.AggregationType.NOT_FOUND;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@RestController
@RequestMapping("${api.prefix}/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderServiceInterface orderServiceInterface;

    @PostMapping("/order")
    public ResponseEntity<ApiResponse> createOrder(@PathVariable Long userId){

        try {
            Order order = orderServiceInterface.placeOrder(userId);
            return ResponseEntity.ok(new ApiResponse("Item Order Success!",order));
        } catch (Exception e) {
            return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(new ApiResponse("Error occurred!",null));
        }
        }

        @GetMapping("/{orderId}/order")
        public ResponseEntity<ApiResponse> getOrderById(@PathVariable Long orderId){
            try {
                OrderDto order = orderServiceInterface.getOrder(orderId);
                return ResponseEntity.ok(new ApiResponse("Item Order Success!",order));
            } catch (ResourceNotFoundException e) {
               return ResponseEntity.status(HttpStatus.NOT_FOUND)
                       .body(new ApiResponse(e.getMessage(),null));
            }
        }

    @GetMapping("/{userId}/order")
    public ResponseEntity<ApiResponse> getUserOrders(@PathVariable Long userId){
        try {
            List<OrderDto> order = orderServiceInterface.getUserOrders(userId);
            return ResponseEntity.ok(new ApiResponse("Item Order Success!",order));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse("order not found!", null));
        }
    }
}
