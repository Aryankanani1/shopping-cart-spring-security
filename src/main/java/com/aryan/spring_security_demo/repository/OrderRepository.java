package com.aryan.spring_security_demo.repository;

import com.aryan.spring_security_demo.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order,Long> {

    // JOIN FETCH the order items up-front to avoid the N+1 that would otherwise
    // fire one extra query per order when the items are mapped to DTOs.
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.orderItems WHERE o.user.id = :userId")
    List<Order> findByUserId(@Param("userId") Long userId);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.orderItems WHERE o.Id = :orderId")
    Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);
}
