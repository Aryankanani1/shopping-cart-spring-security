package com.aryan.spring_security_demo.Service.order;

import com.aryan.spring_security_demo.Service.cart.CartService;
import com.aryan.spring_security_demo.dto.OrderDto;
import com.aryan.spring_security_demo.enums.OrderStatus;
import com.aryan.spring_security_demo.exception.ResourceNotFoundException;
import com.aryan.spring_security_demo.model.Cart;
import com.aryan.spring_security_demo.model.Order;
import com.aryan.spring_security_demo.model.OrderItem;
import com.aryan.spring_security_demo.model.Product;
import com.aryan.spring_security_demo.repository.OrderRepository;
import com.aryan.spring_security_demo.repository.ProductRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService implements OrderServiceInterface{

    private final OrderRepository orderRepository;
    private final ProductRepository  productRepository;
    private final CartService cartService;

    private final ModelMapper modelMapper;
    @Override
    @Transactional
    public OrderDto placeOrder(Long userId) {
        Cart cart = cartService.getCartByUserId(userId);
        Order order = careatOrder(cart);
        List<OrderItem> orderItems = createOrderItems(order,cart);
        order.setOrderItems(new HashSet<>(orderItems));
        order.setTotalAmount(calculateTotalAmount(orderItems));
        Order savedOrdered = orderRepository.save(order);

        cartService.clearCart(cart.getId());

        return convertToDto(savedOrdered);
    }

    @Override
    public OrderDto getOrder(Long orderId) {
        return orderRepository.
                findById(orderId).
                map(this::convertToDto)
                .orElseThrow(() -> new ResourceNotFoundException("order not found!"));
    }

    private Order careatOrder(Cart cart){

        Order order = new Order();
        //set the user
        order.setUser(cart.getUser());
        order.setOrderStatus(OrderStatus.PENDING);
        order.setLocalDate(LocalDate.now());
        return order;

    }

    private List<OrderItem> createOrderItems(Order order, Cart cart){
        //keeping track of the inventory by calculating the total price
        return cart.getCartItems().stream()
                .map(cartItem -> {
                    Product product = cartItem.getProduct();
                    product.setInventory(product.getInventory() - cartItem.getQuantity());
                    productRepository.save(product);
                    // return ordered items via constructor
                    return
                           new OrderItem( order,
                                   product,
                                 cartItem.getQuantity(),
                                 cartItem.getUnitPrice());

                }).toList();
    }

    private BigDecimal calculateTotalAmount(List<OrderItem> orderItemList){
        return  orderItemList.stream()
                .map(item -> item.getPrice()
                        .multiply(new BigDecimal(item.getQuantity()))).reduce(BigDecimal.ZERO,BigDecimal::add);
    }

    @Override
    public List<OrderDto> getUserOrders(Long userId){
        return orderRepository.findByUserId(userId)
                .stream().map(this::convertToDto).toList();
    }

    private OrderDto convertToDto(Order order){
        return modelMapper.map(order,OrderDto.class);
    }
}
