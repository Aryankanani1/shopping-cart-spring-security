package com.aryan.spring_security_demo.repository;
import com.aryan.spring_security_demo.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem,Long> {

    void deleteAllByCartId(Long id);
}
