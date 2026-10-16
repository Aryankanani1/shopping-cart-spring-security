package com.aryan.spring_security_demo.payment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
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
 *   <li>{@value #TIMEOUT_CHARGED}: charged, but the answer never comes back
 *       (a timeout); {@link #findCharge} then reports it approved</li>
 *   <li>{@value #TIMEOUT_NOT_CHARGED}: a timeout before the request reached the
 *       provider; {@link #findCharge} then reports no charge</li>
 *   <li>{@value #REFUND_DOWN}: approved, but unavailable for the first
 *       {@value #REFUND_DOWN_CALLS} calls to refund it (a first try and its
 *       retries), so a later retry is what refunds it</li>
 *   <li>anything else: declined as unknown</li>
 * </ul>
 *
 * <p>Like a real provider, it keeps each answer under the request's idempotency
 * key, and a key it has seen gets the same answer again without charging or
 * refunding again. The answers live in memory, so they are forgotten on restart;
 * a refund of a charge it no longer knows is still refunded. Each new charge or
 * refund is also reported once by webhook ({@link FakeWebhookSender}), when those
 * are on.
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
    public static final String TIMEOUT_CHARGED = "test_timeout_charged";
    public static final String TIMEOUT_NOT_CHARGED = "test_timeout_not_charged";
    static final int REFUND_DOWN_CALLS = 3;

    private final Map<String, ChargeResult> answers = new ConcurrentHashMap<>();
    private final Set<String> flakyKeysTried = ConcurrentHashMap.newKeySet();
    private final Map<String, String> methodByCharge = new ConcurrentHashMap<>();
    private final Map<String, RefundResult> refunds = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> refundCalls = new ConcurrentHashMap<>();
    private final FakeWebhookSender webhooks;
    private final PaymentProperties properties;
    private final Sleeper sleeper;

    /** How a simulated round trip waits; tests pass one that only records it. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    @Autowired
    public FakePaymentGateway(FakeWebhookSender webhooks, PaymentProperties properties) {
        this(webhooks, properties, duration -> Thread.sleep(duration.toMillis()));
    }

    FakePaymentGateway(FakeWebhookSender webhooks, PaymentProperties properties, Sleeper sleeper) {
        this.webhooks = webhooks;
        this.properties = properties;
        this.sleeper = sleeper;
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        roundTrip();
        String method = request.paymentMethod();
        if (PROVIDER_DOWN.equals(method)
                || (FLAKY.equals(method) && flakyKeysTried.add(request.idempotencyKey()))) {
            throw new PaymentProviderUnavailableException("The payment provider is unavailable");
        }
        if (TIMEOUT_NOT_CHARGED.equals(method)) {
            throw new PaymentOutcomeUnknownException("The payment provider didn't answer in time");
        }
        ChargeResult answer = answer(method);
        ChargeResult earlier = answers.putIfAbsent(request.idempotencyKey(), answer);
        ChargeResult result = earlier == null ? answer : earlier;
        if (earlier == null) {
            if (result instanceof ChargeResult.Approved approved) {
                methodByCharge.put(approved.providerReference(), method);
            }
            announce(request.idempotencyKey(), result);
        }
        if (TIMEOUT_CHARGED.equals(method)) {
            throw new PaymentOutcomeUnknownException("The payment provider didn't answer in time");
        }
        return result;
    }

    @Override
    public Optional<ChargeResult> findCharge(String idempotencyKey) {
        roundTrip();
        return Optional.ofNullable(answers.get(idempotencyKey));
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        roundTrip();
        int call = refundCalls.computeIfAbsent(request.idempotencyKey(), key -> new AtomicInteger()).incrementAndGet();
        if (REFUND_DOWN.equals(methodByCharge.get(request.providerReference())) && call <= REFUND_DOWN_CALLS) {
            throw new PaymentProviderUnavailableException("The payment provider is unavailable");
        }
        RefundResult refund = new RefundResult("re_" + UUID.randomUUID().toString().replace("-", ""));
        RefundResult earlier = refunds.putIfAbsent(request.idempotencyKey(), refund);
        if (earlier != null) {
            return earlier;
        }
        webhooks.send(WebhookEvent.REFUND_SUCCEEDED, new WebhookEvent.Data(request.idempotencyKey(),
                request.providerReference(), null, null, null, refund.refundReference()));
        return refund;
    }

    /** Waits as long as a real provider's network round trip would ({@code app.payment.fake.latency}). */
    private void roundTrip() {
        Duration latency = properties.getFake().getLatency();
        if (latency.isZero()) {
            return;
        }
        try {
            sleeper.sleep(latency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentProviderUnavailableException("Interrupted while waiting for the payment provider");
        }
    }

    /** Report a new charge's outcome by webhook, as a real provider does. */
    private void announce(String idempotencyKey, ChargeResult result) {
        if (result instanceof ChargeResult.Approved approved) {
            webhooks.send(WebhookEvent.CHARGE_SUCCEEDED, new WebhookEvent.Data(idempotencyKey,
                    approved.providerReference(), approved.cardBrand(), approved.cardLast4(), null, null));
        } else {
            webhooks.send(WebhookEvent.CHARGE_FAILED, new WebhookEvent.Data(idempotencyKey,
                    null, null, null, ((ChargeResult.Declined) result).reason(), null));
        }
    }

    private static ChargeResult answer(String method) {
        return switch (method) {
            case VISA, FLAKY, REFUND_DOWN, TIMEOUT_CHARGED -> approved("Visa", "4242");
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
