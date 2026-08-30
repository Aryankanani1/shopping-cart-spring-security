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

    // JOIN FETCH the order items AND each item's product up-front. Mapping to
    // OrderItemDto reads product.name/brand, so without fetching the product we
    // get an N+1: one query per line item. Only one collection (orderItems) is
    // fetched, so this stays a single, safe round trip.
    @Query("SELECT DISTINCT o FROM Order o " +
            "LEFT JOIN FETCH o.orderItems oi LEFT JOIN FETCH oi.product " +
            "WHERE o.user.id = :userId")
    List<Order> findByUserId(@Param("userId") Long userId);

    @Query("SELECT DISTINCT o FROM Order o " +
            "LEFT JOIN FETCH o.orderItems oi LEFT JOIN FETCH oi.product " +
            "WHERE o.Id = :orderId")
    Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);
}
