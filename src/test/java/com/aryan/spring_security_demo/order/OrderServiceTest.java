package com.aryan.spring_security_demo.order;

import com.aryan.spring_security_demo.cart.Cart;
import com.aryan.spring_security_demo.cart.CartItem;
import com.aryan.spring_security_demo.cart.CartService;
import com.aryan.spring_security_demo.catalog.InsufficientStockException;
import com.aryan.spring_security_demo.catalog.Product;
import com.aryan.spring_security_demo.catalog.ProductRepository;
import com.aryan.spring_security_demo.common.exception.ResourceNotFoundException;
import com.aryan.spring_security_demo.common.web.SlicedResponse;
import com.aryan.spring_security_demo.identity.User;
import com.aryan.spring_security_demo.identity.security.AuthUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
    @Spy private Clock clock = Clock.fixed(Instant.parse("2026-03-14T12:00:00Z"), ZoneOffset.UTC);

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
                ORDER_ID, OWNER_ID, "a@b.com", LocalDate.of(2026, 3, 14), BigDecimal.TEN, OrderStatus.PENDING);
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
        p.setPrice(new BigDecimal("12.00"));
        CartItem ci = new CartItem();
        ci.setProduct(p);
        ci.setQuantity(2);
        ci.setUnitPrice(BigDecimal.TEN); // stored when added; the price has since gone up
        User owner = new User();
        owner.setId(OWNER_ID);
        Cart cart = new Cart();
        cart.setId(99L);
        cart.setUser(owner);
        cart.getCartItems().add(ci);

        when(authUtils.currentUserId()).thenReturn(OWNER_ID);
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

        orderService.placeOrder(addr);

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
        assertThat(saved.getLocalDate()).isEqualTo(LocalDate.of(2026, 3, 14));  // from the clock
        assertThat(saved.getOrderItems()).hasSize(1);
        // Charged at the current price, not the one stored in the cart.
        assertThat(saved.getOrderItems().iterator().next().getPrice()).isEqualByComparingTo("12.00");
        assertThat(saved.getTotalAmount()).isEqualByComparingTo("24.00");
        // And the cart is emptied after a successful order.
        verify(cartService).clearCart(99L);
    }

    @Test
    void placeOrder_withNoCart_isRejectedAsEmpty() {
        // The cart is deleted after each order, so a repeat checkout finds none.
        when(authUtils.currentUserId()).thenReturn(OWNER_ID);
        when(cartService.getCartByUserId(OWNER_ID)).thenReturn(null);

        assertThatThrownBy(() -> orderService.placeOrder(new PlaceOrderRequest()))
                .isInstanceOf(EmptyCartException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrder_withEmptyCart_isRejected() {
        Cart cart = new Cart();
        cart.setId(99L);
        when(authUtils.currentUserId()).thenReturn(OWNER_ID);
        when(cartService.getCartByUserId(OWNER_ID)).thenReturn(cart);

        assertThatThrownBy(() -> orderService.placeOrder(new PlaceOrderRequest()))
                .isInstanceOf(EmptyCartException.class);

        verify(orderRepository, never()).save(any());
        verify(cartService, never()).clearCart(any());
    }

    @Test
    void placeOrder_lineBeyondStock_isRejectedAndInventoryUntouched() {
        // Stock fell to 5 after 6 went into the cart.
        CartItem ci = new CartItem();
        ci.setProduct(product);
        ci.setQuantity(6);
        ci.setUnitPrice(BigDecimal.TEN);
        Cart cart = new Cart();
        cart.setId(99L);
        cart.getCartItems().add(ci);
        when(authUtils.currentUserId()).thenReturn(OWNER_ID);
        when(cartService.getCartByUserId(OWNER_ID)).thenReturn(cart);

        assertThatThrownBy(() -> orderService.placeOrder(new PlaceOrderRequest()))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(product.getInventory()).isEqualTo(5);
        verify(orderRepository, never()).save(any());
        verify(cartService, never()).clearCart(any());
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

    // Once its account is deleted an order has no user. Cancelling it must reach
    // the ownership check (which refuses an ownerless order) instead of a 500.
    @Test
    void cancelOrder_ofADeletedAccount_isCheckedAsHavingNoOwner() {
        order.setUser(null);
        when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(order));

        orderService.cancelOrder(ORDER_ID);

        verify(authUtils).requireSelfOrAdmin(null);
    }

    @Test
    void prepareForAccountDeletion_cancelsAndUnlinksTheOpenOrders() {
        when(orderRepository.findWithItemsByUserIdAndStatusIn(
                OWNER_ID, List.of(OrderStatus.PENDING, OrderStatus.PROCESSING)))
                .thenReturn(List.of(order));

        orderService.prepareForAccountDeletion(OWNER_ID);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(product.getInventory()).isEqualTo(7); // 5 + the 2 cancelled units
        assertThat(order.getUser()).isNull();
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

    // ---- order history (keyset paging) ------------------------------------

    private static OrderKeysetRow row(long id, String createdAt) {
        return new OrderKeysetRow() {
            @Override public Long getId() { return id; }
            @Override public Instant getCreatedAt() { return Instant.parse(createdAt); }
        };
    }

    private static Order orderWithId(long id) {
        Order o = new Order();
        o.setId(id);
        return o;
    }

    private void mapOrdersToDtosWithTheirIds() {
        when(modelMapper.map(any(Order.class), eq(OrderDto.class))).thenAnswer(inv -> {
            OrderDto dto = new OrderDto();
            dto.setId(inv.<Order>getArgument(0).getId());
            return dto;
        });
    }

    @Test
    void getMyOrders_fullPage_returnsCursorAtTheLastRow() {
        when(authUtils.currentUserId()).thenReturn(OWNER_ID);
        // size 2: the repository is asked for 3 rows, and a 3rd one means there's more.
        when(orderRepository.findUserOrderKeyset(eq(OWNER_ID), isNull(), isNull(), eq(PageRequest.of(0, 3))))
                .thenReturn(List.of(row(30, "2026-03-03T00:00:00Z"), row(20, "2026-03-02T00:00:00Z"),
                        row(10, "2026-03-01T00:00:00Z")));
        // The IN query returns rows in any order; the slice keeps the keyset order.
        when(orderRepository.findWithItemsByIdIn(List.of(30L, 20L)))
                .thenReturn(List.of(orderWithId(20), orderWithId(30)));
        mapOrdersToDtosWithTheirIds();

        SlicedResponse<OrderDto> slice = orderService.getMyOrders(null, 2);

        assertThat(slice.content()).extracting(OrderDto::getId).containsExactly(30L, 20L);
        assertThat(slice.hasNext()).isTrue();
        assertThat(slice.numberOfElements()).isEqualTo(2);
        assertThat(OrderCursor.decode(slice.nextCursor()))
                .isEqualTo(new OrderCursor(Instant.parse("2026-03-02T00:00:00Z"), 20L));
    }

    @Test
    void getMyOrders_lastPage_hasNoCursor() {
        when(authUtils.currentUserId()).thenReturn(OWNER_ID);
        OrderCursor from = new OrderCursor(Instant.parse("2026-03-02T00:00:00Z"), 20L);
        when(orderRepository.findUserOrderKeyset(OWNER_ID, from.createdAt(), from.id(), PageRequest.of(0, 3)))
                .thenReturn(List.of(row(10, "2026-03-01T00:00:00Z")));
        when(orderRepository.findWithItemsByIdIn(List.of(10L))).thenReturn(List.of(orderWithId(10)));
        mapOrdersToDtosWithTheirIds();

        SlicedResponse<OrderDto> slice = orderService.getMyOrders(from.encode(), 2);

        assertThat(slice.content()).extracting(OrderDto::getId).containsExactly(10L);
        assertThat(slice.hasNext()).isFalse();
        assertThat(slice.nextCursor()).isNull();
    }

    @Test
    void getMyOrders_noOrders_isAnEmptySliceWithoutLoadingItems() {
        when(authUtils.currentUserId()).thenReturn(OWNER_ID);
        when(orderRepository.findUserOrderKeyset(eq(OWNER_ID), isNull(), isNull(), any())).thenReturn(List.of());

        SlicedResponse<OrderDto> slice = orderService.getMyOrders(null, 20);

        assertThat(slice.content()).isEmpty();
        assertThat(slice.hasNext()).isFalse();
        verify(orderRepository, never()).findWithItemsByIdIn(any());
    }

    @Test
    void getMyOrders_tamperedCursor_isRejected() {
        assertThatThrownBy(() -> orderService.getMyOrders("garbage!", 20))
                .isInstanceOf(InvalidCursorException.class);
    }

    // ---- single order -----------------------------------------------------

    @Test
    void getOrder_missing_is404NotAnOwnershipCheck() {
        when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(ORDER_ID)).isInstanceOf(ResourceNotFoundException.class);
        verify(authUtils, never()).requireSelfOrAdmin(any());
    }

    @Test
    void getOrder_checksTheOwnerAfterLoading() {
        OrderDto dto = new OrderDto();
        dto.setUserId(OWNER_ID);
        when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(order));
        when(modelMapper.map(order, OrderDto.class)).thenReturn(dto);
        doThrow(new AccessDeniedException("nope")).when(authUtils).requireSelfOrAdmin(OWNER_ID);

        assertThatThrownBy(() -> orderService.getOrder(ORDER_ID)).isInstanceOf(AccessDeniedException.class);
    }
}
