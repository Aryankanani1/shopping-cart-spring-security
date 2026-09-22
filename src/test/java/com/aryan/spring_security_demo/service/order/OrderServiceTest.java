package com.aryan.spring_security_demo.service.order;

import com.aryan.spring_security_demo.dto.OrderDto;
import com.aryan.spring_security_demo.enums.OrderStatus;
import com.aryan.spring_security_demo.exception.InvalidOrderStateException;
import com.aryan.spring_security_demo.exception.ResourceNotFoundException;
import com.aryan.spring_security_demo.model.Order;
import com.aryan.spring_security_demo.model.OrderItem;
import com.aryan.spring_security_demo.model.Product;
import com.aryan.spring_security_demo.model.User;
import com.aryan.spring_security_demo.repository.OrderRepository;
import com.aryan.spring_security_demo.repository.ProductRepository;
import com.aryan.spring_security_demo.security.AuthUtils;
import com.aryan.spring_security_demo.service.cart.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Behaviour of the order-lifecycle operations. The repositories, cart service and
 * auth helper are mocked; the assertions target the two things that actually
 * matter and live in this class: the state-machine guard (illegal hops are
 * rejected) and the inventory restock on cancellation. {@code ModelMapper} is a
 * pure transformer, so its output is stubbed and not itself under test.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Long ORDER_ID = 1L;
    private static final Long OWNER_ID = 42L;

    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CartService cartService;
    @Mock private AuthUtils authUtils;
    @Mock private ModelMapper modelMapper;

    @InjectMocks private OrderService orderService;

    private Product product;
    private Order order;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(7L);
        product.setInventory(5);

        User owner = new User();
        owner.setId(OWNER_ID);

        order = new Order();
        order.setId(ORDER_ID);
        order.setUser(owner);
        order.setOrderStatus(OrderStatus.PROCESSING);
        order.addOrderItem(new OrderItem(product, 2, BigDecimal.TEN));

        // ModelMapper is a pure transformer here — stub it so convertToDto returns
        // a non-null dto without asserting on the mapping itself.
        lenient().when(modelMapper.map(order, OrderDto.class)).thenReturn(new OrderDto());
    }

    @Test
    void cancelOrder_restocksInventoryAndSetsStatusCancelled() {
        when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(order));

        orderService.cancelOrder(ORDER_ID);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(product.getInventory()).isEqualTo(7); // 5 + the 2 cancelled units
        // Ownership is enforced against the order's actual owner.
        verify(authUtils).requireSelfOrAdmin(OWNER_ID);
    }

    @Test
    void cancelOrder_onceShipped_isRejectedAndInventoryUntouched() {
        order.setOrderStatus(OrderStatus.SHIPPED);
        when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(ORDER_ID))
                .isInstanceOf(InvalidOrderStateException.class);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(product.getInventory()).isEqualTo(5); // no restock on a rejected cancel
    }

    @Test
    void cancelOrder_missingOrder_is404() {
        when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(ORDER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStatus_forwardTransition_advancesWithoutRestocking() {
        when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(order));

        orderService.updateStatus(ORDER_ID, OrderStatus.SHIPPED);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(product.getInventory()).isEqualTo(5); // only cancellation restocks
    }

    @Test
    void updateStatus_illegalTransition_isRejected() {
        order.setOrderStatus(OrderStatus.DELIVERED);
        when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateStatus(ORDER_ID, OrderStatus.PROCESSING))
                .isInstanceOf(InvalidOrderStateException.class);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.DELIVERED);
    }
}
