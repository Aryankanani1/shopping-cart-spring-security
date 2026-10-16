package com.aryan.spring_security_demo.payment;

import com.aryan.spring_security_demo.common.web.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * An order's payments. Only the order's owner or an admin gets past the service's
 * ownership check; whoever is paying comes from the token, never the request.
 */
@RestController
@RequestMapping("${api.prefix}/orders/{orderId}/payments")
@RequiredArgsConstructor
@Validated
public class PaymentController {

    /** Long enough to be unique (the storefront sends a UUID), short enough to store. */
    private static final String IDEMPOTENCY_KEY = "[A-Za-z0-9_-]{8,64}";

    private final PaymentServiceInterface paymentService;

    /**
     * Pay for the order. {@code Idempotency-Key} names this attempt: sending the
     * same request again with it (after a timeout, say) returns the first answer
     * with 200 instead of paying twice. A new attempt is 201, with status
     * {@code SUCCEEDED} or, when declined, {@code FAILED} and the reason.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentDto>> pay(
            @PathVariable Long orderId,
            @RequestHeader("Idempotency-Key")
            @Pattern(regexp = IDEMPOTENCY_KEY,
                    message = "Idempotency-Key must be 8 to 64 letters, digits, '-' or '_'") String idempotencyKey,
            @Valid @RequestBody PayOrderRequest request) {
        PaymentServiceInterface.PayResult result = paymentService.pay(orderId, idempotencyKey, request.getPaymentMethod());
        PaymentDto payment = result.payment();
        ApiResponse<PaymentDto> body = new ApiResponse<>(message(payment), payment);
        if (!result.created()) {
            return ResponseEntity.ok(body);
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{paymentId}").buildAndExpand(payment.id()).toUri();
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentDto>>> getPayments(@PathVariable Long orderId) {
        return ResponseEntity.ok(new ApiResponse<>("success!", paymentService.getPayments(orderId)));
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentDto>> getPayment(@PathVariable Long orderId, @PathVariable Long paymentId) {
        return ResponseEntity.ok(new ApiResponse<>("success!", paymentService.getPayment(orderId, paymentId)));
    }

    private static String message(PaymentDto payment) {
        return switch (payment.status()) {
            case SUCCEEDED -> "Payment succeeded";
            case FAILED -> "Payment failed";
            case PROCESSING -> "Payment is being processed";
        };
    }
}
