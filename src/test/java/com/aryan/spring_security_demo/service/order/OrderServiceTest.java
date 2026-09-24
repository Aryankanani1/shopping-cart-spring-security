package com.aryan.spring_security_demo.service.order;

import com.aryan.spring_security_demo.dto.OrderDto;
import com.aryan.spring_security_demo.dto.OrderSummaryDto;
import com.aryan.spring_security_demo.enums.OrderStatus;
import com.aryan.spring_security_demo.exception.InvalidOrderStateException;
import com.aryan.spring_security_demo.exception.ResourceNotFoundException;
import com.aryan.spring_security_demo.model.Cart;
import com.aryan.spring_security_demo.model.CartItem;
import com.aryan.spring_security_demo.model.Order;
import com.aryan.spring_security_demo.model.OrderItem;
import com.aryan.spring_security_demo.model.Product;
import com.aryan.spring_security_demo.model.User;
import com.aryan.spring_security_demo.repository.OrderRepository;
import com.aryan.spring_security_demo.repository.ProductRepository;
import com.aryan.spring_security_demo.request.PlaceOrderRequest;
import com.aryan.spring_security_demo.security.AuthUtils;
import com.aryan.spring_security_demo.service.cart.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    void getAllOrders_returnsRepositorySummaryPage() {
        Pageable pageable = PageRequest.of(0, 20);
        OrderSummaryDto summary = new OrderSummaryDto(
                ORDER_ID, OWNER_ID, "a@b.com", LocalDate.now(), BigDecimal.TEN, OrderStatus.PENDING);
        Page<OrderSummaryDto> page = new PageImpl<>(List.of(summary), pageable, 1);
        when(orderRepository.findAllSummaries(pageable)).thenReturn(page);

        Page<OrderSummaryDto> result = orderService.getAllOrders(pageable);

        assertThat(result.getContent()).containsExactly(summary);
        verify(orderRepository).findAllSummaries(pageable);
    }

    @Test
    void placeOrder_persistsShippingAddressAndClearsCart() {
        // A cart with one item, owned by OWNER_ID.
        Product p = new Product();
        p.setId(7L);
        p.setInventory(5);
        CartItem ci = new CartItem();
        ci.setProduct(p);
        ci.setQuantity(2);
        ci.setUnitPrice(BigDecimal.TEN);
        User owner = new User();
        owner.setId(OWNER_ID);
        Cart cart = new Cart();
        cart.setId(99L);
        cart.setUser(owner);
        cart.getCartItems().add(ci);

        when(cartService.getCartByUserId(OWNER_ID)).thenReturn(cart);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(modelMapper.map(any(Order.class), eq(OrderDto.class))).thenReturn(new OrderDto());

        PlaceOrderRequest addr = new PlaceOrderRequest();
        addr.setRecipientName("Ada Lovelace");
        addr.setAddressLine1("1 Analytical Way");
        addr.setAddressLine2("Apt 2");
        addr.setCity("London");
        addr.setState("LDN");
        addr.setPostalCode("EC1A");
        addr.setCountry("UK");

        orderService.placeOrder(OWNER_ID, addr);

        // The saved order carries the address snapshot, starts PENDING, and has the item.
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order saved = captor.getValue();
        assertThat(saved.getRecipientName()).isEqualTo("Ada Lovelace");
        assertThat(saved.getAddressLine1()).isEqualTo("1 Analytical Way");
        assertThat(saved.getAddressLine2()).isEqualTo("Apt 2");
        assertThat(saved.getCity()).isEqualTo("London");
        assertThat(saved.getState()).isEqualTo("LDN");
        assertThat(saved.getPostalCode()).isEqualTo("EC1A");
        assertThat(saved.getCountry()).isEqualTo("UK");
        assertThat(saved.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(saved.getOrderItems()).hasSize(1);
        // And the cart is emptied after a successful order.
        verify(cartService).clearCart(99L);
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
