package com.aryan.spring_security_demo.wishlist;


import java.time.Instant;
import java.util.List;

/**
 * The authenticated caller's own wishlist. No method takes a user id — every
 * operation applies to the current user, so there is no id to guess (no IDOR).
 * Items are addressed by product id, since a product appears at most once.
 */
public interface WishlistServiceInterface {

    /** Result of an idempotent add: the item, and whether this call created it. */
    record AddResult(WishlistItemDto item, boolean created) {}

    List<WishlistItemDto> getMyWishlist();

    /** Save a product. Adding one that is already saved is a no-op, not an error. */
    AddResult add(Long productId);

    void remove(Long productId);

    WishlistItemDto setReminder(Long productId, Instant remindAt);

    WishlistItemDto clearReminder(Long productId);

    /** Turn price/stock alerts on or off. Turning them on re-baselines to the current price and stock. */
    WishlistItemDto setAlerts(Long productId, boolean enabled);
}
