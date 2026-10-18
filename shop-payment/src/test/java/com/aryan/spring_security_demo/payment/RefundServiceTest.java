package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.order.Order;
import com.aryan.spring_security_demo.order.OrderServiceInterface;
import com.aryan.spring_security_demo.order.RefundedItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** An admin's partial refunds (docs/payment-design.md, section 11), with the order module and repositories mocked. */
@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-18T09:00:00Z");
    private static final Long ORDER_ID = 9L;
    private static final Long PAYMENT_ID = 7L;
    private static final Long ITEM_ID = 21L;
    private static final String ADMIN = "admin@example.com";

    @Mock private PaymentRepository paymentRepository;
    @Mock private RefundRepository refundRepository;
    @Mock private OrderServiceInterface orderService;
    @Mock private ApplicationEventPublisher events;
    @Spy private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @InjectMocks private RefundService refundService;

    private Payment payment;

    @BeforeEach
    void setUp() {
        Order order = new Order();
        order.setId(ORDER_ID);
        order.setTotalAmount(new BigDecimal("59.97"));
        payment = Payment.start(order, "key-12345", FakePaymentGateway.VISA, "USD", NOW);
        payment.succeed("ch_1", "Visa", "4242", NOW);
        ReflectionTestUtils.setField(payment, "id", PAYMENT_ID);
        lenient().when(paymentRepository.findLiveByOrderForUpdate(ORDER_ID)).thenReturn(Optional.of(payment));
        lenient().when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> {
            Refund refund = invocation.getArgument(0);
            ReflectionTestUtils.setField(refund, "id", 11L);
            return refund;
        });
    }

    @Test
    void anItemsRefund_isPricedByTheOrder_owed_andSentOnlyAfterTheCommit() {
        when(orderService.refundItems(ORDER_ID, Map.of(ITEM_ID, 1), true))
                .thenReturn(List.of(new RefundedItem(ITEM_ID, 1, new BigDecimal("19.99"))));

        RefundService.Outcome outcome = refundService.refund(ORDER_ID, "k1", items(true), ADMIN);

        assertThat(outcome.created()).isTrue();
        RefundDto refund = outcome.refund();
        assertThat(refund.kind()).isEqualTo(Refund.Kind.ITEMS);
        assertThat(refund.amount()).isEqualByComparingTo("19.99");
        assertThat(refund.status()).isEqualTo(Refund.Status.PENDING);
        assertThat(refund.items()).containsExactly(new RefundDto.RefundItemDto(ITEM_ID, 1, new BigDecimal("19.99")));
        assertThat(payment.getRefunds()).singleElement()
                .satisfies(owed -> assertThat(owed.getRequestedBy()).isEqualTo(ADMIN));
        verify(orderService).getOrder(ORDER_ID);
        verify(events).publishEvent(new RefundsRequestedEvent(List.of(11L)));
    }

    @Test
    void anAmountRefund_isOwedAsGiven_withoutTouchingTheItems() {
        RefundService.Outcome outcome = refundService.refund(ORDER_ID, "k1", amount("5.00"), ADMIN);

        assertThat(outcome.refund().kind()).isEqualTo(Refund.Kind.AMOUNT);
        assertThat(outcome.refund().amount()).isEqualByComparingTo("5.00");
        verify(orderService, never()).refundItems(anyLong(), anyMap(), anyBoolean());
    }

    // A double click: the second request gets the first refund, and nothing is refunded twice.
    @Test
    void aRepeatedRequest_getsTheFirstRefund_andRefundsNothingMore() {
        refundService.refund(ORDER_ID, "k1", amount("5.00"), ADMIN);

        RefundService.Outcome again = refundService.refund(ORDER_ID, "k1", amount("5.00"), ADMIN);

        assertThat(again.created()).isFalse();
        assertThat(payment.getRefunds()).hasSize(1);
        assertThat(payment.refundableAmount()).isEqualByComparingTo("54.97");
    }

    @Test
    void theSameKey_withAnotherRequest_isRefused() {
        refundService.refund(ORDER_ID, "k1", amount("5.00"), ADMIN);

        assertThatThrownBy(() -> refundService.refund(ORDER_ID, "k1", amount("6.00"), ADMIN))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void anUnpaidOrder_isRefused() {
        when(paymentRepository.findLiveByOrderForUpdate(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refundService.refund(ORDER_ID, "k1", amount("5.00"), ADMIN))
                .isInstanceOf(RefundNotPossibleException.class)
                .hasMessageContaining("isn't paid");
    }

    // Checked before any item is counted as refunded, so a refused refund leaves the items alone.
    @Test
    void aCancelledOrder_isRefused_beforeAnyItemIsCounted() {
        payment.refundTheRest(NOW);

        assertThatThrownBy(() -> refundService.refund(ORDER_ID, "k1", items(false), ADMIN))
                .isInstanceOf(RefundNotPossibleException.class)
                .hasMessageContaining("cancelled");
        verify(orderService, never()).refundItems(anyLong(), anyMap(), anyBoolean());
    }

    @Test
    void moreThanIsLeft_isRefused() {
        assertThatThrownBy(() -> refundService.refund(ORDER_ID, "k1", amount("60.00"), ADMIN))
                .isInstanceOf(RefundNotPossibleException.class)
                .hasMessageContaining("Only 59.97 USD is left");
    }

    private static RefundOrderRequest items(boolean restock) {
        RefundOrderRequest.RefundLine line = new RefundOrderRequest.RefundLine();
        line.setOrderItemId(ITEM_ID);
        line.setQuantity(1);
        RefundOrderRequest request = new RefundOrderRequest();
        request.setItems(List.of(line));
        request.setRestock(restock);
        request.setReason("Returned: wrong size");
        return request;
    }

    private static RefundOrderRequest amount(String amount) {
        RefundOrderRequest request = new RefundOrderRequest();
        request.setAmount(new BigDecimal(amount));
        request.setReason("The box arrived damaged");
        return request;
    }
}
