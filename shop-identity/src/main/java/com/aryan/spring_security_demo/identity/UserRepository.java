package com.aryan.spring_security_demo.identity;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User,Long> {
    boolean existsByEmail(String email);

    /**
     * Lock the user's row (SELECT ... FOR UPDATE) until the transaction ends. A
     * find-or-create for one user (their cart, a wishlist item) takes it first, so
     * a concurrent request for the same user waits, then finds what the first one
     * created instead of inserting a duplicate and failing a unique constraint.
     *
     * <p>It has to come before the "does it exist yet?" read: under MySQL's
     * REPEATABLE READ that read fixes the transaction's snapshot, and a row
     * committed after it would stay invisible.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> lockById(@Param("id") Long id);


    // Fetch the user together with roles so authentication/authorization code can
    // read authorities without a lazy load (works with open-in-view disabled).
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.roles WHERE u.email = :email")
    Optional<User> findByEmailWithRoles(@Param("email") String email);
}
