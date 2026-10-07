package com.aryan.spring_security_demo.cart;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CartRepository extends JpaRepository<Cart,Long> {


    Cart findByUserId(Long userId);

    /**
     * Load a cart and lock its row (SELECT ... FOR UPDATE) until the transaction
     * ends. Every change a customer makes to a cart (add, quantity, remove,
     * clear, checkout) takes this first, so changes to one cart run one at a
     * time: a second request waits, then sees what the first one did. Without
     * it, simultaneous adds raced on the cart row and either deadlocked or
     * failed the cart's version check.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.id = :id")
    Optional<Cart> findByIdForUpdate(@Param("id") Long id);

    /** The user's cart, locked like {@link #findByIdForUpdate} (checkout). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.user.id = :userId")
    Optional<Cart> findByUserIdForUpdate(@Param("userId") Long userId);
}
