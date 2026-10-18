package com.aryan.spring_security_demo.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Settles payments still {@code PROCESSING}, whose outcome was lost: the provider
 * timed out, or the app stopped or failed before recording its answer. The
 * provider knows what happened to the charge sent under the payment's key, so
 * this asks it (with no transaction open) and records that.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentReconciler {

    static final String NEVER_RECEIVED = "The payment never reached the provider; nothing was charged";

    private final PaymentRecorder recorder;
    private final PaymentGatewayClient gateway;

    /**
     * Settle one payment from the provider's record of its charge: approved
     * (the order is paid), declined, or never received (nothing was charged).
     *
     * @return false if the provider is unavailable; the payment stays
     *         {@code PROCESSING} for the next run
     */
    public boolean reconcile(Long paymentId) {
        Optional<ChargeRequest> charge = recorder.processingCharge(paymentId);
        if (charge.isEmpty()) {
            return true;  // settled since, by its request or an earlier run
        }
        ChargeLookup lookup = gateway.findCharge(charge.get());
        if (lookup instanceof ChargeLookup.Unanswered unanswered) {
            // The client's fallback: the payment stays PROCESSING for the next run.
            log.warn("Reconciling payment {}: no answer from the provider ({}), will try again",
                    paymentId, unanswered.reason());
            return false;
        }
        if (lookup instanceof ChargeLookup.NeverReceived) {
            // Safe only because the attempt is old enough that a request which hasn't
            // reached the provider by now never will (see PaymentReconciliationJob).
            recorder.recordFailure(paymentId, NEVER_RECEIVED);
            return true;
        }
        ChargeResult answer = ((ChargeLookup.Found) lookup).result();
        if (answer instanceof ChargeResult.Approved approved) {
            recorder.recordSuccess(paymentId, approved);
        } else {
            recorder.recordFailure(paymentId, ((ChargeResult.Declined) answer).reason());
        }
        return true;
    }

    /**
     * Reconcile every payment still {@code PROCESSING} that started before
     * {@code startedBefore}. Each on its own: one that fails is logged and left, and
     * the others go on.
     *
     * @return how many are settled now
     */
    public int reconcileStuckPayments(Instant startedBefore) {
        int settled = 0;
        for (Long paymentId : recorder.stuckPayments(startedBefore)) {
            try {
                if (reconcile(paymentId)) {
                    settled++;
                }
            } catch (RuntimeException e) {
                log.warn("Reconciling payment {} failed, will try again", paymentId, e);
            }
        }
        return settled;
    }
}
