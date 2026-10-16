package com.aryan.spring_security_demo.payment;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
 *   <li>anything else: declined as unknown</li>
 * </ul>
 *
 * <p>Like a real provider, it keeps each answer under the charge's idempotency
 * key, and a key it has seen gets the same answer again without a new charge.
 * The answers live in memory, so they are forgotten on restart.
 */
@Component
public class FakePaymentGateway implements PaymentGateway {

    public static final String VISA = "test_visa";
    public static final String MASTERCARD = "test_mastercard";
    public static final String DECLINED = "test_declined";
    public static final String INSUFFICIENT_FUNDS = "test_insufficient_funds";
    public static final String FLAKY = "test_flaky";
    public static final String PROVIDER_DOWN = "test_provider_down";

    private final Map<String, ChargeResult> answers = new ConcurrentHashMap<>();
    private final Set<String> flakyKeysTried = ConcurrentHashMap.newKeySet();

    @Override
    public ChargeResult charge(ChargeRequest request) {
        String method = request.paymentMethod();
        if (PROVIDER_DOWN.equals(method)
                || (FLAKY.equals(method) && flakyKeysTried.add(request.idempotencyKey()))) {
            throw new PaymentProviderUnavailableException("The payment provider is unavailable");
        }
        return answers.computeIfAbsent(request.idempotencyKey(), key -> answer(method));
    }

    private static ChargeResult answer(String method) {
        return switch (method) {
            case VISA, FLAKY -> approved("Visa", "4242");
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
