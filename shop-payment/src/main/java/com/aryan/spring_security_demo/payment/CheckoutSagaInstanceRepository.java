package com.aryan.spring_security_demo.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CheckoutSagaInstanceRepository extends JpaRepository<CheckoutSagaInstance, Long> {

    Optional<CheckoutSagaInstance> findByOrderId(Long orderId);

    /**
     * The orders' sagas, row-locked, in order id order. {@link CheckoutSaga} changes a
     * saga only under this lock, taken after the locks on the order, its payments and
     * its refunds, so it can't be part of a deadlock with them.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CheckoutSagaInstance s where s.orderId in :orderIds order by s.orderId")
    List<CheckoutSagaInstance> findByOrderIdsForUpdate(@Param("orderIds") Collection<Long> orderIds);
}
