package com.aryan.spring_security_demo.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    /**
     * The refund with this key with the provider, and its payment: how a refund
     * webhook finds them. Ids only, so the refund isn't loaded before its payment's
     * lock, which reads it as it is now (PaymentRepository).
     */
    @Query("select new com.aryan.spring_security_demo.payment.RefundRepository$RefundOf(r.id, r.payment.id)"
            + " from Refund r where r.idempotencyKey = :idempotencyKey")
    Optional<RefundOf> findRefundOf(@Param("idempotencyKey") String idempotencyKey);

    /** A refund's id and its payment's. */
    record RefundOf(Long refundId, Long paymentId) {
    }

    /** The refund, locked until the transaction ends: read as committed now, as it is recorded. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Refund r where r.id = :refundId")
    Optional<Refund> findByIdForUpdate(@Param("refundId") Long refundId);

    @Query("select r.payment.id from Refund r where r.id = :refundId")
    Optional<Long> findPaymentIdById(@Param("refundId") Long refundId);

    /** What the retry job retries: refunds owed since before {@code requestedBefore}, oldest first. */
    @Query("select r.id from Refund r where r.status = :status and r.requestedAt < :requestedBefore"
            + " order by r.requestedAt, r.id")
    List<Long> findIdsByStatusRequestedBefore(@Param("status") Refund.Status status,
                                              @Param("requestedBefore") Instant requestedBefore);

    /** An order's refunds, oldest first, with their items. */
    @Query("select distinct r from Refund r left join fetch r.items where r.payment.order.id = :orderId"
            + " order by r.requestedAt, r.id")
    List<Refund> findAllByOrder(@Param("orderId") Long orderId);
}
