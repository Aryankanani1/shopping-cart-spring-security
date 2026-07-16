package com.aryan.spring_security_demo.repository;

import com.aryan.spring_security_demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User,Long> {
    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.orders LEFT JOIN FETCH u.cart WHERE u.id = :id")
    Optional<User> findByIdWithDetails(@Param("id") Long id);

    User findByEmail(String email);
}
