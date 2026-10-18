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
import java.security.Principal;
import java.util.List;

/**
 * An order's refunds (docs/payment-design.md, section 11). Refunding is for admins
 * ({@code ShopConfig}); the order's owner and admins can see them.
 */
@RestController
@RequestMapping("${api.prefix}/orders/{orderId}/refunds")
@RequiredArgsConstructor
@Validated
public class RefundController {

    private final RefundService refundService;

    /**
     * Refund part of a paid order: items or an amount, with a reason. 201 with the
     * refund, {@code PENDING} until the provider has refunded it; 200 with the first
     * one when the {@code Idempotency-Key} was used for this order before; 409 when
     * the order can't be refunded, or for more than is left.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<RefundDto>> refund(
            @PathVariable Long orderId,
            @RequestHeader("Idempotency-Key")
            @Pattern(regexp = PaymentController.IDEMPOTENCY_KEY,
                    message = PaymentController.IDEMPOTENCY_KEY_MESSAGE) String idempotencyKey,
            @Valid @RequestBody RefundOrderRequest request,
            Principal admin) {
        RefundService.Outcome outcome = refundService.refund(orderId, idempotencyKey, request, admin.getName());
        RefundDto refund = outcome.refund();
        if (!outcome.created()) {
            return ResponseEntity.ok(new ApiResponse<>("Refund already requested", refund));
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{refundId}").buildAndExpand(refund.id()).toUri();
        return ResponseEntity.created(location).body(new ApiResponse<>("Refund requested", refund));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RefundDto>>> getRefunds(@PathVariable Long orderId) {
        return ResponseEntity.ok(new ApiResponse<>("success!", refundService.getRefunds(orderId)));
    }

    @GetMapping("/{refundId}")
    public ResponseEntity<ApiResponse<RefundDto>> getRefund(@PathVariable Long orderId, @PathVariable Long refundId) {
        return ResponseEntity.ok(new ApiResponse<>("success!", refundService.getRefund(orderId, refundId)));
    }
}
