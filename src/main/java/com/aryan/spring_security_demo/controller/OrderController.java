package com.aryan.spring_security_demo.controller;

import com.aryan.spring_security_demo.Service.order.OrderServiceInterface;
import com.aryan.spring_security_demo.dto.OrderDto;
import com.aryan.spring_security_demo.exception.ResourceNotFoundException;
import com.aryan.spring_security_demo.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

@RestController
@RequestMapping("${api.prefix}/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderServiceInterface orderServiceInterface;

    @PostMapping
    public ResponseEntity<ApiResponse> createOrder(@RequestParam Long userId){

        try {
            OrderDto order = orderServiceInterface.placeOrder(userId);
            URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                    .path("/{id}").buildAndExpand(order.getId()).toUri();
            return ResponseEntity.created(location).body(new ApiResponse("Item Order Success!",order));
        } catch (Exception e) {
            return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(new ApiResponse("Error occurred!",null));
        }
        }

        @GetMapping("/{orderId}")
        public ResponseEntity<ApiResponse> getOrderById(@PathVariable Long orderId){
            try {
                OrderDto order = orderServiceInterface.getOrder(orderId);
                return ResponseEntity.ok(new ApiResponse("Item Order Success!",order));
            } catch (ResourceNotFoundException e) {
               return ResponseEntity.status(HttpStatus.NOT_FOUND)
                       .body(new ApiResponse(e.getMessage(),null));
            }
        }

    @GetMapping(params = "userId")
    public ResponseEntity<ApiResponse> getUserOrders(@RequestParam Long userId){
        try {
            List<OrderDto> order = orderServiceInterface.getUserOrders(userId);
            return ResponseEntity.ok(new ApiResponse("Item Order Success!",order));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiResponse("order not found!", null));
        }
    }
}
