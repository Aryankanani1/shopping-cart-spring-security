package com.aryan.spring_security_demo.wishlist;

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
    // Each query returns the ids of only the rows that need action on this tick,
    // so the scan never walks the whole table in memory. Each id is then handled
    // in a transaction of its own, which loads the row with findByIdWithProduct
    // and checks the same condition again on it (WishlistItem#isReminderDue and
    // the methods beside it), as the row may have changed since the scan.

    @Query("select w.id from WishlistItem w where w.remindAt <= :now")
    List<Long> findDueReminderIds(@Param("now") Instant now);

    @Query("select w.id from WishlistItem w join w.product p " +
            "where w.alertsEnabled = true and p.price < w.alertPrice")
    List<Long> findPriceDropIds();

    @Query("select w.id from WishlistItem w join w.product p " +
            "where w.alertsEnabled = true and w.wasInStock = false and p.inventory > 0")
    List<Long> findRestockedIds();

    @Query("select w.id from WishlistItem w join w.product p " +
            "where w.alertsEnabled = true and w.wasInStock = true and p.inventory <= 0")
    List<Long> findNewlySoldOutIds();

    @Query("select w from WishlistItem w join fetch w.product where w.id = :id")
    Optional<WishlistItem> findByIdWithProduct(@Param("id") Long id);
}
