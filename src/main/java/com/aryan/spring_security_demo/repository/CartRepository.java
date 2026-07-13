package com.aryan.spring_security_demo.repository;

import com.aryan.spring_security_demo.model.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CartRepository extends JpaRepository<Cart,Long> {


    Cart findByUserId(Long userId);
}
