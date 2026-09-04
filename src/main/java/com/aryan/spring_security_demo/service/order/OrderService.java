package com.aryan.spring_security_demo.service.order;

import com.aryan.spring_security_demo.service.cart.CartService;
import com.aryan.spring_security_demo.dto.OrderDto;
import com.aryan.spring_security_demo.enums.OrderStatus;
import com.aryan.spring_security_demo.exception.ResourceNotFoundException;
import com.aryan.spring_security_demo.model.Cart;
import com.aryan.spring_security_demo.model.Order;
import com.aryan.spring_security_demo.model.OrderItem;
import com.aryan.spring_security_demo.model.Product;
import com.aryan.spring_security_demo.pagination.OrderCursor;
import com.aryan.spring_security_demo.repository.OrderKeysetRow;
import com.aryan.spring_security_demo.repository.OrderRepository;
import com.aryan.spring_security_demo.repository.ProductRepository;
import com.aryan.spring_security_demo.response.SlicedResponse;
import com.aryan.spring_security_demo.security.AuthUtils;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService implements OrderServiceInterface{

    private final OrderRepository orderRepository;
    private final ProductRepository  productRepository;
    private final CartService cartService;
    private final AuthUtils authUtils;

    private final ModelMapper modelMapper;
    @Override
    @Transactional
    public OrderDto placeOrder(Long userId) {
        // A user may only place an order for themselves; an admin may act for anyone.
        authUtils.requireSelfOrAdmin(userId);
        Cart cart = cartService.getCartByUserId(userId);
        Order order = careatOrder(cart);
        List<OrderItem> orderItems = createOrderItems(cart);
        orderItems.forEach(order::addOrderItem);
        order.setTotalAmount(calculateTotalAmount(orderItems));
        Order savedOrdered = orderRepository.save(order);

        cartService.clearCart(cart.getId());

        return convertToDto(savedOrdered);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDto getOrder(Long orderId) {
        OrderDto order = orderRepository.
                findByIdWithItems(orderId).
                map(this::convertToDto)
                .orElseThrow(() -> new ResourceNotFoundException("order not found!"));
        // Reject reading another user's order (IDOR) — after the fetch so a missing
        // order is still a 404, not a 403 that would confirm the id exists.
        authUtils.requireSelfOrAdmin(order.getUserId());
        return order;
    }

    private Order careatOrder(Cart cart){

        Order order = new Order();
        //set the user
        order.setUser(cart.getUser());
        order.setOrderStatus(OrderStatus.PENDING);
        order.setLocalDate(LocalDate.now());
        return order;

    }
    private List<OrderItem> createOrderItems(Cart cart){
        //keeping track of the inventory by calculating the total price
        return cart.getCartItems().stream()
                .map(cartItem -> {
                    Product product = cartItem.getProduct();
                    product.setInventory(product.getInventory() - cartItem.getQuantity());
                    productRepository.save(product);
                    // the order is wired in by Order.addOrderItem (owning side)
                    return
                           new OrderItem(product,
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
    @Transactional(readOnly = true)
    public SlicedResponse<OrderDto> getUserOrders(Long userId, String cursor, int size) {
        // Order history is private: only the owner (or an admin) may page it.
        authUtils.requireSelfOrAdmin(userId);
        OrderCursor from = OrderCursor.decode(cursor);

        // Phase 1: index-backed keyset scan for this page of ids. Fetch one extra
        // row so we can tell whether a further slice exists without a COUNT.
        List<OrderKeysetRow> rows = orderRepository.findUserOrderKeyset(
                userId,
                from == null ? null : from.createdAt(),
                from == null ? null : from.id(),
                PageRequest.of(0, size + 1));

        boolean hasNext = rows.size() > size;
        List<OrderKeysetRow> pageRows = hasNext ? rows.subList(0, size) : rows;

        if (pageRows.isEmpty()) {
            return new SlicedResponse<>(List.of(), size, 0, false, null);
        }

        // Phase 2: hydrate the page's orders (items + products) in one JOIN FETCH,
        // then re-impose the keyset order that the id list already carries.
        List<Long> ids = pageRows.stream().map(OrderKeysetRow::getId).toList();
        Map<Long, Order> byId = orderRepository.findWithItemsByIdIn(ids).stream()
                .collect(Collectors.toMap(Order::getId, Function.identity()));
        List<OrderDto> content = ids.stream()
                .map(byId::get)
                .map(this::convertToDto)
                .toList();

        OrderKeysetRow last = pageRows.get(pageRows.size() - 1);
        String nextCursor = hasNext
                ? new OrderCursor(last.getCreatedAt(), last.getId()).encode()
                : null;
        return new SlicedResponse<>(content, size, content.size(), hasNext, nextCursor);
    }

    private OrderDto convertToDto(Order order){
        return modelMapper.map(order,OrderDto.class);
    }
}
