package com.aryan.spring_security_demo.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * The payment, locked until the transaction ends. Recording an outcome takes
     * this lock first, so recorders racing for one payment (its request,
     * reconciliation, a webhook, the refund job) take turns, and each sees what
     * the one before recorded.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    @Query("select p from Payment p where p.order.id = :orderId and p.idempotencyKey = :idempotencyKey")
    Optional<Payment> findByOrderAndKey(@Param("orderId") Long orderId,
                                        @Param("idempotencyKey") String idempotencyKey);

    @Query("select count(p) from Payment p where p.order.id = :orderId and p.status = :status")
    long countByOrderAndStatus(@Param("orderId") Long orderId, @Param("status") PaymentStatus status);

    /**
     * The orders' payments in {@code status}, row-locked in id order: a cancellation
     * reads what is left to refund under the same lock a partial refund takes, so
     * the two can't both count the same money.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.order.id in :orderIds and p.status = :status order by p.id")
    List<Payment> findByOrdersAndStatusForUpdate(@Param("orderIds") Collection<Long> orderIds,
                                                 @Param("status") PaymentStatus status);

    /**
     * An order's payment that hasn't failed (there is at most one, V11), row-locked:
     * a refund is added to it under this lock, so two at once take turns.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.order.id = :orderId"
            + " and p.status <> com.aryan.spring_security_demo.payment.PaymentStatus.FAILED")
    Optional<Payment> findLiveByOrderForUpdate(@Param("orderId") Long orderId);

    @Query("select p.id from Payment p where p.status = :status and p.createdAt < :before order by p.createdAt")
    List<Long> findIdsByStatusCreatedBefore(@Param("status") PaymentStatus status, @Param("before") Instant before);

    @Query("select count(p) from Payment p where p.order.id in :orderIds and p.status = :status")
    long countByOrdersAndStatus(@Param("orderIds") Collection<Long> orderIds, @Param("status") PaymentStatus status);

    /** An order's attempts, oldest first. */
    @Query("select p from Payment p where p.order.id = :orderId order by p.createdAt, p.id")
    List<Payment> findAllByOrder(@Param("orderId") Long orderId);

    @Query("select p from Payment p where p.id = :paymentId and p.order.id = :orderId")
    Optional<Payment> findByIdAndOrder(@Param("paymentId") Long paymentId, @Param("orderId") Long orderId);
}
