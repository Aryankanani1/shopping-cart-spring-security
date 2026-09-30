package com.aryan.spring_security_demo.enums;

/**
 * What an in-app {@link com.aryan.spring_security_demo.model.Notification} is
 * about. All three come from the wishlist: a dated reminder the user set, or an
 * alert the scheduled scan raised because the saved product changed.
 */
public enum NotificationType {

    /** A reminder the user scheduled on a wishlist item came due. */
    WISHLIST_REMINDER,

    /** A wishlisted product's price fell below the lowest price already announced. */
    PRICE_DROP,

    /** A wishlisted product that had sold out is in stock again. */
    BACK_IN_STOCK
}
