package com.aryan.spring_security_demo.repository;

import com.aryan.spring_security_demo.model.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRepository extends JpaRepository<Cart,Long> {



}
