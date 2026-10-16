package com.aryan.spring_security_demo.payment;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A payment provider simulated inside the app, so the project needs no account
 * with a real one and moves no real money. Like a provider's test mode, it
 * answers according to the payment method:
 *
 * <ul>
 *   <li>{@value #VISA}, {@value #MASTERCARD}: approved</li>
 *   <li>{@value #DECLINED}, {@value #INSUFFICIENT_FUNDS}: declined</li>
 *   <li>{@value #FLAKY}: unavailable on the first try of each charge, approved on the retry</li>
 *   <li>{@value #PROVIDER_DOWN}: unavailable every time</li>
 *   <li>{@value #REFUND_DOWN}: approved, but unavailable for the first
 *       {@value #REFUND_DOWN_CALLS} calls to refund it (a first try and its
 *       retries), so a later retry is what refunds it</li>
 *   <li>anything else: declined as unknown</li>
 * </ul>
 *
 * <p>Like a real provider, it keeps each answer under the request's idempotency
 * key, and a key it has seen gets the same answer again without charging or
 * refunding again. The answers live in memory, so they are forgotten on restart;
 * a refund of a charge it no longer knows is still refunded.
 */
@Component
public class FakePaymentGateway implements PaymentGateway {

    public static final String VISA = "test_visa";
    public static final String MASTERCARD = "test_mastercard";
    public static final String DECLINED = "test_declined";
    public static final String INSUFFICIENT_FUNDS = "test_insufficient_funds";
    public static final String FLAKY = "test_flaky";
    public static final String PROVIDER_DOWN = "test_provider_down";
    public static final String REFUND_DOWN = "test_refund_down";
    static final int REFUND_DOWN_CALLS = 3;

    private final Map<String, ChargeResult> answers = new ConcurrentHashMap<>();
    private final Set<String> flakyKeysTried = ConcurrentHashMap.newKeySet();
    private final Map<String, String> methodByCharge = new ConcurrentHashMap<>();
    private final Map<String, RefundResult> refunds = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> refundCalls = new ConcurrentHashMap<>();

    @Override
    public ChargeResult charge(ChargeRequest request) {
        String method = request.paymentMethod();
        if (PROVIDER_DOWN.equals(method)
                || (FLAKY.equals(method) && flakyKeysTried.add(request.idempotencyKey()))) {
            throw new PaymentProviderUnavailableException("The payment provider is unavailable");
        }
        ChargeResult result = answers.computeIfAbsent(request.idempotencyKey(), key -> answer(method));
        if (result instanceof ChargeResult.Approved approved) {
            methodByCharge.put(approved.providerReference(), method);
        }
        return result;
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        int call = refundCalls.computeIfAbsent(request.idempotencyKey(), key -> new AtomicInteger()).incrementAndGet();
        if (REFUND_DOWN.equals(methodByCharge.get(request.providerReference())) && call <= REFUND_DOWN_CALLS) {
            throw new PaymentProviderUnavailableException("The payment provider is unavailable");
        }
        return refunds.computeIfAbsent(request.idempotencyKey(),
                key -> new RefundResult("re_" + UUID.randomUUID().toString().replace("-", "")));
    }

    private static ChargeResult answer(String method) {
        return switch (method) {
            case VISA, FLAKY, REFUND_DOWN -> approved("Visa", "4242");
            case MASTERCARD -> approved("Mastercard", "4444");
            case DECLINED -> new ChargeResult.Declined("Your card was declined.");
            case INSUFFICIENT_FUNDS -> new ChargeResult.Declined("Your card has insufficient funds.");
            default -> new ChargeResult.Declined("Unknown payment method.");
        };
    }

    private static ChargeResult approved(String brand, String last4) {
        return new ChargeResult.Approved("ch_" + UUID.randomUUID().toString().replace("-", ""), brand, last4);
    }
}
