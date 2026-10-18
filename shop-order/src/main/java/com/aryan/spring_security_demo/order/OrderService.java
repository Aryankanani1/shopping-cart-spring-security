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
import com.aryan.spring_security_demo.identity.security.AuthUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService implements OrderServiceInterface{

    /** The states in which a paid order's items can be refunded. */
    private static final Set<OrderStatus> REFUNDABLE = EnumSet.of(
            OrderStatus.PAID, OrderStatus.PROCESSING, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private final OrderRepository orderRepository;
    private final ProductRepository  productRepository;
    private final CartService cartService;
    private final AuthUtils authUtils;
    private final Clock clock;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;
    private final OrderProperties properties;

    private final ModelMapper modelMapper;

    /** Every state an order can still be cancelled from, per the state machine. */
    private static final List<OrderStatus> CANCELLABLE = Arrays.stream(OrderStatus.values())
            .filter(status -> status.canTransitionTo(OrderStatus.CANCELLED))
            .toList();

    @Override
    @Transactional
    public OrderDto placeOrder(PlaceOrderRequest shippingAddress) {
        // Always the caller's own cart: the user comes from the token, never the request.
        // Locked first, so an item added while this runs is either ordered or
        // waits for the cart to be gone, never cleared away without being ordered.
        Long userId = authUtils.currentUserId();
        Cart cart = cartService.getCartByUserIdForUpdate(userId);
        // The cart is deleted after every order and recreated on the next
        // add-to-cart, so "no cart" and "empty cart" both mean nothing to buy.
        if (cart == null || cart.getCartItems().isEmpty()) {
            throw new EmptyCartException("Your cart is empty");
        }
        lockProducts(cart);
        requireStock(cart);
        Order order = careatOrder(cart);
        applyShippingAddress(order, shippingAddress);
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

    @Override
    @Transactional(readOnly = true)
    public Page<OrderSummaryDto> getAllOrders(Pageable pageable) {
        // Admin-only listing (gated at the edge in ShopConfig); returns lightweight
        // summaries straight from a projection query — no per-order authz needed
        // because an admin may see every order. Drop any client sort: the order is
        // fixed (newest first) by the query, and Spring Data would append a client's
        // sort to its ORDER BY, so sorting by a property orders lack was a 500.
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return orderRepository.findAllSummaries(page);
    }

    @Override
    @Transactional
    public OrderDto updateStatus(Long orderId, OrderStatus newStatus) {
        // Admin-only fulfillment action; the edge rule in ShopConfig already
        // requires ROLE_ADMIN for PATCH /orders/*/status, so no owner check here.
        if (newStatus == OrderStatus.PAID) {
            throw new InvalidOrderStateException("An order is marked paid only by a successful payment");
        }
        Order order = loadOrderWithItems(orderId);
        applyTransition(order, newStatus);
        return convertToDto(order);
    }

    @Override
    @Transactional
    public OrderDto cancelOrder(Long orderId) {
        Order order = loadOrderWithItems(orderId);
        // Owner or admin only. Checked after the fetch so a missing order stays a
        // 404 rather than a 403 that would confirm the id exists (IDOR). An order
        // whose account was deleted has no owner, which AuthUtils refuses to all.
        authUtils.requireSelfOrAdmin(order.getUser() == null ? null : order.getUser().getId());
        applyTransition(order, OrderStatus.CANCELLED);
        return convertToDto(order);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Order lockForPayment(Long orderId) {
        // Locked for the rest of the payment's first transaction, so two attempts
        // to pay for one order run one after the other.
        Order order = entityManager.find(Order.class, orderId, LockModeType.PESSIMISTIC_WRITE);
        if (order == null) {
            throw new ResourceNotFoundException("order not found!");
        }
        // After the fetch, so a missing order stays a 404 (as in getOrder).
        authUtils.requireSelfOrAdmin(order.getUser() == null ? null : order.getUser().getId());
        return order;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void startPayment(Order order) {
        // Only for a new attempt, not for a repeated request: a raised version
        // committed while an attempt is in flight would make recording its outcome
        // fail its version check, leaving a charged payment unrecorded.
        entityManager.lock(order, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markPaid(Long orderId) {
        // Read under a lock, so this works from the latest version of the order:
        // the money is already taken, and a version conflict here would leave the
        // payment unrecorded.
        Order order = entityManager.find(Order.class, orderId, LockModeType.PESSIMISTIC_WRITE);
        if (order == null) {
            throw new ResourceNotFoundException("order not found!");
        }
        applyTransition(order, OrderStatus.PAID);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> unpaidOrdersPlacedBefore(Instant placedBefore) {
        return orderRepository.findIdsByStatusCreatedBefore(OrderStatus.PENDING, placedBefore);
    }

    @Override
    @Transactional
    public boolean expireIfUnpaid(Long orderId, Instant placedBefore) {
        // No ownership check: the shop cancels it, not a user. Checked again here,
        // in this order's own transaction: it may have been paid since the scan.
        Order order = orderRepository.findByIdWithItems(orderId).orElse(null);
        if (order == null || order.getOrderStatus() != OrderStatus.PENDING
                || !order.getCreatedAt().isBefore(placedBefore)) {
            return false;
        }
        applyTransition(order, OrderStatus.CANCELLED);
        return true;
    }

    @Override
    @Transactional
    public void prepareForAccountDeletion(Long userId) {
        // No ownership check: the caller is deleting the account, which
        // UserService has already authorized.
        List<Order> open = orderRepository.findWithItemsByUserIdAndStatusIn(userId, CANCELLABLE);
        applyTransition(open, OrderStatus.CANCELLED);
        // The account is deleted later in this transaction, and Hibernate won't
        // flush a loaded order that still points at it. The database unlinks
        // the orders that were never loaded (V8, on delete set null).
        open.forEach(order -> order.setUser(null));
    }

    private void applyTransition(Order order, OrderStatus target) {
        applyTransition(List.of(order), target);
    }

    /**
     * Apply one lifecycle transition to managed orders: validate it for each
     * against the state machine, restock inventory when cancelling, then set the
     * new status. The orders are already managed, so the status change (and any
     * restock) flushes at the transaction boundary; {@code @Version} guards
     * concurrent changes to an order, surfacing as a 409 via the global handler.
     */
    private void applyTransition(List<Order> orders, OrderStatus target) {
        for (Order order : orders) {
            OrderStatus current = order.getOrderStatus();
            if (!current.canTransitionTo(target)) {
                throw new InvalidOrderStateException(
                        "Cannot change order status from " + current + " to " + target);
            }
        }
        if (target == OrderStatus.CANCELLED && !orders.isEmpty()) {
            // Before anything changes, so a listener can refuse (payments does
            // while an order is being paid for) and roll the cancellation back.
            events.publishEvent(new OrderCancellingEvent(orders.stream().map(Order::getId).toList()));
            lockProductsForRestock(orders);
            orders.forEach(this::restock);
        }
        orders.forEach(order -> order.setOrderStatus(target));
    }

    /**
     * Lock the products the orders return stock to, and re-read them under the
     * lock, before restocking. The orders were loaded with their products, unlocked,
     * so a checkout of the same product could commit in between; restocking from
     * that stale read failed Product's version check with a 409. With the lock, a
     * checkout and a cancellation of the same product run one after the other.
     *
     * <p>Each product once, in id order as checkout locks them, so the two can't
     * deadlock; and before any is restocked, since re-reading a product discards
     * changes made to it in this transaction.
     */
    private void lockProductsForRestock(List<Order> orders) {
        orders.stream()
                .flatMap(order -> order.getOrderItems().stream())
                .map(OrderItem::getProduct)
                .distinct()
                .sorted(Comparator.comparing(Product::getId))
                .forEach(product -> entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE));
    }

    /**
     * Return each item's quantity to product inventory when an order is cancelled:
     * all of it but what was refunded already, which a refund that restocked put
     * back then (and one that didn't treated as gone: damaged, or kept).
     */
    private void restock(Order order) {
        order.getOrderItems().forEach(item -> {
            Product product = item.getProduct();
            product.setInventory(product.getInventory() + item.refundableQuantity());
        });
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<RefundedItem> refundItems(Long orderId, Map<Long, Integer> quantities, boolean restock) {
        Order order = loadOrderWithItems(orderId);
        // Raises the order's version at commit, so a cancellation that read the order
        // before this fails its version check (409) instead of restocking these items
        // a second time. The caller locked the payment first, as a cancellation does,
        // so the two can't deadlock.
        entityManager.lock(order, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        if (!REFUNDABLE.contains(order.getOrderStatus())) {
            throw new InvalidOrderStateException(
                    "An order that is " + order.getOrderStatus() + " can't have items refunded");
        }
        Map<Long, OrderItem> items = order.getOrderItems().stream()
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));
        List<RefundedItem> refunded = new ArrayList<>();
        for (Map.Entry<Long, Integer> line : new TreeMap<>(quantities).entrySet()) {
            OrderItem item = items.get(line.getKey());
            if (item == null) {
                throw new BadRequestException("Invalid refund", "Order item " + line.getKey() + " isn't in order " + orderId);
            }
            item.refund(line.getValue());
            refunded.add(new RefundedItem(item.getId(), line.getValue(),
                    item.getPrice().multiply(BigDecimal.valueOf(line.getValue()))));
        }
        if (restock) {
            restockRefunded(refunded.stream().map(line -> items.get(line.orderItemId())).toList(), quantities);
        }
        return refunded;
    }

    /**
     * Put refunded items back in stock. The products are locked and re-read first, in
     * id order, as checkout and cancellation lock them, so none of the three can
     * deadlock with another or restock from a stale read.
     */
    private void restockRefunded(List<OrderItem> items, Map<Long, Integer> quantities) {
        items.stream()
                .map(OrderItem::getProduct)
                .distinct()
                .sorted(Comparator.comparing(Product::getId))
                .forEach(product -> entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE));
        items.forEach(item -> {
            Product product = item.getProduct();
            product.setInventory(product.getInventory() + quantities.get(item.getId()));
        });
    }

    private Order loadOrderWithItems(Long orderId) {
        return orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("order not found!"));
    }

    private Order careatOrder(Cart cart){

        Order order = new Order();
        //set the user
        order.setUser(cart.getUser());
        order.setOrderStatus(OrderStatus.PENDING);
        order.setLocalDate(LocalDate.now(clock));
        return order;

    }

    /** Copy the checkout address onto the order as a shipping snapshot. */
    private void applyShippingAddress(Order order, PlaceOrderRequest address){
        order.setRecipientName(address.getRecipientName());
        order.setAddressLine1(address.getAddressLine1());
        order.setAddressLine2(address.getAddressLine2());
        order.setCity(address.getCity());
        order.setState(address.getState());
        order.setPostalCode(address.getPostalCode());
        order.setCountry(address.getCountry());
    }

    /**
     * Lock the rows of the products in the cart until this checkout commits.
     * Checkouts that share a product then run one after the other: the next one
     * waits, reads the stock the previous one left, and buys from it or is told
     * there isn't enough. Without the lock they all read the same stock, and all
     * but one failed at commit with a 409 (a deadlock or Product's version
     * check), however much stock was left.
     *
     * <p>Must run before anything reads the products: a locking query returns a
     * product already loaded in this transaction as it was loaded, not re-read.
     * The cart items' products are still unloaded here; taking their ids doesn't
     * load them.
     */
    private void lockProducts(Cart cart) {
        List<Long> productIds = cart.getCartItems().stream()
                .map(item -> item.getProduct().getId())
                .toList();
        productRepository.findAllByIdForUpdate(productIds);
    }

    /**
     * Reject the order if any line asks for more than is in stock now — inventory
     * may have dropped since the item went into the cart. Every line is checked
     * before any is decremented, against the stock read under the lock taken by
     * {@link #lockProducts}, so two checkouts racing for the last unit cannot both
     * succeed: the later one sees it gone.
     */
    private void requireStock(Cart cart){
        for (CartItem item : cart.getCartItems()) {
            Product product = item.getProduct();
            if (item.getQuantity() > product.getInventory()) {
                throw new InsufficientStockException(product.getName(), product.getInventory());
            }
        }
    }

    private List<OrderItem> createOrderItems(Cart cart){
        //keeping track of the inventory by calculating the total price
        return cart.getCartItems().stream()
                .map(cartItem -> {
                    Product product = cartItem.getProduct();
                    product.setInventory(product.getInventory() - cartItem.getQuantity());
                    productRepository.save(product);
                    // Charge the price as of now, not the one stored when the item
                    // went into the cart. The order is wired in by
                    // Order.addOrderItem (owning side).
                    return
                           new OrderItem(product,
                                 cartItem.getQuantity(),
                                 product.getPrice());

                }).toList();
    }

    private BigDecimal calculateTotalAmount(List<OrderItem> orderItemList){
        return  orderItemList.stream()
                .map(item -> item.getPrice()
                        .multiply(new BigDecimal(item.getQuantity()))).reduce(BigDecimal.ZERO,BigDecimal::add);
    }

    @Override
    @Transactional(readOnly = true)
    public SlicedResponse<OrderDto> getMyOrders(String cursor, int size) {
        // Order history is private: always the caller's own, taken from the token.
        Long userId = authUtils.currentUserId();
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
        OrderDto dto = modelMapper.map(order,OrderDto.class);
        // When an unpaid order is cancelled by UnpaidOrderExpiryJob.
        if (order.getOrderStatus() == OrderStatus.PENDING && order.getCreatedAt() != null) {
            dto.setPayBy(order.getCreatedAt().plus(properties.getUnpaidExpiry()));
        }
        return dto;
    }
}
