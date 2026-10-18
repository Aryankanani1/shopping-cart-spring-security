package com.aryan.spring_security_demo.order;

import com.aryan.spring_security_demo.cart.Cart;
import com.aryan.spring_security_demo.cart.CartItem;
import com.aryan.spring_security_demo.cart.CartService;
import com.aryan.spring_security_demo.catalog.InsufficientStockException;
import com.aryan.spring_security_demo.catalog.Product;
import com.aryan.spring_security_demo.catalog.ProductRepository;
import com.aryan.spring_security_demo.common.exception.BadRequestException;
import com.aryan.spring_security_demo.common.exception.ResourceNotFoundException;
import com.aryan.spring_security_demo.common.web.SlicedResponse;
import com.aryan.spring_security_demo.identity.User;
import com.aryan.spring_security_demo.identity.security.AuthUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock private EntityManager entityManager;
    @Mock private ApplicationEventPublisher events;
    @Spy private OrderProperties properties = new OrderProperties();
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
        order.setOrderStatus(OrderStatus.PENDING);
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

    // Regression (found by the staging fuzzing): Spring Data appended a client's sort
    // to the query's own ORDER BY, so ?sort=AAA was a 500.
    @Test
    void getAllOrders_dropsTheClientsSort_keepingThePage() {
        when(orderRepository.findAllSummaries(any())).thenReturn(Page.empty());

        orderService.getAllOrders(PageRequest.of(2, 15, Sort.by("AAA")));

        ArgumentCaptor<Pageable> asked = ArgumentCaptor.forClass(Pageable.class);
        verify(orderRepository).findAllSummaries(asked.capture());
        assertThat(asked.getValue().getSort().isSorted()).isFalse();
        assertThat(asked.getValue().getPageNumber()).isEqualTo(2);
        assertThat(asked.getValue().getPageSize()).isEqualTo(15);
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
        when(cartService.getCartByUserIdForUpdate(OWNER_ID)).thenReturn(cart);
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
        when(cartService.getCartByUserIdForUpdate(OWNER_ID)).thenReturn(null);

        assertThatThrownBy(() -> orderService.placeOrder(new PlaceOrderRequest()))
                .isInstanceOf(EmptyCartException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrder_withEmptyCart_isRejected() {
        Cart cart = new Cart();
        cart.setId(99L);
        when(authUtils.currentUserId()).thenReturn(OWNER_ID);
        when(cartService.getCartByUserIdForUpdate(OWNER_ID)).thenReturn(cart);

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
        when(cartService.getCartByUserIdForUpdate(OWNER_ID)).thenReturn(cart);

        assertThatThrownBy(() -> orderService.placeOrder(new PlaceOrderRequest()))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(product.getInventory()).isEqualTo(5);
        verify(orderRepository, never()).save(any());
        verify(cartService, never()).clearCart(any());
    }

    // Regression: checkouts sharing a product read its stock unlocked, so all but
    // one of them failed at commit (deadlock or version conflict) with a 409.
    // Concurrent checkouts are in CheckoutJourneyIntegrationTest.
    @Test
    void placeOrder_locksEveryProductInTheCart_beforeCheckingStock() {
        Product hub = new Product();
        hub.setId(8L);
        hub.setInventory(3);
        Cart cart = new Cart();
        cart.setId(99L);
        cart.getCartItems().add(cartItem(product, 6));  // more than the 5 in stock
        cart.getCartItems().add(cartItem(hub, 1));
        when(authUtils.currentUserId()).thenReturn(OWNER_ID);
        when(cartService.getCartByUserIdForUpdate(OWNER_ID)).thenReturn(cart);

        assertThatThrownBy(() -> orderService.placeOrder(new PlaceOrderRequest()))
                .isInstanceOf(InsufficientStockException.class);

        // Locked even though the stock check then failed: the lock comes first.
        verify(productRepository).findAllByIdForUpdate(argThat(ids ->
                ids.size() == 2 && ids.containsAll(List.of(7L, 8L))));
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
    void cancelOrder_tellsOtherModules_beforeRestocking() {
        when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(order));

        orderService.cancelOrder(ORDER_ID);

        InOrder steps = inOrder(events, entityManager);
        steps.verify(events).publishEvent(new OrderCancellingEvent(List.of(ORDER_ID)));
        steps.verify(entityManager).refresh(product, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void cancelOrder_refusedByAListener_changesNothing() {
        when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(order));
        doThrow(new InvalidOrderStateException("being paid for"))
                .when(events).publishEvent(any(OrderCancellingEvent.class));

        assertThatThrownBy(() -> orderService.cancelOrder(ORDER_ID))
                .isInstanceOf(InvalidOrderStateException.class);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(product.getInventory()).isEqualTo(5);
    }

    // A paid order can be cancelled; the event lets the payment module refund it.
    @Test
    void cancelOrder_oncePaid_isCancelled_andOtherModulesAreTold() {
        order.setOrderStatus(OrderStatus.PAID);
        when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(order));

        orderService.cancelOrder(ORDER_ID);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(product.getInventory()).isEqualTo(7);
        verify(events).publishEvent(new OrderCancellingEvent(List.of(ORDER_ID)));
    }

    @Test
    void cancelOrder_onceShipped_isRejectedAndInventoryUntouched() {
        order.setOrderStatus(OrderStatus.SHIPPED);
        when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(ORDER_ID))
                .isInstanceOf(InvalidOrderStateException.class);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(product.getInventory()).isEqualTo(5); // no restock on a rejected cancel
        verify(entityManager, never()).refresh(any(), any(LockModeType.class));  // nor a lock
        verifyNoInteractions(events);
    }

    // Regression: the restock used the stock as loaded with the order, unlocked, so a
    // checkout of the same product committing meanwhile made the cancellation fail
    // Product's version check (a 409). The race itself is in CheckoutJourneyIntegrationTest.
    @Test
    void cancelOrder_restocksFromTheStockReReadUnderALock() {
        when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(order));
        doAnswer(inv -> {
            product.setInventory(3);  // what the database holds now: 2 more were sold
            return null;
        }).when(entityManager).refresh(product, LockModeType.PESSIMISTIC_WRITE);

        orderService.cancelOrder(ORDER_ID);

        assertThat(product.getInventory()).isEqualTo(5); // 3 + the 2 cancelled units
    }

    @Test
    void prepareForAccountDeletion_locksEachProductOnce_inIdOrder_beforeAnyRestock() {
        Product lamp = new Product();
        lamp.setId(3L);
        lamp.setInventory(1);
        Order second = new Order();
        second.setUser(order.getUser());
        second.setOrderStatus(OrderStatus.PENDING);
        second.addOrderItem(new OrderItem(product, 1, BigDecimal.TEN));  // product 7 again
        second.addOrderItem(new OrderItem(lamp, 4, BigDecimal.TEN));
        when(orderRepository.findWithItemsByUserIdAndStatusIn(
                OWNER_ID, List.of(OrderStatus.PENDING, OrderStatus.PAID, OrderStatus.PROCESSING)))
                .thenReturn(List.of(order, second));

        orderService.prepareForAccountDeletion(OWNER_ID);

        InOrder locks = inOrder(entityManager);
        locks.verify(entityManager).refresh(lamp, LockModeType.PESSIMISTIC_WRITE);
        locks.verify(entityManager).refresh(product, LockModeType.PESSIMISTIC_WRITE);
        locks.verifyNoMoreInteractions();
        assertThat(product.getInventory()).isEqualTo(8); // 5 + 2 + 1, both restocks kept
        assertThat(lamp.getInventory()).isEqualTo(5);    // 1 + 4
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
                OWNER_ID, List.of(OrderStatus.PENDING, OrderStatus.PAID, OrderStatus.PROCESSING)))
                .thenReturn(List.of(order));

        orderService.prepareForAccountDeletion(OWNER_ID);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(product.getInventory()).isEqualTo(7); // 5 + the 2 cancelled units
        assertThat(order.getUser()).isNull();
    }

    @Test
    void prepareForAccountDeletion_withNoOpenOrders_tellsNoOne() {
        when(orderRepository.findWithItemsByUserIdAndStatusIn(OWNER_ID, List.of(OrderStatus.PENDING, OrderStatus.PAID, OrderStatus.PROCESSING)))
                .thenReturn(List.of());

        orderService.prepareForAccountDeletion(OWNER_ID);

        verifyNoInteractions(events);
    }

    // ---- unpaid order expiry ----------------------------------------------

    private static final Instant PLACED = Instant.parse("2026-03-14T12:00:00Z");
    private static final Instant CUTOFF = PLACED.plusSeconds(60);

    @Test
    void unpaidOrdersPlacedBefore_asksForPendingOnes() {
        when(orderRepository.findIdsByStatusCreatedBefore(OrderStatus.PENDING, CUTOFF)).thenReturn(List.of(ORDER_ID));

        assertThat(orderService.unpaidOrdersPlacedBefore(CUTOFF)).containsExactly(ORDER_ID);
    }

    @Test
    void expireIfUnpaid_cancelsAnOldUnpaidOrder_likeItsOwnerWould() {
        order.setCreatedAt(PLACED);
        when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(order));

        assertThat(orderService.expireIfUnpaid(ORDER_ID, CUTOFF)).isTrue();

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(product.getInventory()).isEqualTo(7);  // back on sale
        verify(events).publishEvent(new OrderCancellingEvent(List.of(ORDER_ID)));
        verifyNoInteractions(authUtils);  // the shop cancels it, not a user
    }

    // Checked again in the order's own transaction: it may have changed since the scan.
    @Test
    void expireIfUnpaid_anOrderPaidSinceTheScan_isLeftAlone() {
        order.setCreatedAt(PLACED);
        order.setOrderStatus(OrderStatus.PAID);
        when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(order));

        assertThat(orderService.expireIfUnpaid(ORDER_ID, CUTOFF)).isFalse();

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PAID);
        verifyNoInteractions(events);
    }

    @Test
    void expireIfUnpaid_anOrderPlacedAfterTheCutoff_isLeftAlone() {
        order.setCreatedAt(CUTOFF);
        when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(order));

        assertThat(orderService.expireIfUnpaid(ORDER_ID, CUTOFF)).isFalse();

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void expireIfUnpaid_aMissingOrder_isLeftAlone() {
        when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.empty());

        assertThat(orderService.expireIfUnpaid(ORDER_ID, CUTOFF)).isFalse();
    }

    @Test
    void anUnpaidOrder_saysWhenItMustBePaidBy() {
        order.setCreatedAt(PLACED);
        when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(order));

        assertThat(orderService.getOrder(ORDER_ID).getPayBy()).isEqualTo(PLACED.plus(Duration.ofMinutes(30)));
    }

    @Test
    void aPaidOrder_hasNoPayBy() {
        order.setCreatedAt(PLACED);
        order.setOrderStatus(OrderStatus.PAID);
        when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(order));

        assertThat(orderService.getOrder(ORDER_ID).getPayBy()).isNull();
    }

    // ---- payment hooks ----------------------------------------------------

    @Test
    void lockForPayment_locksTheOrder_thenChecksTheOwner() {
        when(entityManager.find(Order.class, ORDER_ID, LockModeType.PESSIMISTIC_WRITE)).thenReturn(order);

        assertThat(orderService.lockForPayment(ORDER_ID)).isSameAs(order);

        verify(authUtils).requireSelfOrAdmin(OWNER_ID);
        verify(entityManager, never()).lock(any(), any(LockModeType.class));  // the version stays as it is
    }

    @Test
    void startPayment_raisesTheOrdersVersion() {
        orderService.startPayment(order);

        verify(entityManager).lock(order, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
    }

    @Test
    void lockForPayment_someoneElsesOrder_isRefused() {
        when(entityManager.find(Order.class, ORDER_ID, LockModeType.PESSIMISTIC_WRITE)).thenReturn(order);
        doThrow(new AccessDeniedException("not yours")).when(authUtils).requireSelfOrAdmin(OWNER_ID);

        assertThatThrownBy(() -> orderService.lockForPayment(ORDER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void lockForPayment_missingOrder_is404() {
        assertThatThrownBy(() -> orderService.lockForPayment(ORDER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(authUtils);
    }

    @Test
    void lockForPayment_ofADeletedAccount_isCheckedAsHavingNoOwner() {
        order.setUser(null);
        when(entityManager.find(Order.class, ORDER_ID, LockModeType.PESSIMISTIC_WRITE)).thenReturn(order);

        orderService.lockForPayment(ORDER_ID);

        verify(authUtils).requireSelfOrAdmin(null);
    }

    @Test
    void markPaid_movesAPendingOrderToPaid_readUnderALock() {
        when(entityManager.find(Order.class, ORDER_ID, LockModeType.PESSIMISTIC_WRITE)).thenReturn(order);

        orderService.markPaid(ORDER_ID);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(product.getInventory()).isEqualTo(5);  // stock stays reserved
    }

    @Test
    void markPaid_aCancelledOrder_isRejected() {
        order.setOrderStatus(OrderStatus.CANCELLED);
        when(entityManager.find(Order.class, ORDER_ID, LockModeType.PESSIMISTIC_WRITE)).thenReturn(order);

        assertThatThrownBy(() -> orderService.markPaid(ORDER_ID))
                .isInstanceOf(InvalidOrderStateException.class);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void markPaid_missingOrder_is404() {
        assertThatThrownBy(() -> orderService.markPaid(ORDER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStatus_forwardTransition_advancesWithoutRestocking() {
        order.setOrderStatus(OrderStatus.PROCESSING);
        when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(order));

        orderService.updateStatus(ORDER_ID, OrderStatus.SHIPPED);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(product.getInventory()).isEqualTo(5); // only cancellation restocks
        verifyNoInteractions(entityManager);              // and locks
    }

    @Test
    void updateStatus_illegalTransition_isRejected() {
        order.setOrderStatus(OrderStatus.DELIVERED);
        when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateStatus(ORDER_ID, OrderStatus.PROCESSING))
                .isInstanceOf(InvalidOrderStateException.class);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    // Only a successful payment marks an order paid, never an admin by hand.
    @Test
    void updateStatus_toPaid_isRejected() {
        assertThatThrownBy(() -> orderService.updateStatus(ORDER_ID, OrderStatus.PAID))
                .isInstanceOf(InvalidOrderStateException.class);

        verifyNoInteractions(orderRepository);
    }

    @Test
    void updateStatus_unpaidOrder_cannotBeFulfilled() {
        when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateStatus(ORDER_ID, OrderStatus.PROCESSING))
                .isInstanceOf(InvalidOrderStateException.class);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
    }

    // ---- order history (keyset paging) ------------------------------------

    private static OrderKeysetRow row(long id, String createdAt) {
        return new OrderKeysetRow() {
            @Override public Long getId() { return id; }
            @Override public Instant getCreatedAt() { return Instant.parse(createdAt); }
        };
    }

    // --- refunding items (docs/payment-design.md, section 11) -----------------

    private static final long ITEM_ID = 21L;

    /** The order paid for, with its one item (2 at 10) saved under ITEM_ID. */
    private OrderItem paidOrderItem() {
        order.setOrderStatus(OrderStatus.PAID);
        OrderItem item = order.getOrderItems().iterator().next();
        item.setId(ITEM_ID);
        when(orderRepository.findByIdWithItems(ORDER_ID)).thenReturn(Optional.of(order));
        return item;
    }

    @Test
    void refundItems_countsThemRefunded_pricedAtWhatWasPaid_andRaisesTheOrdersVersion() {
        OrderItem item = paidOrderItem();

        List<RefundedItem> refunded = orderService.refundItems(ORDER_ID, Map.of(ITEM_ID, 1), false);

        assertThat(refunded).containsExactly(new RefundedItem(ITEM_ID, 1, BigDecimal.TEN));
        assertThat(item.getRefundedQuantity()).isEqualTo(1);
        assertThat(product.getInventory()).as("not restocked").isEqualTo(5);
        // A cancellation that read the order before this fails its version check.
        verify(entityManager).lock(order, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
    }

    @Test
    void refundItems_withRestock_putsThemBackInStock_afterLockingTheProduct() {
        paidOrderItem();

        orderService.refundItems(ORDER_ID, Map.of(ITEM_ID, 2), true);

        assertThat(product.getInventory()).isEqualTo(7);
        verify(entityManager).refresh(product, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void refundItems_neverMoreThanWereBought() {
        paidOrderItem();
        orderService.refundItems(ORDER_ID, Map.of(ITEM_ID, 2), false);

        assertThatThrownBy(() -> orderService.refundItems(ORDER_ID, Map.of(ITEM_ID, 1), false))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("Only 0");
    }

    @Test
    void refundItems_anItemNotInTheOrder_isABadRequest() {
        paidOrderItem();

        assertThatThrownBy(() -> orderService.refundItems(ORDER_ID, Map.of(99L, 1), false))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void refundItems_ofAnOrderUnpaid_orCancelled_isRefused() {
        paidOrderItem();
        for (OrderStatus status : new OrderStatus[] {OrderStatus.PENDING, OrderStatus.CANCELLED}) {
            order.setOrderStatus(status);

            assertThatThrownBy(() -> orderService.refundItems(ORDER_ID, Map.of(ITEM_ID, 1), false))
                    .as(status.name())
                    .isInstanceOf(InvalidOrderStateException.class);
        }
    }

    // Refunded items went back (or were written off) when they were refunded.
    @Test
    void cancelOrder_afterItemsWereRefunded_restocksOnlyTheRest() {
        OrderItem item = paidOrderItem();
        item.setRefundedQuantity(1);

        orderService.cancelOrder(ORDER_ID);

        assertThat(product.getInventory()).isEqualTo(6);  // 5 + the 1 unit not refunded
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

    private static CartItem cartItem(Product product, int quantity) {
        CartItem item = new CartItem();
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setUnitPrice(BigDecimal.TEN);
        return item;
    }
}
