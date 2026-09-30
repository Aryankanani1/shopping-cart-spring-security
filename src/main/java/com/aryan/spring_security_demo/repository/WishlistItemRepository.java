package com.aryan.spring_security_demo.repository;

import com.aryan.spring_security_demo.model.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    /** A user's wishlist, newest first, with each product fetched in the same query. */
    @Query("select w from WishlistItem w join fetch w.product " +
            "where w.user.id = :userId order by w.createdAt desc, w.id desc")
    List<WishlistItem> findAllByUserIdWithProduct(@Param("userId") Long userId);

    Optional<WishlistItem> findByUserIdAndProductId(Long userId, Long productId);

    // --- alert scan (WishlistAlertService) ------------------------------------
    // Each query returns only the rows that need action on this tick, with the
    // product fetched, so the scan never walks the whole table in memory.

    @Query("select w from WishlistItem w join fetch w.product where w.remindAt <= :now")
    List<WishlistItem> findDueReminders(@Param("now") Instant now);

    @Query("select w from WishlistItem w join fetch w.product p " +
            "where w.alertsEnabled = true and p.price < w.alertPrice")
    List<WishlistItem> findPriceDrops();

    @Query("select w from WishlistItem w join fetch w.product p " +
            "where w.alertsEnabled = true and w.wasInStock = false and p.inventory > 0")
    List<WishlistItem> findRestocked();

    @Query("select w from WishlistItem w join fetch w.product p " +
            "where w.alertsEnabled = true and w.wasInStock = true and p.inventory <= 0")
    List<WishlistItem> findNewlySoldOut();
}
