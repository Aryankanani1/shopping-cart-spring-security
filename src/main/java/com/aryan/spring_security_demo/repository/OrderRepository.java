package com.aryan.spring_security_demo.repository;

import com.aryan.spring_security_demo.model.Order;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order,Long> {

    /**
     * Phase 1 of keyset order-history paging: the next page of order ids in
     * {@code (createdAt DESC, id DESC)} order, seeking past the client's cursor.
     *
     * <p>A collection {@code JOIN FETCH} is deliberately absent here: combining it
     * with {@code LIMIT} makes Hibernate paginate in memory (HHH000104), which
     * would defeat the whole point. So this query touches only the {@code Order}
     * root and is served by the {@code (user_id, created_at, id)} index.
     *
     * <p>The seek predicate is the expanded form of the row-value comparison
     * {@code (createdAt, id) < (:cursorCreatedAt, :cursorId)} — written out with
     * OR because JPQL has no portable tuple comparison. A {@code null} cursor means
     * "from the newest", i.e. the first slice.
     */
    @Query("""
            SELECT o.id AS id, o.createdAt AS createdAt
            FROM Order o
            WHERE o.user.id = :userId
              AND (:cursorCreatedAt IS NULL
                   OR o.createdAt < :cursorCreatedAt
                   OR (o.createdAt = :cursorCreatedAt AND o.id < :cursorId))
            ORDER BY o.createdAt DESC, o.id DESC
            """)
    List<OrderKeysetRow> findUserOrderKeyset(@Param("userId") Long userId,
                                             @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                             @Param("cursorId") Long cursorId,
                                             Pageable limit);

    /**
     * Phase 2: hydrate the page's orders with their items and products in one
     * {@code JOIN FETCH}. Result order is not guaranteed by {@code IN}, so the
     * caller re-imposes the keyset order (the id list is already correctly sorted).
     */
    @Query("SELECT DISTINCT o FROM Order o " +
            "LEFT JOIN FETCH o.orderItems oi LEFT JOIN FETCH oi.product " +
            "WHERE o.id IN :ids")
    List<Order> findWithItemsByIdIn(@Param("ids") List<Long> ids);

    @Query("SELECT DISTINCT o FROM Order o " +
            "LEFT JOIN FETCH o.orderItems oi LEFT JOIN FETCH oi.product " +
            "WHERE o.Id = :orderId")
    Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);
}
