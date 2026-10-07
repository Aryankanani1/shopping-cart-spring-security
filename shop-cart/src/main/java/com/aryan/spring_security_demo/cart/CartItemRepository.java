package com.aryan.spring_security_demo.cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem,Long> {

    void deleteAllByCartId(Long id);

    /** Every cart line holding a product, with its cart (whose total the caller recomputes). */
    @Query("select i from CartItem i join fetch i.cart where i.product.id = :productId")
    List<CartItem> findByProductIdWithCart(@Param("productId") Long productId);
}
