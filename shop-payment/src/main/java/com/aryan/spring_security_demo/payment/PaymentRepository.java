package com.aryan.spring_security_demo.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("select p from Payment p where p.order.id = :orderId and p.idempotencyKey = :idempotencyKey")
    Optional<Payment> findByOrderAndKey(@Param("orderId") Long orderId,
                                        @Param("idempotencyKey") String idempotencyKey);

    @Query("select count(p) from Payment p where p.order.id = :orderId and p.status = :status")
    long countByOrderAndStatus(@Param("orderId") Long orderId, @Param("status") PaymentStatus status);

    @Query("select count(p) from Payment p where p.order.id in :orderIds and p.status = :status")
    long countByOrdersAndStatus(@Param("orderIds") Collection<Long> orderIds, @Param("status") PaymentStatus status);

    /** An order's attempts, oldest first. */
    @Query("select p from Payment p where p.order.id = :orderId order by p.createdAt, p.id")
    List<Payment> findAllByOrder(@Param("orderId") Long orderId);

    @Query("select p from Payment p where p.id = :paymentId and p.order.id = :orderId")
    Optional<Payment> findByIdAndOrder(@Param("paymentId") Long paymentId, @Param("orderId") Long orderId);
}
