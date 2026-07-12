package com.aryan.spring_security_demo.repository;

import com.aryan.spring_security_demo.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order,Long> {
}
